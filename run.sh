#!/usr/bin/env bash

# VoltDB variables
APPNAME="events"
HOST=localhost
DEPLOYMENT=deployment.xml

# WEB SERVER variables
WEB_PORT=8081

# CLIENT variables
SERVERS=localhost


# Get the PID from PIDFILE if we don't have one yet.
if [[ -z "${PID}" && -e web/http.pid ]]; then
  PID=$(cat web/http.pid);
fi

# find voltdb binaries in either installation or distribution directory.
# if [ -n "$(which voltdb 2> /dev/null)" ]; then
#     VOLTDB_BIN=$(dirname "$(which voltdb)")
# else
#     VOLTDB_BIN="$(pwd)/../../../bin"
#     echo "The VoltDB scripts are not in your PATH."
#     echo "For ease of use, add the VoltDB bin directory: "
#     echo
#     echo $VOLTDB_BIN
#     echo
#     echo "to your PATH."
#     echo
# fi
# installation layout has all libraries in $VOLTDB_ROOT/lib/voltdb
# if [ -d "$VOLTDB_BIN/../lib/voltdb" ]; then
#     VOLTDB_BASE=$(dirname "$VOLTDB_BIN")
#     VOLTDB_LIB="$VOLTDB_BASE/lib/voltdb"
#     VOLTDB_VOLTDB="$VOLTDB_LIB"
# distribution layout has libraries in separate lib and voltdb directories
# else
#     VOLTDB_BASE=$(dirname "$VOLTDB_BIN")
#     VOLTDB_LIB="$VOLTDB_BASE/lib"
#     VOLTDB_VOLTDB="$VOLTDB_BASE/voltdb"
# fi

# APPCLASSPATH=$CLASSPATH:$({ \
#     \ls -1 "$VOLTDB_VOLTDB"/voltdb-*.jar; \
#     \ls -1 "$VOLTDB_LIB"/*.jar; \
#     \ls -1 "$VOLTDB_LIB"/extension/*.jar; \
# } 2> /dev/null | paste -sd ':' - )
# VOLTDB="$VOLTDB_BIN/voltdb"
# LOG4J="$VOLTDB_VOLTDB/log4j.xml"

# This script assumes voltdb/bin is in your path
VOLTDB_HOME=$(dirname $(dirname "$(which voltdb)"))

LICENSE="$VOLTDB_HOME/voltdb/license.xml"



# remove non-source files
function clean() {
    rm -rf voltdbroot statement-plans log catalog-report.html
    rm -f web/http.log web/http.pid
    rm -rf db/obj db/$APPNAME.jar db/nohup.log
    rm -rf client/obj client/log
    rm -rf nibbler/obj nibbler/log nibbler/nohup.log
}

function start_web() {
    if [[ -z "${PID}" ]]; then
        cd web
        nohup python -m SimpleHTTPServer $WEB_PORT > http.log 2>&1 &
        echo $! > http.pid
        cd ..
        echo "started http server"
    else
        echo "http server is already running (PID: ${PID})"
    fi
}

function stop_web() {
  if [[ -z "${PID}" ]]; then
    echo "http server is not running (missing PID)."
  else
      kill ${PID}
      rm web/http.pid
      echo "stopped http server (PID: ${PID})."
  fi
}

# compile any java stored procedures
function compile_procedures() {
    mkdir -p db/obj
    CLASSPATH=`ls -1 $VOLTDB_HOME/voltdb/voltdb-*.jar`
    SRC=`find db/procedures -name "*.java"`
	javac -classpath $CLASSPATH -d db/obj $SRC
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# build an application catalog
function catalog() {
    compile_procedures
    voltdb compile --classpath db/obj -o db/$APPNAME.jar db/ddl.sql
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
}

# run the voltdb server locally
function server() {
    nohup_server
    echo "------------------------------------"
    echo "|  Ctrl-C to stop tailing the log  |"
    echo "------------------------------------"
    tail -f db/nohup.log
}

function nohup_server() {
    # if a catalog doesn't exist, build one
    if [ ! -f db/$APPNAME.jar ]; then catalog; fi
    # run the server
    nohup voltdb create -d db/$DEPLOYMENT -l $LICENSE -H $HOST db/$APPNAME.jar > db/nohup.log 2>&1 &
}

function cluster-server() {
    export DEPLOYMENT=deployment-cluster.xml
    server
}

# update catalog on a running database
function update() {
    catalog
    voltadmin update $APPNAME.jar deployment.xml
}

function client() {
    # if the class files don't exist, compile the client
    if [ ! -d client/obj ]; then compile-client; fi
    if [ ! -d nibbler/obj ]; then compile-client; fi

    CLASSPATH=`ls -1 $VOLTDB_HOME/voltdb/voltdb-*.jar`
    CLASSPATH="$CLASSPATH:`ls -1 $VOLTDB_HOME/lib/commons-cli-*.jar`"

    pushd nibbler
    echo "running nibbler..."
    nohup java -classpath obj:$CLASSPATH -Dlog4j.configuration=file://$VOLTDB_HOME/voltdb/log4j.xml \
        nibbler.Nibbler \
        --historyseconds=180 \
        --deletechunksize=10000 \
        --deleteyieldtime=50 \
        > nohup.log 2>&1 &
    popd

    cd client

    echo "running log generator..."
    java -classpath obj:$CLASSPATH:../db/$APPNAME.jar -Dlog4j.configuration=file://$VOLTDB_HOME/voltdb/log4j.xml \
	events.LogGenerator \
	--displayinterval=5 \
	--duration=900 \
	--ratelimit=20000 \
	--servers=$SERVERS

    cd ..
}

function compile-client() {
    CLASSPATH=`ls -1 $VOLTDB_HOME/voltdb/voltdb-*.jar`
    CLASSPATH="$CLASSPATH:`ls -1 $VOLTDB_HOME/lib/commons-cli-*.jar`"

    pushd client
    # compile client
    mkdir -p obj
    SRC=`find src -name "*.java"`
    javac -Xlint:unchecked -classpath $CLASSPATH:../db/$APPNAME.jar -d obj $SRC
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
    popd

    pushd nibbler
    # compile nibbler
    mkdir -p obj
    SRC=`find src -name "*.java"`
    javac -Xlint:unchecked -classpath $CLASSPATH -d obj $SRC
    # stop if compilation fails
    if [ $? != 0 ]; then exit; fi
    popd
}

# compile the catalog and client code
function demo-compile() {
    catalog
    compile-client
}

function demo() {
    export DEPLOYMENT=deployment-demo.xml
    nohup_server
    sleep 10
    echo "starting client..."
    client
}

function help() {
    echo "Usage: ./run.sh {clean|catalog|server}"
}

# Run the target passed as the first arg on the command line
# If no first arg, run server
if [ $# -gt 1 ]; then help; exit; fi
if [ $# = 1 ]; then $1; else server; fi
