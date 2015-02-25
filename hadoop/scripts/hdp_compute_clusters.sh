#!/bin/bash

#++
#   H D P _ C O M P U T E _ C L U S T E R S . S H
#
# this script assumes that you are running on a node with a recent (V2.2) Hortonworks
# Hadoop distribution, it expects that your VoltDB fast data app is exporting avro
# files using the following layout
# 
#     $EXPORTBASE/[generation]/[partition-id]/[table].avro
#
# Parameter 1 ($1) is EXPORTBASE
# Parameter 2 ($2) comma delimited list of VoltDB servers running the fast data app
#
# 1. it renames the EXPORTBASE directory to harvest the exported avro data
# 2. runs a pig script that creates a parquet db from the exported data
# 3. runs a spark task to compute k-means cluster from the parquet db
# 4. store the computed clusters back to voltdb by using a pig script
#--

HERE=.
if [[ "$0" != "${0%/*}" ]]; then
  HERE=${0%/*}
fi

if (( $# != 2 )); then
    >&2 echo "$(basename $0) [export-base-directory] [comma-delimited-list-of-servers]"
    exit 1
fi

EXPORTBASE=${1%/}
PROCESSBASE="${EXPORTBASE##*/}.process_now"
if [[ "${EXPORTBASE}" != "${EXPORTBASE%/*}" ]]; then
    PROCESSBASE="${EXPORTBASE%/*}/${PROCESSBASE}"
fi
SERVERS=$2

if [ -z "$SPARK_HOME" ]; then
    >&2 echo "SPARK_HOME environment variable is not defined"; exit 1
fi

SPARK_CMD="${SPARK_HOME}/bin/spark-submit"
if [ ! -e "$SPARK_CMD" -o ! -f "$SPARK_CMD" -o ! -x "$SPARK_CMD" ]; then
    >&2 echo "$SPARK_CMD is not accessible or executable"; exit 1
fi

if [ -z "$YARN_CONF_DIR" ]; then
    >&2 echo "YARN_CONF_DIR environment variable is not defined"; exit 1
fi

PIGBIN=$(readlink -e `which pig`)
if [ -z "$PIGBIN" ]; then
    >&2 echo "did not find pig"; exit 1
fi

PIGLIB=${PIGBIN//\/bin\/pig/\/lib}
ADDITIONALS=$(ls -1 \
    $PIGLIB/parquet-pig-bundle*.jar \
    $HERE/libs/voltdb*.jar \
    | paste -sd: -)

jsonlist() {
    local IFS=$',' _l=()
    for s in $1; do
        _l+=($'"'$s$'"')
    done
    JSONLIST="${_l[*]}"
    unset IFS
}

pig -Dpig.additional.jars="${ADDITIONALS}" \
    -param EXPORTBASE="$EXPORTBASE" \
    -param PROCESSBASE="$PROCESSBASE" \
    $HERE/hdp.harvest.pig

$SPARK_CMD --class KMeansReferral \
    --master yarn-cluster --num-executors 3 \
    --driver-memory 512m --executor-memory 512m --executor-cores 1 \
    $HERE/kmeans-referral_2.10-1.0.jar ${PROCESSBASE}/source ${PROCESSBASE}/centers

java -jar $HERE/dsa.jar --servers="$SERVERS" --action=CLEAR

jsonlist "${SERVERS}"

pig -Dpig.additional.jars="${ADDITIONALS}" \
    -param SERVERS="${JSONLIST}" \
    -param PROCESSBASE="$PROCESSBASE" \
    $HERE/hdp.load.pig

java -jar $HERE/dsa.jar --servers="$SERVERS" --action=LOAD
