package io.basestar.schema.use;

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

import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.schema.Constraint;
import io.basestar.schema.Instance;
import io.basestar.schema.Schema;
import io.basestar.schema.ViewSchema;
import io.basestar.schema.exception.InvalidTypeException;
import io.basestar.schema.util.Expander;
import io.basestar.schema.util.Ref;
import io.basestar.util.Name;
import lombok.Data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Data
public class UseView implements UseLinkable {

    private final ViewSchema schema;

    @Override
    public <R> R visit(final Visitor<R> visitor) {

        return visitor.visitView(this);
    }

    public static UseView from(final ViewSchema schema, final Object config) {

        return new UseView(schema);
    }

    @Override
    public UseView resolve(final Schema.Resolver resolver) {

        if(schema.isAnonymous()) {
            return this;
        } else {
            final ViewSchema resolved = resolver.requireViewSchema(schema.getQualifiedName());
            if(resolved == schema) {
                return this;
            } else {
                return new UseView(resolved);
            }
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public Instance create(final Object value, final boolean expand, final boolean suppress) {

        if(value == null) {
            return null;
        } else if(value instanceof Map) {
            return schema.create((Map<String, Object>) value, expand, suppress);
        } else if(suppress) {
            return null;
        } else {
            throw new InvalidTypeException();
        }
    }

    @Override
    public Code code() {

        return Code.VIEW;
    }

    @Override
    public void serializeValue(final Instance value, final DataOutput out) throws IOException {

        schema.serialize(value, out);
    }

    @Override
    public Instance deserializeValue(final DataInput in) throws IOException {

        return deserializeAnyValue(in);
    }

    public static Instance deserializeAnyValue(final DataInput in) throws IOException {

        return ViewSchema.deserialize(in);
    }

    @Override
    public Instance expand(final Instance value, final Expander expander, final Set<Name> expand) {

        if(value != null) {
            return schema.expand(value, expander, expand);
        } else {
            return null;
        }
    }

    @Override
    public Set<Constraint.Violation> validate(final Context context, final Name name, final Instance value) {

        if(value == null) {
            return Collections.emptySet();
        } else {
            return schema.validate(context, name, value);
        }
    }

    @Override
    public Set<Expression> refQueries(final Name otherSchemaName, final Set<Name> expand, final Name name) {

        return schema.refQueries(otherSchemaName, expand, name);
    }

    @Override
    public Set<Name> refExpand(final Name otherSchemaName, final Set<Name> expand) {

        return schema.refExpand(otherSchemaName, expand);
    }

    @Override
    public Map<Ref, Long> refVersions(final Instance value) {

        if(value == null) {
            return Collections.emptyMap();
        }
        return schema.refVersions(value);
    }

    @Override
    @Deprecated
    public Set<Name> requiredExpand(final Set<Name> names) {

        return schema.requiredExpand(names);
    }

    @Override
    public String toString() {

        return schema.getQualifiedName().toString();
    }
}
