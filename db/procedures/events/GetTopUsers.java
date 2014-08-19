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

public class GetTopUsers extends VoltProcedure {
    final SQLStmt getTopSecond = new SQLStmt(
            "SELECT src, second_ts AS ts, count_values AS counts " +
            "FROM events_by_second " +
            "WHERE second_ts = ? " +
            "ORDER BY count_values DESC, src LIMIT ?;"
    );

    final SQLStmt getTopMinute = new SQLStmt(
            "SELECT src, minute_ts AS ts, count_values AS counts " +
            "FROM events_by_minute " +
            "WHERE minute_ts = ? " +
            "ORDER BY count_values DESC, src LIMIT ?;"
    );

    public VoltTable run(byte type, int n) throws UnknownHostException
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
        final VoltTable result = voltExecuteSQL(true)[0];
        final VoltTable.ColumnInfo[] schema = result.getTableSchema();
        schema[0] = new VoltTable.ColumnInfo("SRC", VoltType.STRING);
        final VoltTable processed = new VoltTable(schema);
        while (result.advanceRow()) {
            processed.addRow(Utils.itoip((int) result.getLong(0)),
                    result.getTimestampAsLong(1),
                    result.getLong(2));
        }

        return processed;
    }
}
