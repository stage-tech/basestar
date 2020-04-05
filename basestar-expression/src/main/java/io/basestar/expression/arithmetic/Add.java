package io.basestar.expression.arithmetic;

import io.basestar.expression.Binary;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.ExpressionVisitor;
import io.basestar.expression.type.match.BinaryMatch;
import io.basestar.expression.type.match.BinaryNumberMatch;
import lombok.Data;

import java.util.*;

/**
 * Add
 *
 * Numeric addition/String concatenation
 */

@Data
public class Add implements Binary {

    public static final String TOKEN = "+";

    public static final int PRECEDENCE = Mul.PRECEDENCE + 1;

    private final Expression lhs;

    private final Expression rhs;

    /**
     * lhs + rhs
     *
     * @param lhs number|string Left hand operand
     * @param rhs number|string Right hand operand
     */

    public Add(final Expression lhs, final Expression rhs) {

        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public Expression create(final Expression lhs, final Expression rhs) {

        return new Add(lhs, rhs);
    }

    /**
     * lhs + rhs
     *
     * @param lhs Left hand operand
     * @param rhs Right hand operand
     */

    public static String evaluate(final String lhs, final String rhs) {

        return lhs + rhs;
    }

    /**
     * @return number|string
     */

    @Override
    public Object evaluate(final Context context) {

        return VISITOR.apply(lhs.evaluate(context), rhs.evaluate(context));
    }

    @Override
    public String token() {

        return TOKEN;
    }

    @Override
    public int precedence() {

        return PRECEDENCE;
    }

    @Override
    public <T> T visit(final ExpressionVisitor<T> visitor) {

        return visitor.visitAdd(this);
    }

    @Override
    public String toString() {

        return Binary.super.toString(lhs, rhs);
    }

    private static final BinaryNumberMatch<Number> NUMBER_VISITOR = new BinaryNumberMatch.Promoting<Number>() {

        @Override
        public String toString() {

            return TOKEN;
        }

        @Override
        public Number apply(final Long lhs, final Long rhs) {

            return lhs + rhs;
        }

        @Override
        public Number apply(final Double lhs, final Double rhs) {

            return lhs + rhs;
        }
    };

    private static final BinaryMatch<Object> VISITOR = new BinaryMatch.Coercing<Object>() {

        @Override
        public String toString() {

            return TOKEN;
        }

        @Override
        public Object apply(final Number lhs, final Number rhs) {

            return NUMBER_VISITOR.apply(lhs, rhs);
        }

        @Override
        public Object apply(final String lhs, final String rhs) {

            return lhs + rhs;
        }

        @Override
        public Object apply(final Collection<?> lhs, final Collection<?> rhs) {

            final List<Object> sum = new ArrayList<>();
            sum.addAll(lhs);
            sum.addAll(rhs);
            return sum;
        }

        @Override
        public Object apply(final Map<?, ?> lhs, final Map<?, ?> rhs) {

            final Map<Object, Object> sum = new HashMap<>();
            sum.putAll(lhs);
            sum.putAll(rhs);
            return sum;
        }
    };
}
