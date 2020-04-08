package io.basestar.storage;

/*-
 * #%L
 * basestar-storage
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

import com.google.common.base.Charsets;
import com.google.common.collect.*;
import io.basestar.expression.Expression;
import io.basestar.expression.type.Values;
import io.basestar.schema.*;
import io.basestar.storage.exception.ObjectExistsException;
import io.basestar.storage.exception.VersionMismatchException;
import io.basestar.storage.util.Pager;
import io.basestar.util.PagedList;
import io.basestar.util.Path;
import io.basestar.util.Sort;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class TestStorage {

    private static final int RECORD_COUNT = 100;

    private static final String ADDRESS = "Address";

    private static final String SIMPLE = "Simple";

    private static final String POINTSET = "Pointset";

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
            final Storage.WriteTransaction write = storage.write(Consistency.NONE);

            data.asMap().forEach((k, vs) -> {
                final ObjectSchema schema = namespace.requireObjectSchema(k);
                vs.forEach(v -> {
                    final Instance instance = schema.create(v);
                    final String id = Instance.getId(v);
                    write.createObject(schema, id, instance);
                });
            });

            write.commit().join();
        }
    }

    // FIXME: merge with createComplete

    protected Multimap<String, Map<String, Object>> loadAddresses() throws IOException {

        final LocalDateTime now = LocalDateTime.now();
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
    @SuppressWarnings("unchecked")
    public void testIndexes() throws IOException {

        final Storage storage = storage(namespace, loadAddresses());

        final ObjectSchema schema = namespace.requireObjectSchema(ADDRESS);

        assumeConcurrentObjectWrite(storage, schema);

        final List<Sort> sort = ImmutableList.of(
                Sort.asc(Path.of("city")),
                Sort.asc(Path.of("zip"))
        );

        final Expression expr = Expression.parse("country == 'United Kingdom' || state == 'Victoria'");
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expr, Collections.emptyList());
        final Comparator<Map<String, Object>> comparator = Sort.comparator(sort, (t, path) -> (Comparable)path.apply(t));
        final PagedList<Map<String, Object>> results = new Pager<>(comparator, sources, null).page(100).join();
        assertEquals(8, results.size());
    }

    @Test
    public void testCreate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Map<String, Object> data = data();

        final Instance after = instance(schema, id, 1L, data);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, after)
                .commit().join();

        final Map<String, Object> current = storage.readObject(schema, id).join();
        assertNotNull(current);
        assertEquals(1, Instance.getVersion(current));
        data.forEach((k, v) -> {
            final Object v2 = current.get(k);
            assertTrue(Values.equals(v, v2), v + " != " + v2);
        });

        if(storage.storageTraits(schema).getHistoryConsistency().isStronger(Consistency.EVENTUAL)) {
            final Map<String, Object> v1 = storage.readObjectVersion(schema, id, 1L).join();
            assertNotNull(v1);
            assertEquals(1, Instance.getVersion(v1));
        }
    }

    private Map<String, Object> data() {

        return ImmutableMap.<String, Object>builder()
                .put("boolean", true)
                .put("integer", 1L)
                .put("number", 2.5)
                .put("string", "test")
                .put("binary", new byte[]{1, 2, 3, 4})
                .put("struct", new Instance(ImmutableMap.of("x", 1L, "y", 5L)))
                .put("object", new Instance(ImmutableMap.of("id", "test")))
                .put("arrayBoolean", Collections.singletonList(true))
                .put("arrayInteger", Collections.singletonList(1L))
                .put("arrayNumber", Collections.singletonList(2.5))
                .put("arrayString", Collections.singletonList("test"))
                .put("arrayBinary", Collections.singletonList(new byte[]{1, 2, 3, 4}))
                .put("arrayStruct", Collections.singletonList(new Instance(ImmutableMap.of("x", 10L, "y", 5L))))
                .put("arrayObject", Collections.singletonList(new Instance(ImmutableMap.of("id", "test"))))
                .put("mapBoolean", Collections.singletonMap("a", true))
                .put("mapInteger", Collections.singletonMap("a", 1L))
                .put("mapNumber", Collections.singletonMap("a", 2.5))
                .put("mapString", Collections.singletonMap("a", "test"))
                .put("mapBinary", Collections.singletonMap("a", new byte[]{1, 2, 3, 4}))
                .put("mapStruct", Collections.singletonMap("a",new Instance(ImmutableMap.of("x", 10L, "y", 5L))))
                .put("mapObject", Collections.singletonMap("a", new Instance(ImmutableMap.of("id", "test"))))
                .build();
    }


    @Test
    public void testUpdate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());
        assertEquals(1L, before.getVersion());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .commit().join();

        final Map<String, Object> current = storage.readObject(schema, id).join();
        assertNotNull(current);
        assertEquals(2, Instance.getVersion(current));
        if(storage.storageTraits(schema).getHistoryConsistency().isStronger(Consistency.EVENTUAL)) {
            final Map<String, Object> v2 = storage.readObjectVersion(schema, id, 2L).join();
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

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());

        storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, setVersion(before, 1L))
                .commit().join();

        final Map<String, Object> current = storage.readObject(schema, id).join();
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
        data.put(Reserved.ID, id);
        data.put(Reserved.VERSION, 1L);
        final Instance instance = schema.create(data);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, instance)
                .commit().join();

        final BatchResponse results = storage.read(Consistency.ATOMIC)
                .readObject(schema, id)
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

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, after)
                .commit().join();

        assertCause(ObjectExistsException.class, () -> storage.write(Consistency.ATOMIC)
                    .createObject(schema, id, after)
                    .commit().get());
    }

    @Test
    public void testUpdateMissing() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());

        storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, setVersion(before, 1L))
                .commit().join();

        final Instance after = instance(schema, id, 2L);

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .commit().get());
    }

    @Test
    public void testDeleteMissing() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());

        storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, setVersion(before, 1L))
                .commit().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, setVersion(before, 1L))
                .commit().get());
    }

    @Test
    public void testDeleteWrongVersion() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .commit().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, setVersion(before, 1L))
                .commit().get());
    }

    @Test
    public void testUpdateWrongVersion() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        final Instance init = instance(schema, id, 1L);

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, init)
                .commit().join();

        final Instance before = schema.create(storage.readObject(schema, id).join());

        final Instance after = instance(schema, id, 2L);

        storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .commit().join();

//        storage.write(Consistency.ATOMIC)
//                .updateObject(schema, id, 1L, before, after)
//                .commit().join();

        assertCause(VersionMismatchException.class, () -> storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, setVersion(before, 1L), after)
                .commit().get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiValueIndex() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(POINTSET);

        assumeConcurrentObjectWrite(storage, schema);

        createComplete(storage, schema, ImmutableMap.of(
                "points", ImmutableList.of(
                        ImmutableMap.of("x", 10, "y", 100),
                        ImmutableMap.of("x", 5, "y", 10)
                )
        ));

        createComplete(storage, schema, ImmutableMap.of(
                "points", ImmutableList.of(
                        ImmutableMap.of("x", 10, "y", 10),
                        ImmutableMap.of("x", 1, "y", 10)
                )
        ));

        final List<Sort> sort = ImmutableList.of(Sort.asc(Path.of(Reserved.ID)));
        final Expression expr = Expression.parse("p.x == 10 && p.y == 100 for any p of points");
        final List<Pager.Source<Map<String, Object>>> sources = storage.query(schema, expr, Collections.emptyList());
        final Comparator<Map<String, Object>> comparator = Sort.comparator(sort, (t, path) -> (Comparable)path.apply(t));
        final PagedList<Map<String, Object>> results = new Pager<>(comparator, sources, null).page(100).join();
        assertEquals(1, results.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNullBeforeUpdate() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, instance(schema, id, 1L))
                .commit().join();

        storage.write(Consistency.ATOMIC)
                .updateObject(schema, id, null, instance(schema, id, 2L))
                .commit().join();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNullBeforeDelete() {

        final Storage storage = storage(namespace);

        final ObjectSchema schema = namespace.requireObjectSchema(SIMPLE);

        assumeConcurrentObjectWrite(storage, schema);

        final String id = UUID.randomUUID().toString();

        storage.write(Consistency.ATOMIC)
                .createObject(schema, id, instance(schema, id, 1L))
                .commit().join();

        storage.write(Consistency.ATOMIC)
                .deleteObject(schema, id, null)
                .commit().join();
    }

    private void createComplete(final Storage storage, final ObjectSchema schema, final Map<String, Object> data) {

        final StorageTraits traits = storage.storageTraits(schema);
        final Map<String, Object> instance = new HashMap<>(data);
        final String id = UUID.randomUUID().toString();
        Instance.setId(instance, id);
        Instance.setVersion(instance, 1L);
        Instance.setSchema(instance, schema.getName());
        final Storage.WriteTransaction write = storage.write(Consistency.ATOMIC);
        write.createObject(schema, id, instance);
        for(final Index index : schema.getAllIndexes().values()) {
            final Consistency best = traits.getIndexConsistency(index.isMultiValue());
            if(index.getConsistency(best).isAsync()) {
                final Map<Index.Key, Map<String, Object>> records = index.readValues(instance);
                records.forEach((key, projection) -> write.createIndex(schema, index, id, 0L, key, projection));
            }
        }

        write.commit().join();
    }

    private Instance instance(final ObjectSchema schema, final String id, final long version) {

        return instance(schema, id, version, Collections.emptyMap());
    }

    private Instance instance(final ObjectSchema schema, final String id, final long version, final Map<String, Object> data) {

        final Map<String, Object> object = new HashMap<>(data);
        object.put(Reserved.ID, id);
        object.put(Reserved.VERSION, version);
        return schema.create(object);
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
