package games.pixscape.runtime.property;

/**
 * Concrete typed value stored in a {@link PropertySet}.
 *
 * <p>The representation is deliberately non-polymorphic so it can be serialized consistently
 * on Desktop, Android, and GWT. Accessors never coerce between property types.</p>
 */
public final class PropertyValue {
    private PropertyType type = PropertyType.STRING;
    private String stringValue = "";
    private boolean booleanValue;
    private int integerValue;
    private float floatValue;

    /**
     * Creates the valid default value {@code STRING("")} for serializers.
     */
    public PropertyValue() {
    }

    private PropertyValue(PropertyType type) {
        this.type = type;
    }

    public static PropertyValue ofString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("String property value must not be null.");
        }
        PropertyValue property = new PropertyValue(PropertyType.STRING);
        property.stringValue = value;
        return property;
    }

    public static PropertyValue ofBoolean(boolean value) {
        PropertyValue property = new PropertyValue(PropertyType.BOOLEAN);
        property.booleanValue = value;
        return property;
    }

    public static PropertyValue ofInt(int value) {
        PropertyValue property = new PropertyValue(PropertyType.INTEGER);
        property.integerValue = value;
        return property;
    }

    public static PropertyValue ofFloat(float value) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(
                    "Float property value must be finite, got " + value + ".");
        }
        PropertyValue property = new PropertyValue(PropertyType.FLOAT);
        property.floatValue = value;
        return property;
    }

    public PropertyType type() {
        return type;
    }

    public String asString() {
        requireType(PropertyType.STRING);
        return stringValue;
    }

    public boolean asBoolean() {
        requireType(PropertyType.BOOLEAN);
        return booleanValue;
    }

    public int asInt() {
        requireType(PropertyType.INTEGER);
        return integerValue;
    }

    public float asFloat() {
        requireType(PropertyType.FLOAT);
        return floatValue;
    }

    public PropertyValue copy() {
        validateState();
        PropertyValue copy = new PropertyValue(type);
        copy.stringValue = stringValue;
        copy.booleanValue = booleanValue;
        copy.integerValue = integerValue;
        copy.floatValue = floatValue;
        return copy;
    }

    void validateState() {
        if (type == null) {
            throw new IllegalStateException("Property value type must not be null.");
        }
        if (stringValue == null) {
            throw new IllegalStateException("String property storage must not be null.");
        }
        if (type == PropertyType.FLOAT && !isFinite(floatValue)) {
            throw new IllegalStateException(
                    "Float property value must be finite, got " + floatValue + ".");
        }
    }

    private void requireType(PropertyType expected) {
        validateState();
        if (type != expected) {
            throw new IllegalStateException(
                    "Property value has type " + type + ", not " + expected + ".");
        }
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
