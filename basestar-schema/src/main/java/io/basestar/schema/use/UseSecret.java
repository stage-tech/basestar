package io.basestar.schema.use;

import io.basestar.schema.util.ValueContext;
import io.basestar.secret.Secret;
import io.basestar.util.Name;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

@Data
@Slf4j
public class UseSecret implements UseScalar<Secret> {

    public static final UseSecret DEFAULT = new UseSecret();

    public static final String NAME = "secret";

    public static UseSecret from(final Object config) {

        return DEFAULT;
    }

    @Override
    public Object toConfig(final boolean optional) {

        return Use.name(NAME, optional);
    }

    @Override
    public <R> R visit(final Visitor<R> visitor) {

        return visitor.visitSecret(this);
    }

    @Override
    public Secret create(final ValueContext context, final Object value, final Set<Name> expand) {

        return context.createSecret(this, value, expand);
    }

    @Override
    public Code code() {

        return Code.SECRET;
    }

    @Override
    public Type javaType(final Name name) {

        return Secret.class;
    }

    @Override
    public Secret defaultValue() {

        return Secret.encrypted(new byte[0]);
    }

    @Override
    public void serializeValue(final Secret value, final DataOutput out) throws IOException {

        final byte[] encrypted = value.encrypted();
        out.writeInt(encrypted.length);
        out.write(encrypted);
    }

    @Override
    public Secret deserializeValue(final DataInput in) throws IOException {

        final int length = in.readInt();
        final byte[] encrypted = new byte[length];
        in.readFully(encrypted);
        return Secret.encrypted(encrypted);
    }

    @Override
    public Schema<?> openApi(final Set<Name> expand) {

        return new StringSchema();
    }

    @Override
    public boolean areEqual(final Secret a, final Secret b) {

        return Objects.equals(a, b);
    }

    @Override
    public String toString() {

        return NAME;
    }

    @Override
    public String toString(final Secret value) {

        return Objects.toString(value);
    }
}
