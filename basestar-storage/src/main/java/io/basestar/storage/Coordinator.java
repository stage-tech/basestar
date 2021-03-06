package io.basestar.storage;

/*-
 * #%L
 * basestar-storage
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

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public interface Coordinator {

    default CloseableLock lock(final String ... names) {

        return lock(ImmutableSet.copyOf(names));
    }

    CloseableLock lock(final Set<String> names);

    class NoOp implements Coordinator {

        @Override
        public CloseableLock lock(final Set<String> name) {

            return () -> {};
        }
    }

    class Local implements Coordinator {

        private final ConcurrentMap<String, Lock> locks = new ConcurrentHashMap<>();

        @Override
        public CloseableLock lock(final Set<String> names) {

            final List<Lock> locked = names.stream().sorted().map(name -> locks.computeIfAbsent(name, ignored -> new ReentrantLock()))
                    .collect(Collectors.toList());
            locked.forEach(Lock::lock);
            return () -> locked.forEach(Lock::unlock);
        }
    }
}
