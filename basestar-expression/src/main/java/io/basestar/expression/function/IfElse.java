package io.basestar.expression.function;

import com.google.common.collect.ImmutableSet;
import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.ExpressionVisitor;
import io.basestar.expression.PathTransform;
import io.basestar.expression.constant.Constant;
import io.basestar.expression.type.Values;
import io.basestar.util.Path;
import lombok.Data;

import java.util.Set;

/**
 * If-Else
 *
 * Ternary operator
 *
 */

@Data
public class IfElse implements Expression {

    public static final String TOKEN = "?:";

    public static final int PRECEDENCE = Coalesce.PRECEDENCE + 1;

    private final Expression predicate;

    private final Expression then;

    private final Expression otherwise;

    /**
     * predicate ? then : otherwise
     *
     * @param predicate boolean Left hand operand
     * @param then expression Evaluated if true
     * @param otherwise expression Evaluated if false
     */

    public IfElse(final Expression predicate, final Expression then, final Expression otherwise) {

        this.predicate = predicate;
        this.then = then;
        this.otherwise = otherwise;
    }

    @Override
    public Expression bind(final Context context, final PathTransform root) {

        final Expression predicate = this.predicate.bind(context, root);
        if(predicate instanceof Constant) {
            final Object value = predicate.evaluate(context);
            if(Values.isTruthy(value)) {
                return then.bind(context, root);
            } else {
                return otherwise.bind(context, root);
            }
        } else {
            final Expression then = this.then.bind(context, root);
            final Expression _else = this.otherwise.bind(context, root);
            if(predicate == this.predicate && then == this.then && _else == this.otherwise) {
                return this;
            } else {
                return new IfElse(predicate, then, _else);
            }
        }
    }

    @Override
    public Object evaluate(final Context context) {

        if(predicate.evaluatePredicate(context)) {
            return then.evaluate(context);
        } else {
            return otherwise.evaluate(context);
        }
    }

//    @Override
//    public Query query() {
//
//        return Query.and();
//    }

    @Override
    public Set<Path> paths() {

        return ImmutableSet.<Path>builder()
                .addAll(predicate.paths())
                .addAll(then.paths())
                .addAll(otherwise.paths())
                .build();
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

        return visitor.visitIfElse(this);
    }

    @Override
    public String toString() {

        return predicate + " ? " + then + " : " + otherwise;
    }
}
