package io.basestar.storage.aggregate;

/*-
 * #%L
 * basestar-storage
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
import io.basestar.storage.exception.InvalidAggregateException;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Data
public class Avg implements Aggregate {

    public static final String NAME = "avg";

    private final Expression input;

    public static Aggregate create(final List<Expression> args) {

        if(args.size() == 1) {
            return new Avg(args.get(0));
        } else {
            throw new InvalidAggregateException(NAME);
        }
    }

    @Override
    public <T> T visit(final AggregateVisitor<T> visitor) {

        return visitor.visitAvg(this);
    }

    @Override
    public Object evaluate(final Context context, final Stream<? extends Map<String, Object>> values) {

        return values.map(v -> input.evaluate(context.with(v)))
                .map(Entry::from).reduce(Entry::sum).map(Entry::avg)
                .orElse(null);
    }

    @Data
    private static class Entry {

        private final double value;

        private final int count;

        public static Entry from(final Object value) {

            return new Entry(((Number)value).doubleValue(), 1);
        }

        public static Entry sum(final Entry a, final Entry b) {

            return new Entry(a.value + b.value, a.count + b.count);
        }

        public double avg() {

            return value / count;
        }
    }
}
