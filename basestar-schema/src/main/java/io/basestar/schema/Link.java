package io.basestar.schema;

/*-
 * #%L
 * basestar-schema
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.jackson.serde.AbbrevListDeserializer;
import io.basestar.jackson.serde.ExpressionDeseriaizer;
import io.basestar.schema.exception.MissingMemberException;
import io.basestar.schema.exception.ReservedNameException;
import io.basestar.schema.use.Use;
import io.basestar.schema.use.UseArray;
import io.basestar.schema.use.UseRef;
import io.basestar.schema.util.Expander;
import io.basestar.util.Name;
import io.basestar.util.Nullsafe;
import io.basestar.util.PagedList;
import io.basestar.util.Sort;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * Link
 */

@Getter
@Accessors(chain = true)
public class Link implements Member {

    @Nonnull
    private final Name qualifiedName;

    @Nullable
    private final String description;

    @Nonnull
    private final ObjectSchema schema;

    @Nonnull
    private final Expression expression;

    @Nonnull
    private final List<Sort> sort;

    @Nullable
    private final Visibility visibility;

    @Nonnull
    private final Map<String, Object> extensions;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Builder implements Member.Builder {

        @Nullable
        private Name schema;

        @Nullable
        private String description;

        @Nullable
        @JsonSerialize(using = ToStringSerializer.class)
        @JsonDeserialize(using = ExpressionDeseriaizer.class)
        private Expression expression;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        @JsonDeserialize(using = AbbrevListDeserializer.class)
        private List<Sort> sort;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private Visibility visibility;

        @Nullable
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extensions;

        public Link build(final Schema.Resolver resolver, final Name qualifiedName) {

            return new Link(this, resolver, qualifiedName);
        }
    }

    public static Builder builder() {

        return new Builder();
    }

    public Link(final Builder builder, final Schema.Resolver resolver, final Name qualifiedName) {

        this.qualifiedName = qualifiedName;
        this.description = builder.getDescription();
        this.schema = resolver.requireObjectSchema(builder.getSchema());
        this.expression = Nullsafe.require(builder.getExpression());
        this.sort = Nullsafe.immutableCopy(builder.getSort());
        this.visibility = builder.getVisibility();
        this.extensions = Nullsafe.immutableSortedCopy(builder.getExtensions());
        if(Reserved.isReserved(qualifiedName.last())) {
            throw new ReservedNameException(qualifiedName);
        }
    }

    @Override
    public Use<?> getType() {

        return new UseArray<>(new UseRef(schema));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object expand(final Object value, final Expander expander, final Set<Name> expand) {

        if(expand == null) {
            return null;
        } else {
            return expander.expandLink(this, (PagedList<Instance>)value, expand);
        }
    }

    @Override
    public Set<Name> requiredExpand(final Set<Name> names) {

        final Set<Name> result = new HashSet<>();
        result.add(Name.empty());
        result.addAll(schema.requiredExpand(names));
        return result;
    }

    //FIXME
    @Override
    public <T> Use<T> typeOf(final Name name) {

        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Name> transientExpand(final Name name, final Set<Name> expand) {

        return schema.transientExpand(name, expand);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object applyVisibility(final Context context, final Object value) {

        return transform((PagedList<Instance>)value, before -> schema.applyVisibility(context, before));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object evaluateTransients(final Context context, final Object value, final Set<Name> expand) {

        return transform((PagedList<Instance>)value, before -> schema.evaluateTransients(context, before, expand));
    }

    @Override
    public Set<Expression> refQueries(Name otherSchemaName, final Set<Name> expand, final Name name) {

        // FIXME
        return Collections.emptySet();
    }

    @Override
    public Set<Name> refExpand(Name otherSchemaName, final Set<Name> expand) {

        // FIXME
        return Collections.emptySet();
    }

    private PagedList<Instance> transform(final PagedList<Instance> value, final Function<Instance, Instance> fn) {

        if(value == null) {
            return null;
        } else {
            boolean changed = false;
            final List<Instance> results = new ArrayList<>();
            for(final Instance before : value) {
                final Instance after = fn.apply(before);
                results.add(after);
                changed = changed || after != before;
            }
            if(changed) {
                return new PagedList<>(results, value.getPaging(), PagedList.Stats.UNKNOWN);
            } else {
                return value;
            }
        }
    }

    public interface Resolver {

        Map<String, Link> getDeclaredLinks();

        Map<String, Link> getLinks();

        default Link getLink(final String name, final boolean inherited) {

            if(inherited) {
                return getLinks().get(name);
            } else {
                return getDeclaredLinks().get(name);
            }
        }

        default Link requireLink(final String name, final boolean inherited) {

            final Link result = getLink(name, inherited);
            if (result == null) {
                throw new MissingMemberException(name);
            } else {
                return result;
            }
        }
    }
}
