package io.basestar.storage.elasticsearch.mapping;

import io.basestar.schema.ObjectSchema;
import io.basestar.schema.Reserved;
import io.basestar.schema.StructSchema;
import io.basestar.schema.use.*;
import lombok.Data;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Data
public class Mappings {

    private final Map<String, FieldType> properties;

    public XContentBuilder build(XContentBuilder builder) throws IOException {

        builder = builder.startObject("properties");
        for(final Map.Entry<String, FieldType> entry : properties.entrySet()) {
            builder = builder.startObject(entry.getKey());
            builder = entry.getValue().build(builder);
            builder = builder.endObject();
        }
        builder = builder.endObject();
        return builder;
    }

    public interface Factory {

        Mappings mappings(ObjectSchema schema);

        class Default implements Factory {

            @Override
            public Mappings mappings(final ObjectSchema schema) {

                final Map<String, FieldType> properties = new HashMap<>();
                schema.metadataSchema().forEach((k, v) -> properties.put(k, fieldType(schema, k, v)));
                schema.getAllProperties().forEach((k, v) -> properties.put(k, fieldType(schema, k, v.getType())));
                return new Mappings(properties);
            }

            protected FieldType fieldType(final ObjectSchema schema, final String name, final Use<?> use) {

                switch (name) {
                    case Reserved.ID:
                    case Reserved.SCHEMA:
                        return FieldType.KEYWORD;
                    case Reserved.CREATED:
                    case Reserved.UPDATED:
                        return FieldType.DATE;
                    default:
                        return fieldType(use);
                }
            }

            protected FieldType fieldType(final StructSchema schema, final String name, final Use<?> use) {

                return fieldType(use);
            }

            protected FieldType fieldType(final Use<?> use) {

                return use.visit(new Use.Visitor<FieldType>() {

                    @Override
                    public FieldType visitBoolean(final UseBoolean type) {

                        return FieldType.BOOLEAN;
                    }

                    @Override
                    public FieldType visitInteger(final UseInteger type) {

                        return FieldType.LONG;
                    }

                    @Override
                    public FieldType visitNumber(final UseNumber type) {

                        return FieldType.DOUBLE;
                    }

                    @Override
                    public FieldType visitString(final UseString type) {

                        // FIXME
                        return FieldType.KEYWORD;
                    }

                    @Override
                    public FieldType visitEnum(final UseEnum type) {

                        return FieldType.KEYWORD;
                    }

                    @Override
                    public FieldType visitRef(final UseObject type) {

                        final Map<String, FieldType> properties = new HashMap<>();
                        ObjectSchema.REF_SCHEMA.forEach((k, v) -> properties.put(k, v.visit(this)));
                        return new FieldType.NestedType(properties);
                    }

                    @Override
                    public <T> FieldType visitArray(final UseArray<T> type) {

                        return new FieldType.ArrayType(type.getType().visit(this));
                    }

                    @Override
                    public <T> FieldType visitSet(final UseSet<T> type) {

                        return new FieldType.ArrayType(type.getType().visit(this));
                    }

                    @Override
                    public <T> FieldType visitMap(final UseMap<T> type) {

                        return new FieldType.MapType(type.getType().visit(this));
                    }

                    @Override
                    public FieldType visitStruct(final UseStruct type) {

                        final StructSchema schema = type.getSchema();
                        final Map<String, FieldType> properties = new HashMap<>();
                        schema.getAllProperties().forEach((k, v) -> properties.put(k, fieldType(schema, k, v.getType())));
                        return new FieldType.NestedType(properties);
                    }

                    @Override
                    public FieldType visitBinary(final UseBinary type) {

                        return FieldType.BINARY;
                    }
                });
            }
        }
    }
}
