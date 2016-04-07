-- replicated table for cluster centers, will be populated periodically by OLAP
-- systems
CREATE TABLE clusters
(
  id       integer        NOT NULL,
  src      integer        NOT NULL,
  dest     integer        NOT NULL,
  referral integer        NOT NULL,
  agent    integer        NOT NULL,
  CONSTRAINT clusters_pkey PRIMARY KEY (id)
);

CREATE TABLE clusters_dsa
(
  id       integer        NOT NULL,
  src      integer        NOT NULL,
  dest     integer        NOT NULL,
  referral integer        NOT NULL,
  agent    integer        NOT NULL,
  CONSTRAINT clusters_dsa_pkey PRIMARY KEY (id)
);

CREATE PROCEDURE events.TruncateDsa AS ###
    truncateSTMT = new SQLStmt('DELETE FROM clusters_dsa;')
    transactOn = {
        voltQueueSQL(truncateSTMT)
        voltExecuteSQL(true)
    }
### LANGUAGE GROOVY;

CREATE PROCEDURE events.LoadDsa AS ###
    truncateSTMT = new SQLStmt('DELETE FROM clusters;')
    loadSTMT = new SQLStmt('''
        INSERT INTO CLUSTERS (id,src,dest,referral,agent)
        SELECT id,src,dest,referral,agent FROM clusters_dsa
         ORDER BY id;
    ''')
    transactOn = {
        voltQueueSQL(truncateSTMT)
        voltQueueSQL(loadSTMT)
        voltExecuteSQL(true)
    }
### LANGUAGE GROOVY;

-- replicated table for destinations
CREATE TABLE dests
(
  id       integer      NOT NULL,
  url      varchar(127) UNIQUE, -- referral can be null
  CONSTRAINT dests_pkey PRIMARY KEY (id)
);

-- replicated table for user agents
CREATE TABLE agents
(
  id       integer             NOT NULL,
  name     varchar(142) UNIQUE NOT NULL,
  CONSTRAINT agents_pkey PRIMARY KEY (id)
);

-- events
CREATE TABLE events
(
  src      integer        NOT NULL,
  dest     integer        NOT NULL,
  method   varchar(3)     NOT NULL,
  ts       timestamp      NOT NULL,
  size     bigint         NOT NULL,
  referral integer        NOT NULL,
  agent    integer        NOT NULL,
  cluster  integer
);
PARTITION TABLE events ON COLUMN src;

CREATE INDEX event_ts_index ON events (ts);
CREATE INDEX event_src_dest_index ON events (src, dest);

CREATE STREAM events_export
EXPORT TO TARGET hadoop
PARTITION ON COLUMN src
(
  src      integer        NOT NULL,
  dest     integer        NOT NULL,
  method   varchar(3)     NOT NULL,
  ts       timestamp      NOT NULL,
  size     bigint         NOT NULL,
  referral integer        NOT NULL,
  agent    integer        NOT NULL
);

--Events exported view
CREATE VIEW events_by_src_view
    ( src, total_visits)
   AS SELECT src, COUNT(*)
       FROM events_export GROUP BY src;

CREATE VIEW events_by_src_dest_view
    ( src, dest, counts)
   AS SELECT src, dest, COUNT(*)
       FROM events_export
       GROUP BY src, dest;
-- Agg views
CREATE VIEW events_sessions
(
  src,
  dest,
  counts,
  last_ts
)
AS SELECT src, dest, COUNT(*), MAX(ts)
   FROM events
   GROUP BY src, dest;

CREATE VIEW events_by_second
(
  second_ts,
  src,
  count_values
)
AS SELECT TRUNCATE(SECOND, ts), src, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(SECOND, ts), src;

CREATE VIEW dests_by_second
(
  second_ts,
  dest,
  count_values
)
AS SELECT TRUNCATE(SECOND, ts), dest, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(SECOND, ts), dest;

CREATE VIEW events_by_cluster
(
  second_ts,
  cluster,
  count_values
)
AS SELECT TRUNCATE(SECOND, ts), cluster, COUNT(*)
   FROM events
   WHERE cluster IS NOT NULL
   GROUP BY TRUNCATE(SECOND, ts), cluster;

-- stored procedures
CREATE PROCEDURE FROM CLASS events.DeleteAfterDate;
PARTITION PROCEDURE DeleteAfterDate ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.DeleteOldestToTarget;
PARTITION PROCEDURE DeleteOldestToTarget ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.NewEvent;
PARTITION PROCEDURE NewEvent ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.GetTopUsers;
IMPORT CLASS events.Utils;

CREATE PROCEDURE FROM CLASS events.GetTopSources;
CREATE PROCEDURE FROM CLASS events.GetTopSrcDests;

CREATE PROCEDURE GetTopDests AS
SELECT dests.url AS url, SUM(count_values) AS counts
FROM dests_by_second, dests
WHERE TO_TIMESTAMP(SECOND, SINCE_EPOCH(SECOND, NOW) - ?) <= second_ts AND dest = dests.id
GROUP BY url
ORDER BY counts DESC, url LIMIT ?;

CREATE PROCEDURE GetEventsByCluster AS
SELECT cluster, SUM(count_values) AS counts
FROM events_by_cluster
WHERE TO_TIMESTAMP(SECOND, SINCE_EPOCH(SECOND, NOW) - ?) <= second_ts
GROUP BY cluster
ORDER BY cluster;
