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
import org.voltdb.VoltType;

import java.net.UnknownHostException;
import java.util.Calendar;

public class GetTopDests extends VoltProcedure {
    final SQLStmt getTopSecond = new SQLStmt(
            "SELECT dest, second_ts AS ts, count_values AS counts " +
            "FROM dests_by_second " +
            "WHERE second_ts = ? " +
            "ORDER BY count_values DESC, dest LIMIT ?;"
    );

    final SQLStmt getTopMinute = new SQLStmt(
            "SELECT dest, minute_ts AS ts, count_values AS counts " +
            "FROM dests_by_minute " +
            "WHERE minute_ts = ? " +
            "ORDER BY count_values DESC, dest LIMIT ?;"
    );

    public VoltTable[] run(byte type, int n) throws UnknownHostException
    {
        Calendar cal = Calendar.getInstance(); // locale-specific
        cal.setTime(getTransactionTime());
        cal.set(Calendar.MILLISECOND, 0);
        SQLStmt stmt;

        switch (type) {
        case 0:
            // second
            stmt = getTopSecond;
            break;
        case 1:
            // minute
            cal.set(Calendar.SECOND, 0);
            stmt = getTopMinute;
            break;
        default:
            throw new VoltAbortException("Unknown type " + type);
        }

        voltQueueSQL(stmt, cal.getTimeInMillis() * 1000, n);
        return voltExecuteSQL(true);
    }
}
