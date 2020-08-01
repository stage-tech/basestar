package io.basestar.graphql.schema;

/*-
 * #%L
 * basestar-graphql
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

import com.google.common.collect.ImmutableList;
import graphql.language.*;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.basestar.graphql.GraphQLStrategy;
import io.basestar.graphql.GraphQLUtils;
import io.basestar.schema.*;
import io.basestar.schema.use.*;

import java.util.*;

public class SchemaAdaptor {

    private final Namespace namespace;

    private final GraphQLStrategy strategy;

    public SchemaAdaptor(final Namespace namespace, final GraphQLStrategy strategy) {

        this.namespace = namespace;
        this.strategy = strategy;
    }

    public TypeDefinitionRegistry typeDefinitionRegistry() {

        final TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        final Map<String, Use<?>> mapTypes = new HashMap<>();
        namespace.getSchemas().forEach((k, schema) -> {
            registry.add(typeDefinition(schema));
            if(schema instanceof InstanceSchema) {
                final InstanceSchema instanceSchema = (InstanceSchema)schema;
                mapTypes.putAll(mapTypes(instanceSchema));
                if(schema instanceof ObjectSchema) {
                    final ObjectSchema objectSchema = (ObjectSchema)instanceSchema;
                    registry.add(inputExpressionTypeDefinition(objectSchema));
                    registry.add(pageTypeDefinition(objectSchema));
                    registry.add(createInputTypeDefinition(objectSchema));
                    if(objectSchema.hasMutableProperties()) {
                        registry.add(updateInputTypeDefinition(objectSchema));
                        registry.add(patchInputTypeDefinition(objectSchema));
                    }
                } else {
                    registry.add(inputTypeDefinition(instanceSchema));
                }
            }
        });
        registry.add(queryDefinition());
        registry.add(mutationDefinition());
        registry.add(subscriptionDefinition());
        registry.add(batchTypeDefinition());
        registry.add(InputObjectTypeDefinition.newInputObjectDefinition()
                .name(strategy.inputRefTypeName())
                .inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                        .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build())
                .build());
        registry.add(consistencyTypeDefinition());
        mapTypes.forEach((k, v) -> {
            registry.add(mapEntryTypeDefinition(v));
            registry.add(inputMapEntryTypeDefinition(v));
        });
        return registry;
    }

    private ObjectTypeDefinition pageTypeDefinition(final InstanceSchema instanceSchema) {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(strategy.pageTypeName(instanceSchema));
        builder.fieldDefinition(FieldDefinition.newFieldDefinition()
                .name(strategy.pageItemsFieldName())
                .type(new ListType(new TypeName(strategy.typeName(instanceSchema))))
                .build());
        builder.fieldDefinition(FieldDefinition.newFieldDefinition()
                .name(strategy.pagePagingFieldName())
                .type(new TypeName(GraphQLUtils.STRING_TYPE))
                .build());
        return builder.build();
    }

    private ObjectTypeDefinition queryDefinition() {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(GraphQLUtils.QUERY_TYPE);
        namespace.forEachObjectSchema((schemaName, schema) -> {
            builder.fieldDefinition(readDefinition(schema));
            builder.fieldDefinition(queryDefinition(schema));
            schema.getLinks()
                    .forEach((linkName, link) -> builder.fieldDefinition(queryLinkDefinition(schema, link)));
        });
        return builder.build();
    }

    public FieldDefinition readDefinition(final ObjectSchema schema) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.readMethodName(schema));
        builder.type(new TypeName(strategy.typeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.versionArgumentName()).type(new TypeName(GraphQLUtils.INT_TYPE)).build());
        return builder.build();
    }

    public FieldDefinition queryDefinition(final ObjectSchema schema) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.queryMethodName(schema));
        builder.type(new TypeName(strategy.pageTypeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.queryArgumentName()).type(new TypeName(GraphQLUtils.STRING_TYPE)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.sortArgumentName()).type(new ListType(new TypeName(GraphQLUtils.STRING_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.countArgumentName()).type(new TypeName(GraphQLUtils.INT_TYPE)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.pagingArgumentName()).type(new TypeName(GraphQLUtils.STRING_TYPE)).build());
        return builder.build();
    }

    public FieldDefinition queryLinkDefinition(final ObjectSchema schema, final Link link) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.queryLinkMethodName(schema, link));
        builder.type(new TypeName(strategy.pageTypeName(link.getSchema())));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.countArgumentName()).type(new TypeName(GraphQLUtils.INT_TYPE)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.pagingArgumentName()).type(new TypeName(GraphQLUtils.STRING_TYPE)).build());
        return builder.build();
    }

    private ObjectTypeDefinition mutationDefinition() {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(GraphQLUtils.MUTATION_TYPE);
        namespace.forEachObjectSchema((k, v) -> {
            builder.fieldDefinition(createDefinition(v));
            if(v.hasMutableProperties()) {
                builder.fieldDefinition(updateDefinition(v));
                builder.fieldDefinition(patchDefinition(v));
            }
            builder.fieldDefinition(deleteDefinition(v));
        });
        builder.fieldDefinition(batchDefinition());
        return builder.build();
    }

    private FieldDefinition batchDefinition() {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.batchMethodName());
        builder.type(new TypeName(strategy.batchTypeName()));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.consistencyArgumentName())
                .type(new TypeName(strategy.consistencyTypeName())).build());
        return builder.build();
    }

    private ObjectTypeDefinition batchTypeDefinition() {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(strategy.batchTypeName());
        namespace.forEachObjectSchema((k, v) -> {
            builder.fieldDefinition(createDefinition(v));
            if(v.hasMutableProperties()) {
                builder.fieldDefinition(updateDefinition(v));
                builder.fieldDefinition(patchDefinition(v));
            }
            builder.fieldDefinition(deleteDefinition(v));
        });
        return builder.build();
    }

    private EnumTypeDefinition consistencyTypeDefinition() {

        final EnumTypeDefinition.Builder builder = EnumTypeDefinition.newEnumTypeDefinition();
        builder.name(strategy.consistencyTypeName());
        Arrays.stream(Consistency.values()).forEach(v -> {
            if(v != Consistency.NONE) {
                builder.enumValueDefinition(EnumValueDefinition.newEnumValueDefinition().name(v.name()).build());
            }
        });
        return builder.build();
    }

    public FieldDefinition createDefinition(final ObjectSchema schema) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.createMethodName(schema));
        builder.type(new TypeName(strategy.typeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new TypeName(GraphQLUtils.ID_TYPE)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.dataArgumentName()).type(new TypeName(strategy.createInputTypeName(schema))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.expressionsArgumentName()).type(new TypeName(strategy.inputExpressionsTypeName(schema))).build());
        return builder.build();
    }

    public FieldDefinition updateDefinition(final ObjectSchema schema, final String methodName, final String typeName) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(methodName);
        builder.type(new TypeName(strategy.typeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.versionArgumentName()).type(new TypeName(GraphQLUtils.INT_TYPE)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.dataArgumentName()).type(new TypeName(typeName)).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.expressionsArgumentName()).type(new TypeName(strategy.inputExpressionsTypeName(schema))).build());
        return builder.build();
    }

    public FieldDefinition updateDefinition(final ObjectSchema schema) {

        return updateDefinition(schema, strategy.updateMethodName(schema), strategy.updateInputTypeName(schema));
    }

    public FieldDefinition patchDefinition(final ObjectSchema schema) {

        return updateDefinition(schema, strategy.patchMethodName(schema), strategy.patchInputTypeName(schema));
    }

    public FieldDefinition deleteDefinition(final ObjectSchema schema) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.deleteMethodName(schema));
        builder.type(new TypeName(strategy.typeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.versionArgumentName()).type(new TypeName(GraphQLUtils.INT_TYPE)).build());
        return builder.build();
    }

    private ObjectTypeDefinition subscriptionDefinition() {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(GraphQLUtils.SUBSCRIPTION_TYPE);
        namespace.forEachObjectSchema((schemaName, schema) -> {
            builder.fieldDefinition(subscribeDefinition(schema));
        });
        return builder.build();
    }

    public FieldDefinition subscribeDefinition(final ObjectSchema schema) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(strategy.subscribeMethodName(schema));
        builder.type(new TypeName(strategy.typeName(schema)));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(strategy.idArgumentName()).type(new NonNullType(new TypeName(GraphQLUtils.ID_TYPE))).build());
        return builder.build();
    }

    public InputObjectTypeDefinition inputTypeDefinition(final InstanceSchema schema) {

        final InputObjectTypeDefinition.Builder builder = InputObjectTypeDefinition.newInputObjectDefinition();
        builder.name(strategy.inputTypeName(schema));
        builder.description(description(schema.getDescription()));
        schema.getProperties()
                .forEach((k, v) -> builder.inputValueDefinition(inputValueDefinition(v, true)));
        return builder.build();
    }

    public InputObjectTypeDefinition inputTypeDefinition(final ObjectSchema schema, final String name, final boolean create, final boolean required) {

        final InputObjectTypeDefinition.Builder builder = InputObjectTypeDefinition.newInputObjectDefinition();
        builder.name(name);
        builder.description(description(schema.getDescription()));
        schema.getProperties()
                .forEach((k, v) -> {
                    if(create || !v.isImmutable()) {
                        builder.inputValueDefinition(inputValueDefinition(v, required));
                    }
                });
        return builder.build();
    }

    public InputObjectTypeDefinition createInputTypeDefinition(final ObjectSchema schema) {

        return inputTypeDefinition(schema, strategy.createInputTypeName(schema), true, true);
    }

    public InputObjectTypeDefinition updateInputTypeDefinition(final ObjectSchema schema) {

        return inputTypeDefinition(schema, strategy.updateInputTypeName(schema), false, true);
    }

    public InputObjectTypeDefinition patchInputTypeDefinition(final ObjectSchema schema) {

        return inputTypeDefinition(schema, strategy.patchInputTypeName(schema), true, false);
    }

    private SDLDefinition<?> inputExpressionTypeDefinition(final InstanceSchema schema) {

        final InputObjectTypeDefinition.Builder builder = InputObjectTypeDefinition.newInputObjectDefinition();
        builder.name(strategy.inputExpressionsTypeName(schema));
        builder.description(description(schema.getDescription()));
        schema.getProperties()
                .forEach((k, v) -> builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                        .name(k).type(new TypeName(GraphQLUtils.STRING_TYPE))
                        .build()));
        return builder.build();
    }

    public InputValueDefinition inputValueDefinition(final Property property, final boolean required) {

        final InputValueDefinition.Builder builder = InputValueDefinition.newInputValueDefinition();
        builder.name(property.getName());
        if(property.getDescription() != null) {
            builder.description(new Description(property.getDescription(), null, true));
        }
        final Type<?> valueType = inputType(property.getType());
        final Type<?> type = required && property.isRequired() ? new NonNullType(valueType) : valueType;
        builder.type(type);
        return builder.build();
    }

    public TypeDefinition<?> typeDefinition(final Schema<?> schema) {

        if (schema instanceof InstanceSchema) {
            return typeDefinition((InstanceSchema) schema);
        } else if (schema instanceof EnumSchema) {
            return typeDefinition((EnumSchema) schema);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public TypeDefinition<?> typeDefinition(final InstanceSchema schema) {

        if(schema.isConcrete()) {
            final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
            builder.name(strategy.typeName(schema));
            builder.description(description(schema.getDescription()));
            if (schema.getExtend() != null) {
                builder.implementz(implementz(schema));
            }
            fieldDefinitions(schema).forEach(builder::fieldDefinition);
            return builder.build();
        } else {
            final InterfaceTypeDefinition.Builder builder = InterfaceTypeDefinition.newInterfaceTypeDefinition();
            builder.name(strategy.typeName(schema));
            builder.description(description(schema.getDescription()));
            fieldDefinitions(schema).forEach(builder::definition);
            return builder.build();
        }
    }

    @SuppressWarnings("rawtypes")
    private List<Type> implementz(final InstanceSchema schema) {

        final InstanceSchema parent = schema.getExtend();
        if(parent != null) {
            return ImmutableList.<Type>builder()
                    .addAll(implementz(parent))
                    .add(new TypeName(strategy.typeName(parent)))
                    .build();
        } else {
            return ImmutableList.of();
        }
    }

    private List<FieldDefinition> fieldDefinitions(final InstanceSchema schema) {

        final List<FieldDefinition> fields = new ArrayList<>();
        schema.metadataSchema()
                .forEach((k, v) -> fields.add(metadataFieldDefinition(k, v)));
        schema.getProperties()
                .forEach((k, v) -> {
                    if(!v.isAlwaysHidden()) {
                        fields.add(fieldDefinition(v));
                    }
                });
        if(schema instanceof Link.Resolver) {
            ((Link.Resolver) schema).getLinks()
                    .forEach((k, v) -> {
                        if(!v.isAlwaysHidden()) {
                            fields.add(FieldDefinition.newFieldDefinition()
                                    .name(k)
                                    .type(new ListType(new TypeName(strategy.typeName(v.getSchema()))))
                                    .inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                                            .name(strategy.queryArgumentName()).type(new TypeName(GraphQLUtils.STRING_TYPE)).build())
                                    .build());
                        }
                    });
        }
        if(schema instanceof Transient.Resolver) {
            ((Transient.Resolver) schema).getDeclaredTransients()
                    .forEach((k, v) -> {
                        // Can only show typed transients
                        if(!v.isAlwaysHidden() && v.getType() != null) {
                            fields.add(FieldDefinition.newFieldDefinition()
                                    .name(k)
                                    .type(type(v.getType()))
                                    .build());
                        }
                    });
        }
        return fields;
    }

    private Description description(final String description) {

        if(description != null) {
            return new Description(description, null, true);
        } else {
            return null;
        }
    }

    public EnumTypeDefinition typeDefinition(final EnumSchema schema) {

        final EnumTypeDefinition.Builder builder = EnumTypeDefinition.newEnumTypeDefinition();
        builder.name(strategy.typeName(schema));
        if(schema.getDescription() != null) {
            builder.description(new Description(schema.getDescription(), null, true));
        }
        schema.getValues().forEach(v -> builder.enumValueDefinition(EnumValueDefinition.newEnumValueDefinition()
                .name(v).build()));
        return builder.build();
    }

    public FieldDefinition fieldDefinition(final Property property) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(property.getName());
        if(property.getDescription() != null) {
            builder.description(new Description(property.getDescription(), null, true));
        }
        final Type<?> type = type(property.getType());
        builder.type(type);
        return builder.build();
    }

    public FieldDefinition metadataFieldDefinition(final String name, final Use<?> type) {

        final FieldDefinition.Builder builder = FieldDefinition.newFieldDefinition();
        builder.name(name);
        if(strategy.idArgumentName().equals(name)) {
            builder.type(new TypeName(GraphQLUtils.ID_TYPE));
        } else {
            builder.type(type(type));
        }
        return builder.build();
    }

    private InputObjectTypeDefinition inputMapEntryTypeDefinition(final Use<?> type) {

        final InputObjectTypeDefinition.Builder builder = InputObjectTypeDefinition.newInputObjectDefinition();
        builder.name(strategy.inputMapEntryTypeName(type));
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(GraphQLUtils.MAP_KEY).type(new NonNullType(new TypeName(GraphQLUtils.STRING_TYPE))).build());
        builder.inputValueDefinition(InputValueDefinition.newInputValueDefinition()
                .name(GraphQLUtils.MAP_VALUE).type(inputType(type)).build());
        return builder.build();
    }

    private ObjectTypeDefinition mapEntryTypeDefinition(final Use<?> type) {

        final ObjectTypeDefinition.Builder builder = ObjectTypeDefinition.newObjectTypeDefinition();
        builder.name(strategy.mapEntryTypeName(type));
        builder.fieldDefinition(FieldDefinition.newFieldDefinition()
                .name(GraphQLUtils.MAP_KEY).type(new NonNullType(new TypeName(GraphQLUtils.STRING_TYPE))).build());
        builder.fieldDefinition(FieldDefinition.newFieldDefinition()
                .name(GraphQLUtils.MAP_VALUE).type(type(type)).build());
        return builder.build();
    }

    public Type<?> type(final Use<?> type) {

        return type.visit(new Use.Visitor<Type<?>>() {

            @Override
            public Type<?> visitBoolean(final UseBoolean type) {

                return new TypeName(GraphQLUtils.BOOLEAN_TYPE);
            }

            @Override
            public Type<?> visitInteger(final UseInteger type) {

                return new TypeName(GraphQLUtils.INT_TYPE);
            }

            @Override
            public Type<?> visitNumber(final UseNumber type) {

                return new TypeName(GraphQLUtils.FLOAT_TYPE);
            }

            @Override
            public Type<?> visitString(final UseString type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitEnum(final UseEnum type) {

                return new TypeName(strategy.typeName(type.getSchema()));
            }

            @Override
            public Type<?> visitObject(final UseObject type) {

                return new TypeName(strategy.typeName(type.getSchema()));
            }

            @Override
            public <T> Type<?> visitArray(final UseArray<T> type) {

                return new ListType(type.getType().visit(this));
            }

            @Override
            public <T> Type<?> visitSet(final UseSet<T> type) {

                return new ListType(type.getType().visit(this));
            }

            @Override
            public <T> Type<?> visitMap(final UseMap<T> type) {

                return new ListType(new TypeName(strategy.mapEntryTypeName(type.getType())));
            }

            @Override
            public Type<?> visitStruct(final UseStruct type) {

                return new TypeName(strategy.typeName(type.getSchema()));
            }

            @Override
            public Type<?> visitBinary(final UseBinary type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitDate(final UseDate type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitDateTime(final UseDateTime type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitView(final UseView type) {

                return new TypeName(strategy.typeName(type.getSchema()));
            }
        });
    }

    public Type<?> inputType(final Use<?> type) {

        return type.visit(new Use.Visitor<Type<?>>() {

            @Override
            public Type<?> visitBoolean(final UseBoolean type) {

                return new TypeName(GraphQLUtils.BOOLEAN_TYPE);
            }

            @Override
            public Type<?> visitInteger(final UseInteger type) {

                return new TypeName(GraphQLUtils.INT_TYPE);
            }

            @Override
            public Type<?> visitNumber(final UseNumber type) {

                return new TypeName(GraphQLUtils.FLOAT_TYPE);
            }

            @Override
            public Type<?> visitString(final UseString type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitEnum(final UseEnum type) {

                return new TypeName(strategy.typeName(type.getSchema()));
            }

            @Override
            public Type<?> visitObject(final UseObject type) {

                return new TypeName(strategy.inputRefTypeName());
            }

            @Override
            public <T> Type<?> visitArray(final UseArray<T> type) {

                return new ListType(type.getType().visit(this));
            }

            @Override
            public <T> Type<?> visitSet(final UseSet<T> type) {

                return new ListType(type.getType().visit(this));
            }

            @Override
            public <T> Type<?> visitMap(final UseMap<T> type) {

                return new ListType(new TypeName(strategy.inputMapEntryTypeName(type.getType())));
            }

            @Override
            public Type<?> visitStruct(final UseStruct type) {

                return new TypeName(strategy.inputTypeName(type.getSchema()));
            }

            @Override
            public Type<?> visitBinary(final UseBinary type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitDate(final UseDate type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitDateTime(final UseDateTime type) {

                return new TypeName(GraphQLUtils.STRING_TYPE);
            }

            @Override
            public Type<?> visitView(final UseView type) {

                return new TypeName(strategy.inputTypeName(type.getSchema()));
            }
        });
    }

    private Map<String, Use<?>> mapTypes(final InstanceSchema schema) {

        final Map<String, Use<?>> mapTypes = new HashMap<>();
        schema.getDeclaredProperties().forEach((k, v) -> {
            v.getType().visit(new Use.Visitor<Void>() {

                @Override
                public Void visitBoolean(final UseBoolean type) {

                    return null;
                }

                @Override
                public Void visitInteger(final UseInteger type) {

                    return null;
                }

                @Override
                public Void visitNumber(final UseNumber type) {

                    return null;
                }

                @Override
                public Void visitString(final UseString type) {

                    return null;
                }

                @Override
                public Void visitEnum(final UseEnum type) {

                    return null;
                }

                @Override
                public Void visitObject(final UseObject type) {

                    return null;
                }

                @Override
                public <T> Void visitArray(final UseArray<T> type) {

                    type.getType().visit(this);
                    return null;
                }

                @Override
                public <T> Void visitSet(final UseSet<T> type) {

                    type.getType().visit(this);
                    return null;
                }

                @Override
                public <T> Void visitMap(final UseMap<T> type) {

                    type.getType().visit(this);
                    mapTypes.put(strategy.mapEntryTypeName(type.getType()), type.getType());
                    return null;
                }

                @Override
                public Void visitStruct(final UseStruct type) {

                    return null;
                }

                @Override
                public Void visitBinary(final UseBinary type) {

                    return null;
                }

                @Override
                public Void visitDate(final UseDate type) {

                    return null;
                }

                @Override
                public Void visitDateTime(final UseDateTime type) {

                    return null;
                }

                @Override
                public Void visitView(final UseView type) {

                    return null;
                }
            });
        });
        return mapTypes;
    }

}
