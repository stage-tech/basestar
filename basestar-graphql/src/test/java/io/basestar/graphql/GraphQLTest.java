package io.basestar.graphql;

/*-
 * #%L
 * basestar-graphql
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
import com.google.common.collect.ImmutableSet;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.basestar.auth.Caller;
import io.basestar.database.DatabaseServer;
import io.basestar.database.options.CreateOptions;
import io.basestar.graphql.schema.SchemaConverter;
import io.basestar.graphql.subscription.SubscriberContext;
import io.basestar.schema.Namespace;
import io.basestar.storage.MemoryStorage;
import io.basestar.util.Name;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class GraphQLTest {

    private Namespace namespace() throws Exception {

        return Namespace.load(GraphQLTest.class.getResource("schema.yml"));
    }

    private GraphQL graphQL(final Namespace namespace) throws Exception {

        final MemoryStorage storage = MemoryStorage.builder().build();
        final DatabaseServer databaseServer = new DatabaseServer(namespace, storage);

        databaseServer.create(Caller.SUPER, CreateOptions.builder()
                .schema(Name.of("Test4"))
                .id("test4")
                .data(ImmutableMap.of(
                        "test", ImmutableMap.of(
                                "id","test1"
                        )
                ))
                .build()).get();

        databaseServer.create(Caller.SUPER, CreateOptions.builder()
                .schema(Name.of("Test1"))
                .id("test1")
                .data(ImmutableMap.of(
                        "z", ImmutableMap.of(
                                "test", ImmutableMap.of(
                                        "id", "test4"
                                )
                        )
                ))
                .build()).get();

        return GraphQLAdaptor.builder().database(databaseServer).namespace(namespace).build().graphQL();
    }

    @Test
    public void testConvert() throws Exception {

        final SchemaParser parser = new SchemaParser();
        final TypeDefinitionRegistry tdr = parser.parse(GraphQLTest.class.getResourceAsStream("schema.gql"));

        final SchemaConverter converter = new SchemaConverter();
        final Namespace.Builder ns = converter.namespace(tdr);
        assertNotNull(ns);
        ns.yaml(System.out);
    }

    @Test
    public void testGet() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final Map<String, Object> get = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("query {\n" +
                        "  readTest1(id:\"test1\") {\n" +
                        "    id\n" +
                        "    z {\n" +
                        "      key\n" +
                        "      value {\n" +
                        "        test {\n" +
                        "          id\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}")
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .build()).getData();
        assertEquals(Collections.singletonMap(
                "readTest1", ImmutableMap.of(
                        "id", "test1",
                        "z", ImmutableList.of(ImmutableMap.of(
                                "key", "test",
                                "value", ImmutableMap.of("test", ImmutableMap.of(
                                        "id", "test1"
                                )))
                        )
                )
        ), get);
    }

    @Test
    public void testGetMissing() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final Map<String, Object> get = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("query {\n" +
                        "  readTest1(id:\"x\") {\n" +
                        "    id\n" +
                        "  }\n" +
                        "}")
                .build()).getData();
        assertEquals(Collections.singletonMap(
                "readTest1", null
        ), get);
    }

    @Test
    public void testCreate() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final Map<String, Map<String, Object>> create = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("mutation {\n" +
                        "  createTest1(id:\"x\", data:{x:\"x\"}) {\n" +
                        "    id\n" +
                        "    version\n" +
                        "    created\n" +
                        "    updated\n" +
                        "  }\n" +
                        "}")
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .build()).getData();
        assertEquals(4, create.get("createTest1").size());

        final Map<String, Object> get = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("query {\n" +
                        "  readTest1(id:\"x\") {\n" +
                        "    id\n" +
                        "  }\n" +
                        "}")
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .build()).getData();
        assertEquals(ImmutableMap.of("readTest1", ImmutableMap.of("id", "x")), get);
    }

    @Test
    public void testMultiMutate() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final Map<String, Object> result = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("mutation {\n" +
                        "  a: createTest1(id:\"x\", data:{x:\"x\"}) {\n" +
                        "    id\n" +
                        "  }\n" +
                        "  b: createTest1(id:\"y\", data:{x:\"y\"}) {\n" +
                        "    id\n" +
                        "  }\n" +
                        "}")
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .build()).getData();
        assertEquals(ImmutableMap.of(
                "a", ImmutableMap.of("id", "x"),
                "b", ImmutableMap.of("id", "y")
        ), result);
    }

    @Test
    public void testNullErrors() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final Map<String, Object> result = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("mutation {\n" +
                        "  a: updateTest1(data:{x:\"x\"}) {\n" +
                        "    id\n" +
                        "  }\n" +
                        "}")
                .build()).getData();
    }

    @Test
    public void testSubscribe() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final SubscriberContext subscriberContext = mock(SubscriberContext.class);
        when(subscriberContext.subscribe(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        graphQL.execute(ExecutionInput.newExecutionInput()
                .context(GraphQLContext.newContext().of("subscriber", subscriberContext).build())
                .query("subscription {\n" +
                        "  a: subscribeTest1(id:\"x\") {\n" +
                        "    id\n" +
                        "  }\n" +
                        "}")
                .build()).getData();

        verify(subscriberContext).subscribe(namespace.requireObjectSchema("Test1"), "x", "a", ImmutableSet.of(Name.of("id")));
    }

    @Test
    public void testBatch() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .query("mutation {\n" +
                        "  batch {\n" +
                        "    a:createTest1(id:\"x\", data:{x:\"x\"}) {\n" +
                        "      id\n" +
                        "    }\n" +
                        "  }\n" +
                        "}")
                .build());

        System.err.println((Object)result.getData());
        System.err.println(result.getErrors());
    }

    @Test
    public void testExpressionInVariables() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .query("query Assets($filter: String) { queryTest1(query: $filter) { items { id } } }")
                .variables(ImmutableMap.of(
                        "filter", "asset.fileType != 'UNKNOWN' && location.locationType == 'INBOX' && ('2018' IN asset.tags || 'student' IN asset.tags)"
                ))
                .build());

        System.err.println((Object)result.getData());
        System.err.println(result.getErrors());
    }

    @Test
    public void testCreateOrganization() throws Exception {

        final Namespace namespace = namespace();
        final GraphQL graphQL = graphQL(namespace);

        final ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
                .context(GraphQLContext.newContext().of("caller", Caller.SUPER).build())
                .query("mutation { createOrganization(id: \"nmp\", data: { name: \"NMP\", organizationType: SERVICE_PROVIDER }) { id name organizationType } }")
                .build());

        System.err.println((Object)result.getData());
        System.err.println(result.getErrors());
    }
}
