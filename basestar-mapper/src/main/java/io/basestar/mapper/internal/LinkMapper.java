package io.basestar.mapper.internal;

import io.basestar.expression.Expression;
import io.basestar.mapper.MappingContext;
import io.basestar.mapper.SchemaMapper;
import io.basestar.schema.Link;
import io.basestar.schema.ObjectSchema;
import io.basestar.type.PropertyContext;
import io.basestar.util.Sort;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

public class LinkMapper implements MemberMapper<ObjectSchema.Builder> {

    private final String name;

    private final PropertyContext property;

    private final Expression expression;

    private final List<Sort> sort;

    private final TypeMapper type;

    private final TypeMapper.OfCustom itemType;

    public LinkMapper(final MappingContext context, final String name, final PropertyContext property, final Expression expression, final List<Sort> sort) {

        this.name = name;
        this.property = property;
        this.expression = expression;
        this.sort = sort;
        this.type = TypeMapper.from(context, property.type());
        if(type instanceof TypeMapper.OfArray) {
            final TypeMapper.OfArray array = (TypeMapper.OfArray)type;
            if(array.getValue() instanceof TypeMapper.OfCustom) {
                itemType = (TypeMapper.OfCustom)array.getValue();
            } else {
                throw new IllegalStateException("Cannot create link item mapper for " + array.getValue());
            }
        } else {
            throw new IllegalStateException("Cannot create link mapper for " + type);
        }
    }

    @Override
    public TypeMapper getType() {

        return type;
    }

    @Override
    public void addToSchema(final ObjectSchema.Builder builder) {

        final SchemaMapper<?, ?> schema = itemType.getMapper();
        builder.setLink(name, Link.builder()
                .setSchema(schema.name())
                .setExpression(expression)
                .setSort(sort));
    }

    @Override
    public void unmarshall(final Object source, final Map<String, Object> target) throws InvocationTargetException, IllegalAccessException {

        if(property.canGet()) {
            final Object value = property.get(source);
            target.put(name, type.unmarshall(value));
        }
    }

    @Override
    public void marshall(final Map<String, Object> source, final Object target) throws InvocationTargetException, IllegalAccessException {

        if(property.canSet()) {
            final Object value = source.get(name);
            property.set(target, type.marshall(value));
        }
    }
}