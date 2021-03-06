package io.basestar.expression.literal;

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

import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.ExpressionVisitor;
import io.basestar.expression.Renaming;
import io.basestar.expression.constant.Constant;
import io.basestar.expression.iterate.ForAll;
import io.basestar.util.Name;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Literal Array
 *
 * Create an array by providing values
 */

@Data
public class LiteralArray implements Expression {

    public static final String TOKEN = "[]";

    public static final int PRECEDENCE = ForAll.PRECEDENCE + 1;

    private final List<Expression> args;

    /**
     * [args...]
     *
     * @param args expression Array values
     */

    public LiteralArray(final List<Expression> args) {

        this.args = args;
    }

    public LiteralArray(final Expression ... args) {

        this(Arrays.asList(args));
    }

    @Override
    public Expression bind(final Context context, final Renaming root) {

        boolean changed = false;
        boolean constant = true;
        final List<Expression> args = new ArrayList<>();
        for(final Expression before : this.args) {
            final Expression after = before.bind(context, root);
            args.add(after);
            constant = constant && after instanceof Constant;
            changed = changed || before != after;
        }
        if(constant) {
            return new Constant(evaluate(args, context));
        } else if(changed) {
            return new LiteralArray(args);
        } else {
            return this;
        }
    }

    @Override
    public List<?> evaluate(final Context context) {

        return evaluate(args, context);
    }

    private static List<?> evaluate(final List<Expression> args, final Context context) {

        return args.stream()
                .map(v -> v.evaluate(context)).collect(Collectors.toList());
    }

    @Override
    public Set<Name> names() {

        return args.stream().flatMap(v -> v.names().stream())
                .collect(Collectors.toSet());
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
    public boolean isConstant(final Closure closure) {

        return args.stream().allMatch(arg -> arg.isConstant(closure));
    }

    @Override
    public <T> T visit(final ExpressionVisitor<T> visitor) {

        return visitor.visitLiteralArray(this);
    }

    @Override
    public List<Expression> expressions() {

        return args;
    }

    @Override
    public Expression copy(final List<Expression> expressions) {

        return args == expressions ? this : new LiteralArray(expressions);
    }

    @Override
    public String toString() {

        return "[" + args.stream().map(Expression::toString).collect(Collectors.joining(", ")) + "]";
    }
}
