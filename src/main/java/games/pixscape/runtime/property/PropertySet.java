package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Case-sensitive collection of named, typed Pixscape custom properties.
 *
 * <p>V1 supports string, boolean, signed 32-bit integer, and finite float values.</p>
 *
 * <p>Names are preserved exactly as supplied. Typed getters return their fallback only when a
 * property is absent and throw when an existing property has a different type.</p>
 */
public final class PropertySet {
    private ObjectMap<String, PropertyValue> values;

    public PropertySet() {
        this(0);
    }

    /**
     * Creates an empty property set sized for the expected authored property count.
     */
    public PropertySet(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException(
                    "Expected property count must not be negative: " + expectedSize + ".");
        }
        values = new ObjectMap<String, PropertyValue>(expectedSize);
    }

    public PropertySet(PropertySet source) {
        copyFrom(source);
    }

    public int size() {
        return values.size;
    }

    public boolean isEmpty() {
        return values.size == 0;
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /**
     * Returns the declared type, or {@code null} when the property is absent.
     */
    public PropertyType typeOf(String name) {
        PropertyValue value = value(name);
        return value != null ? value.type() : null;
    }

    public PropertySet put(String name, PropertyValue value) {
        requireName(name);
        if (value == null) {
            throw new IllegalArgumentException("Property value must not be null.");
        }
        value.validateState();
        requireBackingMap();
        values.put(name, value.copy());
        return this;
    }

    public PropertySet putString(String name, String value) {
        return put(name, PropertyValue.ofString(value));
    }

    public PropertySet putBoolean(String name, boolean value) {
        return put(name, PropertyValue.ofBoolean(value));
    }

    public PropertySet putInt(String name, int value) {
        return put(name, PropertyValue.ofInt(value));
    }

    public PropertySet putFloat(String name, float value) {
        return put(name, PropertyValue.ofFloat(value));
    }

    public String getString(String name, String fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.STRING);
        return value.stringValue();
    }

    public boolean getBoolean(String name, boolean fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.BOOLEAN);
        return value.booleanValue();
    }

    public int getInt(String name, int fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.INTEGER);
        return value.integerValue();
    }

    public float getFloat(String name, float fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.FLOAT);
        return value.floatValue();
    }

    public void clear() {
        if (values == null) values = new ObjectMap<String, PropertyValue>(0);
        else values.clear();
    }

    public PropertySet copy() {
        return new PropertySet(this);
    }

    public PropertySet copyFrom(PropertySet source) {
        if (source == null) {
            throw new IllegalArgumentException("Source PropertySet must not be null.");
        }
        if (source == this) return this;

        source.validate();
        ObjectMap<String, PropertyValue> copied =
                new ObjectMap<String, PropertyValue>(source.values.size);
        for (ObjectMap.Entry<String, PropertyValue> entry : source.values) {
            copied.put(entry.key, entry.value.copy());
        }
        values = copied;
        return this;
    }

    /**
     * Validates the complete authored representation after construction or deserialization.
     */
    public void validate() {
        requireBackingMap();
        for (ObjectMap.Entry<String, PropertyValue> entry : values) {
            requireName(entry.key);
            if (entry.value == null) {
                throw new IllegalStateException(
                        "Property '" + entry.key + "' must not have a null value.");
            }
            entry.value.validateState();
        }
    }

    /**
     * Shrinks deserialized backing storage to the current property count.
     */
    public void compact() {
        values.shrink(values.size);
    }

    private PropertyValue value(String name) {
        return values.get(name);
    }

    private void requireBackingMap() {
        if (values == null) {
            throw new IllegalStateException("PropertySet backing map must not be null.");
        }
    }

    private static void requireName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Property name must not be null.");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("Property name must not be empty.");
        }
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isWhitespace(name.charAt(i))) return;
        }
        throw new IllegalArgumentException("Property name must not contain only whitespace.");
    }

    private static void requireType(
            String name, PropertyValue value, PropertyType expected) {
        PropertyType actual = value.type();
        if (actual != expected) {
            throw new IllegalStateException(
                    "Property '" + name + "' has type " + actual
                            + ", not " + expected + ".");
        }
    }
}
