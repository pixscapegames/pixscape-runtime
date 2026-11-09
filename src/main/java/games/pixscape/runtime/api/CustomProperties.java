package games.pixscape.runtime.api;

import games.pixscape.runtime.property.PropertyType;

/**
 * {@code HIGH_LEVEL} read-only custom properties of a Runtime entity or tile definition.
 *
 * <p>V1 supports {@link PropertyType#STRING}, {@link PropertyType#BOOLEAN},
 * {@link PropertyType#INTEGER}, {@link PropertyType#FLOAT}, {@link PropertyType#COLOR}, and
 * {@link PropertyType#OBJECT}, and recursively nested {@link PropertyType#CLASS} values. COLOR
 * values are packed RGBA8888 integers ({@code 0xRRGGBBAA}). Unsupported imported tile values
 * use {@link PropertyType#IMPORTED}: their source type and payload remain available through
 * {@link #getImportedValue(String)}, without conversion to STRING or entity stable IDs.</p>
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
     * Returns a packed RGBA8888 COLOR value ({@code 0xRRGGBBAA}), or {@code fallback} when
     * absent.
     */
    int getColorRgba8888(String name, int fallback);

    /**
     * Returns an OBJECT property's persistent Pixscape entity stable ID, or {@code fallback}
     * when absent. {@code -1} represents no referenced entity. Resolve a positive stable ID
     * through {@link EntitiesAPI#ofStableId(int)} when an entity reference is required.
     */
    int getObjectStableId(String name, int fallbackStableId);

    /**
     * Returns the read-only nested class value, or {@code null} when the property is absent.
     * An existing property of another type throws an {@link IllegalStateException}.
     */
    ClassProperty getClassValue(String name);

    /** Returns preserved imported data, null when absent; throws for a different property type. */
    ImportedProperty getImportedValue(String name);
}
