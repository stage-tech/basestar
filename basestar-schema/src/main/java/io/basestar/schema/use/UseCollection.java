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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.basestar.expression.Context;
import io.basestar.schema.Expander;
import io.basestar.schema.Instance;
import io.basestar.util.Path;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public interface UseCollection<V, T extends Collection<V>> extends Use<T> {

    Use<V> getType();

    T transform(T value, Function<V, V> fn);

    @Override
    default void serializeValue(final T value, final DataOutput out) throws IOException {

        final Use<V> type = getType();
        out.writeInt(value.size());
        for(final V v : value) {
            type.serialize(v, out);
        }
    }

    @Override
    default Use<?> typeOf(final Path path) {

        if(path.isEmpty()) {
            return this;
        } else {
            return getType().typeOf(path);
        }
    }

    @Override
    default T expand(final T value, final Expander expander, final Set<Path> expand) {

        final Use<V> type = getType();
        return transform(value, before -> type.expand(before, expander, expand));
    }

    @Override
    default T applyVisibility(final Context context, final T value) {

        final Use<V> type = getType();
        return transform(value, before -> type.applyVisibility(context, before));
    }

    @Override
    default T evaluateTransients(final Context context, final T value, final Set<Path> expand) {

        final Use<V> type = getType();
        return transform(value, before -> type.evaluateTransients(context, before, expand));
    }

    @Override
    default Set<Path> transientExpand(final Path path, final Set<Path> expand) {

        return getType().transientExpand(path, expand);
    }

    @Override
    default Set<Path> requiredExpand(final Set<Path> paths) {

        return getType().requiredExpand(paths);
    }

    @Override
    @Deprecated
    default Multimap<Path, Instance> refs(final T value) {

        final Multimap<Path, Instance> result = HashMultimap.create();
        if(value != null) {
            value.forEach(v -> getType().refs(v).forEach(result::put));
        }
        return result;
    }
}

