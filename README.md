# VoltDB Fast Data Example App

Use Case
--------

The Fast Data app simulates real-time click stream processing. Click events are
ingested into VoltDB at a high rate, then cleaned up and persisted into a data
warehouse for historical analysis. Segmentation information is calculated in
data warehouse and stored back into VoltDB. VoltDB uses the information to
segment real-time events for per-event decisioning.

The click events can come from persistent queues like [Apache
Kafka](http://kafka.apache.org). For simplicity, this sample app generates
random events at a constant rate. Each click event contains basic information
like source IP address, destination URL, timestamp, HTTP method name, referral
URL, and the user agent string. More information can be included in a real-world
use case.

The stream is ingested into VoltDB, cleaned up, then exported into a data
warehouse like [Hadoop](http://hadoop.apache.org) for long term persistence. The
data warehouse runs machine learning algorithm on the full historical dataset to
segment the click events into clusters periodically. Each cluster is represented
by its center. The cluster centers are sent back into VoltDB.

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
- "hadoop": a collection of pig/shell scripts, and a Spark program

See below for instructions on running these applications.  For any questions,
please contact fieldengineering@voltdb.com.

Pre-requisites
--------------

1. Before running these scripts you need to have VoltDB 4.8 or later installed,
   and the bin subdirectory should be added to your PATH environment variable.
   For example, if you installed VoltDB Enterprise 4.8 in your home directory,
   you could add it to the PATH with the following command:

    ```bash
    export PATH="$PATH:$HOME/voltdb-ent-4.8/bin"
    ```
    
Hadoop Demo
-----------

Running with Hortonworks
------------------------
This demo requires a [Hortonworks HDP](http://www.hortonworks.com/) Hadoop installation (HDP).
The demo consists of a VoltDB server writing export data to files stored in Hadoop, and
a set of scripts, and programs that collect the exported data, compute k-means clusters on the
collected data, and store the computations back to VoltDB.

1. On all Hortonworks cluster nodes install Spark by following these
   [instructions](http://hortonworks.com/hadoop-tutorial/using-apache-spark-hdp/)

2. On the server where VoltDB is installed set the environment variable
   `WEBHDFS_ENDPOINT` to a WebHDFS endpoint that matches the following pattern

   ```
   http://[host]:[port]/webhdfs/v1/[export-base-directory]/%g/%p/%t.avro?user.name=[user]
   ```
3. Download this [archive](http://downloads.voltdb.com/technologies/other/fastdata-kmeans.tar.bz2)
   and unpack it on an Hadoop node

   ```bash
   $ tar -jxf fastdata-kmeans.tar.bz2
   ```
4. Change your working directory to `fastdata-kmeans` and run the `hdp_compute_clusters.sh`
   script when you to want to process exported data from VoltDB (see 
   [**Demo Instructions**](#demo-instructions) section bellow)

   ```bash
   $ cd fastdata-kmeans
   #
   # Assuming you are exporting to /export/fastdata, and VoltDB is
   # running on volthost
   $ ./hdp_compute_clusters.sh /export/fastdata volthost
   ```

   This script

   * Harvests the exported data by renaming the export base directory
   * Invokes the `hdp.harvest.pig` pig script to write the harvested data into a
     Parquet database
   * Starts a Spark job that computes the K-Means clusters on the data stored
     in the parquet database, and creates another parquet db with the resulting
     computations.
   * Invokes the `hdp.load.pig` script that reads the K-Means computation Parquet
     database, and writes its content back to VoltDB by utilizing the
     [VoltDB hadoop extensions](https://github.com/VoltDB/voltdb-hadoop-extension)


Running with Cloudera
---------------------
This demo requires a [Cloudera](http://www.cloudera.com/content/cloudera/en/downloads/cloudera_manager/cm-5-2-1.html)
Hadoop installation, whose configured services also include [Spark](https://spark.apache.org/).
The demo consists of a VoltDB server writing export data to files stored in Hadoop, and
a set of scripts, and programs that collect the exported data, compute k-means clusters on the
collected data, and store the computations back to VoltDB.

1. On the server where VoltDB is installed set the environment variable
   `WEBHDFS_ENDPOINT` to a WebHDFS endpoint that matches the following pattern

   ```
   http://[host]:[port]/webhdfs/v1/[export-base-directory]/%g/%p/%t.avro?user.name=[user]
   ```
2. Download this [archive](http://downloads.voltdb.com/technologies/other/fastdata-kmeans.tar.bz2)
   and unpack it on an Hadoop node

   ```bash
   $ tar -jxf fastdata-kmeans.tar.bz2
   ```
3. Change your working directory to `fastdata-kmeans` and run the `compute_clusters.sh`
   script when you to want to process exported data from VoltDB (see 
   [**Demo Instructions**](#demo-instructions) section bellow)

   ```bash
   $ cd fastdata-kmeans
   #
   # Assuming you are exporting to export/fastdata, and VoltDB is
   # running on volthost
   $ ./compute_clusters.sh export/fastdata volthost
   ```

   This script

   * Harvests the exported data by renaming the export base directory
   * Invokes the `harvest.pig` pig script to write the harvested data into a
     Parquet database
   * Starts a Spark job that computes the K-Means clusters on the data stored
     in the parquet database, and creates another parquet db with the resulting
     computations.
   * Invokes the `load.pig` script that reads the K-Means computation Parquet
     database, and writes its content back to VoltDB by utilizing the
     [VoltDB hadoop extensions](https://github.com/VoltDB/voltdb-hadoop-extension)

Vertica Demo
------------

1. This example also requires [Vertica](http://www.vertica.com) installed on the
   same machine or a machine that the VoltDB machine has access to. A Vertica
   database must be created with the username **dbadmin** with no password. Once
   Vertica is running, set the environment variable `VERTICAIP` to point to the
   Vertica machine on the VoltDB machine. For example, if your Vertica is
   running on 192.168.0.1, run the following command on the VoltDB machine:
    ```bash
    export VERTICAIP="192.168.0.1"
    ```

1. VoltDB uses JDBC to export click events to Vertica. Vertica's JDBC driver
   must be present in the classpath before starting VoltDB. If your Vertica is
   installed in `/opt/vertica`, you can find the JDBC driver in
   `/opt/vertica/java/lib/`. Copy the JAR file in that directory to the VoltDB
   machine and put it in the `lib/extension` sub-directory in your VoltDB
   installation.

1. To run the K-means clustering algorithm in Vertica, it requires the R
   language package to be installed. Please follow the instructions in [Vertica
   documentation](https://my.vertica.com/docs/7.1.x/HTML/index.htm#Authoring/ProgrammersGuide/UserDefinedFunctions/UDxR/InstallingRForHPVertica.htm)
   to install the R language pack.

1. Copy the `vertica` sub-directory in the example to the vertica machine. This
   directory contains Vertica extensions for K-means clustering and loading data
   back into VoltDB. The following instructions assume that the directory is
   copied to `/tmp/vertica`.

Demo Instructions
-----------------

1. Start the web server
    ```bash
    ./run.sh start_web
    ```

2. Start the database and client
    ```bash
    # for Hadoop run
    $ ./run.sh hadoop_demo
    # while for Vertical run
    $ ./run.sh demo
    ```

3. Open a web browser to http://hostname:8081

4. For Vertica run the `updatemodel.sh` script on the Vertica machine to run
   the K-means clustering algorithm on the data in Vertica. You can run this
   command periodically to update the cluster model in VoltDB.
    ```bash
    $ /tmp/vertica/updatemodel.sh
    ```

5. To stop the demo:

Stop the client (if it hasn't already completed) by pressing Ctrl-C

Stop the database
```bash
$ voltadmin shutdown
```

Stop the web server
```bash
$ ./run.sh stop_web
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
