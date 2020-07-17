package io.basestar.database;

/*-
 * #%L
 * basestar-database-server
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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import io.basestar.auth.Caller;
import io.basestar.database.util.ExpandKey;
import io.basestar.database.util.LinkKey;
import io.basestar.database.util.RefKey;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.schema.*;
import io.basestar.schema.util.Expander;
import io.basestar.storage.Storage;
import io.basestar.storage.util.Pager;
import io.basestar.util.Name;
import io.basestar.util.PagedList;
import io.basestar.util.PagingToken;
import io.basestar.util.Sort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Provides read implementation for database, does not apply permissions, the database needs to handle applying
 * permissions to returned data, and calculating the necessary expand sets to be able to apply permissions.
 */

@Slf4j
@RequiredArgsConstructor
public class ReadProcessor {

    private static final int EXPAND_LINK_SIZE = 100;

    protected final Namespace namespace;

    protected final Storage storage;

    protected ObjectSchema objectSchema(final Name schema) {

        return namespace.requireObjectSchema(schema);
    }

    protected CompletableFuture<Instance> readImpl(final ObjectSchema objectSchema, final String id, final Long version) {

        // Will make 2 reads if the request schema doesn't match result schema

        return readRaw(objectSchema, id, version).thenCompose(raw -> cast(objectSchema, raw));
    }

    private CompletableFuture<Map<String, Object>> readRaw(final ObjectSchema objectSchema, final String id, final Long version) {

        if (version == null) {
            return storage.readObject(objectSchema, id);
        } else {
            return storage.readObject(objectSchema, id)
                    .thenCompose(current -> {
                        if (current == null) {
                            return CompletableFuture.completedFuture(null);
                        } else {
                            final Long currentVersion = Instance.getVersion(current);
                            assert currentVersion != null;
                            if (currentVersion.equals(version)) {
                                return CompletableFuture.completedFuture(current);
                            } else if (version > currentVersion) {
                                return CompletableFuture.completedFuture(null);
                            } else {
                                return storage.readObjectVersion(objectSchema, id, version);
                            }
                        }
                    });
        }
    }

    protected CompletableFuture<PagedList<Instance>> queryLinkImpl(final Context context, final Link link, final Instance owner,
                                                                   final int count, final PagingToken paging) {

        final Expression expression = link.getExpression()
                .bind(context.with(ImmutableMap.of(
                        Reserved.THIS, owner
                )));

        //FIXME: must support views
        final ObjectSchema linkSchema = (ObjectSchema)link.getSchema();
        return queryImpl(context, linkSchema, expression, link.getSort(), count, paging);
    }

    protected CompletableFuture<PagedList<Instance>> queryImpl(final Context context, final ObjectSchema objectSchema, final Expression expression,
                                                               final List<Sort> sort, final int count, final PagingToken paging) {

        final List<Sort> pageSort = ImmutableList.<Sort>builder()
                .addAll(sort)
                .add(Sort.asc(Name.of(Reserved.ID)))
                .build();

        final List<Pager.Source<Instance>> sources = storage.query(objectSchema, expression, sort).stream()
                .map(source -> (Pager.Source<Instance>) (c, t, stats) -> source.page(c, t, stats)
                        .thenCompose(data -> cast(objectSchema, data)))
                .collect(Collectors.toList());

        return pageImpl(context, sources, expression, pageSort, count, paging);
    }

    @SuppressWarnings("rawtypes")
    protected CompletableFuture<PagedList<Instance>> pageImpl(final Context context, final List<Pager.Source<Instance>> sources, final Expression expression,
                                                              final List<Sort> sort, final int count, final PagingToken paging) {

        if(sources.isEmpty()) {
            throw new IllegalStateException("Query not supported");
        } else {

            @SuppressWarnings("unchecked")
            final Comparator<Instance> comparator = Sort.comparator(sort, (t, path) -> (Comparable)path.apply(t));
            final Pager<Instance> pager = new Pager<>(comparator, sources, paging);
            return pager.page(count)
                    .thenApply(results -> {
                        // Expressions that could not be pushed down are applied after auto-paging.
                        return results.filter(instance -> {
                            try {
                                return expression.evaluatePredicate(context.with(instance));
                            } catch (final Exception e) {
                                // FIXME:
                                log.info("Failed to evaluate predicate", e);
                                return false;
                            }
                        });
                    });
        }
    }

    protected CompletableFuture<Instance> expand(final Context context, final Instance item, final Set<Name> expand) {

        if(item == null) {
            return CompletableFuture.completedFuture(null);
        } else if(expand == null || expand.isEmpty()) {
            return CompletableFuture.completedFuture(item);
        } else {
            final ExpandKey<RefKey> expandKey = ExpandKey.from(RefKey.from(item), expand);
            return expand(context, Collections.singletonMap(expandKey, item))
                    .thenApply(results -> results.get(expandKey));
        }
    }

    protected CompletableFuture<PagedList<Instance>> expand(final Context context, final PagedList<Instance> items, final Set<Name> expand) {

        if(items == null) {
            return CompletableFuture.completedFuture(null);
        } else if(expand == null || expand.isEmpty() || items.isEmpty()) {
            return CompletableFuture.completedFuture(items);
        } else {
            final Map<ExpandKey<RefKey>, Instance> expandKeys = items.stream()
                    .collect(Collectors.toMap(
                            item -> ExpandKey.from(RefKey.from(item), expand),
                            item -> item
                    ));
            return expand(context, expandKeys)
                    .thenApply(expanded -> items.withPage(
                            items.stream()
                                    .map(v -> expanded.get(ExpandKey.from(RefKey.from(v), expand)))
                                    .collect(Collectors.toList())
                    ));
        }
    }

    protected CompletableFuture<Map<ExpandKey<RefKey>, Instance>> expand(final Context context, final Map<ExpandKey<RefKey>, Instance> items) {

        return expandImpl(context, items)
                .thenApply(results -> {
                    final Map<ExpandKey<RefKey>, Instance> evaluated = new HashMap<>();
                    results.forEach((k, v) -> {
                        final ObjectSchema schema = objectSchema(Instance.getSchema(v));
                        evaluated.put(k, schema.evaluateTransients(context, v, k.getExpand()));
                    });
                    return evaluated;
                });
    }

    // FIXME: poor performance, need to batch more
    protected CompletableFuture<Map<ExpandKey<RefKey>, Instance>> expandImpl(final Context context, final Map<ExpandKey<RefKey>, Instance> items) {

        final Set<ExpandKey<RefKey>> refs = new HashSet<>();
        final Map<ExpandKey<LinkKey>, CompletableFuture<PagedList<Instance>>> links = new HashMap<>();

        final Consistency consistency = Consistency.ATOMIC;

        items.forEach((ref, object) -> {
            if(!ref.getExpand().isEmpty()) {
                final ObjectSchema schema = objectSchema(ref.getKey().getSchema());
                schema.expand(object, new Expander() {
                    @Override
                    public Instance expandRef(final ObjectSchema schema, final Instance ref, final Set<Name> expand) {

                        if(ref == null) {
                            return null;
                        } else {
                            refs.add(ExpandKey.from(RefKey.from(schema, ref), expand));
                            return ref;
                        }
                    }

                    @Override
                    public PagedList<Instance> expandLink(final Link link, final PagedList<Instance> value, final Set<Name> expand) {

                        final RefKey refKey = ref.getKey();
                        final ExpandKey<LinkKey> linkKey = ExpandKey.from(LinkKey.from(refKey, link.getName()), expand);
                        log.debug("Expanding link: {}", linkKey);
                        // FIXME: do we need to pre-expand here? original implementation did
                        links.put(linkKey, queryLinkImpl(context, link, object, EXPAND_LINK_SIZE, null)
                                .thenCompose(results -> expand(context, results, expand)));
                        return null;
                    }
                }, ref.getExpand());
            }
        });

        if(refs.isEmpty() && links.isEmpty()) {

            return CompletableFuture.completedFuture(items);

        } else {

            return CompletableFuture.allOf(links.values().toArray(new CompletableFuture<?>[0])).thenCompose(ignored -> {

                if(!refs.isEmpty()) {
                    log.debug("Expanding refs: {}", refs);
                }

                final Storage.ReadTransaction readTransaction = storage.read(consistency);
                refs.forEach(ref -> {
                    final RefKey refKey = ref.getKey();
                    final ObjectSchema objectSchema = objectSchema(refKey.getSchema());
                    readTransaction.readObject(objectSchema, refKey.getId());
                });

                return readTransaction.read().thenCompose(results -> {

                    final Map<ExpandKey<RefKey>, Instance> resolved = new HashMap<>();
                    for (final Map<String, Object> initial : results.values()) {

                        final Name schemaName = Instance.getSchema(initial);
                        final String id = Instance.getId(initial);
                        final ObjectSchema schema = objectSchema(schemaName);
                        final Instance object = schema.create(initial);

                        refs.forEach(ref -> {
                            final RefKey refKey = ref.getKey();
                            if (refKey.getId().equals(id)) {
                                resolved.put(ExpandKey.from(refKey, ref.getExpand()), object);
                            }
                        });
                    }

                    return cast(resolved).thenCompose(v -> expand(context, v)).thenApply(expanded -> {

                        final Map<ExpandKey<RefKey>, Instance> result = new HashMap<>();

                        items.forEach((ref, object) -> {
                            final RefKey refKey = ref.getKey();
                            final ObjectSchema schema = objectSchema(refKey.getSchema());

                            result.put(ref, schema.expand(object, new Expander() {
                                @Override
                                public Instance expandRef(final ObjectSchema schema, final Instance ref, final Set<Name> expand) {

                                    final ExpandKey<RefKey> expandKey = ExpandKey.from(RefKey.from(schema, ref), expand);
                                    Instance result = expanded.get(expandKey);
                                    if (result == null) {
                                        result = ObjectSchema.ref(Instance.getId(ref));
                                    }
                                    return result;
                                }

                                @Override
                                public PagedList<Instance> expandLink(final Link link, final PagedList<Instance> value, final Set<Name> expand) {

                                    final ExpandKey<LinkKey> linkKey = ExpandKey.from(LinkKey.from(refKey, link.getName()), expand);
                                    final CompletableFuture<PagedList<Instance>> future = links.get(linkKey);
                                    if(future != null) {
                                        final PagedList<Instance> result = future.getNow(null);
                                        assert result != null;
                                        return result;
                                    } else {
                                        return null;
                                    }
                                }
                            }, ref.getExpand()));

                        });

                        return result;
                    });

                });
            });
        }
    }

    protected CompletableFuture<Instance> cast(final ObjectSchema baseSchema, final Map<String, Object> data) {

        if(data == null) {
            return CompletableFuture.completedFuture(null);
        }
        final Name castSchemaName = Instance.getSchema(data);
        if(baseSchema.getQualifiedName().equals(castSchemaName)) {
            return CompletableFuture.completedFuture(baseSchema.create(data));
        } else {
            final String id = Instance.getId(data);
            final Long version = Instance.getVersion(data);
            final ObjectSchema castSchema = objectSchema(castSchemaName);
            return readRaw(castSchema, id, version)
                    .thenApply(castSchema::create);
        }
    }

    protected CompletableFuture<PagedList<Instance>> cast(final ObjectSchema baseSchema, final PagedList<? extends Map<String, Object>> data) {

        final Multimap<Name, Map<String, Object>> needed = ArrayListMultimap.create();
        data.forEach(v -> {
            final Name actualSchema = Instance.getSchema(v);
            if(!baseSchema.getQualifiedName().equals(actualSchema)) {
                needed.put(actualSchema, v);
            }
        });
        if(needed.isEmpty()) {
            return CompletableFuture.completedFuture(data.map(v -> {
                final ObjectSchema schema = objectSchema(Instance.getSchema(v));
                return schema.create(v, true, true);
            }));
        } else {
            final Storage.ReadTransaction readTransaction = storage.read(Consistency.NONE);
            needed.asMap().forEach((schemaName, items) -> {
                final ObjectSchema schema = objectSchema(schemaName);
                items.forEach(item -> {
                    final String id = Instance.getId(item);
                    final Long version = Instance.getVersion(item);
                    assert version != null;
                    readTransaction.readObjectVersion(schema, id, version);
                });
            });
            return readTransaction.read().thenApply(results -> {

                final Map<RefKey, Map<String, Object>> mapped = new HashMap<>();
                results.forEach((key, result) -> mapped.put(new RefKey(key.getSchema(), key.getId()), result));

                return data.map(v -> {
                    final RefKey key = RefKey.from(v);
                    final Map<String, Object> result = MoreObjects.firstNonNull(mapped.get(key), v);
                    final ObjectSchema schema = objectSchema(Instance.getSchema(result));
                    return schema.create(result, true, true);
                });
            });
        }
    }

    protected CompletableFuture<Map<ExpandKey<RefKey>, Instance>> cast(final Map<ExpandKey<RefKey>, ? extends Map<String, Object>> data) {

        final Multimap<Name, Map<String, Object>> needed = ArrayListMultimap.create();
        data.forEach((k, v) -> {
            final Name actualSchema = Instance.getSchema(v);
            if(!k.getKey().getSchema().equals(actualSchema)) {
                needed.put(actualSchema, v);
            }
        });
        if(needed.isEmpty()) {
            final Map<ExpandKey<RefKey>, Instance> results = new HashMap<>();
            data.forEach((k, v) -> {
                final ObjectSchema schema = objectSchema(Instance.getSchema(v));
                final Instance instance = schema.create(v);
                results.put(k, instance);
            });
            return CompletableFuture.completedFuture(results);
        } else {
            final Storage.ReadTransaction readTransaction = storage.read(Consistency.NONE);
            needed.asMap().forEach((schemaName, items) -> {
                final ObjectSchema schema = objectSchema(schemaName);
                items.forEach(item -> {
                    final String id = Instance.getId(item);
                    final Long version = Instance.getVersion(item);
                    assert version != null;
                    readTransaction.readObjectVersion(schema, id, version);
                });
            });
            return readTransaction.read().thenApply(results -> {

                final Map<RefKey, Map<String, Object>> mapped = new HashMap<>();
                results.forEach((key, result) -> mapped.put(new RefKey(key.getSchema(), key.getId()), result));

                final Map<ExpandKey<RefKey>, Instance> remapped = new HashMap<>();
                data.forEach((k, v) ->  {
                    final RefKey key = RefKey.from(v);
                    final Map<String, Object> result = MoreObjects.firstNonNull(mapped.get(key), v);
                    final ObjectSchema schema = objectSchema(Instance.getSchema(result));
                    final Instance instance = schema.create(result);
                    remapped.put(k, instance);
                });
                return remapped;
            });
        }
    }

    protected CompletableFuture<Caller> expandCaller(final Context context, final Caller caller, final Set<Name> expand) {

        if(caller.getId() == null || caller.isSuper()) {

            return CompletableFuture.completedFuture(caller instanceof ExpandedCaller ? caller : new ExpandedCaller(caller, null));

        } else {

            if(caller instanceof ExpandedCaller) {

                final Instance object = ((ExpandedCaller)caller).getObject();
                return expand(context, object, expand)
                        .thenApply(result -> result == object ? caller : new ExpandedCaller(caller, result));

            } else {

                if(caller.getSchema() != null) {
                    final Schema<?> schema = namespace.getSchema(caller.getSchema());
                    if(schema instanceof ObjectSchema) {
                        return readImpl((ObjectSchema)schema, caller.getId(), null)
                                .thenCompose(unexpanded -> expand(context, unexpanded, expand))
                                .thenApply(result -> new ExpandedCaller(caller, result));
                    }
                }
                return CompletableFuture.completedFuture(new ExpandedCaller(caller, null));
            }
        }
    }

    protected static class ExpandedCaller extends Caller.Delegating {

        private final Instance object;

        public ExpandedCaller(final Caller caller, final Instance object) {

            super(caller);
            this.object = object != null ? object : getObject(caller);
        }

        public static Instance getObject(final Caller caller) {

            if(caller instanceof ExpandedCaller) {
                final Map<String, Object> object = ((ExpandedCaller)caller).getObject();
                if(object != null) {
                    return new Instance(object);
                }
            }
            final HashMap<String, Object> object = new HashMap<>();
            object.put(Reserved.ID, caller.getId());
            object.put(Reserved.SCHEMA, caller.getSchema());
            return new Instance(object);
        }

        public Instance getObject() {

            return object;
        }
    }
}
