package io.basestar.database.util;

/*-
 * #%L
 * basestar-database-server
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

import io.basestar.schema.Instance;
import io.basestar.util.Name;
import lombok.Data;

import java.util.Map;

@Data
public class LinkKey {

    private final Name schema;

    private final String id;

    private final String link;

    public static LinkKey from(final Map<String, Object> item, final String link) {

        return new LinkKey(Instance.getSchema(item), Instance.getId(item), link);
    }

    public static LinkKey from(final RefKey ref, final String link) {

        return new LinkKey(ref.getSchema(), ref.getId(), link);
    }
}
