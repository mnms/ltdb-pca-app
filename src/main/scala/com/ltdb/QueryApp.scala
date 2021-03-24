package com.ltdb

import org.apache.spark.ml.feature.{PCA, StandardScaler}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf

/**
 * @author ${user.name}
 */
object QueryApp {

  def main(args : Array[String]) {

    val spark:SparkSession = SparkSession.builder()
      .appName("LTDB-PCA")
      .enableHiveSupport()
      .getOrCreate()
    import spark.implicits._
    var doPca = false
    var query_string = args{0}
    var i = query_string.indexOf(' ')
    var first_word = query_string.substring(0, i)
    var rest_word = query_string.substring(i);
    if (first_word.equals("PCA:")) doPca = true

    if (doPca){
      // query result
      var query_result= spark.sql(rest_word)

      // convert float array to Vectors
      val convertToVector = udf((array: Seq[Float]) => {
        Vectors.dense(array.map(_.toDouble).toArray)
      })
      val vectorDf = query_result.withColumn("feature_value_vector", convertToVector($"feature_value"))

      // do StandardScaler
      val scaler = new StandardScaler()
        .setInputCol("feature_value_vector")
        .setOutputCol("feature_value_scale")
        .setWithStd(true)
        .setWithMean(true)
      val scalerModel = scaler.fit(vectorDf)
      val scaledDf = scalerModel.transform(vectorDf).select("project_id", "instance_id", "feature_value_scale", "cropped_image_path")

      // do PCA
      val count = scaledDf.count()
      if (count > 0) {
        val pca = new PCA()
          .setInputCol("feature_value_scale")
          .setOutputCol("feature_value_3d")
          .setK(3)
          .fit(scaledDf)
        val result = pca.transform(scaledDf).select("project_id", "instance_id", "feature_value_3d", "cropped_image_path")
        val output = result.toJSON.collectAsList()
        println(output)
      } else {
        val output = "No result"
        println(output)
      }
    } else {
      var query_result= spark.sql(query_string)
      println(query_result.toJSON.collectAsList())
    }
  }

}
