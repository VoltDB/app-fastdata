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

public class GetTopSources extends VoltProcedure {
    final SQLStmt getTopSource = new SQLStmt(
        "SELECT src as Sources, total_visits as Counts  FROM events_by_src_view ORDER BY total_visits DESC LIMIT ?;"
    );

    public VoltTable run(int n)  
    {
        voltQueueSQL(getTopSource, n);
        return voltExecuteSQL(true)[0];
    }
}