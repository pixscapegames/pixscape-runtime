package games.pixscape.runtime.api;

import games.pixscape.runtime.property.PropertyType;

/**
 * Read-only custom properties of a Runtime entity.
 *
 * <p>V1 supports {@link PropertyType#STRING}, {@link PropertyType#BOOLEAN},
 * {@link PropertyType#INTEGER}, {@link PropertyType#FLOAT}, and recursively nested
 * {@link PropertyType#CLASS} values.</p>
 *
 * <p>Property names are case-sensitive. Missing properties use the supplied getter fallback;
 * existing properties of the wrong type throw an {@link IllegalStateException}. A stale entity
 * reference or an entity without a custom-properties component behaves like an empty set.</p>
 */
public interface CustomProperties {
    int size();

    boolean isEmpty();

    boolean contains(String name);

    /**
     * Returns the declared type, or {@code null} when the property is absent.
     */
    PropertyType typeOf(String name);

    String getString(String name, String fallback);

    boolean getBoolean(String name, boolean fallback);

    int getInt(String name, int fallback);

    float getFloat(String name, float fallback);

    /**
     * Returns the read-only nested class value, or {@code null} when the property is absent.
     * An existing property of another type throws an {@link IllegalStateException}.
     */
    ClassProperty getClassValue(String name);
}
