package events;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;

import java.net.UnknownHostException;

public class GetTopSources extends VoltProcedure {
    final SQLStmt getTopSource = new SQLStmt(
    		"SELECT top ? src as Sources, total_visits as Counts  FROM events_export_view ORDER BY total_visits DESC;"
    );

    public VoltTable run(int n) throws UnknownHostException
    {
        voltQueueSQL(getTopSource, n);
        final VoltTable result = voltExecuteSQL(true)[0];
        final VoltTable.ColumnInfo[] schema = result.getTableSchema();
        schema[0] = new VoltTable.ColumnInfo("Sources", VoltType.STRING);
        final VoltTable processed = new VoltTable(schema);
        while (result.advanceRow()) {
            processed.addRow(Utils.itoip((int) result.getLong(0)),
                    result.getLong(1));
        }

        return processed;
    }
}