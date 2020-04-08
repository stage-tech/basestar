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

import com.google.common.collect.ImmutableList;
import io.basestar.expression.Expression;
import io.basestar.schema.Consistency;
import io.basestar.schema.ObjectSchema;
import io.basestar.schema.Reserved;
import io.basestar.spark.SparkUtils;
import io.basestar.storage.Storage;
import io.basestar.storage.StorageTraits;
import io.basestar.storage.util.Pager;
import io.basestar.util.PagedList;
import io.basestar.util.Sort;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


@Slf4j
public class SparkStorage implements Storage {

    private final SparkSession session;

    private final SparkRouting routing;

    private final ExecutorService executor;

    private SparkStorage(final Builder builder) {

        this.session = builder.session;
        this.routing = builder.routing;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static Builder builder() {

        return new Builder();
    }

    @Setter
    @Accessors(chain = true)
    public static class Builder {

        private SparkSession session;

        private SparkRouting routing;

        public SparkStorage build() {

            return new SparkStorage(this);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> readObject(final ObjectSchema schema, final String id) {

        return CompletableFuture.supplyAsync(() -> {

            Dataset<Row> ds = routing.objectRead(session, schema);
            ds = ds.filter(ds.col(Reserved.ID).equalTo(id));
            final Row row = ds.first();
            return row == null ? null : SparkUtils.fromSpark(schema, row);

        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> readObjectVersion(final ObjectSchema schema, final String id, final long version) {

        return CompletableFuture.supplyAsync(() -> {

            Dataset<Row> ds = routing.historyRead(session, schema);
            ds = ds.filter(ds.col(Reserved.ID).equalTo(id)
                    .and(ds.col(Reserved.VERSION).equalTo(version)));
            final Row row = ds.first();
            return row == null ? null : SparkUtils.fromSpark(schema, row);

        }, executor);
    }

    @Override
    public List<Pager.Source<Map<String, Object>>> query(final ObjectSchema schema, final Expression query, final List<Sort> sort) {

        return ImmutableList.of((count, paging) -> CompletableFuture.supplyAsync(() -> {

            Dataset<Row> ds = routing.historyRead(session, schema);
            final Column column = query.visit(new SparkExpressionVisitor(ds));
            ds = ds.filter(column);
            ds = ds.limit(count);

            final List<Row> rows = ds.collectAsList();

            final List<Map<String, Object>> items = rows.stream()
                    .map(row -> SparkUtils.fromSpark(schema, row))
                    .collect(Collectors.toList());

            return new PagedList<>(items, null);

        }, executor));
    }

    // FIXME: can request more than one at a time here

    @Override
    public ReadTransaction read(final Consistency consistency) {

        return new ReadTransaction.Basic(this);
    }

    @Override
    public WriteTransaction write(final Consistency consistency) {

        throw new UnsupportedOperationException();
    }

    @Override
    public EventStrategy eventStrategy(final ObjectSchema schema) {

        return EventStrategy.SUPPRESS;
    }

    @Override
    public StorageTraits storageTraits(final ObjectSchema schema) {

        return SparkStorageTraits.INSTANCE;
    }
}
