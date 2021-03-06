package io.basestar.storage.leveldb;

/*-
 * #%L
 * basestar-storage-leveldb
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

import io.basestar.schema.Namespace;
import io.basestar.storage.Storage;
import io.basestar.storage.TestStorage;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

class TestLevelDBStorage extends TestStorage {

    private static final File BASEDIR = new File("target/db");

    @BeforeAll
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void beforeAll() {

        BASEDIR.mkdirs();
    }

    @Override
    protected Storage storage(final Namespace namespace) {

        try {
            final Options options = new Options();
            options.createIfMissing(true);
            final DB db = factory.open(new File(BASEDIR, UUID.randomUUID().toString()), options);
            return LevelDBStorage.builder()
                    .db(db)
                    .build();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
