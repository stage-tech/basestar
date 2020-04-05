package io.basestar.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.basestar.schema.exception.InvalidTypeException;
import io.basestar.schema.exception.ReservedNameException;
import io.basestar.util.Nullsafe;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Enum Schema
 *
 * Enum schemas may be used to constrain strings to a predefined set of values. They are persisted by-value.
 *
 * <strong>Example</strong>
 * <pre>
 * MyEnum:
 *   type: enum
 *   values:
 *   - VALUE1
 *   - VALUE2
 *   - VALUE3
 * </pre>
 */

@Getter
public class EnumSchema implements Schema<String> {

    @Nonnull
    private final String name;

    private final int slot;

    /** Text description */

    @Nullable
    private final String description;

    /** Valid values for the enumeration (case sensitive) */

    @Nonnull
    private final List<String> values;

    @Data
    @Accessors(chain = true)
    public static class Builder implements Schema.Builder<String> {

        public static final String TYPE = "enum";

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String description;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private List<String> values;

        @Override
        public EnumSchema build(final Resolver.Cyclic resolver, final String name, final int slot) {

            return new EnumSchema(this, resolver, name, slot);
        }
    }

    public static Builder builder() {

        return new Builder();
    }

    private EnumSchema(final Builder builder, final Resolver.Cyclic resolver, final String name, final int slot) {

        resolver.constructing(this);
        this.name = name;
        this.slot = slot;
        this.description = builder.getDescription();
        this.values = Nullsafe.immutableCopy(builder.getValues());
        if(Reserved.isReserved(name)) {
            throw new ReservedNameException(name);
        }
    }

    @Override
    public String create(final Object value) {

        if(value == null) {
            return null;
        } else if(value instanceof String) {
            if(values.contains(value)) {
                return (String)value;
            } else {
                throw new InvalidTypeException();
            }
        } else {
            throw new InvalidTypeException();
        }
    }
}
