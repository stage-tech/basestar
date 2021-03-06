package io.basestar.expression.arithmetic;

/*-
 * #%L
 * basestar-expression
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

import io.basestar.expression.Binary;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.ExpressionVisitor;
import io.basestar.expression.match.BinaryMatch;
import io.basestar.expression.match.BinaryNumberMatch;
import io.basestar.expression.type.DecimalContext;
import io.basestar.expression.type.Numbers;
import lombok.Data;

import java.math.BigDecimal;
import java.math.MathContext;
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
     * @return number|string
     */

    @Override
    public Object evaluate(final Context context) {

        return apply(lhs.evaluate(context), rhs.evaluate(context));
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

    public static Object apply(final Object lhs, final Object rhs) {

        return VISITOR.apply(lhs, rhs);
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

        @Override
        public Number apply(final BigDecimal lhs, final BigDecimal rhs) {

            final DecimalContext.PrecisionAndScale ps = DecimalContext.DEFAULT.addition(lhs, rhs);
            final MathContext mc = new MathContext(ps.getPrecision(), Numbers.DECIMAL_ROUNDING_MODE);
            return lhs.add(rhs, mc).setScale(ps.getScale(), Numbers.DECIMAL_ROUNDING_MODE);
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

            if(lhs instanceof Set && rhs instanceof Set) {
                final Set<Object> sum = new HashSet<>();
                sum.addAll(lhs);
                sum.addAll(rhs);
                return sum;
            } else if(lhs instanceof List && rhs instanceof List) {
                final List<Object> sum = new ArrayList<>();
                sum.addAll(lhs);
                sum.addAll(rhs);
                return sum;
            } else {
                return defaultApply(lhs, rhs);
            }
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
