package io.basestar.api;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.basestar.auth.Caller;
import io.basestar.util.Nullsafe;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class EnvelopedAPI implements API {

    private final API api;

    public EnvelopedAPI(final API api) {

        this.api = api;
    }

    @Override
    public CompletableFuture<APIResponse> handle(final APIRequest request) throws IOException {

        final RequestBody body = request.readBody(RequestBody.class);
        final byte[] bytes = request.getContentType().getMapper().writeValueAsBytes(body.getBody());
        return api.handle(new APIRequest() {
            @Override
            public Caller getCaller() {

                return request.getCaller();
            }

            @Override
            public Method getMethod() {

                return Nullsafe.orDefault(body.getMethod(), request.getMethod());
            }

            @Override
            public String getPath() {

                return Nullsafe.orDefault(body.getPath(), request.getPath());
            }

            @Override
            public ListMultimap<String, String> getQuery() {

                final ListMultimap<String, String> result = ArrayListMultimap.create();
                request.getQuery().forEach(result::put);
                if (body.getQuery() != null) {
                    body.getQuery().forEach(result::put);
                }
                return result;
            }

            @Override
            public ListMultimap<String, String> getHeaders() {

                final ListMultimap<String, String> result = ArrayListMultimap.create();
                result.put("content-type", request.getContentType().getContentType());
                result.put("content-length", Integer.toString(bytes.length));
                request.getHeaders().asMap().forEach((k, vs) -> {
                    final String name = k.toLowerCase();
                    if (!result.containsKey(name)) {
                        result.putAll(name, vs);
                    }
                });
                // For security and consistency purposes, do not allow overriding any existing request header params
                if (body.getHeaders() != null) {
                    body.getHeaders().forEach((k, v) -> {
                        final String name = k.toLowerCase();
                        if (!result.containsKey(name)) {
                            result.put(name, v);
                        } else {
                            log.warn("Skipping enveloped header {} because it appears at request level", name);
                        }
                    });
                }
                log.debug("Enveloped API request transformed to {}", result);
                return result;
            }

            @Override
            public InputStream readBody() {

                return new ByteArrayInputStream(bytes);
            }

            @Override
            public String getRequestId() {

                return request.getRequestId();
            }
        });
    }

    @Override
    public CompletableFuture<OpenAPI> openApi() {

        return CompletableFuture.completedFuture(new OpenAPI());
    }

    @Data
    public static class RequestBody {

        private APIRequest.Method method;

        private Map<String, String> query;

        private Map<String, String> headers;

        private String path;

        private Object body;
    }
}
