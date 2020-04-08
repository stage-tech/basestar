package io.basestar.schema.use;

/*-
 * #%L
 * basestar-schema
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

import io.basestar.schema.exception.InvalidTypeException;
import lombok.Data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Boolean Type
 *
 * <strong>Example</strong>
 * <pre>
 * type: boolean
 * </pre>
 */

@Data
public class UseBoolean implements UseScalar<Boolean> {

    public static UseBoolean DEFAULT = new UseBoolean();

    public static final String NAME = "boolean";

    @Override
    public <R> R visit(final Visitor<R> visitor) {

        return visitor.visitBoolean(this);
    }

    public static UseBoolean from(final Object config) {

        return new UseBoolean();
    }

    @Override
    public Object toJson() {

        return NAME;
    }

    @Override
    public Boolean create(final Object value) {

        if(value == null) {
            return null;
        } else if(value instanceof Boolean) {
            return (Boolean)value;
        } else if(value instanceof Number) {
            return ((Number)value).intValue() != 0;
        } else if(value instanceof String) {
            return !(((String)value).isEmpty() || value.equals("false"));
        } else {
            throw new InvalidTypeException();
        }
    }

    @Override
    public Code code() {

        return Code.BOOLEAN;
    }

    @Override
    public void serializeValue(final Boolean value, final DataOutput out) throws IOException {

        out.writeBoolean(value);
    }

    @Override
    public Boolean deserializeValue(final DataInput in) throws IOException {

        return in.readBoolean();
    }

    @Override
    public String toString() {

        return NAME;
    }

//    @Override
//    public Map<String, Object> openApiType() {
//
//        return ImmutableMap.of(
//                "type", "boolean"
//        );
//    }
}
