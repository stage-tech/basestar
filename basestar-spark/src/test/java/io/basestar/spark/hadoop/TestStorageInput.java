package io.basestar.spark.hadoop;

import io.basestar.schema.Instance;
import io.basestar.schema.Namespace;
import io.basestar.schema.util.Ref;
import io.basestar.spark.AbstractSparkTest;
import io.basestar.storage.MemoryStorage;
import io.basestar.storage.Storage;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class TestStorageInput extends AbstractSparkTest {

    private static final MemoryStorage storage = MemoryStorage.builder().build();

    private static final Namespace namespace;
    static {
        try {
            namespace = Namespace.load(AbstractSparkTest.class.getResourceAsStream("schema.yml"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class ExampleStorageProvider implements StorageProvider {

        @Override
        public Namespace namespace(final Configuration configuration) {

            return namespace;
        }

        @Override
        public Storage storage(final Configuration configuration) {

            return storage;
        }
    }

    @Test
    void testStorageInput() {

        final SparkSession session = session();

        final Configuration config = new Configuration();

        config.set(StorageProvider.PROVIDER, ExampleStorageProvider.class.getName());

        session.sparkContext().newAPIHadoopRDD(config, StorageInputFormat.class, Ref.class, Instance.class)
                .collect();
    }
}
