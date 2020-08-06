package io.basestar.storage.cognito;

/*-
 * #%L
 * basestar-storage-cognito
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.basestar.expression.Expression;
import io.basestar.schema.Consistency;
import io.basestar.schema.ObjectSchema;
import io.basestar.storage.BatchResponse;
import io.basestar.storage.Storage;
import io.basestar.storage.StorageTraits;
import io.basestar.storage.Versioning;
import io.basestar.util.Name;
import io.basestar.util.Page;
import io.basestar.util.Pager;
import io.basestar.util.Sort;
import lombok.Setter;
import lombok.experimental.Accessors;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CognitoGroupStorage implements Storage.WithoutWriteIndex, Storage.WithoutHistory, Storage.WithoutAggregate, Storage.WithoutExpand {

    private static final String DESCRIPTION_KEY = "description";

    private final CognitoIdentityProviderAsyncClient client;

    private final CognitoGroupStrategy strategy;

    private CognitoGroupStorage(final Builder builder) {

        this.client = builder.client;
        this.strategy = builder.strategy;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Setter
    @Accessors(chain = true)
    public static class Builder {

        private CognitoIdentityProviderAsyncClient client;

        private CognitoGroupStrategy strategy;

        public CognitoGroupStorage build() {

            return new CognitoGroupStorage(this);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> readObject(final ObjectSchema schema, final String id, final Set<Name> expand) {

        final String userPoolId = strategy.getUserPoolId(schema);
        return client.getGroup(GetGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(id)
                .build())
                .thenApply(response -> fromGroup(response.group()));
    }

    @Override
    public List<Pager.Source<Map<String, Object>>> query(final ObjectSchema schema, final Expression query, final List<Sort> sort, final Set<Name> expand) {

        return ImmutableList.of(
                (count, token, stats) -> {
                    final String userPoolId = strategy.getUserPoolId(schema);
                    return client.listGroups(ListGroupsRequest.builder()
                            .userPoolId(userPoolId)
                            .limit(count)
                            .nextToken(decodePaging(token))
                            .build()).thenApply(response -> {
                        final List<GroupType> groups = response.groups();
                        return new Page<>(groups.stream().map(this::fromGroup)
                                .collect(Collectors.toList()), encodePaging(response.nextToken()));

                    });
                });
    }

    private String decodePaging(final Page.Token token) {

        return null;
    }

    private Page.Token encodePaging(final String s) {

        return null;
    }

    @Override
    public ReadTransaction read(final Consistency consistency) {

        return new ReadTransaction.Basic(this);
    }

    @Override
    public WriteTransaction write(final Consistency consistency, final Versioning versioning) {

        return new WriteTransaction() {

            private final List<Supplier<CompletableFuture<BatchResponse>>> requests = new ArrayList<>();

            @Override
            public WriteTransaction createObject(final ObjectSchema schema, final String id, final Map<String, Object> after) {

                requests.add(() -> {
                    final String userPoolId = strategy.getUserPoolId(schema);
                    final String description = null;
                    return client.createGroup(CreateGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .groupName(id)
                            .description(description)
                            .build())
                            .thenApply(ignored -> BatchResponse.single(schema.getQualifiedName(), after));
                });
                return this;
            }

            @Override
            public WriteTransaction updateObject(final ObjectSchema schema, final String id, final Map<String, Object> before, final Map<String, Object> after) {

                requests.add(() -> {
                    final String userPoolId = strategy.getUserPoolId(schema);
                    final String description = null;
                    return client.updateGroup(UpdateGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .groupName(id)
                            .description(description)
                            .build())
                            .thenApply(ignored -> BatchResponse.single(schema.getQualifiedName(), after));
                });
                return this;
            }

            @Override
            public WriteTransaction deleteObject(final ObjectSchema schema, final String id, final Map<String, Object> before) {

                requests.add(() -> {
                    final String userPoolId = strategy.getUserPoolId(schema);
                    return client.deleteGroup(DeleteGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .groupName(id)
                            .build())
                            .thenApply(ignored -> BatchResponse.empty());
                });
                return this;
            }

            @Override
            public CompletableFuture<BatchResponse> write() {

                return BatchResponse.mergeFutures(requests.stream().map(Supplier::get));
            }
        };
    }

    @Override
    public EventStrategy eventStrategy(final ObjectSchema schema) {

        return EventStrategy.EMIT;
    }

    @Override
    public StorageTraits storageTraits(final ObjectSchema schema) {

        return CognitoStorageTraits.INSTANCE;
    }

    public String getDescription(final Map<String, Object> data) {

        return (String)data.get(DESCRIPTION_KEY);
    }

    private Map<String, Object> fromGroup(final GroupType group) {

        return ImmutableMap.of(
                ObjectSchema.ID, group.groupName(),
                DESCRIPTION_KEY, group.description()
        );
    }
}
