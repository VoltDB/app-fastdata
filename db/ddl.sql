-- events
CREATE TABLE events
(
  src      integer        NOT NULL,
  dest     varchar(127)   NOT NULL,
  method   varchar(3)     NOT NULL,
  ts       timestamp      NOT NULL,
  size     bigint         NOT NULL,
  referral varchar(127),
  agent    varchar(142)   NOT NULL
);
PARTITION TABLE events ON COLUMN src;

CREATE INDEX event_src_index ON events (src, ts);
CREATE INDEX event_ts_index ON events (ts);

CREATE TABLE attackers
(
  src integer NOT NULL,
  CONSTRAINT attacker_pkey PRIMARY KEY (src)
);

-- export events
CREATE TABLE events_export
(
  src      varchar(15)    NOT NULL,
  dest     varchar(127)   NOT NULL,
  method   varchar(3)     NOT NULL,
  ts       timestamp      NOT NULL,
  size     bigint         NOT NULL,
  referral varchar(127),
  agent    varchar(142)   NOT NULL
);
EXPORT TABLE events_export;

-- Agg views
CREATE VIEW events_by_second
(
  second_ts,
  src,
  count_values
)
AS SELECT TRUNCATE(SECOND, ts), src, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(SECOND, ts), src;

CREATE VIEW events_by_minute
(
  minute_ts,
  src,
  count_values
)
AS SELECT TRUNCATE(MINUTE, ts), src, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(MINUTE, ts), src;

CREATE VIEW dests_by_second
(
  second_ts,
  dest,
  count_values
)
AS SELECT TRUNCATE(SECOND, ts), dest, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(SECOND, ts), dest;

CREATE VIEW dests_by_minute
(
  minute_ts,
  dest,
  count_values
)
AS SELECT TRUNCATE(MINUTE, ts), dest, COUNT(*)
   FROM events
   GROUP BY TRUNCATE(MINUTE, ts), dest;

CREATE TABLE alerts
(
  src  integer   NOT NULL,
  ts   timestamp NOT NULL,
  CONSTRAINT alerts_pkey PRIMARY KEY (src, ts)
);
PARTITION TABLE alerts ON COLUMN src;

CREATE VIEW alerts_by_second
(
  ts,
  counts
)
AS SELECT TRUNCATE(SECOND, ts), COUNT(*)
   FROM alerts
   GROUP BY TRUNCATE(SECOND, ts);

-- stored procedures
CREATE PROCEDURE FROM CLASS events.DeleteAfterDate;
PARTITION PROCEDURE DeleteAfterDate ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.DeleteOldestToTarget;
PARTITION PROCEDURE DeleteOldestToTarget ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.NewEvent;
PARTITION PROCEDURE NewEvent ON TABLE events COLUMN src;

CREATE PROCEDURE FROM CLASS events.GetTopUsers;
IMPORT CLASS events.Utils;

CREATE PROCEDURE FROM CLASS events.GetTopDests;

CREATE PROCEDURE GetAlertsPerSec AS
SELECT ts, counts
FROM alerts_by_second
WHERE ts >= TO_TIMESTAMP(SECOND, SINCE_EPOCH(SECOND, NOW) - ?)
ORDER BY ts;
