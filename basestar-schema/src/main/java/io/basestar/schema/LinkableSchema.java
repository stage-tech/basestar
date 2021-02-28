package io.basestar.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.basestar.jackson.serde.AbbrevListDeserializer;
import io.basestar.schema.use.Use;
import io.basestar.util.Immutable;
import io.basestar.util.Name;
import io.basestar.util.Nullsafe;
import io.basestar.util.Warnings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface LinkableSchema extends InstanceSchema, Link.Resolver, Permission.Resolver {

    interface Descriptor<S extends LinkableSchema> extends InstanceSchema.Descriptor<S>, Link.Resolver.Descriptor, Permission.Resolver.Descriptor {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Set<Name> getExpand();

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonDeserialize(using = AbbrevListDeserializer.class)
        List<Bucketing> getBucket();

        interface Self<S extends LinkableSchema> extends InstanceSchema.Descriptor.Self<S>, Descriptor<S> {

            @Override
            default Set<Name> getExpand() {

                return self().getDeclaredExpand();
            }

            default List<Bucketing> getBucket() {

                return self().getDeclaredBucketing();
            }

            @Override
            default Map<String, Link.Descriptor> getLinks() {

                return self().describeDeclaredLinks();
            }

            @Override
            default Map<String, Permission.Descriptor> getPermissions() {

                return self().describeDeclaredPermissions();
            }
        }
    }

    interface Builder<B extends Builder<B, S>, S extends LinkableSchema> extends InstanceSchema.Builder<B, S>, Descriptor<S>, Link.Resolver.Builder<B>, Permission.Resolver.Builder<B> {

        B setExpand(Set<Name> expand);

        B setBucket(List<Bucketing> bucket);
    }

    static SortedSet<Name> extendExpand(final List<? extends InstanceSchema> base, final Set<Name> extend) {

        return Immutable.sortedSet(Stream.concat(
                base.stream().flatMap(schema -> schema.getExpand().stream()),
                Nullsafe.orDefault(extend).stream()
        ).collect(Collectors.toSet()));
    }

    Set<Name> getDeclaredExpand();

    List<Bucketing> getDeclaredBucketing();

    default List<Bucketing> getEffectingBucketing() {

        final List<Bucketing> bucketing = getDeclaredBucketing();
        if(bucketing.isEmpty()) {
            return ImmutableList.of(
                    new Bucketing(ImmutableList.of(Name.of(id())))
            );
        } else {
            return bucketing;
        }
    }

    @Override
    Descriptor<? extends LinkableSchema> descriptor();

    String id();

    String id(Map<String, Object> data);

    @SuppressWarnings(Warnings.RETURN_GENERIC_WILDCARD)
    Use<?> typeOfId();

    default Instance deleted(final String id) {

        return new Instance(ImmutableMap.of(
                id(), id,
                Reserved.DELETED, true
        ));
    }
}
