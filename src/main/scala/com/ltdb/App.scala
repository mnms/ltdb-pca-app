package com.ltdb

import org.apache.spark.ml.feature.{PCA, StandardScaler}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf

/**
 * @author ${user.name}
 */
object App {

  def main(args : Array[String]) {

    val spark:SparkSession = SparkSession.builder()
      .appName("LTDB-PCA")
      .enableHiveSupport()
      .getOrCreate()
    import spark.implicits._
    var createSchema = false
    if(args{0}.equals("create")) createSchema = true

    if(createSchema) {
      spark.sql("DROP TABLE IF EXISTS instances")
      spark.sql(
        """
          |CREATE TABLE instances (instance_id long, project_id long,
          |dataset_id long, file_id long, cropped_image string,
          |track_id long, binding_id long, class_name string,
          |region_type string, region string, create_date string,
          |update_date string, feature_value array<float>,
          |feature_value_3d string, model_id long, extracting_method string,
          |gender string, age string, expression string, action string,
          |posture string, color string, cropped_image_path string,
          |file_id_key long, model_id_is_not_null boolean, tags string)
          |    USING r2 OPTIONS (table '401', host 'BPP-TVIEW-AIOPS-SEARCH01',
          |    port '18400',
          |    partitions 'project_id dataset_id file_id_key model_id_is_not_null',
          |    mode 'nvkvs', rowstore 'false', at_least_one_partition_enabled 'no')
                """.stripMargin)

      var query_result = spark.sql("DESC instances")
      println(query_result.toJSON.collectAsList())
    } else {
      // query result
      var query = args{0}
      var query_result= spark.sql(query)

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
    }
  }

}
