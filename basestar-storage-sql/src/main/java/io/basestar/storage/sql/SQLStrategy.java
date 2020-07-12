package io.basestar.storage.sql;

/*-
 * #%L
 * basestar-storage-sql
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

import io.basestar.schema.Index;
import io.basestar.schema.ObjectSchema;
import io.basestar.schema.Reserved;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.Collection;

public interface SQLStrategy {

    Name objectTableName(ObjectSchema schema);

    Name historyTableName(ObjectSchema schema);

    // Only used for multi-value indexes
    Name indexTableName(ObjectSchema schema, Index index);

    void createTables(DSLContext context, Collection<ObjectSchema> schemas);

    @Data
    @Slf4j
    class Simple implements SQLStrategy {

        private final String objectSchemaName;

        private final String historySchemaName;

        private String name(final ObjectSchema schema) {

            return schema.getQualifiedName().toString(Reserved.PREFIX);
        }

        @Override
        public Name objectTableName(final ObjectSchema schema) {

            return DSL.name(DSL.name(objectSchemaName), DSL.name(name(schema)));
        }

        @Override
        public Name historyTableName(final ObjectSchema schema) {

            return DSL.name(DSL.name(historySchemaName), DSL.name(name(schema)));
        }

        @Override
        public Name indexTableName(final ObjectSchema schema, final Index index) {

            final String name = name(schema) + Reserved.PREFIX + index.getName();
            return DSL.name(DSL.name(objectSchemaName), DSL.name(name));
        }

        @Override
        public void createTables(final DSLContext context, final Collection<ObjectSchema> schemas) {

            try(final CreateSchemaFinalStep create = context.createSchemaIfNotExists(DSL.name(objectSchemaName))) {
                create.execute();
            }
            try(final CreateSchemaFinalStep create = context.createSchemaIfNotExists(DSL.name(historySchemaName))) {
                create.execute();
            }

            for(final ObjectSchema schema : schemas) {

                final Name objectTableName = objectTableName(schema);
                final Name historyTableName = historyTableName(schema);

                log.info("Creating table {}", objectTableName);
                try(final CreateTableFinalStep create = context.createTableIfNotExists(objectTableName)
                        .columns(SQLUtils.fields(schema))
                        .constraint(DSL.primaryKey(Reserved.ID))) {
                    create.execute();
                }

                for(final Index index : schema.getIndexes().values()) {
                    if(index.isMultiValue()) {
                        final Name indexTableName = indexTableName(schema, index);
                        log.info("Creating multi-value index table {}", indexTableName);
                        try(final CreateTableFinalStep create = context.createTableIfNotExists(indexTableName(schema, index))
                                .columns(SQLUtils.fields(schema, index))
                                .constraints(SQLUtils.primaryKey(schema, index))) {
                            create.execute();
                        }
                    } else if(index.isUnique()) {
                        log.info("Creating unique index {}:{}", objectTableName, index.getName());
                        try(final CreateIndexFinalStep create = context.createUniqueIndexIfNotExists(index.getName())
                                .on(DSL.table(objectTableName), SQLUtils.indexKeys(index))) {
                            create.execute();
                        }
                    } else {
                        log.info("Creating index {}:{}", objectTableName, index.getName());
                        try(final CreateIndexFinalStep create = context.createIndexIfNotExists(index.getName())
                                .on(DSL.table(objectTableName), SQLUtils.indexKeys(index))) {
                            create.execute();
                        }
                    }
                }

                log.info("Creating table {}", historyTableName);
                try(final CreateTableFinalStep create = context.createTableIfNotExists(historyTableName)
                        .columns(SQLUtils.fields(schema))
                        .constraint(DSL.primaryKey(Reserved.ID, Reserved.VERSION))) {
                    create.execute();
                }
            }
        }

    }
}