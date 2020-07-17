package io.basestar.mapper;

/*-
 * #%L
 * basestar-mapper
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

import io.basestar.mapper.internal.EnumSchemaMapper;
import io.basestar.mapper.internal.StructSchemaMapper;
import io.basestar.mapper.internal.annotation.SchemaDeclaration;
import io.basestar.mapper.internal.annotation.SchemaModifier;
import io.basestar.schema.Namespace;
import io.basestar.type.AnnotationContext;
import io.basestar.type.TypeContext;
import io.basestar.type.has.HasType;
import io.basestar.util.Name;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MappingContext {

    private final ConcurrentMap<Class<?>, SchemaMapper<?, ?>> mappers = new ConcurrentHashMap<>();

    public Namespace.Builder namespace(final Class<?> ... classes) {

        return namespace(Arrays.asList(classes));
    }

    public Namespace.Builder namespace(final Collection<Class<?>> classes) {

        final Set<Class<?>> dependencies = new HashSet<>(classes);
        final Map<Class<?>, SchemaMapper<?, ?>> all = new HashMap<>();
        while(!dependencies.isEmpty()) {
            final Set<Class<?>> next = new HashSet<>();
            dependencies.forEach(cls -> {
                if(!all.containsKey(cls)) {
                    final SchemaMapper<?, ?> schemaMapper = schemaMapper(cls);
                    all.put(cls, schemaMapper);
                    next.addAll(schemaMapper.dependencies());
                }
            });
            dependencies.clear();
            dependencies.addAll(next);
        }
        final Namespace.Builder builder = Namespace.builder();
        all.forEach((cls, schemaMapper) -> builder.setSchema(schemaMapper.name(), schemaMapper.schema()));
        return builder;
    }

    @SuppressWarnings("unchecked")
    public <T, O> SchemaMapper<T, O> schemaMapper(final Class<T> cls) {

        return (SchemaMapper<T, O>) this.mappers.computeIfAbsent(cls, this::newSchemaMapper);
    }

    @SuppressWarnings("unchecked")
    private <T, O> SchemaMapper<T, O> newSchemaMapper(final Class<T> cls) {

        final TypeContext type = TypeContext.from(cls);

        final List<AnnotationContext<?>> declAnnotations = type.annotations().stream()
                .filter(a -> a.type().annotations().stream()
                        .anyMatch(HasType.match(SchemaDeclaration.class)))
                .collect(Collectors.toList());

        if (declAnnotations.size() == 0) {
            final Name name = Name.of(type.simpleName());
            if (type.isEnum()) {
                return (SchemaMapper<T, O>) new EnumSchemaMapper<>(this, name, type);
            } else {
                return (SchemaMapper<T, O>) new StructSchemaMapper<>(this, name, type);
            }
        } else if (declAnnotations.size() == 1) {
            try {
                final AnnotationContext<?> annotation = declAnnotations.get(0);
                final SchemaDeclaration schemaDeclaration = annotation.type().annotation(SchemaDeclaration.class).annotation();
                final TypeContext declType = TypeContext.from(schemaDeclaration.value());
                final SchemaDeclaration.Declaration decl = declType.declaredConstructors().get(0).newInstance(annotation.annotation());
                return applyModifiers(type, (SchemaMapper<T, O>) decl.mapper(this, type));
            } catch (final IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        } else {
            final String names = declAnnotations.stream().map(v -> v.type().simpleName())
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Annotations " + names + " are not allowed on the same type");
        }
    }

    private <T, O> SchemaMapper<T, O> applyModifiers(final TypeContext type, final SchemaMapper<T, O> input) throws IllegalAccessException, InstantiationException, InvocationTargetException {

        final List<AnnotationContext<?>> modAnnotations = type.annotations().stream()
                .filter(a -> a.type().annotations().stream()
                        .anyMatch(HasType.match(SchemaModifier.class)))
                .collect(Collectors.toList());

        SchemaMapper<T, O> output = input;

        for(final AnnotationContext<?> annotation : modAnnotations) {
            final SchemaModifier schemaModifier = annotation.type().annotation(SchemaModifier.class).annotation();
            final TypeContext modType = TypeContext.from(schemaModifier.value());
            final Class<?> modMapperType = modType.find(SchemaModifier.Modifier.class).typeParameters().get(0).type().erasedType();
            if(modMapperType.isAssignableFrom(output.getClass())) {
                final SchemaModifier.Modifier<SchemaMapper<T, O>> mod = modType.declaredConstructors().get(0).newInstance(annotation.annotation());
                output = mod.modify(output);
            } else {
                throw new IllegalStateException("Modifier " + modType.erasedType() + " not supported on " + output.getClass());
            }
        }

        return output;
    }
}
