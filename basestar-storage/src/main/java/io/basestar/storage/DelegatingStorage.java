package io.basestar.storage;

/*-
 * #%L
 * basestar-storage
 * %%
 * Copyright (C) 2019 - 2020 Basestar.IO
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

import io.basestar.expression.Expression;
import io.basestar.expression.aggregate.Aggregate;
import io.basestar.schema.Consistency;
import io.basestar.schema.Index;
import io.basestar.schema.ObjectSchema;
import io.basestar.storage.util.Pager;
import io.basestar.util.Sort;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DelegatingStorage extends Storage {

    Storage storage(ObjectSchema schema);

    @Override
    default void validate(final ObjectSchema schema) {

        storage(schema).validate(schema);
    }

    @Override
    default String name(final ObjectSchema schema) {

        return storage(schema).name(schema);
    }

    @Override
    default CompletableFuture<Map<String, Object>> readObject(final ObjectSchema schema, final String id) {

        return storage(schema).readObject(schema, id);
    }

    @Override
    default List<Pager.Source<Map<String, Object>>> aggregate(final ObjectSchema schema, final Expression query, final Map<String, Expression> group, final Map<String, Aggregate> aggregates) {

        return storage(schema).aggregate(schema, query, group, aggregates);
    }

    @Override
    default CompletableFuture<Map<String, Object>> readObjectVersion(final ObjectSchema schema, final String id, final long version) {

        return storage(schema).readObjectVersion(schema, id, version);
    }

    @Override
    default List<Pager.Source<Map<String, Object>>> query(final ObjectSchema schema, final Expression query, final List<Sort> sort) {

        return storage(schema).query(schema, query, sort);
    }

    @Override
    default EventStrategy eventStrategy(final ObjectSchema schema) {

        return storage(schema).eventStrategy(schema);
    }

    @Override
    default StorageTraits storageTraits(final ObjectSchema schema) {

        return storage(schema).storageTraits(schema);
    }

    @Override
    default CompletableFuture<?> asyncIndexCreated(final ObjectSchema schema, final Index index, final String id, final long version, final Index.Key key, final Map<String, Object> projection) {

        return storage(schema).asyncIndexCreated(schema, index, id, version, key, projection);
    }

    @Override
    default CompletableFuture<?> asyncIndexUpdated(final ObjectSchema schema, final Index index, final String id, final long version, final Index.Key key, final Map<String, Object> projection) {

        return storage(schema).asyncIndexUpdated(schema, index, id, version, key, projection);
    }

    @Override
    default CompletableFuture<?> asyncIndexDeleted(final ObjectSchema schema, final Index index, final String id, final long version, final Index.Key key) {

        return storage(schema).asyncIndexDeleted(schema, index, id, version, key);
    }

    @Override
    default CompletableFuture<?> asyncHistoryCreated(final ObjectSchema schema, final String id, final long version, final Map<String, Object> after) {

        return storage(schema).asyncHistoryCreated(schema, id, version, after);
    }

    @Override
    default ReadTransaction read(final Consistency consistency) {

        final IdentityHashMap<Storage, ReadTransaction> transactions = new IdentityHashMap<>();
        return new ReadTransaction() {
            @Override
            public ReadTransaction readObject(final ObjectSchema schema, final String id) {

                transactions.computeIfAbsent(storage(schema), v -> v.read(consistency))
                        .readObject(schema, id);

                return this;
            }

            @Override
            public ReadTransaction readObjectVersion(final ObjectSchema schema, final String id, final long version) {

                transactions.computeIfAbsent(storage(schema), v -> v.read(consistency))
                        .readObjectVersion(schema, id, version);

                return this;
            }

            @Override
            public CompletableFuture<BatchResponse> read() {

                return BatchResponse.mergeFutures(transactions.values().stream().map(ReadTransaction::read));
            }
        };
    }

    @Override
    default WriteTransaction write(final Consistency consistency) {

        final IdentityHashMap<Storage, WriteTransaction> transactions = new IdentityHashMap<>();
        return new WriteTransaction() {
            @Override
            public WriteTransaction createObject(final ObjectSchema schema, final String id, final Map<String, Object> after) {

                transactions.computeIfAbsent(storage(schema), v -> v.write(consistency))
                        .createObject(schema, id, after);

                return this;
            }

            @Override
            public WriteTransaction updateObject(final ObjectSchema schema, final String id, final Map<String, Object> before, final Map<String, Object> after) {

                transactions.computeIfAbsent(storage(schema), v -> v.write(consistency))
                        .updateObject(schema, id, before, after);

                return this;
            }

            @Override
            public WriteTransaction deleteObject(final ObjectSchema schema, final String id, final Map<String, Object> before) {

                transactions.computeIfAbsent(storage(schema), v -> v.write(consistency))
                        .deleteObject(schema, id, before);

                return this;
            }

            @Override
            public CompletableFuture<BatchResponse> commit() {

                if(consistency != Consistency.NONE && transactions.size() > 1) {
                    throw new IllegalStateException("Consistent write transaction spanned multiple storage engines");
                } else {

                    return BatchResponse.mergeFutures(transactions.values().stream().map(WriteTransaction::commit));
                }
            }
        };
    }
}
