package games.pixscape.runtime.property;

import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.api.CustomProperties;
import games.pixscape.runtime.api.ImportedProperty;

/**
 * {@code SUPPORTED_EXPERT} concrete typed value stored in a
 * {@link PropertySet}.
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
    private String className = "";
    private PropertySet classProperties = new PropertySet();
    private transient ClassProperty classView;
    private String importedType = "";
    private String importedCustomType = "";
    private transient ImportedProperty importedView;

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

    /**
     * Creates a COLOR property stored as packed RGBA8888 ({@code 0xRRGGBBAA}).
     * Every {@code int} bit pattern is a valid color value.
     */
    public static PropertyValue ofColorRgba8888(int value) {
        PropertyValue property = new PropertyValue(PropertyType.COLOR);
        property.integerValue = value;
        return property;
    }

    /**
     * Creates an OBJECT property containing a persistent Pixscape entity stable ID.
     * {@code -1} represents no referenced entity.
     */
    public static PropertyValue ofObjectStableId(int stableId) {
        requireObjectStableId(stableId);
        PropertyValue property = new PropertyValue(PropertyType.OBJECT);
        property.integerValue = stableId;
        return property;
    }

    public static PropertyValue ofClass(String className, PropertySet members) {
        requireClassName(className);
        if (members == null) {
            throw new IllegalArgumentException("Class property members must not be null.");
        }
        members.validate();
        PropertyValue property = new PropertyValue(PropertyType.CLASS);
        property.className = className;
        property.classProperties = members.copy();
        return property;
    }

    public PropertyType type() {
        return type;
    }

    /** Preserves a non-editable Tiled value without coercing it to a STRING property. */
    public static PropertyValue ofImported(String type, String customType, String sourceXml) {
        PropertyValue value = new PropertyValue(PropertyType.IMPORTED);
        value.importedType = type;
        value.importedCustomType = customType == null ? "" : customType;
        value.stringValue = sourceXml;
        value.validateState();
        return value;
    }

    public ImportedProperty asImported() {
        requireType(PropertyType.IMPORTED);
        if (importedView == null) importedView = new ImportedProperty() {
            @Override public String typeName() { return importedType; }
            @Override public String customTypeName() { return importedCustomType; }
            @Override public String sourceXml() { return stringValue; }
        };
        return importedView;
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

    /**
     * Returns this COLOR value as packed RGBA8888 ({@code 0xRRGGBBAA}).
     */
    public int asColorRgba8888() {
        requireType(PropertyType.COLOR);
        return integerValue;
    }

    /**
     * Returns this OBJECT value as a persistent Pixscape entity stable ID.
     */
    public int asObjectStableId() {
        requireType(PropertyType.OBJECT);
        return integerValue;
    }

    public ClassProperty asClass() {
        requireType(PropertyType.CLASS);
        if (classView == null) classView = new ClassPropertyView(this);
        return classView;
    }

    /**
     * Returns the CLASS type name. The returned string is immutable.
     */
    public String className() {
        requireType(PropertyType.CLASS);
        return className;
    }

    /**
     * Returns an independent copy of the CLASS members for authoring workflows.
     */
    public PropertySet classPropertiesCopy() {
        requireType(PropertyType.CLASS);
        return classProperties.copy();
    }

    public PropertyValue copy() {
        validateState();
        PropertyValue copy = new PropertyValue(type);
        copy.stringValue = stringValue;
        copy.booleanValue = booleanValue;
        copy.integerValue = integerValue;
        copy.floatValue = floatValue;
        copy.className = className;
        copy.importedType = importedType;
        copy.importedCustomType = importedCustomType;
        copy.classProperties = classProperties != null ? classProperties.copy() : null;
        return copy;
    }

    String stringValue() {
        return stringValue;
    }

    boolean booleanValue() {
        return booleanValue;
    }

    int integerValue() {
        return integerValue;
    }

    float floatValue() {
        return floatValue;
    }

    void validateState() {
        validateState(0);
    }

    void validateState(int depth) {
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
        if (type == PropertyType.OBJECT) {
            requireObjectStableIdState(integerValue);
        }
        if (type == PropertyType.CLASS) {
            requireClassNameState(className);
            if (classProperties == null) {
                throw new IllegalStateException("Class property members must not be null.");
            }
            classProperties.validate(depth + 1);
        }
        if (type == PropertyType.IMPORTED && (importedType == null || importedType.trim().isEmpty()
                || importedCustomType == null || stringValue.isEmpty())) {
            throw new IllegalStateException("Imported property requires its declared type and source XML.");
        }
    }

    void compactNested() {
        if (type == PropertyType.CLASS && classProperties != null) classProperties.compact();
    }

    private void requireType(PropertyType expected) {
        if (type != expected) {
            throw new IllegalStateException(
                    "Property value has type " + type + ", not " + expected + ".");
        }
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static void requireClassName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Class property type name must not be null.");
        }
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Class property type name must not be blank.");
        }
    }

    private static void requireObjectStableId(int stableId) {
        if (stableId == -1 || stableId > 0) return;
        throw new IllegalArgumentException(
                "OBJECT property stableId must be -1 or positive, got " + stableId + ".");
    }

    private static void requireObjectStableIdState(int stableId) {
        if (stableId == -1 || stableId > 0) return;
        throw new IllegalStateException(
                "OBJECT property stableId must be -1 or positive, got " + stableId + ".");
    }

    private static void requireClassNameState(String value) {
        if (value == null) {
            throw new IllegalStateException("Class property type name must not be null.");
        }
        if (value.trim().isEmpty()) {
            throw new IllegalStateException("Class property type name must not be blank.");
        }
    }

    private static final class ClassPropertyView implements ClassProperty {
        private final PropertyValue value;
        private final CustomProperties properties;

        private ClassPropertyView(PropertyValue value) {
            this.value = value;
            this.properties = new PropertySetReadView(value.classProperties);
        }

        @Override
        public String typeName() {
            return value.className;
        }

        @Override
        public CustomProperties properties() {
            return properties;
        }
    }

    static final class PropertySetReadView implements CustomProperties {
        private final PropertySet properties;

        PropertySetReadView(PropertySet properties) {
            this.properties = properties;
        }

        @Override public int size() { return properties.size(); }
        @Override public boolean isEmpty() { return properties.isEmpty(); }
        @Override public boolean contains(String name) { return properties.contains(name); }
        @Override public PropertyType typeOf(String name) { return properties.typeOf(name); }
        @Override public String getString(String name, String fallback) {
            return properties.getString(name, fallback);
        }
        @Override public boolean getBoolean(String name, boolean fallback) {
            return properties.getBoolean(name, fallback);
        }
        @Override public int getInt(String name, int fallback) {
            return properties.getInt(name, fallback);
        }
        @Override public float getFloat(String name, float fallback) {
            return properties.getFloat(name, fallback);
        }
        @Override public int getColorRgba8888(String name, int fallback) {
            return properties.getColorRgba8888(name, fallback);
        }
        @Override public int getObjectStableId(String name, int fallback) {
            return properties.getObjectStableId(name, fallback);
        }
        @Override public ClassProperty getClassValue(String name) {
            return properties.getClassValue(name);
        }
        @Override public ImportedProperty getImportedValue(String name) {
            return properties.getImportedValue(name);
        }
    }
}
