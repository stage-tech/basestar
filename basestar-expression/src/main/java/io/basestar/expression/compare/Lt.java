package io.basestar.expression.compare;

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
import io.basestar.expression.type.Values;
import lombok.Data;

import java.lang.reflect.Type;

/**
 * Less Than
 */

@Data
public class Lt implements Binary {

    public static final String TOKEN = "<";

    public static final int PRECEDENCE = Gte.PRECEDENCE;

    private final Expression lhs;

    private final Expression rhs;

    /**
     * lhs < rhs
     *
     * @param lhs any Left hand operand
     * @param rhs any Right hand operand
     */

    public Lt(final Expression lhs, final Expression rhs) {

        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public Expression create(final Expression lhs, final Expression rhs) {

        return new Lt(lhs, rhs);
    }

    @Override
    public Boolean evaluate(final Context context) {

        return Values.compare(lhs.evaluate(context), rhs.evaluate(context)) < 0;
    }

    @Override
    public Type type(final Context context) {

        return Boolean.class;
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

        return visitor.visitLt(this);
    }

    @Override
    public String toString() {

        return Binary.super.toString(lhs, rhs);
    }
}
