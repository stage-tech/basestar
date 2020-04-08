package io.basestar.mapper.annotation;

/*-
 * #%L
 * basestar-mapper
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2020 basestar.io
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

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(Index.Multi.class)
public @interface Index {

    String name();

    String[] partition() default {};

    String[] sort() default {};

    boolean unique() default false;

    Over[] over() default {};

    String[] projection() default {};

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @interface Multi {

        Index[] value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @interface Over {

        String as();

        String path();
    }
}
