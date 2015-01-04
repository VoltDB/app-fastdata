/* This file is part of VoltDB.
 * Copyright (C) 2008-2015 VoltDB Inc.
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

/**
 * Periodically print a report of insert rates, latencies and failures,
 * as well as average values of the 'val' column in the 'timedata' table
 * over several moving windows. The delay between report printing is
 * specified by the user in the configuration.
 *
 */
public class Reporter implements Runnable {

    final Nibbler app;

    Reporter(Nibbler app) {
        this.app = app;
    }

    @Override
    public void run() {
        // Lock protects other (well-behaved) printing from being interspersed with
        // this report printing.
        synchronized(app) {
            long now = System.currentTimeMillis();
            long time = Math.round((now - app.startTS) / 1000.0);

            // Print out how long the processing has been running
            System.out.printf("%02d:%02d:%02d Report:\n", time / 3600, (time / 60) % 60, time % 60);

            // Print out an update on how many tuples have been deleted.
            System.out.printf("  Deleted %d tuples since last report\n", app.getDeletesSinceLastChecked());

            //
            // FAILURE REPORTING FOR PERIODIC OPERATIONS
            //
            long partitionTrackerFailures = app.partitionTracker.failureCount.getAndSet(0);
            if (partitionTrackerFailures > 0) {
                System.out.printf("  Partition Tracker failed %d times since last report.\n",
                                  partitionTrackerFailures);
            }
            long continuousDeleterFailures = app.deleter.failureCount.getAndSet(0);
            if (continuousDeleterFailures > 0) {
                System.out.printf("  Continuous Deleter failed %d times since last report.\n",
                                  continuousDeleterFailures);
            }

            System.out.println();
            System.out.flush();
        }
    }
}
