package io.basestar.storage;

/*-
 * #%L
 * basestar-storage
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

import io.basestar.schema.Concurrency;
import io.basestar.schema.Consistency;

public interface StorageTraits {

    Consistency getHistoryConsistency();

    Consistency getSingleValueIndexConsistency();

    Consistency getMultiValueIndexConsistency();

    boolean supportsPolymorphism();

    boolean supportsMultiObject();

    Concurrency getObjectConcurrency();

    default Consistency getIndexConsistency(boolean multi) {

        return multi ? getMultiValueIndexConsistency() : getSingleValueIndexConsistency();
    }


//    Consistency getObjectReadConsistency();
//
//    Consistency getObjectWriteConsistency();
//
//    Consistency getHistoryReadConsistency();
//
//    Consistency getHistoryWriteConsistency();
//
//    Consistency getPolymorphicReadConsistency();
//
//    Consistency getPolymorphicWriteConsistency();
//
//    Consistency getSingleIndexReadConsistency();
//
//    Consistency getSingleIndexWriteConsistency();
//
//    Consistency getMultiValueIndexReadConsistency();
//
//    Consistency getMultiValueIndexWriteConsistency();
//
//    Consistency getRefIndexReadConsistency();
//
//    Consistency getRefIndexWriteConsistency();
//
//    Consistency getMultiObjectReadConsistency();
//
//    Consistency getMultiObjectWriteConsistency();
}
