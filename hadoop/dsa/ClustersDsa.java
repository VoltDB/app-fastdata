/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import org.voltdb.client.exampleutils.AppHelper;
import org.voltdb.client.*;
import com.google_voltpatches.common.base.Splitter;

import java.io.IOException;
import java.util.List;

public class ClustersDsa {

    enum Action {
        LOAD("LoadDsa"), 
        CLEAR("TruncateDsa");
        
        private String procedure;
        
        Action(String procedure) {
            this.procedure = procedure;
        }

        public String procedure() {
            return procedure;
        }
    }

    private static final Splitter SPLITTER = 
        Splitter.on(',').trimResults().omitEmptyStrings();
    
    public static void main(String [] args) throws Exception {

        AppHelper apph = new AppHelper(ClustersDsa.class.getCanonicalName())
            .add("servers","HOST,...", "List of VoltDB servers to connect to.", "localhost")
            .add("action","load|clear", "Perform specified action: LOAD or CLEAR", "CLEAR")
            .setArguments(args);

        Action action = null;
        try {
            action = Action.valueOf(apph.stringValue("action").trim().toUpperCase());
        } catch (IllegalArgumentException notAValidAction) {
            apph.validate("action", action != null);
        }

        List<String> servers = SPLITTER.splitToList(apph.stringValue("servers"));
        
        ClientConfig cc = new ClientConfig();
        Client client = ClientFactory.createClient(cc);
        
        int failCount = 0, attemptCount = 0;
        for (String hostName: servers) try {
            attemptCount += 1;
            client.createConnection(hostName);
        } catch (IOException e) {
            failCount += 1;
            System.err.println("Failed to connect to host " + hostName);
            e.printStackTrace();
        }

        if (failCount == attemptCount) {
            throw new IOException("Failed to connect to hosts " + servers);
        }

        ClientResponse cr = client.callProcedure(action.procedure());
        if (cr.getStatus() != ClientResponse.SUCCESS) {
            throw new IOException("unable to " + action + " the CLUSTERS_DSA table: " + cr.getStatusString());
        }

        client.close();
    }
}