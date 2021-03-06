package io.basestar.api;


import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.basestar.auth.Authenticator;
import io.basestar.auth.BasicAuthenticator;
import io.basestar.auth.Caller;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAuthenticatingAPI {

    final Authenticator authenticator = new BasicAuthenticator() {
        @Override
        protected CompletableFuture<Caller> verify(final String username, final String password) {

            return CompletableFuture.completedFuture(Caller.builder().setId(username).build());
        }

        @Override
        public Map<String, SecurityScheme> openApi() {

            return null;
        }
    };
    final AuthenticatingAPI api = new AuthenticatingAPI(authenticator, new API() {
        @Override
        public CompletableFuture<APIResponse> handle(final APIRequest request) throws IOException {

            return CompletableFuture.completedFuture(APIResponse.success(request, request.getCaller()));
        }

        @Override
        public CompletableFuture<OpenAPI> openApi() {

            return null;
        }
    });

    @Test
    void testAuth() throws IOException, ExecutionException, InterruptedException {

        final APIResponse response = api.handle(new APIRequest.Delegating(null) {

            @Override
            public ListMultimap<String, String> getHeaders() {

                final ListMultimap<String, String> map = ArrayListMultimap.create();
                map.put("authorization", "Basic " + Base64.getEncoder().encodeToString("matt:password".getBytes(Charsets.UTF_8)));
                return map;
            }

            @Override
            public ListMultimap<String, String> getQuery() {

                return ArrayListMultimap.create();
            }

            @Override
            public String getRequestId() {

                return "null";
            }

        }).get();

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void testAlternateAuth() throws IOException, ExecutionException, InterruptedException {

        final APIResponse response = api.handle(new APIRequest.Delegating(null) {

            @Override
            public ListMultimap<String, String> getHeaders() {

                final ListMultimap<String, String> map = ArrayListMultimap.create();
                map.put("X-Basic-Authorization", Base64.getEncoder().encodeToString("matt:password".getBytes(Charsets.UTF_8)));
                return map;
            }

            @Override
            public ListMultimap<String, String> getQuery() {

                return ArrayListMultimap.create();
            }

            @Override
            public String getRequestId() {

                return "null";
            }

        }).get();

        assertEquals(200, response.getStatusCode());
    }
}
