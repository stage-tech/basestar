package io.basestar.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.basestar.schema.exception.MissingTypeException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Schema
 *
 * Base type for schema definitions
 *
 * @param <T>
 */

public interface Schema<T> extends Named, Described {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = ObjectSchema.Builder.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(name = EnumSchema.Builder.TYPE, value = EnumSchema.Builder.class),
            @JsonSubTypes.Type(name = StructSchema.Builder.TYPE, value = StructSchema.Builder.class),
            @JsonSubTypes.Type(name = ObjectSchema.Builder.TYPE, value = ObjectSchema.Builder.class)
    })
    interface Builder<T> extends Described {

        Schema<T> build(Resolver.Cyclic resolver, String name, int slot);
    }

    T create(Object value);

    int getSlot();

    interface Resolver {

        // 'Magic' resolver that handles cycles in namespace builder, instance under construction
        // must call resolver.constructing(this); as first constructor line.

        interface Cyclic extends Resolver {

            void constructing(Schema<?> schema);
        }

        @Nullable
        Schema<?> getSchema(String name);

        @Nonnull
        default Schema<?> requireSchema(final String name) {

            final Schema result = getSchema(name);
            if(result == null) {
                throw new MissingTypeException(name);
            } else {
                return result;
            }
        }

        @Nonnull
        default InstanceSchema requireInstanceSchema(final String name) {

            final Schema<?> schema = requireSchema(name);
            if(schema instanceof InstanceSchema) {
                return (InstanceSchema)schema;
            } else {
                throw new IllegalStateException(name + " is not an instance schema");
            }
        }

        @Nonnull
        default ObjectSchema requireObjectSchema(final String name) {

            final Schema<?> schema = requireSchema(name);
            if (schema instanceof ObjectSchema) {
                return (ObjectSchema) schema;
            } else {
                throw new IllegalStateException(name + " is not an object schema");
            }
        }

        @Nonnull
        default StructSchema requireStructSchema(final String name) {

            final Schema<?> schema = requireSchema(name);
            if(schema instanceof StructSchema) {
                return (StructSchema)schema;
            } else {
                throw new IllegalStateException(name + " is not a struct schema");
            }
        }

        @Nonnull
        default EnumSchema requireEnumSchema(final String name) {

            final Schema<?> schema = requireSchema(name);
            if(schema instanceof EnumSchema) {
                return (EnumSchema)schema;
            } else {
                throw new IllegalStateException(name + " is not an enum schema");
            }
        }
    }
}
