package io.basestar.storage.hazelcast.serde;

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

import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import com.hazelcast.nio.serialization.ClassDefinitionBuilder;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import io.basestar.schema.Instance;
import io.basestar.schema.InstanceSchema;
import io.basestar.schema.Reserved;
import io.basestar.schema.use.Use;
import io.basestar.schema.use.UseArray;
import io.basestar.schema.use.UseDate;
import io.basestar.schema.use.UseDateTime;
import io.basestar.secret.Secret;
import io.basestar.util.Bytes;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface AttributeType<T> {

    BooleanType BOOLEAN = new BooleanType();

    BooleanArrayType BOOLEAN_ARRAY = new BooleanArrayType();

    IntegerType INTEGER = new IntegerType();

    IntegerArrayType INTEGER_ARRAY = new IntegerArrayType();

    NumberType NUMBER = new NumberType();

    NumberArrayType NUMBER_ARRAY = new NumberArrayType();

    StringType STRING = new StringType();

    StringArrayType STRING_ARRAY = new StringArrayType();

    DateType DATE = new DateType();

    DateArrayType DATE_ARRAY = new DateArrayType();

    DateTimeType DATETIME = new DateTimeType();

    DateTimeArrayType DATETIME_ARRAY = new DateTimeArrayType();

    BinaryType BINARY = new BinaryType();

    SecretType SECRET = new SecretType();

    RefType REF = new RefType(false);

    RefType VERSIONED_REF = new RefType(true);

    RefArrayType REF_ARRAY = new RefArrayType(false);

    RefArrayType VERSIONED_REF_ARRAY = new RefArrayType(true);

    static <T> EncodedType<T> encoded(final Use<T> use) {

        return new EncodedType<>(use);
    }

    static <T> EncodedType<List<T>> encodedArray(final Use<T> type) {

        return new EncodedType<>(new UseArray<>(type));
    }

    static StructType struct(final InstanceSchema schema) {

        return new StructType(schema);
    }

    static StructArrayType structArray(final InstanceSchema schema) {

        return new StructArrayType(schema);
    }

    T readValue(PortableReader reader, String name) throws IOException;

    default T read(final PortableReader reader, final String name) throws IOException {

        if(reader.readBoolean(existsAttribute(name))) {
            return readValue(reader, name);
        } else {
            return null;
        }
    }

    void writeValue(PortableSchemaFactory factory, PortableWriter writer, String name, T value) throws IOException;

    default void write(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final T value) throws IOException {

        if(value != null) {
            writer.writeBoolean(existsAttribute(name), true);
            writeValue(factory, writer, name, value);
        } else {
            writer.writeBoolean(existsAttribute(name), false);
        }
    }

    void defineValue(PortableSchemaFactory factory, ClassDefinitionBuilder builder, String name);

    default void def(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

        builder.addBooleanField(existsAttribute(name));
        defineValue(factory, builder, name);
    }

    static String existsAttribute(final String name) {

        return name + Reserved.PREFIX + "exists";
    }

    class BooleanType implements AttributeType<Boolean> {

        @Override
        public Boolean readValue(final PortableReader reader, final String name) throws IOException {

            return reader.readBoolean(name);
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Boolean value) throws IOException {

            writer.writeBoolean(name, value);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addBooleanField(name);
        }
    }

    class BooleanArrayType implements AttributeType<List<Boolean>> {

        @Override
        public List<Boolean> readValue(final PortableReader reader, final String name) throws IOException {

            return Booleans.asList(reader.readBooleanArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Boolean> value) throws IOException {

            writer.writeBooleanArray(name, Booleans.toArray(value));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addBooleanArrayField(name);
        }
    }

    class IntegerType implements AttributeType<Long> {

        @Override
        public Long readValue(final PortableReader reader, final String name) throws IOException {

            return reader.readLong(name);
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Long value) throws IOException {

            writer.writeLong(name, value);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addLongField(name);
        }
    }

    class IntegerArrayType implements AttributeType<List<Long>> {

        @Override
        public List<Long> readValue(final PortableReader reader, final String name) throws IOException {

            return Longs.asList(reader.readLongArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Long> value) throws IOException {

            writer.writeLongArray(name, Longs.toArray(value));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addLongArrayField(name);
        }
    }

    class NumberType implements AttributeType<Double> {

        @Override
        public Double readValue(final PortableReader reader, final String name) throws IOException {

            return reader.readDouble(name);
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Double value) throws IOException {

            writer.writeDouble(name, value);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addDoubleField(name);
        }
    }

    class NumberArrayType implements AttributeType<List<Double>> {

        @Override
        public List<Double> readValue(final PortableReader reader, final String name) throws IOException {

            return Doubles.asList(reader.readDoubleArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Double> value) throws IOException {

            writer.writeDoubleArray(name, Doubles.toArray(value));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addDoubleArrayField(name);
        }
    }

    class StringType implements AttributeType<String> {

        @Override
        public String readValue(final PortableReader reader, final String name) throws IOException {

            return reader.readUTF(name);
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final String value) throws IOException {

            writer.writeUTF(name, value);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFField(name);
        }
    }

    class StringArrayType implements AttributeType<List<String>> {

        @Override
        public List<String> readValue(final PortableReader reader, final String name) throws IOException {

            return Arrays.asList(reader.readUTFArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<String> value) throws IOException {

            writer.writeUTFArray(name, value.toArray(new String[0]));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFArrayField(name);
        }
    }

    class DateType implements AttributeType<LocalDate> {

        @Override
        public LocalDate readValue(final PortableReader reader, final String name) throws IOException {

            return UseDate.DEFAULT.create(reader.readUTF(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final LocalDate value) throws IOException {

            writer.writeUTF(name, value.toString());
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFField(name);
        }
    }

    class DateArrayType implements AttributeType<List<LocalDate>> {

        @Override
        public List<LocalDate> readValue(final PortableReader reader, final String name) throws IOException {

            return Arrays.stream(reader.readUTFArray(name)).map(UseDate.DEFAULT::create).collect(Collectors.toList());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<LocalDate> value) throws IOException {

            writer.writeUTFArray(name, value.stream().map(UseDate.DEFAULT::toString).toArray(String[]::new));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFArrayField(name);
        }
    }

    class DateTimeType implements AttributeType<Instant> {

        @Override
        public Instant readValue(final PortableReader reader, final String name) throws IOException {

            return UseDateTime.DEFAULT.create(reader.readUTF(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Instant value) throws IOException {

            writer.writeUTF(name, value.toString());
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFField(name);
        }
    }

    class DateTimeArrayType implements AttributeType<List<Instant>> {

        @Override
        public List<Instant> readValue(final PortableReader reader, final String name) throws IOException {

            return Arrays.stream(reader.readUTFArray(name)).map(UseDateTime.DEFAULT::create).collect(Collectors.toList());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Instant> value) throws IOException {

            writer.writeUTFArray(name, value.stream().map(UseDateTime.DEFAULT::toString).toArray(String[]::new));
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addUTFArrayField(name);
        }
    }

    class BinaryType implements AttributeType<Bytes> {

        @Override
        public Bytes readValue(final PortableReader reader, final String name) throws IOException {

            return new Bytes(reader.readByteArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Bytes value) throws IOException {

            writer.writeByteArray(name, value.getBytes());
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addByteArrayField(name);
        }
    }

    class SecretType implements AttributeType<Secret> {

        @Override
        public Secret readValue(final PortableReader reader, final String name) throws IOException {

            return Secret.encrypted(reader.readByteArray(name));
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Secret value) throws IOException {

            writer.writeByteArray(name, value.encrypted());
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addByteArrayField(name);
        }
    }

    class EncodedType<T> implements AttributeType<T> {

        private final Use<T> use;

        public EncodedType(final Use<T> use) {

            this.use = use;
        }

        @Override
        public T readValue(final PortableReader reader, final String name) throws IOException {

            final byte[] bytes = reader.readByteArray(name);
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 final DataInputStream dais = new DataInputStream(bais)) {
                return use.deserializeValue(dais);
            }
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final T value) throws IOException {

            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 final DataOutputStream daos = new DataOutputStream(baos)) {
                use.serializeValue(value, daos);
                final byte[] bytes = baos.toByteArray();
                writer.writeByteArray(name, bytes);
            }
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addByteArrayField(name);
        }
    }

    class StructType implements AttributeType<Map<String, Object>> {

        private final InstanceSchema schema;

        public StructType(final InstanceSchema schema) {

            this.schema = schema;
        }

        @Override
        public Instance readValue(final PortableReader reader, final String name) throws IOException {

            final CustomPortable portable = reader.readPortable(name);
            return schema.create(portable.getData());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Map<String, Object> value) throws IOException {

            final CustomPortable portable = factory.create(schema);
            portable.setData(value);
            writer.writePortable(name, portable);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addPortableField(name, factory.def(schema));
        }
    }

    class StructArrayType implements AttributeType<List<Map<String, Object>>> {

        private final InstanceSchema schema;

        public StructArrayType(final InstanceSchema schema) {

            this.schema = schema;
        }

        @Override
        public List<Map<String, Object>> readValue(final PortableReader reader, final String name) throws IOException {

            final Portable[] portables = reader.readPortableArray(name);
            return Arrays.stream(portables).map(v -> {
                final CustomPortable portable = (CustomPortable)v;
                return schema.create(portable.getData());
            }).collect(Collectors.toList());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Map<String, Object>> value) throws IOException {

            final Portable[] portables = value.stream().map(v -> {
                final CustomPortable portable = factory.create(schema);
                portable.setData(v);
                return portable;
            }).toArray(Portable[]::new);
            writer.writePortableArray(name, portables);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addPortableArrayField(name, factory.def(schema));
        }
    }

    @RequiredArgsConstructor
    class RefType implements AttributeType<Map<String, Object>> {

        private final boolean versioned;

        @Override
        public Map<String, Object> readValue(final PortableReader reader, final String name) throws IOException {

            final CustomPortable portable = reader.readPortable(name);
            return new Instance(portable.getData());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final Map<String, Object> value) throws IOException {

            final CustomPortable portable = factory.createRef(versioned);
            portable.setData(value);
            writer.writePortable(name, portable);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addPortableField(name, factory.refDef(versioned));
        }
    }

    @RequiredArgsConstructor
    class RefArrayType implements AttributeType<List<Map<String, Object>>> {

        private final boolean versioned;

        @Override
        public List<Map<String, Object>> readValue(final PortableReader reader, final String name) throws IOException {

            final Portable[] portables = reader.readPortableArray(name);
            return Arrays.stream(portables).map(v -> {
                final CustomPortable portable = (CustomPortable)v;
                return new Instance(portable.getData());
            }).collect(Collectors.toList());
        }

        @Override
        public void writeValue(final PortableSchemaFactory factory, final PortableWriter writer, final String name, final List<Map<String, Object>> value) throws IOException {

            final Portable[] portables = value.stream().map(v -> {
                final CustomPortable portable = factory.createRef(versioned);
                portable.setData(v);
                return portable;
            }).toArray(Portable[]::new);
            writer.writePortableArray(name, portables);
        }

        @Override
        public void defineValue(final PortableSchemaFactory factory, final ClassDefinitionBuilder builder, final String name) {

            builder.addPortableArrayField(name, factory.refDef(versioned));
        }
    }
}
