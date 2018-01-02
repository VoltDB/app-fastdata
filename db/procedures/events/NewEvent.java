/*
 * This file is part of VoltDB.
 * Copyright (C) 2008-2018 VoltDB Inc.
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

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.types.TimestampType;

import java.net.UnknownHostException;

public class NewEvent extends VoltProcedure {
    final SQLStmt getCluster = new SQLStmt(
            "SELECT id, POWER(? - src, 2) + POWER(? - dest, 2) + POWER(? - referral, 2) + POWER(? - agent, 2) AS score FROM clusters GROUP BY id ORDER BY score LIMIT 1;");

    final SQLStmt getUrlId = new SQLStmt(
            "SELECT id FROM dests WHERE url = ?;"
    );

    final SQLStmt getAgentId = new SQLStmt(
            "SELECT id FROM agents WHERE name = ?;"
    );

    final SQLStmt checkSession = new SQLStmt(
            "SELECT last_ts FROM events_sessions WHERE src = ? AND dest = ? AND TO_TIMESTAMP(SECOND, SINCE_EPOCH(SECOND, ?) - 30) <= last_ts;"
    );

    final SQLStmt insertEvent = new SQLStmt(
            "INSERT INTO events (src, dest, method, ts, size, referral, agent, cluster) VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

    final SQLStmt exportEvent = new SQLStmt(
            "INSERT INTO events_export (src, dest, method, ts, size, referral, agent) VALUES (?, ?, ?, ?, ?, ?, ?);");

    public long run(int src, String dest, String method, TimestampType ts, long size, String referral, String agent)
            throws UnknownHostException
    {
        voltQueueSQL(getUrlId, dest);
        voltQueueSQL(getUrlId, referral);
        voltQueueSQL(getAgentId, agent);
        final VoltTable[] batchResult = voltExecuteSQL();

        final int destId = (int) batchResult[0].asScalarLong();
        final int referralId = (int) batchResult[1].asScalarLong();
        final int agentId = (int) batchResult[2].asScalarLong();

        voltQueueSQL(getCluster, EXPECT_ZERO_OR_ONE_ROW, src, destId, referralId, agentId);
        voltQueueSQL(checkSession, src, destId, ts);
        final VoltTable[] secondResult = voltExecuteSQL();
        final VoltTable clusterResult = secondResult[0];
        Integer cluster = null;
        if (clusterResult.advanceRow()) {
            cluster = (int) clusterResult.getLong("id");
        }
        final boolean hasExported = secondResult[1].advanceRow();

        voltQueueSQL(insertEvent, EXPECT_SCALAR_LONG, src, destId, method, ts, size, referralId, agentId, cluster);
        if (!hasExported) {
            voltQueueSQL(exportEvent, EXPECT_SCALAR_LONG, src, destId, method, ts, size, referralId, agentId);
        }
        voltExecuteSQL(true);

        return cluster == null ? -1 : cluster;
    }
}
