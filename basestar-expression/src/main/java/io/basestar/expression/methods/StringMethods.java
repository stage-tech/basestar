package io.basestar.expression.methods;

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

import com.google.common.base.CharMatcher;
import io.basestar.util.ISO8601;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@SuppressWarnings("unused")
public class StringMethods implements Serializable {

    public boolean isEmpty(final String target) {

        return target.isEmpty();
    }

    public boolean isBlank(final String target) {

        return target.trim().isEmpty();
    }

    public int size(final String target) {

        return target.length();
    }

    public String substr(final String target, final Number begin) {

        return target.substring(begin.intValue());
    }

    public String substr(final String target, final Number begin, final Number end) {

        return target.substring(begin.intValue(), end.intValue());
    }

    public String trim(final String target) {

        return target.trim();
    }

    public static String ltrim(final String target) {
        int i = 0;
        while (i < target.length() && Character.isWhitespace(target.charAt(i))) {
            i++;
        }
        return target.substring(i);
    }

    public static String rtrim(final String target) {
        int i = target.length()-1;
        while (i > 0 && Character.isWhitespace(target.charAt(i))) {
            i--;
        }
        return target.substring(0,i+1);
    }

    public LocalDate toDate(final String value, final String format) {

        return ISO8601.parseDate(value, format);
    }

    public Instant toDatetime(final String value, final String format) {

        return ISO8601.parseDateTime(value, format);
    }
}
