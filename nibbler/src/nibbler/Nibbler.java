/* This file is part of VoltDB.
 * Copyright (C) 2008-2018 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package nibbler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.voltdb.CLIConfig;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientStatusListenerExt;
import org.voltdb.types.TimestampType;

/**
 * <p>
 * Nibbler that nibbles/deletes data periodically from a table.</p>
 *
 * <ol>
 * <li>Continuously delete tuples that are either too old or over a table size limit.</li>
 * </ol>
 *
 */
public class Nibbler {

    // handy, rather than typing this out several times
    static final String HORIZONTAL_RULE =
            "----------" + "----------" + "----------" + "----------" +
            "----------" + "----------" + "----------" + "----------" + "\n";

    private final AtomicLong deletesSinceLastChecked = new AtomicLong(0);

    // these values are updated by the UpdatePartitionData class each time it is run
    private final AtomicReference<Map<Long, PartitionInfo>> partitionData =
            new AtomicReference<Map<Long, PartitionInfo>>(new HashMap<Long, PartitionInfo>());
    private final AtomicLong targetRowsPerPartition = new AtomicLong(Long.MAX_VALUE);

    /**
     * Uses included {@link CLIConfig} class to
     * declaratively state command line options with defaults
     * and validation.
     */
    static class NibblerConfig extends CLIConfig {
        @Option(desc = "Interval for performance feedback, in seconds.")
        long displayinterval = 5;

        @Option(desc = "Comma separated list of the form server[:port] to connect to.")
        String servers = "localhost";

        @Option(desc = "User name for connection.")
        String user = "";

        @Option(desc = "Password for connection.")
        String password = "";

        @Option(desc = "Global maximum row target. Zero if using history target.")
        long maxrows = 0;

        @Option(desc = "Global maximum history targert. Zero if using row count target.")
        long historyseconds = 30;

        @Option(desc = "Maximum number of rows to delete in one transaction.")
        long deletechunksize = 100;

        @Option(desc = "Time to pause between deletes when there was nothing to delete at last check.")
        long deleteyieldtime = 100;

        @Override
        public void validate() {
            if (displayinterval <= 0) {
                exitWithMessageAndUsage("displayinterval must be > 0");
            }
            if (maxrows < 0) exitWithMessageAndUsage("maxrows must be >= 0");
            if (historyseconds < 0) exitWithMessageAndUsage("historyseconds must be >= 0");
            if (deletechunksize <= 0) exitWithMessageAndUsage("deletechunksize must be > 0");
            if (deleteyieldtime < 0) exitWithMessageAndUsage("deleteyieldtime must be >= 0");
            if ((historyseconds > 0) && (maxrows > 0)) {
                exitWithMessageAndUsage("only one of historyseconds or maxrows can be nonzero");
            }
        }
    }

    /////
    // PACKAGE VISIBLE SHARED STATE ACCESS BELOW
    /////

    static final class PartitionInfo {
        Long partitionKey;
        long tupleCount;
    }

    // Reference to the database connection we will use
    final Client client;

    // validated command line configuration
    final NibblerConfig config;

    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    final long startTS = System.currentTimeMillis();

    final ContinuousDeleter deleter;
    final PartitionDataTracker partitionTracker;
    final Reporter reporter;

    Map<Long, PartitionInfo> getPartitionData() {
        return partitionData.get();
    }

    long getTargetRowsPerPartition() {
        return targetRowsPerPartition.get();
    }

    TimestampType getTargetDate() {
        long targetTimestampMillis = System.currentTimeMillis() - (config.historyseconds * 1000);
        return new TimestampType(targetTimestampMillis * 1000);
    }

    void updatePartitionInfo(Map<Long, PartitionInfo> partitionData) {
        this.partitionData.set(partitionData);
        targetRowsPerPartition.set(config.maxrows / partitionData.size());
    }

    void addToDeletedTuples(long count) {
        deletesSinceLastChecked.addAndGet(count);
    }

    long getDeletesSinceLastChecked() {
        return deletesSinceLastChecked.getAndSet(0);
    }

    void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        try {
            // block until all outstanding txns return
            client.drain();
            // close down the client connections
            client.close();
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Provides a callback to be notified on node failure.
     * This example only logs the event.
     */
    class StatusListener extends ClientStatusListenerExt {
        @Override
        public void connectionLost(String hostname, int port, int connectionsLeft, DisconnectCause cause) {
        }
    }

    /**
     * Constructor for benchmark instance.
     * Configures VoltDB client and prints configuration.
     *
     * @param config Parsed & validated CLI options.
     */
    public Nibbler(NibblerConfig config) {
        this.config = config;

        System.out.print(HORIZONTAL_RULE);
        System.out.println(" Command Line Configuration");
        System.out.println(HORIZONTAL_RULE);

        System.out.println(config.getConfigDumpString());

        System.out.print(HORIZONTAL_RULE);
        System.out.println(" Setup & Initialization");
        System.out.println(HORIZONTAL_RULE);

        ClientConfig clientConfig = new ClientConfig(config.user, config.password, new StatusListener());

        Client insertsClient = ClientFactory.createClient(clientConfig);
        client = ClientFactory.createClient(clientConfig);

        // connect to one or more servers, loop until success
        try {
            connect(insertsClient, config.servers);
            connect(client, config.servers);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        partitionTracker = new PartitionDataTracker(this);
        deleter = new ContinuousDeleter(this);
        reporter = new Reporter(this);

    }

    /**
     * Connect to a single server with retry. Limited exponential backoff.
     * No timeout. This will run until the process is killed if it's not
     * able to connect.
     *
     * @param server hostname:port or just hostname (hostname can be ip).
     */
    static void connectToOneServerWithRetry(final Client client, String server) {
        int sleep = 1000;
        while (true) {
            try {
                client.createConnection(server);
                break;
            }
            catch (Exception e) {
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep / 1000);
                try { Thread.sleep(sleep); } catch (Exception interruted) {}
                if (sleep < 8000) sleep += sleep;
            }
        }
        System.out.printf("Connected to VoltDB node at: %s.\n", server);
    }

    /**
     * Connect to a set of servers in parallel. Each will retry until
     * connection. This call will block until all have connected.
     *
     * @param servers A comma separated list of servers using the hostname:port
     * syntax (where :port is optional).
     * @throws InterruptedException if anything bad happens with the threads.
     */
    static void connect(final Client client, String servers) throws InterruptedException {
        System.out.println("Connecting to VoltDB...");

        String[] serverArray = servers.split(",");
        final CountDownLatch connections = new CountDownLatch(serverArray.length);

        // use a new thread to connect to each server
        for (final String server : serverArray) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToOneServerWithRetry(client, server);
                    connections.countDown();
                }
            }).start();
        }
        // block until all have connected
        connections.await();
    }

    /**
     * Core benchmark code.
     * Connect. Initialize. Run the loop. Cleanup. Print Results.
     *
     */
    public void run() {
        // Update the partition key set, row counts and redundancy level once per second
        scheduler.scheduleWithFixedDelay(partitionTracker,
                                               1,
                                               1,
                                               TimeUnit.SECONDS);

        // Delete data as often as need be
        //  -- This will resubmit itself at varying rates according to insert load
        scheduler.execute(deleter);
        System.out.print(HORIZONTAL_RULE);
        System.out.println(" Starting Processing");
        System.out.println(HORIZONTAL_RULE);
        // Print periodic stats/analysis to the console
        scheduler.scheduleWithFixedDelay(reporter,
                config.displayinterval,
                config.displayinterval,
                TimeUnit.SECONDS);

    }

    /**
     * Main routine creates an instance of this app and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link WindowingConfig}
     */
    public static void main(String[] args) throws Exception {
        // create a configuration from the arguments
        NibblerConfig config = new NibblerConfig();
        config.parse(Nibbler.class.getName(), args);

        Nibbler app = new Nibbler(config);
        app.run();
    }
}
