package io.basestar.expression.bitwise;

import io.basestar.expression.Binary;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.ExpressionVisitor;
import io.basestar.expression.compare.Eq;
import io.basestar.expression.type.match.BinaryMatch;
import io.basestar.expression.type.match.BinaryNumberMatch;
import lombok.Data;

/**
 * Bitwise And
 */

@Data
public class BitAnd implements Binary {

    public static final String TOKEN = "&";

    public static final int PRECEDENCE = Eq.PRECEDENCE + 1;

    private final Expression lhs;

    private final Expression rhs;

    /**
     * lhs & rhs
     *
     * @param lhs integer Left hand operand
     * @param rhs integer Right hand operand
     */

    public BitAnd(final Expression lhs, final Expression rhs) {

        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public Expression create(final Expression lhs, final Expression rhs) {

        return new BitAnd(lhs, rhs);
    }

    @Override
    public Long evaluate(final Context context) {

        return VISITOR.apply(lhs.evaluate(context), rhs.evaluate(context));
    }

//    @Override
//    public Query query() {
//
//        return Query.and();
//    }

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

        return visitor.visitBitAnd(this);
    }

    @Override
    public String toString() {

        return Binary.super.toString(lhs, rhs);
    }

    private static final BinaryNumberMatch<Long> NUMBER_VISITOR = new BinaryNumberMatch.Promoting<Long>() {

        @Override
        public String toString() {

            return TOKEN;
        }

        @Override
        public Long apply(final Long lhs, final Long rhs) {

            return lhs & rhs;
        }
    };

    private static final BinaryMatch<Long> VISITOR = new BinaryMatch.Coercing<Long>() {

        @Override
        public String toString() {

            return TOKEN;
        }

        @Override
        public Long apply(final Number lhs, final Number rhs) {

            return NUMBER_VISITOR.apply(lhs, rhs);
        }
    };
}
