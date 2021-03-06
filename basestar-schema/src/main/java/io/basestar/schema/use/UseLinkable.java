package io.basestar.schema.use;

import io.basestar.schema.LinkableSchema;
import io.basestar.schema.ReferableSchema;
import io.basestar.schema.ViewSchema;
import io.basestar.util.Name;

import java.util.Map;
import java.util.Set;

public interface UseLinkable extends UseInstance {

    static UseLinkable from(final LinkableSchema schema, final Object config) {

        if(schema instanceof ReferableSchema) {
            return UseRef.from((ReferableSchema)schema, config);
        } else if(schema instanceof ViewSchema) {
            return UseView.from((ViewSchema)schema, config);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    LinkableSchema getSchema();


    @Override
    default void collectMaterializationDependencies(final Set<Name> expand, final Map<Name, LinkableSchema> out) {

        final LinkableSchema schema = getSchema();
        if(!out.containsKey(schema.getQualifiedName())) {
            out.put(schema.getQualifiedName(), schema);
            schema.collectMaterializationDependencies(expand, out);
        }
        UseInstance.super.collectMaterializationDependencies(expand, out);
    }
}
