package io.basestar.expression.methods;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public abstract class TemporalAccessorMethods<T extends TemporalAccessor> {

    public String format(final T target, final String format) {

        return DateTimeFormatter.ofPattern(format).format(target);
    }
}