package io.basestar.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBucketing {

    @Test
    void testCompatibleBucketing() throws Exception {

        final Namespace namespace = Namespace.load(TestObjectSchema.class.getResource("bucketing.yml"));

        assertTrue(namespace.requireViewSchema("SimpleView").isCoBucketed());
        assertFalse(namespace.requireViewSchema("DifferentOrderPointView").isCoBucketed());
        assertFalse(namespace.requireViewSchema("DifferentCountPointView").isCoBucketed());
        assertFalse(namespace.requireViewSchema("DifferentFunctionPointView").isCoBucketed());
        assertTrue(namespace.requireViewSchema("CompatiblePointView").isCoBucketed());
        assertTrue(namespace.requireViewSchema("NestedCompatiblePointView").isCoBucketed());
    }
}
