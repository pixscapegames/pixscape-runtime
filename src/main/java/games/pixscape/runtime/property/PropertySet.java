package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.api.ClassProperty;

/**
 * {@code SUPPORTED_EXPERT} case-sensitive collection of named, typed Pixscape
 * custom properties.
 *
 * <p>Supports string, boolean, signed 32-bit integer, finite float, packed
 * RGBA8888 color, persistent Pixscape stable-ID object references, and
 * recursively nested class values.</p>
 *
 * <p>Names are preserved exactly as supplied. Typed getters return their fallback only when a
 * property is absent and throw when an existing property has a different type.</p>
 */
public final class PropertySet {
    private static final int MAX_NESTED_CLASS_DEPTH = 64;
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
     * Copies the current property names into {@code out}. The returned names do not expose the
     * backing map and preserve their authored spelling.
     */
    public void copyNamesTo(Array<String> out) {
        if (out == null) {
            throw new IllegalArgumentException("Output names array must not be null.");
        }
        requireBackingMap();
        out.clear();
        out.ensureCapacity(values.size);
        for (ObjectMap.Entry<String, PropertyValue> entry : values) {
            out.add(entry.key);
        }
    }

    /**
     * Returns an independent copy of a property value, or {@code null} when it is absent.
     */
    public PropertyValue valueCopy(String name) {
        PropertyValue value = value(name);
        return value != null ? value.copy() : null;
    }

    /**
     * Removes a property when present. Missing names are a deterministic no-op.
     */
    public boolean remove(String name) {
        if (name == null || values == null) return false;
        return values.remove(name) != null;
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

    /**
     * Stores a packed RGBA8888 color ({@code 0xRRGGBBAA}).
     */
    public PropertySet putColorRgba8888(String name, int value) {
        return put(name, PropertyValue.ofColorRgba8888(value));
    }

    /**
     * Stores a persistent Pixscape entity stable ID. {@code -1} represents no referenced entity.
     */
    public PropertySet putObjectStableId(String name, int stableId) {
        return put(name, PropertyValue.ofObjectStableId(stableId));
    }

    public PropertySet putClass(String name, String className, PropertySet members) {
        requireName(name);
        PropertyValue value = PropertyValue.ofClass(className, members);
        requireBackingMap();
        values.put(name, value);
        return this;
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

    /**
     * Returns a packed RGBA8888 color ({@code 0xRRGGBBAA}), or {@code fallback} when absent.
     */
    public int getColorRgba8888(String name, int fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.COLOR);
        return value.integerValue();
    }

    /**
     * Returns an OBJECT property's persistent Pixscape entity stable ID, or {@code fallback}
     * when absent. {@code -1} represents no referenced entity.
     */
    public int getObjectStableId(String name, int fallback) {
        PropertyValue value = value(name);
        if (value == null) return fallback;
        requireType(name, value, PropertyType.OBJECT);
        return value.integerValue();
    }

    public ClassProperty getClassValue(String name) {
        PropertyValue value = value(name);
        if (value == null) return null;
        requireType(name, value, PropertyType.CLASS);
        return value.asClass();
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
        validate(0);
    }

    void validate(int depth) {
        if (depth > MAX_NESTED_CLASS_DEPTH) {
            throw new IllegalStateException(
                    "Custom property nesting exceeds the maximum depth of "
                            + MAX_NESTED_CLASS_DEPTH + ".");
        }
        requireBackingMap();
        for (ObjectMap.Entry<String, PropertyValue> entry : values) {
            requireName(entry.key);
            if (entry.value == null) {
                throw new IllegalStateException(
                        "Property '" + entry.key + "' must not have a null value.");
            }
            entry.value.validateState(depth);
        }
    }

    /**
     * Shrinks deserialized backing storage to the current property count.
     */
    public void compact() {
        values.shrink(values.size);
        for (ObjectMap.Entry<String, PropertyValue> entry : values) {
            if (entry.value != null) entry.value.compactNested();
        }
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
