#!/bin/bash

#++
#   C O M P U T E _ C L U S T E R S . S H
#
# this script assumes that you are running on a node with a recent (V5+) Cloudera
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

HERE=$(dirname $(readlink -e $0))

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

PIGBIN=$(readlink -e `which pig`)
if [ -z "$PIGBIN" ]; then
    >&2 echo "did not find pig"
    exit 1
fi

CDHLIB=${PIGBIN//\/bin\/pig/\/lib}
ADDITIONALS=$(ls -1 \
    $CDHLIB/parquet/parquet-pig-bundle.jar \
    $CDHLIB/pig/lib/avro.jar \
    $CDHLIB/pig/lib/avro-mapred.jar \
    $CDHLIB/pig/lib/json-*.jar \
    $CDHLIB/pig/piggybank.jar \
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

hadoop fs -rm -R -f -skipTrash $PROCESSBASE
hadoop fs -mv $EXPORTBASE $PROCESSBASE

pig -Dpig.additional.jars="${ADDITIONALS}" \
    -param PROCESSBASE="$PROCESSBASE" \
    $HERE/harvest.pig

spark-submit \
  --class KMeansReferral \
  --master yarn-master --deploy-mode cluster \
  --num-executors 4 --executor-cores 2 \
  --driver-memory 1g --executor-memory 1g \
  $HERE/kmeans-referral_2.10-1.0.jar ${PROCESSBASE}/source ${PROCESSBASE}/centers

java -jar $HERE/dsa.jar --servers="$SERVERS" --action=CLEAR

jsonlist "${SERVERS}"

pig -Dpig.additional.jars="${ADDITIONALS}" \
    -param SERVERS="${JSONLIST}" \
    -param PROCESSBASE="$PROCESSBASE" \
    $HERE/load.pig

java -jar $HERE/dsa.jar --servers="$SERVERS" --action=LOAD
