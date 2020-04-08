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
import com.amazonaws.services.dynamodbv2.model.Record;
import io.basestar.spark.Transform;
import org.apache.spark.rdd.RDD;

import java.util.Map;

public class DynamoDBNewImageTransform implements Transform<RDD<Record>, RDD<Map<String, AttributeValue>>> {

    @Override
    public RDD<Map<String, AttributeValue>> apply(final RDD<Record> input) {

        return input.toJavaRDD().map(v -> v.getDynamodb().getNewImage()).rdd();
    }
}
