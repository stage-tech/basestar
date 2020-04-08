package io.basestar.database.transport;

/*-
 * #%L
 * basestar-database-client
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

import com.google.common.collect.Multimap;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public interface Transport {

    <T> CompletableFuture<T> get(String url, Multimap<String, String> query,
                                 Multimap<String, String> headers, BodyReader<T> reader);

    <T> CompletableFuture<T> post(String url, Multimap<String, String> query,
                                  Multimap<String, String> headers, BodyWriter writer,
                                  BodyReader<T> reader);

    <T> CompletableFuture<T> put(String url, Multimap<String, String> query,
                                 Multimap<String, String> headers, BodyWriter writer,
                                 BodyReader<T> reader);

    <T> CompletableFuture<T> delete(String url, Multimap<String, String> query,
                                    Multimap<String, String> headers, BodyReader<T> reader);

    interface BodyReader<T> {

        T readFrom(InputStream is);
    }

    interface BodyWriter {

        void writeTo(OutputStream os);
    }
}
