package io.basestar.storage.spark;

/*-
 * #%L
 * basestar-storage-spark
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

import com.google.common.collect.Multimap;
import io.basestar.schema.Namespace;
import io.basestar.schema.ObjectSchema;
import io.basestar.spark.SparkUtils;
import io.basestar.storage.Storage;
import io.basestar.storage.TestStorage;
import io.basestar.util.Nullsafe;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TestSparkStorage extends TestStorage {

    @Override
    protected Storage storage(final Namespace namespace, final Multimap<String, Map<String, Object>> data) {

        final SparkSession session = SparkSession.builder()
                .master("local")
                .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                .getOrCreate();

        final SparkRouting routing = new SparkRouting() {

            @Override
            public Dataset<Row> objectRead(final SparkSession session, final ObjectSchema schema) {

                final StructType structType = SparkUtils.structType(schema);
                final List<Row> items = Nullsafe.of(data.get(schema.getName())).stream()
                        .map(schema::create)
                        .map(v -> SparkUtils.toSpark(schema, structType, v))
                        .collect(Collectors.toList());

                return session.sqlContext()
                        .createDataFrame(items, structType);
            }

            @Override
            public Dataset<Row> historyRead(final SparkSession session, final ObjectSchema schema) {

                return objectRead(session, schema);
            }
        };

        return SparkStorage.builder()
                .setRouting(routing)
                .setSession(session)
                .build();
    }

    @Override
    public void testLarge() {

    }

    @Override
    public void testCreate() {

    }

    @Override
    public void testUpdate() {

    }

    @Override
    public void testDelete() {

    }
}
