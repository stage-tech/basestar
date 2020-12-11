package io.basestar.jackson.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.basestar.secret.Secret;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public class SecretDeserializer extends JsonDeserializer<Secret> {

    private final boolean visibleSecrets;

    @Override
    public Secret deserialize(final JsonParser parser, final DeserializationContext deserializationContext) throws IOException {

        final String str = parser.getValueAsString();
        if(visibleSecrets) {
            return Secret.plaintext(str);
        } else {
            return Secret.encrypted(str);
        }
    }
}
