package io.basestar.storage.elasticsearch.mapping;

/*-
 * #%L
 * basestar-storage-elasticsearch
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

import lombok.Builder;
import lombok.Data;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

@Data
@Builder
public class Settings {

    private final int shards;

    private final int replicas;

    public XContentBuilder build(XContentBuilder builder) throws IOException {

        builder = builder.field("number_of_shards", shards);
        builder = builder.field("number_of_replicas", replicas);
        return builder;
    }
}
