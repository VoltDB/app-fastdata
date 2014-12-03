#!/bin/bash

#++
#  B U I L D . S H
#
# builds the dsa.jar artifact
#--
HERE=.
if [[ "$0" != "${0%/*}" ]]; then
  HERE=${0%/*}
fi

VOLTDBBIN=$(readlink -e $(which voltdb))
if [ -z "${VOLTDBBIN}" ]; then
  &>2 echo "did not find voltdb command"
  exit 1
fi

VOLTDBHOME=${VOLTDBBIN%/bin/voltdb}
CLIENTJAR=$(ls -1 ${VOLTDBHOME}/voltdb/voltdbclient*.jar)

mkdir -p ${HERE}/obj

javac -cp "${CLIENTJAR}" -d ${HERE}/obj ${HERE}/ClustersDsa.java

cat > ${HERE}/manifest.txt <<-MANIFEST
	Main-Class: ClustersDsa
	Class-Path: libs/${CLIENTJAR##*/}
MANIFEST
jar cfm ${HERE}/dsa.jar ${HERE}/manifest.txt -C ${HERE}/obj .

