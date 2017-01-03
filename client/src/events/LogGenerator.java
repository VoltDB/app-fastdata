/*
 * This file is part of VoltDB.
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package events;

import au.com.bytecode.opencsv_voltpatches.CSVReader;
import org.voltcore.utils.Pair;
import org.voltdb.CLIConfig;
import org.voltdb.client.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class LogGenerator {
    private final Config config;
    private final Client client;

    private final List<Pair<Integer, Integer>> ipRanges = new ArrayList<>();
    private final List<String> urls = new ArrayList<>();
    private final List<String> agents = new ArrayList<>();

    private final Random rand;
    private final ClientStatsContext periodicStatsContext;
    private Timer timer;
    private long benchmarkStartTS;

    static class Config extends CLIConfig {
        @Option(desc = "Interval for performance feedback, in seconds.")
        long displayinterval = 5;

        @Option(desc = "Duration, in seconds.")
        int duration = 1800;

        @Option(desc = "Comma separated list of the form server[:port] to connect to.")
        String servers = "localhost";

        @Option(desc = "Maximum TPS rate for benchmark.")
        int ratelimit = Integer.MAX_VALUE;

        @Option(desc = "User name for connection.")
        String user = "";

        @Option(desc = "Password for connection.")
        String password = "";

        @Override
        public void validate()
        {
            if (duration <= 0) exitWithMessageAndUsage("duration must be > 0");
            if (ratelimit <= 0) exitWithMessageAndUsage("ratelimit must be > 0");
        }
    }

    class EventCallback implements ProcedureCallback {
        @Override
        public void clientCallback(ClientResponse response) throws Exception
        {
            if (response.getStatus() != ClientResponse.SUCCESS) {
                System.err.println("Error: " + response.getStatusString());
            }
        }
    }

    private void loadIpRanges() throws IOException
    {
        final CSVReader reader = new CSVReader(new FileReader("data/ips.csv"));
        String[] line;
        while ((line = reader.readNext()) != null) {
            ipRanges.add(Pair.of(Utils.iptoi(line[0]), Utils.iptoi(line[1])));
        }
        reader.close();
    }

    private int nextIp()
    {
        final Pair<Integer, Integer> range = ipRanges.get(rand.nextInt(ipRanges.size()));
        return range.getFirst() + rand.nextInt(range.getSecond() - range.getFirst() + 1);
    }

    private void loadUrls() throws IOException, InterruptedException
    {
        final BufferedReader reader = new BufferedReader(new FileReader("data/urls.txt"));
        String line;
        int i = 0;
        while ((line = reader.readLine()) != null) {
            urls.add(line);
            client.callProcedure(new NullCallback(), "DESTS.insert", i++, line);
        }
        reader.close();

        client.callProcedure(new NullCallback(), "DESTS.insert", i, "");
        client.drain();
    }

    private void loadAgents() throws IOException, InterruptedException
    {
        final BufferedReader reader = new BufferedReader(new FileReader("data/agents.txt"));
        String line;
        int i = 0;
        while ((line = reader.readLine()) != null) {
            agents.add(line);
            client.callProcedure(new NullCallback(), "AGENTS.insert", i++, line);
        }
        reader.close();
        client.drain();
    }

    public LogGenerator(Config config) throws IOException {
        this.config = config;

        ClientConfig clientConfig = new ClientConfig(config.user, config.password);
        clientConfig.setMaxTransactionsPerSecond(config.ratelimit);
        clientConfig.setReconnectOnConnectionLoss(true);

        client = ClientFactory.createClient(clientConfig);

        periodicStatsContext = client.createStatsContext();

        rand = new Random();
    }

    /**
     * Connect to a single server with retry. Limited exponential backoff.
     * No timeout. This will run until the process is killed if it's not
     * able to connect.
     *
     * @param server hostname:port or just hostname (hostname can be ip).
     */
    void connectToOneServerWithRetry(String server) {
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
    void connect(String servers) throws InterruptedException {
        System.out.println("Connecting to VoltDB...");

        String[] serverArray = servers.split(",");
        final CountDownLatch connections = new CountDownLatch(serverArray.length);

        // use a new thread to connect to each server
        for (final String server : serverArray) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToOneServerWithRetry(server);
                    connections.countDown();
                }
            }).start();
        }
        // block until all have connected
        connections.await();
    }

    /**
     * Create a Timer task to display performance data on the Vote procedure
     * It calls printStatistics() every displayInterval seconds
     */
    public void schedulePeriodicStats() {
        timer = new Timer();
        TimerTask statsPrinting = new TimerTask() {
            @Override
            public void run() { printStatistics(); }
        };
        timer.scheduleAtFixedRate(statsPrinting,
                config.displayinterval * 1000,
                config.displayinterval * 1000);
    }

    /**
     * Prints a one line update on performance that can be printed
     * periodically during a benchmark.
     */
    public synchronized void printStatistics() {
        ClientStats stats = periodicStatsContext.fetchAndResetBaseline().getStats();
        long time = Math.round((stats.getEndTimestamp() - benchmarkStartTS) / 1000.0);

        System.out.printf("%02d:%02d:%02d ", time / 3600, (time / 60) % 60, time % 60);
        System.out.printf("Throughput %d/s, ", stats.getTxnThroughput());
        System.out.printf("Aborts/Failures %d/%d",
                stats.getInvocationAborts(), stats.getInvocationErrors());
        System.out.printf("\n");
    }

    public void run() throws IOException, InterruptedException
    {
        connect(config.servers);

        // Load US IPs from the data file
        loadIpRanges();
        // Load user agent strings
        loadAgents();
        // Load the URLs
        loadUrls();

        benchmarkStartTS = System.currentTimeMillis();
        final long endTs = benchmarkStartTS + config.duration * 1000;
        final EventCallback callback = new EventCallback();
        schedulePeriodicStats();

        while (System.currentTimeMillis() < endTs) {
            client.callProcedure(callback,
                    "NewEvent",
                    nextIp(),
                    urls.get(rand.nextInt(urls.size())),
                    "GET",
                    System.currentTimeMillis() * 1000, // microseconds
                    Math.abs(rand.nextInt()),
                    rand.nextBoolean() ? "" : urls.get(rand.nextInt(urls.size())),
                    agents.get(rand.nextInt(agents.size())));
        }

        timer.cancel();
        client.drain();
        client.close();
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {
        final Config config = new Config();
        config.parse(LogGenerator.class.getName(), args);

        final LogGenerator generator = new LogGenerator(config);
        generator.run();
    }
}
