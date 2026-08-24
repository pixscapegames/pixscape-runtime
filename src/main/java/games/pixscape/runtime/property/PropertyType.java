package games.pixscape.runtime.property;

/**
 * {@code HIGH_LEVEL} type contract for a Pixscape custom property value.
 *
 * <p>{@link #STRING}, {@link #BOOLEAN}, {@link #INTEGER} and {@link #FLOAT}
 * retain their corresponding authored scalar values. {@link #COLOR} is a
 * packed RGBA8888 integer. {@link #OBJECT} stores a persistent Pixscape stable
 * ID, where {@code -1} means no referenced entity. {@link #CLASS} is a typed,
 * nested {@link games.pixscape.runtime.api.CustomProperties} value.</p>
 */
public enum PropertyType {
    STRING,
    BOOLEAN,
    INTEGER,
    FLOAT,
    COLOR,
    OBJECT,
    CLASS
}
