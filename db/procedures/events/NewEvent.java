/*
 * This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
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
    final SQLStmt getSecondCount = new SQLStmt(
            "SELECT COUNT(*) FROM events WHERE src = ? AND (SINCE_EPOCH(MILLIS, NOW) - SINCE_EPOCH(MILLIS, ts)) <= 1000;");

    final SQLStmt checkAttackers = new SQLStmt(
            "SELECT COUNT(*) FROM attackers WHERE src = ?;");

    final SQLStmt insertEvent = new SQLStmt(
            "INSERT INTO events (src, dest, method, ts, size, referral, agent) VALUES (?, ?, ?, ?, ?, ?, ?);");

    final SQLStmt exportEvent = new SQLStmt(
            "INSERT INTO events_export (src, dest, method, ts, size, referral, agent) VALUES (?, ?, ?, ?, ?, ?, ?);");

    final SQLStmt checkAlert = new SQLStmt(
            "SELECT * FROM alerts WHERE src = ? AND ts = ?;");

    final SQLStmt insertAlert = new SQLStmt(
            "INSERT INTO alerts (src, ts) VALUES (?, ?);");

    public long run(int src, String dest, String method, TimestampType ts, long size, String referral, String agent,
                    long maxEventsPerSec)
            throws UnknownHostException
    {
        boolean isAttack = false;
        long eventsInPastSec = 0;

        voltQueueSQL(getSecondCount, EXPECT_ZERO_OR_ONE_ROW, src);
        voltQueueSQL(checkAttackers, EXPECT_SCALAR_LONG, src);
        voltQueueSQL(insertEvent, EXPECT_SCALAR_LONG, src, dest, method, ts, size, referral, agent);
        voltQueueSQL(exportEvent, EXPECT_SCALAR_LONG, Utils.itoip(src), dest, method, ts, size, referral, agent);
        final VoltTable[] batchResult = voltExecuteSQL();
        final VoltTable countResult = batchResult[0];
        if (countResult.getRowCount() == 1) {
            eventsInPastSec = countResult.asScalarLong();
        }

        if (batchResult[1].asScalarLong() == 1 || eventsInPastSec > maxEventsPerSec) {
            // It's a DDOS attack
            isAttack = true;

            // Send alert
            voltQueueSQL(checkAlert, EXPECT_ZERO_OR_ONE_ROW, src, ts);
            if (voltExecuteSQL()[0].getRowCount() == 0) {
                voltQueueSQL(insertAlert, EXPECT_SCALAR_LONG, src, ts);
                voltExecuteSQL(true);
            }
        }

        // Return 1 if it's an attack, otherwise 0
        return isAttack ? 1 : 0;
    }
}
