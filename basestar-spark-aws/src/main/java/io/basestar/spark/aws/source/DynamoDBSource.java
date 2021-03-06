//package io.basestar.spark.aws.source;
//
///*-
// * #%L
// * basestar-spark-aws
// * %%
// * Copyright (C) 2019 - 2020 Basestar.IO
// * %%
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// * #L%
// */
//
//import com.amazonaws.services.dynamodbv2.model.AttributeValue;
//import io.basestar.spark.sink.Sink;
//import io.basestar.spark.source.Source;
//import lombok.Builder;
//import org.apache.hadoop.dynamodb.DynamoDBItemWritable;
//import org.apache.hadoop.dynamodb.read.DynamoDBInputFormat;
//import org.apache.hadoop.io.Text;
//import org.apache.hadoop.mapred.JobConf;
//import org.apache.spark.SparkContext;
//import org.apache.spark.rdd.RDD;
//import org.apache.spark.sql.SparkSession;
//
//import java.util.Map;
//
//@Builder(builderClassName = "Builder")
//public class DynamoDBSource implements Source<RDD<Map<String, AttributeValue>>> {
//
//    private final SparkSession session;
//
//    private final String tableName;
//
//    private final int minPartitions;
//
//    @Override
//    public void then(final Sink<RDD<Map<String, AttributeValue>>> sink) {
//
//        final SparkContext sc = session.sparkContext();
//        final JobConf jobConf = new JobConf(sc.hadoopConfiguration());
//        jobConf.set("dynamodb.input.tableName", tableName);
//        jobConf.set("mapred.input.format.class", "org.apache.hadoop.dynamodb.read.DynamoDBInputFormat");
//
//        sink.accept(sc.hadoopRDD(jobConf, DynamoDBInputFormat.class, Text.class, DynamoDBItemWritable.class, minPartitions)
//                .toJavaRDD().map(v -> {
//                    final DynamoDBItemWritable item = v._2();
//                    return item.getItem();
//                }).rdd());
//    }
//}
