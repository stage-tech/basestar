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

import com.google.common.collect.ImmutableMap;
import io.basestar.schema.Schema;
import io.basestar.schema.util.ValueContext;
import io.basestar.util.Name;
import io.leangen.geantyref.TypeFactory;
import io.swagger.v3.oas.models.media.ArraySchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInput;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Array Type
 *
 * <strong>Example</strong>
 * <pre>
 * type:
 *   array: string
 * </pre>
 *
 * @param <T>
 */

@Data
@Slf4j
public class UseArray<T> implements UseCollection<T, List<T>> {

    public static UseArray<Object> DEFAULT = new UseArray<>(UseAny.DEFAULT);

    public static final String NAME = "array";

    private final Use<T> type;

    @Override
    public <R> R visit(final Visitor<R> visitor) {

        return visitor.visitArray(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T2> UseArray<T2> transform(final Function<Use<T>, Use<T2>> fn) {

        final Use<T2> type2 = fn.apply(type);
        if(type2 == type ) {
            return (UseArray<T2>)this;
        } else {
            return new UseArray<>(type2);
        }
    }

    public static <T> UseArray<T> from(final Use<T> type) {

        return new UseArray<>(type);
    }

    public static UseArray<?> from(final Object config) {

        return Use.fromNestedConfig(config, (type, nestedConfig) -> new UseArray<>(type));
    }

    @Override
    public Object toConfig(final boolean optional) {

        return ImmutableMap.of(
                Use.name(NAME, optional), type
        );
    }

    @Override
    public UseArray<?> resolve(final Schema.Resolver resolver) {

        final Use<?> resolved = type.resolve(resolver);
        if(resolved == type) {
            return this;
        } else {
            return new UseArray<>(resolved);
        }
    }

    @Override
    public List<T> create(final ValueContext context, final Object value, final Set<Name> expand) {

        return context.createArray(this, value, expand);
    }

    @Override
    public Code code() {

        return Code.ARRAY;
    }

    @Override
    public Type javaType(final Name name) {

        if(name.isEmpty()) {
            return TypeFactory.parameterizedClass(List.class, type.javaType());
        } else {
            return type.javaType(name.withoutFirst());
        }
    }

    @Override
    public List<T> defaultValue() {

        return Collections.emptyList();
    }

    @Override
    public io.swagger.v3.oas.models.media.Schema<?> openApi(final Set<Name> expand) {

        return new ArraySchema().items(type.openApi(expand));
    }

    @Override
    public List<T> deserializeValue(final DataInput in) throws IOException {

        return deserializeAnyValue(in);
    }

    public static <T> List<T> deserializeAnyValue(final DataInput in) throws IOException {

        final List<T> result = new ArrayList<>();
        final int size = in.readInt();
        for(int i = 0; i != size; ++i) {
            result.add(Use.deserializeAny(in));
        }
        return result;
    }

    @Override
    public List<T> transformValues(final List<T> value, final BiFunction<Use<T>, T, T> fn) {

        if(value != null) {
            boolean changed = false;
            final List<T> result = new ArrayList<>();
            for(final T before : value) {
                final T after = fn.apply(type, before);
                result.add(after);
                changed = changed || (before != after);
            }
            return changed ? result : value;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {

        return NAME + "<" + type + ">";
    }

    @Override
    public boolean areEqual(final List<T> a, final List<T> b) {

        if(a == null || b == null) {
            return a == null && b == null;
        } else if(a.size() == b.size()) {
            for(int i = 0; i != a.size(); ++i) {
                if(!type.areEqual(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
