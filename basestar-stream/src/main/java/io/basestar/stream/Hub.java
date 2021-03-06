package io.basestar.stream;

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

import io.basestar.auth.Caller;
import io.basestar.event.Event;
import io.basestar.event.Handler;
import io.basestar.expression.Expression;
import io.basestar.util.Name;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface Hub extends Handler<Event> {

    default CompletableFuture<?> subscribe(final Caller caller, final String sub, final String channel, final Name schema, final Expression expression, final SubscriptionMetadata info) {

        return subscribe(caller, sub, channel, schema, EnumSet.allOf(Change.Event.class), expression, info);
    }

    CompletableFuture<?> subscribe(Caller caller, String sub, String channel, Name schema, Set<Change.Event> events, Expression expression, SubscriptionMetadata info);

    CompletableFuture<?> unsubscribe(Caller caller, String sub, String channel);

    CompletableFuture<?> unsubscribeAll(Caller caller, String sub);
}
