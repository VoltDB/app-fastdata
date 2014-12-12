/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SQLContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.rdd.RDD

object KMeansReferral {

    case class Center(id:Int, src:Int, dest:Int, referral:Int, agent:Int)

    def main(args: Array[String]) {
        if (args.length != 2) {
            System.err.println("Usage: KMeans [source-parquet] [centers-parquet]")
            System.exit(1)
        }

        val conf = new SparkConf().setAppName("KMeansReferral")
        val sc = new SparkContext(conf)
        val sqlContext = new SQLContext(sc)
    
        import sqlContext.createSchemaRDD

        val parquetFile = sqlContext.parquetFile(args(0))
        val parsedData = parquetFile.map(r => Vectors.dense((for (f<-r) yield f.asInstanceOf[Int].toDouble).toArray))
        val clusters = KMeans.train(parsedData, 20, 20)
        val intarrays = for (v<-clusters.clusterCenters) yield for (d<-v.toArray) yield d.round.toInt

        val centers = for ((a,i)<-intarrays.zipWithIndex) yield Center(i,a(0),a(1),a(2),a(3))
        val outrdd: RDD[Center] = sc.parallelize(centers)

        outrdd.saveAsParquetFile(args(1))
    }
}
