// schedule refresh functions to run periodically
function RefreshData(){
    con.BeginExecute('GetTopUsers',
                     [60, 10],
                     function(response) {
                         DrawTable(response,'#table_top_users');
                     }
                    );

    con.BeginExecute('GetTopDests',
                     [60, 10],
                     function(response) {
                         DrawTable(response,'#table_top_dests');
                     }
                    );

    con.BeginExecute('GetEventsByCluster',
                     [60],
                     function(response) {
                         DrawBarChart(response,'#events_chart');
                     }
                    );
     con.BeginExecute('GetTopSources',
                     [10],
                     function(response) {
                         DrawTable(response,'#table_top_sources');
                     }
                    );
     con.BeginExecute('GetTopSrcDests',
                     [10],
                     function(response) {
                         DrawTable(response,'#table_top_src_dests');
                     }
                    );
}

function RefreshStats() {
    con.BeginExecute('@Statistics',
                     ['PROCEDUREPROFILE','0'],
                     function(response) {
                         DrawTPSChart(response,'#tps_chart');
                     }
                    );

}

function DrawBarChart(response, placeholder) {
    var tables = response.results;
    var t0 = tables[0];
    var events = [];
    var tickVals = [];

    for(var r=0; r<t0.data.length; r++){ // for each row
        var cluster = t0.data[r][0];
        var count = t0.data[r][1];
        events.push([cluster, count]);
        tickVals.push(cluster);
    }
    var eventline = { data: events };

    var options = {
        series: {
            bars: {
                show: true,
                align: "center"
            },
	        points: { show: false }
        },
        xaxis: {
            ticks: tickVals,
            tickDecimals: 0
        }
    };

    $.plot($(placeholder), [eventline], options);
}

function DrawTable(response, tableName) {
    try {
        var tables = response.results;
        var hmt = tables[0];
        var colcount = hmt.schema.length;

        // the first time, initialize the table head
        if ($(tableName+' thead tr').length == 0) {
            var theadhtml = '<tr>';
            for (var i=0; i<colcount; i++) {
                theadhtml += '<th>' + hmt.schema[i].name + '</th>';
            }
            $(tableName).append('<thead></thead>');
            $(tableName).append('<tbody></tbody>');
            $(tableName).children('thead').html(theadhtml);
        }

        var tbodyhtml;
        for(var r=0;r<hmt.data.length;r++){ // for each row
            tbodyhtml += '<tr>';
            for (var c=0;c<colcount;c++) { // for each column
                var f = hmt.data[r][c];

                // if type is DECIMAL
                if (hmt.schema[c].type == 22 || hmt.schema[c].type == 8) {
                    f = formatDecimal(f);
                }

                if (hmt.schema[c].type == 11) {
                    f = formatDateAsTime(f);
                }
                tbodyhtml += '<td>' + f + '</td>';
            }
            tbodyhtml += '</tr>';
        }
        $(tableName).children('tbody').html(tbodyhtml);

    } catch(x) {}
}
