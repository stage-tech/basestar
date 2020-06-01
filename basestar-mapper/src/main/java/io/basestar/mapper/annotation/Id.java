package io.basestar.mapper.annotation;

/*-
 * #%L
 * basestar-mapper
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

import io.basestar.mapper.context.PropertyContext;
import io.basestar.mapper.internal.annotation.BindSchema;
import io.basestar.schema.InstanceSchema;
import io.basestar.schema.Reserved;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@BindSchema(Id.Binder.class)
public @interface Id {

    String expression() default "";

    @RequiredArgsConstructor
    class Binder implements BindSchema.Handler {

        private final Id annotation;

        @Override
        public String name(final PropertyContext property) {

            return Reserved.ID;
        }

        @Override
        public void addToSchema(final InstanceSchema.Builder parent, final PropertyContext prop) {

            assert parent instanceof io.basestar.schema.ObjectSchema.Builder;
            // FIXME
        }
    }
}
