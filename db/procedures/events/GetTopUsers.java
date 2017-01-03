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

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class GetTopUsers extends VoltProcedure {
    final SQLStmt getTopSecond = new SQLStmt(
            "SELECT src As Sources, SUM(count_values) AS counts " +
            "FROM events_by_second " +
            "WHERE TO_TIMESTAMP(SECOND, SINCE_EPOCH(SECOND, NOW) - ?) <= second_ts " +
            "GROUP BY src " +
            "ORDER BY counts DESC, src LIMIT ?;"
    );

    public VoltTable run(int seconds, int n)
    {
        voltQueueSQL(getTopSecond, seconds, n);
        return voltExecuteSQL(true)[0];
    }
}
