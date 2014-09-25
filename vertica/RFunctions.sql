-- VoltDB UDx for loading cluster centers back into VoltDB
SELECT SET_CONFIG_PARAMETER('JavaBinaryForUDx','$JAVAPATH');
\set libSfile '\'$PATH/voltdb-udxload.jar\''
CREATE LIBRARY VoltDBFunctions AS :libSfile LANGUAGE 'JAVA';
CREATE FUNCTION voltdbload AS LANGUAGE 'Java' NAME 'org.voltdb.vertica.VoltDBLoader' LIBRARY VoltDBFunctions ;
CREATE FUNCTION voltdbcall AS LANGUAGE 'Java' NAME 'org.voltdb.vertica.VoltDBCall' LIBRARY VoltDBFunctions ;

-- R UDx for calculating K-means
\set libfile '\'$PATH/RFunctions.R\''
CREATE LIBRARY rlib AS :libfile LANGUAGE 'R';
CREATE TRANSFORM FUNCTION mykmeansPoly
AS LANGUAGE 'R' NAME 'kmeansFactoryPoly' LIBRARY rlib;

-- Invoke the UDx to perform k-means clustering on the input and load the cluster centers back to VoltDB
SELECT COUNT(*) FROM EVENTS_EXPORT;
SELECT voltdbload(ROW_NUMBER() OVER(), src, dest, referral, agent using parameters voltservers='$VOLTDBIP', volttable='clusters') FROM
  (SELECT mykmeansPoly(src, dest, referral, agent using parameters k=20) OVER()
   FROM EVENTS_EXPORT
   ORDER BY 1, 2, 3, 4) AS k;

-- Cleanup
DROP LIBRARY VoltDBFunctions CASCADE;
DROP LIBRARY rLib CASCADE;
