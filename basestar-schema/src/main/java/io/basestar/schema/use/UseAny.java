package io.basestar.schema.use;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.schema.Constraint;
import io.basestar.schema.Instance;
import io.basestar.schema.Schema;
import io.basestar.schema.util.Expander;
import io.basestar.schema.util.Ref;
import io.basestar.util.Name;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

// FIXME: not properly implemented, only being used currently in codegen

public class UseAny implements Use<Object> {

    public static final String NAME = "any";

    public static final UseAny DEFAULT = new UseAny();

    @Override
    public <R> R visit(final Use.Visitor<R> visitor) {

        return visitor.visitAny(this);
    }

    public static UseMap<?> from(final Object config) {

        return Use.fromNestedConfig(config, (type, nestedConfig) -> new UseMap<>(type));
    }

    @Override
    public Object toConfig() {

        return NAME;
    }

    @Override
    public UseAny resolve(final Schema.Resolver resolver) {

        return this;
    }

    @Override
    public Object create(final Object value, final boolean expand, final boolean suppress) {

        return value;
    }

    @Override
    public Use.Code code() {

        return Use.Code.ANY;
    }

    @Override
    public io.swagger.v3.oas.models.media.Schema<?> openApi() {

        throw new IllegalStateException("FIXME");
    }

    @Override
    public void serializeValue(final Object value, final DataOutput out) throws IOException {

        throw new IllegalStateException("FIXME");
    }

    @Override
    public Object deserializeValue(final DataInput in) throws IOException {

        return deserializeAnyValue(in);
    }

    public static Object deserializeAnyValue(final DataInput in) throws IOException {

        throw new IllegalStateException("FIXME");
    }

    @Override
    public Set<Expression> refQueries(final Name otherSchemaName, final Set<Name> expand, final Name name) {

        return Collections.emptySet();
    }

    @Override
    public Set<Name> refExpand(final Name otherSchemaName, final Set<Name> expand) {

        return Collections.emptySet();
    }

    @Override
    public Map<Ref, Long> refVersions(final Object value) {

        return Collections.emptyMap();
    }

    @Override
    public Use<?> typeOf(final Name name) {

        if(name.isEmpty()) {
            return this;
        } else {
            throw new IllegalStateException();
        }
    }

    private static Set<Name> branch(final Map<String, Set<Name>> branches, final String key) {

        return Collections.emptySet();
    }

    @Override
    public Object expand(final Object value, final Expander expander, final Set<Name> expand) {

        return value;
    }

    @Override
    public Object applyVisibility(final Context context, final Object value) {

        return value;
    }

    @Override
    public Object evaluateTransients(final Context context, final Object value, final Set<Name> expand) {

        return value;
    }

    @Override
    public Set<Name> transientExpand(final Name name, final Set<Name> expand) {

        return Collections.emptySet();
    }

    @Override
    public Set<Constraint.Violation> validate(final Context context, final Name name, final Object value) {

        return Collections.emptySet();
    }

    @Override
    public Set<Name> requiredExpand(final Set<Name> names) {

        return Collections.emptySet();
    }

    @Override
    @Deprecated
    public Multimap<Name, Instance> refs(final Object value) {

        return HashMultimap.create();
    }

    @Override
    public String toString() {

        return NAME;
    }

    @Override
    public void collectDependencies(final Set<Name> expand, final Map<Name, Schema<?>> out) {

    }
}