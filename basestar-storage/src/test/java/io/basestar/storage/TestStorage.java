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

import com.google.common.base.Charsets;
import com.google.common.collect.*;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.aggregate.Count;
import io.basestar.expression.type.Values;
import io.basestar.schema.*;
import io.basestar.storage.exception.ObjectExistsException;
import io.basestar.storage.exception.VersionMismatchException;
import io.basestar.util.Streams;
import io.basestar.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class TestStorage {

    private static final int RECORD_COUNT = 100;

    private static final String ADDRESS = "Address";

    private static final String SIMPLE = "Simple";

    private static final String POINTSET = "Pointset";

    private static final String REF_TARGET = "RefTarget";

    private static final String REF_SOURCE = "RefSource";

    private static final String DATE_SORT = "DateSort";

    private final Namespace namespace;

    public TestStorage() {

        try {
            this.namespace = Namespace.load(TestStorage.class.getResource("schema.yml"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected Storage storage(final Namespace namespace) {

        return storage(namespace, HashMultimap.create());
    }

    protected abstract Storage storage(final Namespace namespace, final Multimap<String, Map<String, Object>> data);

    // FIXME: merge with createComplete

    protected void writeAll(final Storage storage, final Namespace namespace, final Multimap<String, Map<String, Object>> data) {

        if(!data.isEmpty()) {
            final Storage.WriteTransaction write = storage.write(Consistency.QUORUM, Versioning.CHECKED);

            data.asMap().forEach((k, vs) -> {
                final ObjectSchema schema = namespace.requireObjectSchema(k);
                vs.forEach(v -> {
                    final Instance instance = schema.create(v);
                    final String id = Instance.getId(v);
                    write.createObject(schema, id, instance);
                });
            });

            write.write().join();
        }
    }

    // FIXME: merge with createComplete

    protected Multimap<String, Map<String, Object>> loadAddresses() throws IOException {

        final Instant now = Instant.now();
        final Multimap<String, Map<String, Object>> results = ArrayListMultimap.create();

        try(final InputStream is = TestStorage.class.getResourceAsStream("addresses.csv")) {
            final CSVParser parser = CSVParser.parse(is, Charsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader());
            final List<String> headers = parser.getHeaderNames();
            Streams.stream(parser).limit(RECORD_COUNT).forEach(record -> {

                final String id = UUID.randomUUID().toString();

                final Map<String, Object> data = new HashMap<>();
                headers.forEach(h -> data.put(h, record.get(h)));
                Instance.setId(data, id);
                Instance.setVersion(data, 1L);
                Instance.setCreated(data, now);
                Instance.setUpdated(data, now);

                results.put(ADDRESS, data);
            });
        }

        return results;
    }

    @Test
    public void testIndexes() throws IOException {

        final Storage storage = storage(namespace, loadAddresses());

        final ObjectSchema schema = namespace.requireObjectSchema(ADDRESS);

        assumeConcurrentObjectWrite(storage, schema);

        final List<Sort> sort = ImmutableList.of(
                Sort.asc(Name.of("city")),
                Sort.asc(Name.of("zip"))
        );

        final Expression expr = Expression.parse("country == 'United Kingdom' || state == 'Victoria'");
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expr, Collections.emptyList(), Collections.emptySet());
        final Comparator<Map<String, Object>> comparator = Instance.comparator(sort);
        final Page<Map<String, Object>> results = new Pager<>(comparator, sources, null).page(100).join();
        assertEquals(8, results.size());
    }

    // FIXME: needs to cover non-trivial case(s)

    @Test
    public void testSortAndPaging() {

        final Instant now = Instant.now();

        // Horrible index usage, but high storage support
        final String country = UUID.randomUUID().toString();
        final Multimap<String, Map<String, Object>> init = HashMultimap.create();
        for(int i = 0; i != 100; ++i) {

            final String id = UUID.randomUUID().toString();

            final Map<String, Object> data = new HashMap<>();
            data.put("country", country);
            data.put("city", UUID.randomUUID().toString());
            data.put("zip", UUID.randomUUID().toString());
            Instance.setId(data, id);
            Instance.setVersion(data, 1L);
            Instance.setCreated(data, now);
            Instance.setUpdated(data, now);

            init.put(ADDRESS, data);
        }

        final Storage storage = storage(namespace, init);

        final ObjectSchema schema = namespace.requireObjectSchema(ADDRESS);

        assumeConcurrentObjectWrite(storage, schema);

        final List<Sort> sort = ImmutableList.of(
                Sort.asc(Name.of("city")),
                Sort.asc(Name.of("zip"))
        );

        final Expression expr = Expression.parse("country == '" + country + "'");
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expr, sort, Collections.emptySet());
        final Comparator<Map<String, Object>> comparator = Instance.comparator(sort);

        final List<Map<String, Object>> results = new ArrayList<>();
        PagingToken paging = null;
        for(int i = 0; i != 10; ++i) {
            final Pager<Map<String, Object>> pager = new Pager<>(comparator, sources, paging);
            final Page<Map<String, Object>> page = pager.page(10).join();
            results.addAll(page);
            paging = page.getPaging();
            assertNotNull(paging);
        }
        assertEquals(100, results.size());
        final Page<Map<String, Object>> empty = new Pager<>(comparator, sources, paging).page(10).join();
        assertEquals(0, empty.size());
        assertNull(empty.getPaging());
        assertTrue(Ordering.from(comparator).isOrdered(results));
    }

    @Test
    public void testCreate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Map<String, Object> data = data();

        final Instance after = instance(schema, id, 1L, data);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, after)
                .write().join();

        final Map<String, Object> current = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());
        assertNotNull(current);
        assertEquals(1, Instance.getVersion(current));
        data.forEach((k, v) -> {
            final Object v2 = current.get(k);
            assertTrue(Values.equals(v, v2), k + ": " + v + " != " + v2);
        });

        if(storage.storageTraits(schema).getHistoryConsistency().isStronger(Consistency.EVENTUAL)) {
            final Map<String, Object> v1 = storage.readObjectVersion(schema, id, 1L, ImmutableSet.of()).join();
            assertNotNull(v1);
            assertEquals(1, Instance.getVersion(v1));
        }
    }

    @Test
    public void testEmptyString() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Map<String, Object> data = ImmutableMap.of("string", "");

        final Instance after = instance(schema, id, 1L, data);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, after)
                .write().join();

        final Map<String, Object> current = storage.readObject(schema, id, ImmutableSet.of()).join();
        assertNotNull(current);
        assertEquals("", current.get("string"));
    }

    private Map<String, Object> data() {

        return ImmutableMap.<String, Object>builder()
                .put("boolean", true)
                .put("integer", 1L)
                .put("number", 2.5)
                .put("string", "test")
                .put("binary", new byte[]{1, 2, 3, 4})
                .put("date", ISO8601.parseDate("2030-01-01"))
                .put("datetime", ISO8601.parseDateTime("2030-01-01T01:02:03Z"))
                .put("struct", new Instance(ImmutableMap.of("x", 1L, "y", 5L)))
                .put("object", new Instance(ImmutableMap.of("id", "test")))
                .put("arrayBoolean", Collections.singletonList(true))
                .put("arrayInteger", Collections.singletonList(1L))
                .put("arrayNumber", Collections.singletonList(2.5))
                .put("arrayString", Collections.singletonList("test"))
                .put("arrayBinary", Collections.singletonList(new byte[]{1, 2, 3, 4}))
                .put("arrayStruct", Collections.singletonList(new Instance(ImmutableMap.of("x", 10L, "y", 5L))))
                .put("arrayObject", Collections.singletonList(new Instance(ImmutableMap.of("id", "test"))))
                .put("arrayDate", Collections.singletonList(ISO8601.parseDate("2030-01-01")))
                .put("arrayDatetime", Collections.singletonList(ISO8601.parseDateTime("2030-01-01T01:02:03Z")))
                .put("mapBoolean", Collections.singletonMap("a", true))
                .put("mapInteger", Collections.singletonMap("a", 1L))
                .put("mapNumber", Collections.singletonMap("a", 2.5))
                .put("mapString", Collections.singletonMap("a", "test"))
                .put("mapBinary", Collections.singletonMap("a", new byte[]{1, 2, 3, 4}))
                .put("mapStruct", Collections.singletonMap("a",new Instance(ImmutableMap.of("x", 10L, "y", 5L))))
                .put("mapObject", Collections.singletonMap("a", new Instance(ImmutableMap.of("id", "test"))))
                .put("mapDate", Collections.singletonMap("a", ISO8601.parseDate("2030-01-01")))
                .put("mapDatetime", Collections.singletonMap("a", ISO8601.parseDateTime("2030-01-01T01:02:03Z")))
                .build();
    }

    @Test
    public void testUpdate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());
        assertEquals(1L, before.getVersion());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .write().join();

        final Map<String, Object> current = storage.readObject(schema, id, ImmutableSet.of()).join();
        assertNotNull(current);
        assertEquals(2, Instance.getVersion(current));
        if(storage.storageTraits(schema).getHistoryConsistency().isStronger(Consistency.EVENTUAL)) {
            final Map<String, Object> v2 = storage.readObjectVersion(schema, id, 2L, ImmutableSet.of()).join();
            assertNotNull(v2);
            assertEquals(2, Instance.getVersion(v2));
        }
    }

    private Map<String, Object> setVersion(final Map<String, Object> before, final long version) {

        final Map<String, Object> copy = new HashMap<>(before);
        Instance.setVersion(copy, version);
        return copy;
    }

    @Test
    public void testDelete() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, setVersion(before, 1L))
                .write().join();

        final Map<String, Object> current = storage.readObject(schema, id, ImmutableSet.of()).join();
        assertNull(current);
        // FIXME
//        if(storage.storageTraits(schema).getHistoryConsistency().isStronger(Consistency.EVENTUAL)) {
//            final Map<String, Object> v1 = storage.readObjectVersion(schema, id, 1L).join();
//            assertNull(v1);
//        }
    }

    @Test
    public void testLarge() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

//        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final StringBuilder str = new StringBuilder();
        for(int i = 0; i != 1000000; ++i) {
            str.append("test");
        }

        final Map<String, Object> data = new HashMap<>();
        data.put("string", str.toString());
        final Instance instance = instance(schema, id, 1L, data);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, instance)
                .write().join();

        final BatchResponse results = storage.read(Consistency.ATOMIC)
                .readObject(schema, id, ImmutableSet.of())
                .read().join();

        assertEquals(1, results.size());
    }

    @Test
    public void testCreateConflict() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, after)
                .write().join();

        assertCause(ObjectExistsException.class, () -> storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                    .createObject(schema, id, after)
                    .write().get());
    }

    @Test
    public void testUpdateMissing() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, setVersion(before, 1L))
                .write().join();

        final Instance after = instance(schema, id, 2L);

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .write().get());
    }

    @Test
    public void testDeleteMissing() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, setVersion(before, 1L))
                .write().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, setVersion(before, 1L))
                .write().get());
    }

    @Test
    public void testDeleteWrongVersion() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .write().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, setVersion(before, 1L))
                .write().get());
    }

    @Test
    public void testUpdateWrongVersion() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, init)
                .write().join();

        final Instance before = schema.create(storage.readObject(schema, id, ImmutableSet.of()).join());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .write().join();

//        storage.write(Consistency.ATOMIC)
//                .updateObject(schema, id, 1L, before, after)
//                .commit().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .write().get());
    }

    @Test
    public void testMultiValueIndex() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(POINTSET);

        assumeConcurrentObjectWrite(storage, schema);

        createComplete(storage, schema, ImmutableMap.of(
                "points", ImmutableList.of(
                        new Instance(ImmutableMap.of("x", 10L, "y", 100L)),
                        new Instance(ImmutableMap.of("x", 5L, "y", 10L))
                )
        ));

        createComplete(storage, schema, ImmutableMap.of(
                "points", ImmutableList.of(
                        new Instance(ImmutableMap.of("x", 10L, "y", 10L)),
                        new Instance(ImmutableMap.of("x", 1L, "y", 10L))
                )
        ));

        final List<Sort> sort = ImmutableList.of(Sort.asc(Name.of(ObjectSchema.ID)));
        final Expression expr = Expression.parse("p.x == 10 && p.y == 100 for any p of points");
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expr, Collections.emptyList(), Collections.emptySet());
        final Comparator<Map<String, Object>> comparator = Instance.comparator(sort);
        final Page<Map<String, Object>> results = new Pager<>(comparator, sources, null).page(100).join();
        assertEquals(1, results.size());
    }

    @Test
    public void testNullBeforeUpdate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, instance(schema, id, 1L))
                .write().join();

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .updateObject(schema, id, null, instance(schema, id, 2L))
                .write().join();
    }

    @Test
    public void testNullBeforeDelete() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .createObject(schema, id, instance(schema, id, 1L))
                .write().join();

        storage.write(Consistency.ATOMIC, Versioning.CHECKED)
                .deleteObject(schema, id, null)
                .write().join();
    }

    @Test
    public void testLike() throws IOException {

        assumeTrue(supportsLike());

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(ADDRESS);

        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "london"
        ));
        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "London"
        ));
        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "l%ndon"
        ));
        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "L_ndon"
        ));
        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "L*ndon"
        ));
        createComplete(storage, schema, ImmutableMap.of(
                "country", "United Kingdom",
                "city", "l?ndon"
        ));

        final List<Sort> sort = ImmutableList.of(
                Sort.asc(Name.of("city")),
                Sort.asc(Name.of("zip"))
        );

        assertEquals(6, page(storage, schema, Expression.parse("country == 'United Kingdom' && city ILIKE 'l%'"), sort, 10).size());
        assertEquals(3, page(storage, schema, Expression.parse("country == 'United Kingdom' && city LIKE 'l%'"), sort, 10).size());
        assertEquals(1, page(storage, schema, Expression.parse("country == 'United Kingdom' && city LIKE 'l\\\\%n_on'"), sort, 10).size());
        assertEquals(1, page(storage, schema, Expression.parse("country == 'United Kingdom' && city LIKE 'L\\\\_n_on'"), sort, 10).size());
        assertEquals(1, page(storage, schema, Expression.parse("country == 'United Kingdom' && city LIKE 'l\\\\?n_on'"), sort, 10).size());
        assertEquals(1, page(storage, schema, Expression.parse("country == 'United Kingdom' && city LIKE 'L\\\\*n_on'"), sort, 10).size());
    }

    protected boolean supportsLike() {

        return false;
    }

    @Test
    public void testAggregation() throws IOException {

        final Storage storage = storage(namespace, loadAddresses());

        final ObjectSchema schema = namespace.requireObjectSchema(ADDRESS);

        assumeTrue(storage.storageTraits(schema).supportsAggregation(),
                "Aggregation must be enabled for this test");

        final List<Sort> sort = ImmutableList.of(Sort.asc(Name.of("country")), Sort.asc(Name.of(ObjectSchema.ID)));
        final List<Pager.Source<Map<String, Object>>> sources = storage.aggregate(schema, Expression.parse("true"),
                ImmutableMap.of("country", Expression.parse("country")),
                ImmutableMap.of("count", new Count()));
        final Comparator<Map<String, Object>> comparator = Instance.comparator(sort);

        final Page<Map<String, Object>> results = new Pager<>(comparator, sources, null).page(100).join();
        //assertEquals(?, results.size());
    }

    @Test
    public void testRefIndex() {

        final Storage storage = storage(namespace);

        final ObjectSchema target = namespace.requireObjectSchema(REF_TARGET);
        final ObjectSchema source = namespace.requireObjectSchema(REF_SOURCE);

        final Set<Name> expand = Name.parseSet("target");

        assumeConcurrentObjectWrite(storage, target);
        assumeConcurrentObjectWrite(storage, source);

        final String targetId = createComplete(storage, target, ImmutableMap.of());
        createComplete(storage, source, ImmutableMap.of(
                "target", new Instance(ImmutableMap.of(ObjectSchema.ID, targetId))
        ));

        final List<Sort> sort = Sort.parseList("id");
        final Page<Map<String, Object>> page = page(storage, source, Expression.parse("target.id == '" + targetId + "'"), sort, 10);
        assertEquals(1, page.size());
    }

    @Test
    public void testRefExpandQuery() {

        final Storage storage = storage(namespace);

        final ObjectSchema target = namespace.requireObjectSchema(REF_TARGET);
        final ObjectSchema source = namespace.requireObjectSchema(REF_SOURCE);

        final Set<Name> expand = Name.parseSet("target");

        assumeConcurrentObjectWrite(storage, target);
        assumeConcurrentObjectWrite(storage, source);
        assumeTrue(storage.supportedExpand(source, expand).containsAll(expand));

        final String targetId = createComplete(storage, target, ImmutableMap.of(
                "hello", "world"
        ));
        createComplete(storage, source, ImmutableMap.of(
                "target", new Instance(ImmutableMap.of(ObjectSchema.ID, targetId))
        ));

        final List<Sort> sort = Sort.parseList("id");
        final Page<Map<String, Object>> page = page(storage, source, Expression.parse("target.hello == 'world'"), sort, 10);
        assertEquals(1, page.size());
    }

    @Test
    public void testRefDeepExpandQuery() {

        final Storage storage = storage(namespace);

        final ObjectSchema target = namespace.requireObjectSchema(REF_TARGET);
        final ObjectSchema source = namespace.requireObjectSchema(REF_SOURCE);

        final Set<Name> expand = Name.parseSet("target.source");

        assumeConcurrentObjectWrite(storage, target);
        assumeConcurrentObjectWrite(storage, source);
        assumeTrue(storage.supportedExpand(source, expand).containsAll(expand));

        final String sourceId = createComplete(storage, source, ImmutableMap.of(
                "hello", "pluto"
        ));
        final String targetId = createComplete(storage, target, ImmutableMap.of(
                "hello", "world",
                "source", ObjectSchema.ref(sourceId)
        ));
        createComplete(storage, source, ImmutableMap.of(
                "target", ObjectSchema.ref(targetId)
        ));

        final List<Sort> sort = Sort.parseList("id");
        final Page<Map<String, Object>> page = page(storage, source, Expression.parse("target.source.hello == 'pluto'"), sort, 10);
        assertEquals(1, page.size());
    }

    @Test
    public void testDateSort() {

        final Storage storage = storage(namespace);

        final ObjectSchema dateSort = namespace.requireObjectSchema(DATE_SORT);

        assumeConcurrentObjectWrite(storage, dateSort);

        for(int i = 0; i != 10; ++i) {
            createComplete(storage, dateSort, ImmutableMap.of(
                    "group", "test"
            ));
        }

        final List<Sort> sort = Sort.parseList("created", "id");
        final Page<Map<String, Object>> page = page(storage, dateSort, Expression.parse("group == 'test'"), sort, 5);
        assertEquals(5, page.size());
    }

    private Page<Map<String, Object>> page(final Storage storage, final ObjectSchema schema, final Expression expression, final List<Sort> sort, final int count) {

        final Comparator<Map<String, Object>> comparator = Instance.comparator(sort);
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expression.bind(Context.init()), sort, Collections.emptySet());
        return new Pager<>(comparator, sources, null).page(count).join();
    }

    private String createComplete(final Storage storage, final ObjectSchema schema, final Map<String, Object> data) {

        final Instant now = Instant.now();
        final StorageTraits traits = storage.storageTraits(schema);
        final String id = UUID.randomUUID().toString();
        final Map<String, Object> instance = instance(schema, id, 1L, data);
        final Storage.WriteTransaction write = storage.write(Consistency.ATOMIC, Versioning.CHECKED);
        write.createObject(schema, id, instance);
        for(final Index index : schema.getIndexes().values()) {
            final Consistency best = traits.getIndexConsistency(index.isMultiValue());
            if(index.getConsistency(best).isAsync() && write instanceof Storage.WithWriteIndex.WriteTransaction) {
                final Map<Index.Key, Map<String, Object>> records = index.readValues(instance);
                records.forEach((key, projection) -> ((Storage.WithWriteIndex.WriteTransaction)write).createIndex(schema, index, id, 0L, key, projection));
            }
        }

        write.write().join();
        return id;
    }

    private Instance instance(final ObjectSchema schema, final String id, final long version) {

        return instance(schema, id, version, Collections.emptyMap());
    }

    private Instance instance(final ObjectSchema schema, final String id, final long version, final Map<String, Object> data) {

        final Instant now = Instant.now();
        final Map<String, Object> instance = new HashMap<>(data);
        Instance.setId(instance, id);
        Instance.setVersion(instance, version);
        Instance.setSchema(instance, schema.getQualifiedName());
        Instance.setCreated(instance, now);
        Instance.setUpdated(instance, now);
        Instance.setHash(instance, schema.hash(instance));
        return schema.create(instance);
    }

    private static void assertCause(final Class<? extends Throwable> except, final Executable exe) {

        boolean thrown = true;
        try {
            exe.execute();
            thrown = false;
        } catch (final Throwable t) {
            assertThrows(except, () -> {
                if (t.getCause() == null) {
                    throw t;
                } else {
                    throw t.getCause();
                }
            });
        }
        if(!thrown) {
            // Should have thrown
            assertThrows(except, () -> {
            });
        }
    }

    private static void assumeConcurrentObjectWrite(final Storage storage, final ObjectSchema schema) {

        assumeTrue(storage.storageTraits(schema).getObjectConcurrency().isEnabled(),
                "Object concurrency must be enabled for this test");
    }
}
