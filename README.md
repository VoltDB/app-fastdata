# VoltDB Fast Data Example App

Use Case
--------

The Fast Data app simulates real-time click stream processing. Click events are
ingested into VoltDB at a high rate, then cleaned up and persisted into a data
warehouse for historical analysis. Segmentation information is calculated in
data warehouse and stored back into VoltDB. VoltDB uses the information to
segment real-time events for per-event decisioning.

The click events are generated with random data at a constant rate. Each click
event contains basic information like source IP address, destination URL,
timestamp, HTTP method name, referral URL, and the user agent string. More
information can be included in a real-world use case.

The stream is ingested into VoltDB, cleaned up, then exported into a data
warehouse for long term persistence. The data warehouse runs machine learning
algorithm on the full historical dataset to segment the click events into
clusters periodically. Each cluster is represented by its center. The cluster
centers are sent back into VoltDB.

VoltDB uses the clustering information to further segment new click events into
the corresponding cluster at real-time. VoltDB can use this to make per-event
real-time decisions like what advertisement to show a user, or whether a user
should be blocked for spamming.

Events are aged out of VoltDB after they are persisted in the data
warehouse. This limits the dataset inside VoltDB to only include relatively hot
data.

The example includes a dashboard that shows click stream cluster distribution,
top users and top visited URLs in a moving window. All of these information is
calculated from materialized views on relevant source tables.

Code organization
-----------------

The code is divided into projects:

- "db": the database project, which contains the schema, stored procedures and
  other configurations that are compiled into a catalog and run in a VoltDB
  database.
- "client": a java client that generates click events.
- "web": a simple web server that provides the demo dashboard.

See below for instructions on running these applications.  For any questions,
please contact fieldengineering@voltdb.com.

Pre-requisites
--------------

Before running these scripts you need to have VoltDB 4.7 or later installed, and
the bin subdirectory should be added to your PATH environment variable.  For
example, if you installed VoltDB Enterprise 4.7 in your home directory, you
could add it to the PATH with the following command:
```bash
export PATH="$PATH:$HOME/voltdb-ent-4.7/bin"
```

Demo Instructions
-----------------

1. Start the web server
    ```bash
    ./run.sh start_web
    ```

2. Start the database and client
    ```bash
    ./run.sh demo
    ```

3. Open a web browser to http://hostname:8081

4. To stop the demo:

Stop the client (if it hasn't already completed) by pressing Ctrl-C

Stop the database
```bash
voltadmin shutdown
```

Stop the web server
```bash
./run.sh stop_web
```

Options
-------

You can control various characteristics of the demo by modifying the parameters
passed into the LogGenerator java application in the "client" function within
the run.sh script.

Speed & Duration:

    --duration=120                (benchmark duration in seconds)
    --ratelimit=20000             (run up to this rate of requests/second)


Instructions for running on a cluster
-------------------------------------

Before running this demo on a cluster, make the following changes:

1. On each server, edit the run.sh file to set the HOST variable to the name of
   the **first** server in the cluster:

    HOST=voltserver01

2. On each server, edit db/deployment.xml to change hostcount from 1 to the
   actual number of servers:
    ```
    <cluster hostcount="1" sitesperhost="3" kfactor="0" />
    ```

4. On each server, start the database
    ```bash
    ./run.sh server
    ```

5. On one server, Edit the run.sh script to set the SERVERS variable to a
   comma-separated list of the servers in the cluster

    SERVERS=voltserver01,voltserver02,voltserver03

6. Run the client script:
    ```bash
    ./run.sh client
    ```
