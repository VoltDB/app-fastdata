#!/bin/bash

JAVAPATH=$(which java)
SCRIPTPATH=$( dirname "${BASH_SOURCE[0]}" )

: ${VOLTDBIP:?"Please set the VOLTDBIP environment variable to point to the VoltDB node"}
sed "s,\$VOLTDBIP,$VOLTDBIP,g; s,\$JAVAPATH,$JAVAPATH,g; s,\$PATH,$SCRIPTPATH,g" \
    $SCRIPTPATH/RFunctions.sql | vsql
