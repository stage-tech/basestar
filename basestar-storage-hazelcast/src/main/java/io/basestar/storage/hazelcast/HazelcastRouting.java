package io.basestar.storage.hazelcast;

import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.MapConfig;
import io.basestar.schema.Namespace;
import io.basestar.schema.ObjectSchema;
import io.basestar.util.Nullsafe;
import lombok.Builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface HazelcastRouting {

    String objectMapName(ObjectSchema schema);

    String historyMapName(ObjectSchema schema);

    default Map<String,MapConfig> mapConfigs(final Namespace namespace) {

        final Map<String, MapConfig> results = new HashMap<>();
        namespace.getSchemas().forEach((name, schema) -> {
            if(schema instanceof ObjectSchema) {
                final ObjectSchema objectSchema = (ObjectSchema)schema;
                final String historyName = historyMapName(objectSchema);
                final String objectName = objectMapName(objectSchema);
                results.put(historyName, new MapConfig()
                        .setName(historyName));
                results.put(objectName, new MapConfig()
                        .setName(objectName)
                        .setIndexConfigs(indexes(objectSchema)));

            }
        });
        return results;
    }

    static List<IndexConfig> indexes(final ObjectSchema schema) {

        return schema.getAllIndexes().values().stream()
                .flatMap(index -> {
                    if(!index.isMultiValue()) {
                        final List<String> keys = new ArrayList<>();
                        index.getPartition().forEach(p -> keys.add(p.toString()));
                        index.getSort().forEach(s -> keys.add(s.getPath().toString()));

                        return Stream.of(new IndexConfig(IndexType.SORTED, keys.toArray(new String[0])));
                    } else {
                        return Stream.empty();
                    }
                }).collect(Collectors.toList());
    }

    @Builder
    class Simple implements HazelcastRouting {

        private final String objectPrefix;

        private final String objectSuffix;

        private final String historyPrefix;

        private final String historySuffix;

        @Override
        public String objectMapName(final ObjectSchema schema) {

            return Nullsafe.of(objectPrefix) + schema.getName() + Nullsafe.of(objectSuffix);
        }

        @Override
        public String historyMapName(final ObjectSchema schema) {

            return Nullsafe.of(historyPrefix) + schema.getName() + Nullsafe.of(historySuffix);
        }

    }
}
