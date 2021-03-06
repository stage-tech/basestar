package io.basestar.stream.event;

/*-
 * #%L
 * basestar-stream
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import io.basestar.event.Event;
import io.basestar.schema.ObjectSchema;
import io.basestar.stream.Change;
import io.basestar.util.Name;
import io.basestar.util.Page;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class SubscriptionQueryEvent implements Event {

    private Name schema;

    private Change.Event event;

    private Map<String, Object> before;

    private Map<String, Object> after;

    private Page.Token paging;

    public static SubscriptionQueryEvent of(final Name schema, final Change.Event event, final Map<String, Object> before, final Map<String, Object> after) {

        return of(schema, event, before, after, null);
    }

    public static SubscriptionQueryEvent of(final Name schema, final Change.Event event, final Map<String, Object> before, final Map<String, Object> after, final Page.Token paging) {

        return new SubscriptionQueryEvent().setSchema(schema).setEvent(event).setBefore(before).setAfter(after).setPaging(paging);
    }

    public SubscriptionQueryEvent withPaging(final Page.Token paging) {

        return of(schema, event, before, after, paging);
    }

    @Override
    public Event abbreviate() {

        return this;
    }

    @Override
    @JsonIgnore
    public Map<String, String> eventMetadata() {

        return ImmutableMap.of(
                ObjectSchema.SCHEMA, getSchema().toString()
        );
    }
}
