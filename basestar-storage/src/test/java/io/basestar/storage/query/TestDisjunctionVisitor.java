package io.basestar.storage.query;

import com.google.common.collect.ImmutableSet;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.compare.Eq;
import io.basestar.expression.constant.Constant;
import io.basestar.expression.constant.PathConstant;
import io.basestar.expression.iterate.ForAny;
import io.basestar.expression.iterate.Of;
import io.basestar.expression.logical.And;
import io.basestar.expression.logical.Or;
import io.basestar.util.Path;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDisjunctionVisitor {

    @Test
    public void testDisjunction() {

        final Eq a = new Eq(new PathConstant(Path.of("a")), new Constant(1));
        final Eq b = new Eq(new PathConstant(Path.of("b")), new Constant(2));
        final Eq c = new Eq(new PathConstant(Path.of("c")), new Constant(3));
        final Eq d = new Eq(new PathConstant(Path.of("d")), new Constant(3));
        final Eq e = new Eq(new PathConstant(Path.of("e")), new Constant(4));
        final Eq f = new Eq(new PathConstant(Path.of("f")), new Constant(5));
        final Eq g = new Eq(new PathConstant(Path.of("g")), new Constant(6));

        assertEquals(ImmutableSet.of(a, b), disjunction(new Or(a, b)));

        assertEquals(ImmutableSet.of(new And(a, c), new And(b, c)),
                disjunction(new And(new Or(a, b), c)));

        assertEquals(ImmutableSet.of(), disjunction(new And(new And(new Or(), a), b)));

        assertEquals(ImmutableSet.of(new And(a, b)),
                disjunction(new And(new Or(new Or(), a), b)));

        assertEquals(ImmutableSet.of(
                new And(a, d),
                new And(b, d),
                new And(c, d),
                e,
                new And(f, g)),
                disjunction(new Or(new And(new Or(a, b, c), d), new Or(e, new And(f, g)))));
    }

    private Set<Expression> disjunction(final Expression e) {

        return new DisjunctionVisitor().visit(e);
    }

    @Test
    public void testForAnyOr() {

        final Expression root = Expression.parse("x.a || x.b for any x of y").bind(Context.init());
        final Set<Expression> result = disjunction(root);

        assertEquals(ImmutableSet.of(
                new ForAny(
                        new PathConstant(Path.of("x", "a")),
                        new Of("x", new PathConstant(Path.of("y")))),
                new ForAny(
                        new PathConstant(Path.of("x", "b")),
                        new Of("x", new PathConstant(Path.of("y"))))
        ), result);

        // x.a || x.b for any x of y
        // x.a for any x of y || x.b for any x of y
    }
}
