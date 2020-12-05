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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import io.basestar.expression.Context;
import io.basestar.jackson.serde.NameDeserializer;
import io.basestar.schema.exception.ReservedNameException;
import io.basestar.schema.use.Use;
import io.basestar.util.Name;
import io.basestar.util.Nullsafe;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Object Schema
 *
 * Objects are persisted by reference, and may be polymorphic.
 *
 * Objects may contain properties, transients and links, these member types share the same
 * namespace, meaning you cannot define a property and a transient or link with the same name.
 *
 * <strong>Example</strong>
 * <pre>
 * MyObject:
 *   type: object
 *   properties:
 *      myProperty1:
 *          type: string
 * </pre>
 */

@Getter
@Accessors(chain = true)
public class ObjectSchema implements ReferableSchema, Index.Resolver, Transient.Resolver, Permission.Resolver {

    public static final String ID = ReferableSchema.ID;

    public static final String SCHEMA = ReferableSchema.SCHEMA;

    public static final String CREATED = ReferableSchema.CREATED;

    public static final String UPDATED = ReferableSchema.UPDATED;

    public static final String VERSION = ReferableSchema.VERSION;

    public static final String HASH = ReferableSchema.HASH;

    public static final Name ID_NAME = Name.of(ID);

    public static final SortedMap<String, Use<?>> METADATA_SCHEMA = ReferableSchema.METADATA_SCHEMA;

    public static final SortedMap<String, Use<?>> REF_SCHEMA = ReferableSchema.REF_SCHEMA;

    @Nonnull
    private final Name qualifiedName;

    private final int slot;

    /**
     * Current version of the schema, defaults to 1
     */

    private final long version;

    /**
     * Parent schema, may be another object schema or a struct schema
     */

    @Nullable
    private final ReferableSchema extend;

    /**
     * Id configuration
     */

    @Nullable
    private final Id id;

    /**
     * History configuration
     */

    @Nonnull
    private final History history;

    /**
     * Description of the schema
     */

    @Nullable
    private final String description;

    /**
     * Map of property definitions (shares namespace with transients and links)
     */

    @Nonnull
    private final SortedMap<String, Property> declaredProperties;

    /**
     * Map of property definitions (shares namespace with transients and links)
     */

    @Nonnull
    private final SortedMap<String, Property> properties;

    /**
     * Map of transient definitions (shares namespace with properties and links)
     */

    @Nonnull
    private final SortedMap<String, Transient> declaredTransients;

    /**
     * Map of link definitions (shares namespace with properties and transients)
     */

    @Nonnull
    private final SortedMap<String, Transient> transients;

    /**
     * Map of link definitions (shares namespace with properties and transients)
     */

    @Nonnull
    private final SortedMap<String, Link> declaredLinks;

    /**
     * Map of link definitions (shares namespace with properties and transients)
     */

    @Nonnull
    private final SortedMap<String, Link> links;

    /**
     * Map of index definitions
     */

    @Nonnull
    private final SortedMap<String, Index> declaredIndexes;

    /**
     * Map of index definitions
     */

    @Nonnull
    private final SortedMap<String, Index> indexes;

    /**
     * Map of permissions
     */

    @Nonnull
    private final SortedMap<String, Permission> declaredPermissions;

    @Nonnull
    private final SortedMap<String, Permission> permissions;

    @Nonnull
    private final List<Constraint> constraints;

    @Nonnull
    private final SortedSet<Name> declaredExpand;

    @Nonnull
    private final SortedSet<Name> expand;

    private final boolean concrete;

    private final boolean readonly;

    @Nonnull
    private final SortedMap<String, Serializable> extensions;

    private final Collection<Schema<?>> directlyExtending;

    @JsonDeserialize(as = Builder.class)
    public interface Descriptor extends ReferableSchema.Descriptor {

        String TYPE = "object";

        @Override
        default String getType() {

            return TYPE;
        }

        Long getVersion();

        Id.Descriptor getId();

        Boolean getConcrete();

        Boolean getReadonly();

        History getHistory();

        @Override
        default ObjectSchema build(final Resolver.Constructing resolver, final Version version, final Name qualifiedName, final int slot) {

            return new ObjectSchema(this, resolver, version, qualifiedName, slot);
        }

        @Override
        default ObjectSchema build(final Name qualifiedName) {

            return build(Resolver.Constructing.ANONYMOUS, Version.CURRENT, qualifiedName, Schema.anonymousSlot());
        }

        @Override
        default ObjectSchema build() {

            return build(Schema.anonymousQualifiedName());
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"type", "description", "version", "extend", "concrete", "id", "history", "properties", "transients", "links", "indexes", "permissions", "extensions"})
    public static class Builder implements InstanceSchema.Builder, Descriptor, Link.Resolver.Builder, Transient.Resolver.Builder {

        private Long version;

        @Nullable
        private Name extend;

        @Nullable
        private Id.Builder id;

        @Nullable
        private Boolean concrete;

        @Nullable
        private Boolean readonly;

        @Nullable
        private History history;

        @Nullable
        private String description;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private Map<String, Property.Descriptor> properties;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private Map<String, Transient.Descriptor> transients;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private Map<String, Link.Descriptor> links;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private Map<String, Index.Descriptor> indexes;

        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private List<? extends Constraint> constraints;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        private Map<String, Permission.Descriptor> permissions;

        @Nullable
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
        @JsonDeserialize(contentUsing = NameDeserializer.class)
        private Set<Name> expand;

        @Nullable
        private Map<String, Serializable> extensions;

        @Override
        public Builder setProperty(final String name, final Property.Descriptor v) {

            properties = Nullsafe.immutableCopyPut(properties, name, v);
            return this;
        }

        @Override
        public Builder setTransient(final String name, final Transient.Descriptor v) {

            transients = Nullsafe.immutableCopyPut(transients, name, v);
            return this;
        }

        @Override
        public Builder setLink(final String name, final Link.Descriptor v) {

            links = Nullsafe.immutableCopyPut(links, name, v);
            return this;
        }

        public Builder setIndex(final String name, final Index.Descriptor v) {

            indexes = Nullsafe.immutableCopyPut(indexes, name, v);
            return this;
        }

        public Builder setPermission(final String name, final Permission.Descriptor v) {

            permissions = Nullsafe.immutableCopyPut(permissions, name, v);
            return this;
        }
    }

    public static Builder builder() {

        return new Builder();
    }

    private ObjectSchema(final Descriptor descriptor, final Schema.Resolver.Constructing resolver, final Version version, final Name qualifiedName, final int slot) {

        resolver.constructing(this);
        this.qualifiedName = qualifiedName;
        this.slot = slot;
        this.version = Nullsafe.orDefault(descriptor.getVersion(), 1L);
        if (descriptor.getExtend() != null) {
            this.extend = resolver.requireObjectSchema(descriptor.getExtend());
        } else {
            this.extend = null;
        }
        this.description = descriptor.getDescription();
        this.id = descriptor.getId() == null ? null : descriptor.getId().build(qualifiedName.with(ID));
        this.history = Nullsafe.orDefault(descriptor.getHistory(), History.ENABLED);
        this.declaredProperties = Nullsafe.immutableSortedCopy(descriptor.getProperties(), (k, v) -> v.build(resolver, version, qualifiedName.with(k)));
        this.declaredTransients = Nullsafe.immutableSortedCopy(descriptor.getTransients(), (k, v) -> v.build(qualifiedName.with(k)));
        this.declaredLinks = Nullsafe.immutableSortedCopy(descriptor.getLinks(), (k, v) -> v.build(resolver, qualifiedName.with(k)));
        this.declaredIndexes = Nullsafe.immutableSortedCopy(descriptor.getIndexes(), (k, v) -> v.build(qualifiedName.with(k)));
        this.constraints = Nullsafe.immutableCopy(descriptor.getConstraints());
        this.declaredPermissions = Nullsafe.immutableSortedCopy(descriptor.getPermissions(), (k, v) -> v.build(k));
        this.declaredExpand = Nullsafe.immutableSortedCopy(descriptor.getExpand());
        this.concrete = Nullsafe.orDefault(descriptor.getConcrete(), Boolean.TRUE);
        this.readonly = Nullsafe.orDefault(descriptor.getReadonly());
        this.extensions = Nullsafe.immutableSortedCopy(descriptor.getExtensions());
        if (Reserved.isReserved(qualifiedName.last())) {
            throw new ReservedNameException(qualifiedName);
        }
        Stream.of(this.declaredProperties, this.declaredLinks, this.declaredTransients)
                .flatMap(v -> v.keySet().stream()).forEach(k -> {
            if (METADATA_SCHEMA.containsKey(k)) {
                throw new ReservedNameException(k);
            }
        });
        if (extend != null) {
            this.properties = merge(extend.getProperties(), declaredProperties);
            if (extend instanceof ObjectSchema) {
                final ObjectSchema objectExtend = (ObjectSchema) extend;
                this.transients = merge(objectExtend.getTransients(), declaredTransients);
                this.links = merge(objectExtend.getLinks(), declaredLinks);
                this.indexes = merge(objectExtend.getIndexes(), declaredIndexes);
                this.permissions = mergePermissions(objectExtend.getPermissions(), declaredPermissions);
                this.expand = merge(objectExtend.getExpand(), declaredExpand);
            } else {
                this.transients = declaredTransients;
                this.links = declaredLinks;
                this.indexes = declaredIndexes;
                this.permissions = declaredPermissions;
                this.expand = declaredExpand;
            }
        } else {
            this.properties = declaredProperties;
            this.transients = declaredTransients;
            this.links = declaredLinks;
            this.indexes = declaredIndexes;
            this.permissions = declaredPermissions;
            this.expand = declaredExpand;
        }
        this.directlyExtending = ImmutableList.copyOf(resolver.getExtendingSchemas(qualifiedName));
    }

    private static <T> SortedMap<String, T> merge(final Map<String, T> a, final Map<String, T> b) {

        final SortedMap<String, T> merged = new TreeMap<>();
        merged.putAll(a);
        merged.putAll(b);
        return Collections.unmodifiableSortedMap(merged);
    }

    private static <T extends Comparable<T>> SortedSet<T> merge(final Set<T> a, final Set<T> b) {

        final SortedSet<T> merged = new TreeSet<>();
        merged.addAll(a);
        merged.addAll(b);
        return Collections.unmodifiableSortedSet(merged);
    }

    private static SortedMap<String, Permission> mergePermissions(final Map<String, Permission> a, final Map<String, Permission> b) {

        final SortedMap<String, Permission> merged = new TreeMap<>(a);
        b.forEach((k, v) -> {
            if (merged.containsKey(k)) {
                merged.put(k, merged.get(k).merge(v));
            } else {
                merged.put(k, v);
            }
        });
        return Collections.unmodifiableSortedMap(merged);
    }

    @Override
    public Set<Constraint.Violation> validate(final Context context, final Name name, final Instance before, final Instance after) {

        final Set<Constraint.Violation> violations = new HashSet<>();
        if (id != null) {
            violations.addAll(id.validate(name, Instance.getId(after), context));
        }
        violations.addAll(ReferableSchema.super.validate(context, name, before, after));
        return violations;
    }

    @Override
    public Descriptor descriptor() {

        return new Descriptor() {

            @Override
            public Long getVersion() {

                return version;
            }

            @Override
            public Name getExtend() {

                return extend == null ? null : extend.getQualifiedName();
            }

            @Override
            public Id.Descriptor getId() {

                return id == null ? null : id.descriptor();
            }

            @Override
            public Boolean getConcrete() {

                return concrete;
            }

            @Override
            public Boolean getReadonly() {

                return readonly;
            }

            @Override
            public History getHistory() {

                return history;
            }

            @Override
            public Map<String, Property.Descriptor> getProperties() {

                return declaredProperties.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().descriptor()
                ));
            }

            @Override
            public Map<String, Transient.Descriptor> getTransients() {

                return declaredTransients.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().descriptor()
                ));
            }

            @Override
            public Map<String, Link.Descriptor> getLinks() {

                return declaredLinks.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().descriptor()
                ));
            }

            @Override
            public Map<String, Index.Descriptor> getIndexes() {

                return declaredIndexes.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().descriptor()
                ));
            }

            @Override
            public List<? extends Constraint> getConstraints() {

                return constraints;
            }

            @Override
            public Map<String, Permission.Descriptor> getPermissions() {

                return declaredPermissions.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().descriptor()
                ));
            }

            @Override
            public Set<Name> getExpand() {

                return expand;
            }

            @Nullable
            @Override
            public String getDescription() {

                return description;
            }

            @Override
            public Map<String, Serializable> getExtensions() {

                return extensions;
            }
        };
    }

    @Override
    public boolean equals(final Object other) {

        return other instanceof ObjectSchema && qualifiedNameEquals(other);
    }

    @Override
    public int hashCode() {

        return qualifiedNameHashCode();
    }
}
