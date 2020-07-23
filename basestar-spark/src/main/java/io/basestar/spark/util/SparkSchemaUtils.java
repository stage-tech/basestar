package io.basestar.spark.util;

/*-
 * #%L
 * basestar-spark
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.basestar.schema.*;
import io.basestar.schema.use.*;
import io.basestar.util.Name;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema;
import org.apache.spark.sql.types.*;
import scala.collection.Seq;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SparkSchemaUtils {

    public static StructType structType(final InstanceSchema schema, final Set<Name> expand) {

        return structType(schema, expand, ImmutableMap.of());
    }

    public static StructType structType(final InstanceSchema schema, final Set<Name> expand, final Map<String, Use<?>> extraMetadata) {

        final List<StructField> fields = new ArrayList<>();
        final Map<String, Set<Name>> branches = Name.branch(expand);
        schema.getProperties()
                .forEach((name, property) -> fields.add(field(name, property, branches.get(name))));
        schema.metadataSchema()
                .forEach((name, type) -> fields.add(field(name, type, null)));
        extraMetadata.forEach((name, type) -> fields.add(field(name, type, null)));
        if(schema instanceof Link.Resolver) {
            ((Link.Resolver)schema).getLinks()
                    .forEach((name, link) -> {
                        final Set<Name> branch = branches.get(name);
                        if(branch != null) {
                            fields.add(field(name, link, branch));
                        }
                    });
        }
        if(schema instanceof Transient.Resolver) {
            ((Transient.Resolver)schema).getTransients()
                    .forEach((name, trans) -> {
                        final Set<Name> branch = branches.get(name);
                        if(branch != null) {
                            fields.add(field(name, trans, branch));
                        }
                    });
        }
        fields.sort(Comparator.comparing(StructField::name));
        return DataTypes.createStructType(fields);
    }

    public static StructType refType(final ObjectSchema schema, final Set<Name> expand) {

        if(expand == null) {
            return refType();
        } else {
            return structType(schema, expand);
        }
    }

    public static StructType refType() {

        final List<StructField> fields = new ArrayList<>();
        ObjectSchema.REF_SCHEMA
                .forEach((name, type) -> fields.add(field(name, type, null)));
        fields.sort(Comparator.comparing(StructField::name));
        return DataTypes.createStructType(fields);
    }

    public static StructField field(final String name, final Property property, final Set<Name> expand) {

        return field(name, property.getType(), expand);
    }

    public static StructField field(final String name, final Link link, final Set<Name> expand) {

        return field(name, link.getType(), expand);
    }

    public static StructField field(final String name, final Transient trans, final Set<Name> expand) {

        if(trans.isTyped()) {
            return field(name, trans.getType(), expand);
        } else {
            throw new IllegalStateException("Cannot expand untyped transient");
        }
    }

    public static StructField field(final String name, final Use<?> type, final Set<Name> expand) {

        return field(name, type(type, expand));
    }

    public static StructField field(final String name, final DataType type) {

        return StructField.apply(name, type, true, Metadata.empty());
    }

    public static DataType type(final Schema<?> schema, final Set<Name> expand) {

        if (schema instanceof ObjectSchema) {
            return structType((ObjectSchema) schema, expand);
        } else if(schema instanceof StructSchema) {
            return structType((StructSchema) schema, expand);
        } else if (schema instanceof EnumSchema) {
            return DataTypes.StringType;
        } else {
            throw new IllegalStateException();
        }
    }

    public static DataType type(final Use<?> type, final Set<Name> expand) {

        return type.visit(new Use.Visitor<DataType>() {

            @Override
            public DataType visitBoolean(final UseBoolean type) {

                return DataTypes.BooleanType;
            }

            @Override
            public DataType visitInteger(final UseInteger type) {

                return DataTypes.LongType;
            }

            @Override
            public DataType visitNumber(final UseNumber type) {

                return DataTypes.DoubleType;
            }

            @Override
            public DataType visitString(final UseString type) {

                return DataTypes.StringType;
            }

            @Override
            public DataType visitEnum(final UseEnum type) {

                return DataTypes.StringType;
            }

            @Override
            public DataType visitObject(final UseObject type) {

                return refType(type.getSchema(), expand);
            }

            @Override
            public <T> DataType visitArray(final UseArray<T> type) {

                return DataTypes.createArrayType(type.getType().visit(this));
            }

            @Override
            public <T> DataType visitSet(final UseSet<T> type) {

                return DataTypes.createArrayType(type.getType().visit(this));
            }

            @Override
            public <T> DataType visitMap(final UseMap<T> type) {

                return DataTypes.createMapType(DataTypes.StringType, type.getType().visit(this));
            }

            @Override
            public DataType visitStruct(final UseStruct type) {

                return structType(type.getSchema(), expand);
            }

            @Override
            public DataType visitBinary(final UseBinary type) {

                return DataTypes.BinaryType;
            }

            @Override
            public DataType visitDate(final UseDate type) {

                return DataTypes.DateType;
            }

            @Override
            public DataType visitDateTime(final UseDateTime type) {

                return DataTypes.TimestampType;
            }

            @Override
            public DataType visitView(final UseView type) {

                return structType(type.getSchema(), expand);
            }
        });
    }

    public static Map<String, Object> fromSpark(final InstanceSchema schema, final Row row) {

        return fromSpark(schema, ImmutableSet.of(), ImmutableMap.of(), row);
    }

    public static Map<String, Object> fromSpark(final InstanceSchema schema, final Set<Name> expand, final Row row) {

       return fromSpark(schema, expand, ImmutableMap.of(), row);
    }

    public static Map<String, Object> fromSpark(final InstanceSchema schema, final Set<Name> expand, final Map<String, Use<?>> extraMetadata, final Row row) {

        final Map<String, Set<Name>> branches = Name.branch(expand);
        final Map<String, Object> object = new HashMap<>();
        schema.getProperties().forEach((name, property) -> object.put(name, fromSpark(property.getType(), branches.get(name), get(row, name))));
        schema.metadataSchema().forEach((name, type) -> object.put(name, fromSpark(type, get(row, name))));
        extraMetadata.forEach((name, type) -> object.put(name, fromSpark(type, get(row, name))));
        if(schema instanceof Link.Resolver) {
            ((Link.Resolver)schema).getLinks()
                    .forEach((name, link) -> {
                        final Set<Name> branch = branches.get(name);
                        if(branch != null) {
                            object.put(name, fromSpark(link, branch, get(row, name)));
                        }
                    });
        }
        return schema.create(object, true, true);
    }

    protected static Object fromSpark(final Link link, final Set<Name> expand, final Object value) {

        if(link.isSingle()) {
            return fromSpark(link.getSchema(), expand, (Row)value);
        } else {
            final List<Map<String, Object>> results = new ArrayList<>();
            ((scala.collection.Iterable<?>)value).foreach(ScalaUtils.scalaFunction(v -> {
                results.add(fromSpark(link.getSchema(), expand, (Row)v));
                return null;
            }));
            return results;
        }
    }

    public static Map<String, Object> refFromSpark(final Row row) {

        final Map<String, Object> object = new HashMap<>();
        ObjectSchema.REF_SCHEMA
                .forEach((name, type) -> object.put(name, fromSpark(type, get(row, name))));
        return object;
    }

    public static Object fromSpark(final Use<?> type, final Object value) {

        return fromSpark(type, ImmutableSet.of(), value);
    }

    public static Object fromSpark(final Use<?> type, final Set<Name> expand, final Object value) {

        if(value == null) {
            return null;
        }
        return type.visit(new Use.Visitor<Object>() {

            @Override
            public Object visitBoolean(final UseBoolean type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitInteger(final UseInteger type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitNumber(final UseNumber type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitString(final UseString type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitEnum(final UseEnum type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitObject(final UseObject type) {

                if(value instanceof Row) {
                    return refFromSpark((Row)value);
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Object visitArray(final UseArray<T> type) {

                if(value instanceof Seq<?>) {
                    final List<T> result = new ArrayList<>();
                    ((Seq<?>)value).foreach(ScalaUtils.scalaFunction(v -> {
                        result.add((T)fromSpark(type.getType(), v));
                        return null;
                    }));
                    return result;
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Object visitSet(final UseSet<T> type) {

                if(value instanceof Seq<?>) {
                    final Set<T> result = new HashSet<>();
                    ((Seq<?>)value).foreach(ScalaUtils.scalaFunction(v -> {
                        result.add((T)fromSpark(type.getType(), v));
                        return null;
                    }));
                    return result;
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Object visitMap(final UseMap<T> type) {

                if(value instanceof scala.collection.Map<?, ?>) {
                    final Map<String, T> result = new HashMap<>();
                    ((scala.collection.Map<?, ?>)value).foreach(ScalaUtils.scalaFunction(e -> {
                        final String k = (String)e._1();
                        final Object v = e._2();
                        result.put(k, (T)fromSpark(type.getType(), v));
                        return null;
                    }));
                    return result;
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            public Object visitStruct(final UseStruct type) {

                if(value instanceof Row) {
                    return fromSpark(type.getSchema(), (Row)value);
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            public Object visitBinary(final UseBinary type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitDate(final UseDate type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitDateTime(final UseDateTime type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitView(final UseView type) {

                if(value instanceof Row) {
                    return fromSpark(type.getSchema(), (Row)value);
                } else {
                    throw new IllegalStateException();
                }
            }
        });
    }

    // FIXME::
    public static Row toSpark(final InstanceSchema schema, final StructType structType, final Map<String, Object> object) {

        return toSpark(schema, ImmutableMap.of(), structType, object);
    }

    // FIXME::
    public static Row toSpark(final InstanceSchema schema, final Map<String, Use<?>> extraMetadata, final StructType structType, final Map<String, Object> object) {

        final StructField[] fields = structType.fields();
        final Object[] values = new Object[fields.length];
        schema.metadataSchema().forEach((name, type) -> {
            final int i = structType.fieldIndex(name);
            values[i] = toSpark(type, fields[i].dataType(), object.get(name));
        });
        schema.getProperties().forEach((name, property) -> {
            final int i = structType.fieldIndex(name);
            values[i] = toSpark(property.getType(), fields[i].dataType(), object.get(name));
        });
        extraMetadata.forEach((name, type) -> {
            final int i = structType.fieldIndex(name);
            values[i] = toSpark(type, fields[i].dataType(), object.get(name));
        });
        return new GenericRowWithSchema(values, structType);
    }

    // FIXME::
    public static Row refToSpark(final StructType structType, final Map<String, Object> object) {

        final StructField[] fields = structType.fields();
        final Object[] values = new Object[fields.length];
        ObjectSchema.REF_SCHEMA.forEach((name, type) -> {
            final int i = structType.fieldIndex(name);
            values[i] = toSpark(type, fields[i].dataType(), object.get(name));
        });
        return new GenericRowWithSchema(values, structType);
    }

    public static Object toSpark(final Use<?> type, final DataType dataType, final Object value) {

        if(value == null) {
            return null;
        }
        return type.visit(new Use.Visitor<Object>() {

            @Override
            public Object visitBoolean(final UseBoolean type) {

                final Boolean result = type.create(value, false, true);
                return result;
//                return result == null ? null : scala.Boolean.box(result);
            }

            @Override
            public Object visitInteger(final UseInteger type) {

                final Long result = type.create(value, false, true);
                return result;
//                return result == null ? null : scala.Long.box(result);
            }

            @Override
            public Object visitNumber(final UseNumber type) {

                final Double result = type.create(value, false, true);
                return result;
//                return result == null ? null : scala.Double.box(result);
            }

            @Override
            public Object visitString(final UseString type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitEnum(final UseEnum type) {

                return type.create(value, false, true);
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object visitObject(final UseObject type) {

                if(value instanceof Map<?, ?> && dataType instanceof StructType) {
                    return refToSpark((StructType)dataType, (Map<String, Object>) value);
                } else {
                    throw new IllegalStateException();
                }
            }

            @SuppressWarnings("unchecked")
            private <T> Object visitCollection(final UseCollection<T, ?> type) {

                if(value instanceof Collection<?> && dataType instanceof ArrayType) {
                    final DataType valueDataType = ((ArrayType) dataType).elementType();
                    final Stream<Object> stream = ((Collection<T>)value).stream()
                            .map(v -> toSpark(type.getType(), valueDataType, v));
                    return ScalaUtils.asScalaSeq(stream.iterator());
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            public <T> Object visitArray(final UseArray<T> type) {

                return visitCollection(type);
            }

            @Override
            public <T> Object visitSet(final UseSet<T> type) {

                return visitCollection(type);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Object visitMap(final UseMap<T> type) {

                if(value instanceof Map<?, ?> && dataType instanceof MapType) {
                    final DataType valueDataType = ((MapType) dataType).valueType();
                    final Map<String, Object> tmp = new HashMap<>();
                    ((Map<String, T>)value)
                            .forEach((k, v) -> tmp.put(k, toSpark(type.getType(), valueDataType, v)));
                    return ScalaUtils.asScalaMap(tmp);
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object visitStruct(final UseStruct type) {

                if(value instanceof Map<?, ?> && dataType instanceof StructType) {
                    return toSpark(type.getSchema(), (StructType)dataType, (Map<String, Object>)value);
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            public Object visitBinary(final UseBinary type) {

                return type.create(value, false, true);
            }

            @Override
            public Object visitDate(final UseDate type) {

                final LocalDate converted = type.create(value, false, true);
                return new java.sql.Date(converted.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000);
            }

            @Override
            public Object visitDateTime(final UseDateTime type) {

                final LocalDateTime converted = type.create(value, false, true);
                return new java.sql.Timestamp(converted.toEpochSecond(ZoneOffset.UTC) * 1000);
            }

            @Override
            public Object visitView(final UseView type) {

                throw new UnsupportedOperationException();
            }
        });
    }

    public static Optional<StructField> findField(final StructType type, final String name) {

        return Arrays.stream(type.fields()).filter(f -> name.equalsIgnoreCase(f.name())).findFirst();
    }

    public static StructSchema structSchema(final DataType dataType) {

        return StructSchema.builder()
                .setProperties(Arrays.stream(((StructType) dataType).fields()).collect(Collectors.toMap(
                        StructField::name, f -> Property.builder().setType(type(f.dataType()))
                )))
                .build();
    }

    public static Use<?> type(final DataType dataType) {

        if(dataType instanceof BooleanType) {
            return UseBoolean.DEFAULT;
        } else if(dataType instanceof ByteType
                | dataType instanceof ShortType
                | dataType instanceof IntegerType
                | dataType instanceof LongType) {
            return UseInteger.DEFAULT;
        } else if(dataType instanceof FloatType
                | dataType instanceof DoubleType
                | dataType instanceof DecimalType) {
            return UseNumber.DEFAULT;
        } else if(dataType instanceof StringType) {
            return UseString.DEFAULT;
        } else if(dataType instanceof BinaryType) {
            return UseBinary.DEFAULT;
        } else if(dataType instanceof ArrayType) {
            return new UseArray<>(type(((ArrayType) dataType).elementType()));
        } else if(dataType instanceof MapType) {
            return new UseMap<>(type(((MapType) dataType).valueType()));
        } else if(dataType instanceof StructType) {
            return new UseStruct(structSchema(dataType));
        } else if(dataType instanceof ObjectType) {
            return new UseStruct(structSchema(dataType));
        } else if(dataType instanceof DateType) {
            return new UseDate();
        } else if(dataType instanceof TimestampType) {
            return new UseDateTime();
        } else {
            throw new UnsupportedOperationException("Cannot understand " + dataType);
        }
    }

    /**
     * Move row fields around and insert nulls to match the target schema.
     *
     * Note: this will not try to cast or coerce scalars.
     */

    public static Object conform(final Object source, final DataType targetType) {

        if(source == null) {
            return null;
        } else if(targetType instanceof ArrayType) {
            final DataType elementType = ((ArrayType) targetType).elementType();
            return ScalaUtils.asScalaSeq(ScalaUtils.asJavaStream((Seq<?>)source)
                    .map(v -> conform(v, elementType)).iterator());
        } else if(targetType instanceof MapType) {
            final DataType valueType = ((MapType) targetType).valueType();
            return ScalaUtils.asScalaMap(ScalaUtils.asJavaMap((scala.collection.Map<?, ?>)source)
                    .entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> conform(e.getValue(), valueType)
                    )));
        } else if(targetType instanceof StructType) {
            return conform((Row)source, (StructType)targetType);
        } else {
            return source;
        }
    }

    public static Row conform(final Row source, final StructType targetType) {

        if(source == null) {
            return null;
        } else {
            final StructType sourceType = source.schema();
            final StructField[] sourceFields = sourceType.fields();
            final StructField[] targetFields = targetType.fields();
            final Seq<Object> sourceValues = source.toSeq();
            final Object[] targetValues = new Object[targetFields.length];
            for (int i = 0; i != targetFields.length; ++i) {
                final StructField targetField = targetFields[i];
                for (int j = 0; j != sourceFields.length; ++j) {
                    if (sourceFields[j].name().equalsIgnoreCase(targetField.name())) {
                        targetValues[i] = conform(sourceValues.apply(j), targetField.dataType());
                        break;
                    }
                }
            }
            return new GenericRowWithSchema(targetValues, targetType);
        }
    }

    public static Row transform(final Row source, final BiFunction<StructField, Object, ?> fn) {

        final StructType sourceType = source.schema();
        final StructField[] sourceFields = sourceType.fields();
        final Object[] targetValues = new Object[sourceFields.length];
        for (int i = 0; i != sourceFields.length; ++i) {
            final StructField sourceField = sourceFields[i];
            targetValues[i] = fn.apply(sourceField, source.get(i));
        }
        return new GenericRowWithSchema(targetValues, sourceType);
    }

    public static Row remove(final Row source, final String name) {

        return remove(source, (field, value) -> name.equals(field.name()));
    }

    public static Row remove(final Row source, final BiPredicate<StructField, Object> fn) {

        final StructType sourceType = source.schema();
        final StructField[] sourceFields = sourceType.fields();
        final List<StructField> outputFields = new ArrayList<>();
        final List<Object> outputValues = new ArrayList<>();
        for (int i = 0; i != sourceFields.length; ++i) {
            final StructField sourceField = sourceFields[i];
            final Object sourceValue = source.get(i);
            if(!fn.test(sourceField, sourceValue)) {
                outputFields.add(sourceField);
                outputValues.add(sourceValue);
            }
        }
        return new GenericRowWithSchema(outputValues.toArray(),
                DataTypes.createStructType(outputFields.toArray(new StructField[0])));
    }

    public static Row set(final Row source, final Map<String, ?> of) {

        return transform(source, (field, value) -> {
            if(of.containsKey(field.name())) {
                return of.get(field.name());
            } else {
                return value;
            }
        });
    }

    public static Row set(final Row source, final String name, final Object newValue) {

        return transform(source, (field, value) -> {
            if(name.equals(field.name())) {
                return newValue;
            } else {
                return value;
            }
        });
    }

    // Case insensitive
    public static Object get(final Row source, final String name) {

        final StructType sourceType = source.schema();
        final StructField[] sourceFields = sourceType.fields();
        for (int i = 0; i != sourceFields.length; ++i) {
            final StructField sourceField = sourceFields[i];
            if(name.equalsIgnoreCase(sourceField.name())) {
                return source.get(i);
            }
        }
        return null;
    }

    public static Row append(final Row source, final StructField field, final Object value) {

        final StructType sourceType = source.schema();
        final StructField[] sourceFields = sourceType.fields();
        final StructType outputType = append(sourceType, field);
        final List<Object> outputValues = new ArrayList<>();
        for (int i = 0; i != sourceFields.length; ++i) {
            outputValues.add(source.get(i));
        }
        outputValues.add(value);
        return new GenericRowWithSchema(outputValues.toArray(), outputType);
    }

    public static StructType append(final StructType sourceType, final StructField field) {

        final List<StructField> fields = Lists.newArrayList(sourceType.fields());
        fields.add(field);
        return DataTypes.createStructType(fields.toArray(new StructField[0]));
    }

    // Presto in default config wants partition columns after all other columns
    public static StructType orderForPresto(final StructType structType, final List<String> partitionColumns) {

        final SortedMap<String, StructField> data = new TreeMap<>();
        final SortedMap<Integer, StructField> partition = new TreeMap<>();
        Arrays.stream(structType.fields()).forEach(field -> {
            final int indexOf = partitionColumns.indexOf(field.name());
            if(indexOf < 0) {
                data.put(field.name(), field);
            } else {
                partition.put(0, field);
            }
        });
        final List<StructField> fields = new ArrayList<>();
        fields.addAll(data.values());
        fields.addAll(partition.values());
        return DataTypes.createStructType(fields);
    }

//    public static Encoder<?> encoder(final Use<?> type, final Set<Name> expand) {
//
//        return type.visit(new Use.Visitor<Encoder<?>>() {
//
//            @Override
//            public Encoder<?> visitBoolean(final UseBoolean type) {
//
//                return Encoders.BOOLEAN();
//            }
//
//            @Override
//            public Encoder<?> visitInteger(final UseInteger type) {
//
//                return Encoders.LONG();
//            }
//
//            @Override
//            public Encoder<?> visitNumber(final UseNumber type) {
//
//                return Encoders.DOUBLE();
//            }
//
//            @Override
//            public Encoder<?> visitString(final UseString type) {
//
//                return Encoders.STRING();
//            }
//
//            @Override
//            public Encoder<?> visitEnum(final UseEnum type) {
//
//                return Encoders.STRING();
//            }
//
//            @Override
//            public Encoder<?> visitObject(final UseObject type) {
//
//                return refType(type.getSchema(), expand);
//            }
//
//            @Override
//            public <T> Encoder<?> visitArray(final UseArray<T> type) {
//
//                return DataTypes.createArrayType(type.getType().visit(this));
//            }
//
//            @Override
//            public <T> Encoder<?> visitSet(final UseSet<T> type) {
//
//                return DataTypes.createArrayType(type.getType().visit(this));
//            }
//
//            @Override
//            public <T> Encoder<?> visitMap(final UseMap<T> type) {
//
//                return DataTypes.createMapType(DataTypes.StringType, type.getType().visit(this));
//            }
//
//            @Override
//            public Encoder<?> visitStruct(final UseStruct type) {
//
//                return structType(type.getSchema(), expand);
//            }
//
//            @Override
//            public Encoder<?> visitBinary(final UseBinary type) {
//
//                return Encoders.BINARY();
//            }
//
//            @Override
//            public Encoder<?> visitDate(final UseDate type) {
//
//                return Encoders.DATE();
//            }
//
//            @Override
//            public Encoder<?> visitDateTime(final UseDateTime type) {
//
//                return Encoders.DATE();
//            }
//        });
//    }
}
