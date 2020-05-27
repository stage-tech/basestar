package io.basestar.auth.cognito;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import io.basestar.auth.BasicAuthenticator;
import io.basestar.auth.Caller;
import io.basestar.auth.exception.AuthenticationFailedException;
import io.basestar.util.Throwables;
import io.swagger.v3.oas.models.security.SecurityScheme;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CognitoBasicAuthenticator extends BasicAuthenticator {

    private final JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);

    private final CognitoIdentityProviderAsyncClient client;

    private final String userPoolId;

    private final String clientId;

    @lombok.Builder(builderClassName = "Builder")
    CognitoBasicAuthenticator(final CognitoIdentityProviderAsyncClient client, final String userPoolId, final String clientId) {

        this.client = client;
        this.userPoolId = userPoolId;
        this.clientId = clientId;
    }

    @Override
    protected CompletableFuture<Caller> verify(final String username, final String password) {

        final AdminInitiateAuthRequest request = AdminInitiateAuthRequest.builder()
                .userPoolId(userPoolId)
                .clientId(clientId)
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .authParameters(ImmutableMap.of(
                        "USERNAME", username,
                        "PASSWORD", password
                ))
                .build();

        return client.adminInitiateAuth(request).exceptionally(e -> {

            final String reason = Throwables.find(e, CognitoIdentityProviderException.class)
                    .map(AwsServiceException::awsErrorDetails).map(AwsErrorDetails::errorMessage)
                    .orElse("unknown");

            throw new AuthenticationFailedException("Authentication failed (reason: " + reason + ")");

        }).thenApply(result -> {

            if(result.authenticationResult() == null) {
                if(result.challengeName() != null) {
                    throw new AuthenticationFailedException("Authentication failed (reason: challenge was " + result.challengeName() + ")");
                } else {
                    throw new AuthenticationFailedException("Authentication failed (reason: unknown)");
                }
            }

            final Map<String, Object> claims = parseClaims(result.authenticationResult().idToken());

            return new Caller() {
                @Override
                public boolean isAnon() {

                    return false;
                }

                @Override
                public boolean isSuper() {

                    return false;
                }

                @Override
                public String getSchema() {

                    return "User";
                }

                @Override
                public String getId() {

                    return (String)claims.get("sub");
                }

                @Override
                public Map<String, Object> getClaims() {

                    return claims;
                }
            };

        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseClaims(final String token) {

        final String[] parts = token.split("\\.");
        if(parts.length != 3) {
            throw new AuthenticationFailedException("Authentication failed (reason: bad response format)");
        }

        try {
            final String decoded = new String(BaseEncoding.base64().decode(parts[1]), Charsets.UTF_8);
            return (Map<String, Object>)parser.parse(decoded);
        } catch (final ParseException e) {
            throw new AuthenticationFailedException("Authentication failed (reason: bad claims section)");
        }
    }

    @Override
    public Map<String, SecurityScheme> openApi() {

        return ImmutableMap.of("Basic", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .in(SecurityScheme.In.HEADER)
                .scheme("basic"));
    }
}
