package io.basestar.storage.view;

import io.basestar.expression.Context;
import io.basestar.expression.Expression;
import io.basestar.expression.aggregate.Aggregate;
import io.basestar.expression.aggregate.AggregateExtractingVisitor;
import io.basestar.expression.constant.NameConstant;
import io.basestar.schema.*;
import io.basestar.schema.expression.InferenceContext;
import io.basestar.schema.expression.InferenceVisitor;
import io.basestar.schema.use.Use;
import io.basestar.util.Immutable;
import io.basestar.util.Name;
import io.basestar.util.Nullsafe;
import io.basestar.util.Sort;

import java.util.*;

public interface QueryPlanner<T extends QueryStage> {

    T plan(QueryStageVisitor<T> visitor, LinkableSchema schema, Expression expression, List<Sort> sort, Set<Name> expand);

    class Default<T extends QueryStage> implements QueryPlanner<T> {

        @Override
        public T plan(final QueryStageVisitor<T> visitor, final LinkableSchema schema, final Expression expression, final List<Sort> sort, final Set<Name> expand) {

            return stage(visitor, schema, expression, sort, expand);
        }

        protected T stage(final QueryStageVisitor<T> visitor, final LinkableSchema schema, final Expression expression, final List<Sort> sort, final Set<Name> expand) {

            final boolean constExpr = expression != null && expression.isConstant();
            if(constExpr && !expression.evaluatePredicate(Context.init())) {
                return visitor.empty(schema, expand);
            } else {
                T stage = stage(visitor, schema);
                final Set<Name> remainingExpand = Immutable.copyRemoveAll(expand, stage.getLayout().getExpand());
                if(!remainingExpand.isEmpty()) {
                    stage = visitor.expand(stage, schema, remainingExpand);
                }
                // TODO check if expression is already covered in the stage
                if (!constExpr && expression != null) {
                    stage = visitor.filter(stage, expression);
                }
                // TODO check enclosing/equivalent rather than only equal
                if(!sort.isEmpty() && !sort.equals(stage.getSort())) {
                    stage = visitor.sort(stage, sort);
                }
                return stage;
            }
        }

        protected T stage(final QueryStageVisitor<T> visitor, final LinkableSchema schema) {

            if (schema instanceof ViewSchema) {
                return viewStage(visitor, (ViewSchema)schema);
            } else {
                return refStage(visitor, (ReferableSchema)schema);
            }
        }

        protected T refStage(final QueryStageVisitor<T> visitor, final ReferableSchema schema) {

            return visitor.schema(visitor.source(schema), schema);
        }

        protected T viewStage(final QueryStageVisitor<T> visitor, final ViewSchema schema) {

            final ViewSchema.From from = schema.getFrom();
            final LinkableSchema fromSchema = from.getSchema();
            T stage = stage(visitor, fromSchema, schema.getWhere(), schema.getSort(), from.getExpand());
            if (schema.isAggregating() || schema.isGrouping()) {
                final Map<String, Use<?>> outputSchema = schema.getSchema();
                final List<String> group = schema.getGroup();

                // Extract aggregates and create output map stage
                final Map<String, Expression> output = new HashMap<>();
                final AggregateExtractingVisitor extractAggregates = new AggregateExtractingVisitor();
                for(final Map.Entry<String, Property> entry : schema.getProperties().entrySet()) {
                    final String name = entry.getKey();
                    final Expression expr = Nullsafe.require(entry.getValue().getExpression());
                    if(expr.isAggregate()) {
                        final Expression withoutAggregates = extractAggregates.visit(expr);
                        output.put(name, withoutAggregates);
                    } else if(group.contains(name)) {
                        output.put(name, new NameConstant(name));
                    } else {
                        throw new IllegalStateException("Property " + name + " must be group or aggregate");
                    }
                }

                final Map<String, Expression> input = new HashMap<>();
                final Map<String, Use<?>> inputSchema = new HashMap<>();
                final Map<String, Use<?>> aggSchema = new HashMap<>();
                for(final Map.Entry<String, Property> entry : schema.getGroupProperties().entrySet()) {
                    final String name = entry.getKey();
                    final Property property = entry.getValue();
                    input.put(name, Nullsafe.require(property.getExpression()));
                    inputSchema.put(name, property.typeOf());
                    aggSchema.put(name, property.typeOf());
                }

                final InferenceContext context = InferenceContext.from(from.getSchema())
                        .overlay(Reserved.THIS, InferenceContext.from(schema));
                final InferenceVisitor inference = new InferenceVisitor(context);

                // Replace non-constant aggregate args with lookups to the first map stage
                final Map<String, Aggregate> aggregates = new HashMap<>();
                for(final Map.Entry<String, Aggregate> entry : extractAggregates.getAggregates().entrySet()) {
                    final String name = entry.getKey();
                    final Aggregate aggregate = entry.getValue();
                    final List<Expression> args = new ArrayList<>();
                    for(final Expression expr : aggregate.expressions()) {
                        if(expr.isConstant()) {
                            args.add(expr);
                        } else {
                            final String id = "_" + expr.digest();
                            args.add(new NameConstant(id));
                            input.put(id, expr);
                            inputSchema.put(id, inference.visit(expr));
                        }
                    }
                    aggregates.put(name, aggregate.copy(args));
                    aggSchema.put(name, inference.visit(aggregate));
                }

                stage = visitor.map(stage, input, inputSchema);
                stage = visitor.aggregate(stage, group, aggregates, aggSchema);
                stage = visitor.map(stage, output, outputSchema);
                stage = visitor.schema(stage, schema);

            } else {
                final Map<String, Expression> output = new HashMap<>();
                final Map<String, Use<?>> outputSchema = new HashMap<>();
                for(final Map.Entry<String, Property> entry : schema.getProperties().entrySet()) {
                    final String name = entry.getKey();
                    final Property property = entry.getValue();
                    output.put(name, Nullsafe.require(property.getExpression()));
                    outputSchema.put(name, property.typeOf());
                }
                output.put(schema.id(), new NameConstant(fromSchema.id()));
                outputSchema.put(schema.id(), schema.typeOfId());

                stage = visitor.map(stage, output, outputSchema);
                stage = visitor.schema(stage, schema);
            }
            return stage;
        }
    }
}
