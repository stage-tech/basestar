package io.basestar.schema.exception;

/*-
 * #%L
 * basestar-schema
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

import com.google.common.collect.ImmutableSet;
import io.basestar.exception.ExceptionMetadata;
import io.basestar.exception.HasExceptionMetadata;
import io.basestar.schema.Constraint;

import java.util.Set;

public class ConstraintViolationException extends RuntimeException implements HasExceptionMetadata {

    public static final int STATUS = 400;

    public static final String CODE = "ConstraintViolation";

    public static final String VIOLATIONS = "violations";

    private final Set<Constraint.Violation> violations;

    public ConstraintViolationException(final Set<Constraint.Violation> violations) {

        super("Property constraints violated " + violations.toString());
        this.violations = ImmutableSet.copyOf(violations);
    }

    @Override
    public ExceptionMetadata getMetadata() {

        return new ExceptionMetadata()
                .setStatus(STATUS)
                .setCode(CODE)
                .setMessage("Property constraints violated")
                .putData(VIOLATIONS, violations);
    }
}
