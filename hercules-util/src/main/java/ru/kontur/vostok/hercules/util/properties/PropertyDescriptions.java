package ru.kontur.vostok.hercules.util.properties;

import ru.kontur.vostok.hercules.util.parsing.Parsers;

/**
 * PropertyDescriptions
 *
 * @author Kirill Sulim
 */
public final class PropertyDescriptions {

    public static PropertyDescriptionBuilder<Integer> integerProperty(String name) {
        return PropertyDescriptionBuilder.start(name, Integer.class, Parsers::parseInteger);
    }

    public static PropertyDescriptionBuilder<Long> longProperty(String name) {
        return PropertyDescriptionBuilder.start(name, Long.class, Parsers::parseLong);
    }

    public static PropertyDescriptionBuilder<String> stringProperty(String name) {
        return PropertyDescriptionBuilder.start(name, String.class, Parsers::parseString);
    }

    public static PropertyDescriptionBuilder<Boolean> booleanProperty(String name) {
        return PropertyDescriptionBuilder.start(name, Boolean.class, Parsers::parseBoolean);
    }

    public static <T> PropertyDescriptionBuilder<T> propertyOfType(Class<T> clazz, String name) {
        return PropertyDescriptionBuilder.start(name, clazz, null);
    }
}
