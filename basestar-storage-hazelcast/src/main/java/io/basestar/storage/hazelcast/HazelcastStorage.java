package io.basestar.storage.hazelcast;

/*-
 * #%L
 * basestar-storage-hazelcast
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionalMap;
import io.basestar.expression.Expression;
import io.basestar.schema.*;
import io.basestar.storage.BatchResponse;
import io.basestar.storage.Storage;
import io.basestar.storage.StorageTraits;
import io.basestar.storage.Versioning;
import io.basestar.storage.exception.ObjectExistsException;
import io.basestar.storage.exception.VersionMismatchException;
import io.basestar.storage.hazelcast.serde.CustomPortable;
import io.basestar.storage.hazelcast.serde.PortableSchemaFactory;
import io.basestar.util.*;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
public class HazelcastStorage implements Storage.WithWriteHistory, Storage.WithoutWriteIndex, Storage.WithoutAggregate, Storage.WithoutExpand, Storage.WithoutRepair {

    @Nonnull
    private final HazelcastInstance instance;

    @Nonnull
    private final HazelcastStrategy strategy;

    @Nonnull
    private final PortableSchemaFactory schemaFactory;

    private final LoadingCache<ObjectSchema, IMap<BatchResponse.Key, CustomPortable>> object;

    private final LoadingCache<ObjectSchema, IMap<BatchResponse.Key, CustomPortable>> history;

    private HazelcastStorage(final Builder builder) {

        this.instance = Nullsafe.require(builder.instance);
        this.strategy = Nullsafe.require(builder.strategy);
        this.schemaFactory = Nullsafe.require(builder.schemaFactory);
        this.object = CacheBuilder.newBuilder()
                .build(new CacheLoader<ObjectSchema, IMap<BatchResponse.Key, CustomPortable>>() {
                    @Override
                    public IMap<BatchResponse.Key, CustomPortable> load(final ObjectSchema s) {
                        return instance.getMap(strategy.objectMapName(s));
                    }
                });
        this.history = CacheBuilder.newBuilder()
                .build(new CacheLoader<ObjectSchema, IMap<BatchResponse.Key, CustomPortable>>() {
                    @Override
                    public IMap<BatchResponse.Key, CustomPortable> load(final ObjectSchema s) {
                        return instance.getMap(strategy.historyMapName(s));
                    }
                });
    }

    public static Builder builder() {

        return new Builder();
    }

    @Setter
    @Accessors(chain = true)
    public static class Builder {

        @Nullable
        private HazelcastInstance instance;

        @Nullable
        private HazelcastStrategy strategy;

        @Nullable
        private PortableSchemaFactory schemaFactory;

        public HazelcastStorage build() {

            return new HazelcastStorage(this);
        }
    }


    @Override
    public CompletableFuture<Map<String, Object>> readObject(final ObjectSchema schema, final String id, final Set<Name> expand) {

        try {
            final IMap<BatchResponse.Key, CustomPortable> map = object.get(schema);

            return map.getAsync(BatchResponse.Key.latest(schema.getQualifiedName(), id))
                    .thenApply(this::fromRecord)
                    .toCompletableFuture();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> fromRecord(final CustomPortable record) {

        return record == null ? null : record.getData();
    }

    private CustomPortable toRecord(final InstanceSchema schema, final Map<String, Object> data) {

        final CustomPortable record = schemaFactory.create(schema);
        record.setData(data);
        return record;
    }

    @Override
    public CompletableFuture<Map<String, Object>> readObjectVersion(final ObjectSchema schema, final String id, final long version, final Set<Name> expand) {

        try {
            final IMap<BatchResponse.Key, CustomPortable> map = history.get(schema);

            return map.getAsync(BatchResponse.Key.version(schema.getQualifiedName(), id, version))
                    .thenApply(this::fromRecord)
                    .toCompletableFuture();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Pager.Source<Map<String, Object>>> queryObject(final ObjectSchema schema, final Expression query, final List<Sort> sort, final Set<Name> expand) {

        return ImmutableList.of((count, token, stats) -> CompletableFuture.supplyAsync(() -> {
            try {

                final Predicate<BatchResponse.Key, CustomPortable> predicate = query.visit(new HazelcastExpressionVisitor<>());
                final IMap<BatchResponse.Key, CustomPortable> map = object.get(schema);

                final List<Map<String, Object>> results = new ArrayList<>();
                for (final Map.Entry<BatchResponse.Key, CustomPortable> entry : map.entrySet(predicate)) {
                    results.add(fromRecord(entry.getValue()));
                }
                // FIXME: need to check sorting
                return new Page<>(results, null);

            } catch (final ExecutionException e) {
                throw new IllegalStateException(e);
            }

        }));
    }

    @Override
    public ReadTransaction read(final Consistency consistency) {

        return new ReadTransaction() {

            private final Map<String, Set<BatchResponse.Key>> requests = new HashMap<>();

            @Override
            public ReadTransaction readObject(final ObjectSchema schema, final String id, final Set<Name> expand) {

                final String target = strategy.objectMapName(schema);
                requests.computeIfAbsent(target, ignored -> new HashSet<>())
                        .add(BatchResponse.Key.latest(schema.getQualifiedName(), id));
                return this;
            }

            @Override
            public ReadTransaction readObjectVersion(final ObjectSchema schema, final String id, final long version, final Set<Name> expand) {

                final String target = strategy.historyMapName(schema);
                requests.computeIfAbsent(target, ignored -> new HashSet<>())
                        .add(BatchResponse.Key.version(schema.getQualifiedName(), id, version));
                return this;
            }

            @Override
            public CompletableFuture<BatchResponse> read() {

                final List<CompletableFuture<? extends BatchResponse>> futures = requests.entrySet().stream()
                        .map(entry -> {
                            final String target = entry.getKey();
                            final IMap<BatchResponse.Key, Map<String, Object>> map = instance.getMap(target);
                            return CompletableFuture
                                    .supplyAsync(() -> new BatchResponse.Basic(map.getAll(entry.getValue())));
                        }).collect(Collectors.toList());

                return BatchResponse.mergeFutures(futures.stream());
            }
        };
    }

    private interface WriteAction extends Serializable  {

        CustomPortable apply(BatchResponse.Key key, CustomPortable value);
    }

    protected class WriteTransaction implements WithWriteHistory.WriteTransaction {

        private final Map<String, Map<BatchResponse.Key, WriteAction>> requests = new IdentityHashMap<>();

        @Override
        public WriteTransaction createObject(final ObjectSchema schema, final String id, final Map<String, Object> after) {

            final String target = strategy.objectMapName(schema);
            final Name schemaName = schema.getQualifiedName();
            requests.computeIfAbsent(target, ignored -> new HashMap<>())
                    .put(BatchResponse.Key.latest(schemaName, id), (key, value) -> {
                        if(value == null) {
                            return toRecord(schema, after);
                        } else {
                            throw new ObjectExistsException(schemaName, id);
                        }
                    });

            return createHistory(schema, id, after);
        }

        private boolean checkExists(final Map<String, Object> current, final Long version) {

            if(current == null) {
                return false;
            } else if(version != null) {
                return version.equals(Instance.getVersion(current));
            } else {
                return true;
            }
        }

        @Override
        public WriteTransaction updateObject(final ObjectSchema schema, final String id, final Map<String, Object> before, final Map<String, Object> after) {

            final String target = strategy.objectMapName(schema);
            final Name schemaName = schema.getQualifiedName();
            final Long version = before == null ? null : Instance.getVersion(before);
            requests.computeIfAbsent(target, ignored -> new HashMap<>())
                    .put(BatchResponse.Key.latest(schemaName, id), (key, value) -> {
                        final Map<String, Object> current = fromRecord(value);
                        if(checkExists(current, version)) {
                            return toRecord(schema, after);
                        } else {
                            throw new VersionMismatchException(schemaName, id, version);
                        }
                    });

            return createHistory(schema, id, after);
        }

        @Override
        public WriteTransaction deleteObject(final ObjectSchema schema, final String id, final Map<String, Object> before) {

            final String target = strategy.objectMapName(schema);
            final Name schemaName = schema.getQualifiedName();
            final Long version = before == null ? null : Instance.getVersion(before);
            requests.computeIfAbsent(target, ignored -> new HashMap<>())
                    .put(BatchResponse.Key.latest(schemaName, id), (key, value) -> {
                        final Map<String, Object> current = fromRecord(value);
                        if(checkExists(current, version)) {
                            return null;
                        } else {
                            throw new VersionMismatchException(schemaName, id, version);
                        }
                    });
            return this;
        }

        @Override
        public WriteTransaction createHistory(final ObjectSchema schema, final String id, final long version, final Map<String, Object> after) {

            final String target = strategy.historyMapName(schema);
            final Name schemaName = schema.getQualifiedName();
            requests.computeIfAbsent(target, ignored -> new HashMap<>())
                    .put(BatchResponse.Key.version(schemaName, id, version), (key, value) -> toRecord(schema, after));
            return this;
        }

        private WriteTransaction createHistory(final ObjectSchema schema, final String id, final Map<String, Object> after) {

            final History history = schema.getHistory();
            if(history.isEnabled() && history.getConsistency(Consistency.ATOMIC).isStronger(Consistency.ASYNC)) {
                final Long afterVersion = Instance.getVersion(after);
                assert afterVersion != null;
                return createHistory(schema, id, afterVersion, after);
            } else {
                return this;
            }
        }

        @Override
        public CompletableFuture<BatchResponse> write() {

            return CompletableFuture.supplyAsync(() -> {

                final TransactionOptions options = new TransactionOptions().setTransactionType(TransactionOptions.TransactionType.TWO_PHASE);
                final TransactionContext context = instance.newTransactionContext(options);

                context.beginTransaction();

                final Map<BatchResponse.Key, Map<String, Object>> results = new HashMap<>();

                try {
                    requests.forEach((target, actions) -> {
                        final TransactionalMap<BatchResponse.Key, CustomPortable> map = context.getMap(target);

                        actions.forEach((key, action) -> {
                            final CustomPortable oldValue = map.getForUpdate(key);
                            final CustomPortable newValue = action.apply(key, oldValue);
                            if(newValue != null) {
                                map.put(key, newValue);
                            } else if(oldValue != null) {
                                map.delete(key);
                            }
                        });
                    });
                    context.commitTransaction();
                } catch (final Throwable e) {
                    log.error("Rolling back", e);
                    context.rollbackTransaction();
                    throw e;
                }

                return new BatchResponse.Basic(results);
            });
        }
    }

    @Override
    public WriteTransaction write(final Consistency consistency, final Versioning versioning) {

        return new WriteTransaction();
    }

    @Override
    public EventStrategy eventStrategy(final ObjectSchema schema) {

        return EventStrategy.EMIT;
    }

    @Override
    public StorageTraits storageTraits(final ObjectSchema schema) {

        return HazelcastStorageTraits.INSTANCE;
    }
}
