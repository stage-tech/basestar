package io.basestar.spark.aws;

/*-
 * #%L
 * basestar-spark-aws
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2020 basestar.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.basestar.schema.InstanceSchema;
import io.basestar.spark.SparkUtils;
import io.basestar.spark.Transform;
import lombok.RequiredArgsConstructor;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;

@RequiredArgsConstructor
public class DynamoDBOutputTransform implements Transform<Dataset<Row>, RDD<Map<String, AttributeValue>>> {

    private final InstanceSchema schema;

    @Override
    public RDD<Map<String, AttributeValue>> apply(final Dataset<Row> input) {

        return input.toJavaRDD().map(row -> {
            final Map<String, Object> data = SparkUtils.fromSpark(schema, row);
            return DynamoDBSparkUtils.toDynamoDB(data);
        }).rdd();
    }
}
