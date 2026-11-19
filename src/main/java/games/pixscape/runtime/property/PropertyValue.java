package games.pixscape.runtime.property;

import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.api.CustomProperties;
import games.pixscape.runtime.api.ImportedProperty;
import games.pixscape.runtime.api.ListProperty;
import games.pixscape.runtime.api.EnumProperty;
import com.badlogic.gdx.utils.Array;

/**
 * {@code SUPPORTED_EXPERT} concrete typed value stored in a
 * {@link PropertySet}.
 *
 * <p>The representation is deliberately non-polymorphic so it can be serialized consistently
 * on Desktop, Android, and GWT. Accessors never coerce between property types.</p>
 */
public final class PropertyValue {
    PropertyType type = PropertyType.STRING;
    String stringValue = "";
    boolean booleanValue;
    int integerValue;
    double floatValue;
    String className = "";
    PropertySet classProperties = new PropertySet();
    private transient ClassProperty classView;
    String importedType = "";
    String importedCustomType = "";
    private transient ImportedProperty importedView;
    Array<PropertyValue> listValues;
    EnumDefinition enumDefinition;
    private transient ListProperty listView;
    private transient EnumProperty enumView;
    transient int enumSelection;


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

    public static PropertyValue ofFloat(double value) {
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

    /**
     * Preserves a non-editable Tiled value without coercing it to a STRING property.
     */
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
            @Override
            public String typeName() {
                return importedType;
            }

            @Override
            public String customTypeName() {
                return importedCustomType;
            }

            @Override
            public String sourceXml() {
                return stringValue;
            }
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
        return (float) floatValue;
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

    /** Cold structural equality, preserving order, duplicates, declared types and exact values. */
    public boolean contentEquals(PropertyValue other) {
        return PropertyTree.same(this, other);
    }

    public PropertyValue copy() {
        return (PropertyValue) PropertyTree.copy(this, null);
    }

    /** Constructs an independent ordered list; null items and cycles are rejected. */
    public static PropertyValue ofList(Array<PropertyValue> items) {
        if (items == null) {
            throw new IllegalArgumentException("List items must not be null.");
        }
        PropertyValue value = new PropertyValue(PropertyType.LIST);
        value.listValues = items;
        return value.copy();
    }

    public static PropertyValue ofEnum(EnumDefinition definition, String value) {
        if (definition == null || definition.storageType() != PropertyType.STRING) {
            throw new IllegalArgumentException("STRING enum definition required.");
        }
        PropertyValue result = new PropertyValue(PropertyType.ENUM);
        result.enumDefinition = definition.copy();
        result.stringValue = value;
        result.validateState();
        return result;
    }

    public static PropertyValue ofEnum(EnumDefinition definition, int value) {
        if (definition == null || definition.storageType() != PropertyType.INTEGER) {
            throw new IllegalArgumentException("INTEGER enum definition required.");
        }
        PropertyValue result = new PropertyValue(PropertyType.ENUM);
        result.enumDefinition = definition.copy();
        result.integerValue = value;
        result.validateState();
        return result;
    }

    public EnumDefinition enumDefinitionCopy() {
        requireType(PropertyType.ENUM);
        return enumDefinition.copy();
    }

    public Array<PropertyValue> listValuesCopy() {
        requireType(PropertyType.LIST);
        return copy().listValues;
    }

    public double asDouble() {
        requireType(PropertyType.FLOAT);
        return floatValue;
    }

    public ListProperty asList() {
        requireType(PropertyType.LIST);
        if (listView == null) {
            listView = new ListView();
        }
        return listView;
    }

    public EnumProperty asEnum() {
        requireType(PropertyType.ENUM);
        if (enumView == null) {
            enumView = new EnumView();
        }
        return enumView;
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

    double floatValue() {
        return floatValue;
    }

    void validateState() {
        PropertyTree.validate(this);
    }

    void validateShallow() {
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

        }
        if (type == PropertyType.LIST && listValues == null) {
            throw new IllegalStateException("List items must not be null.");
        }
        if (type == PropertyType.ENUM) {
            if (enumDefinition == null) {
                throw new IllegalStateException("Enum definition must not be null.");
            }
            enumDefinition.validate();
            enumSelection = enumDefinition.selection(stringValue, integerValue);
        }
        if (type == PropertyType.IMPORTED && (importedType == null || importedType.trim().isEmpty()
                || importedCustomType == null || stringValue.isEmpty())) {
            throw new IllegalStateException("Imported property requires its declared type and source XML.");
        }
    }

    private void requireType(PropertyType expected) {
        if (type != expected) {
            throw new IllegalStateException(
                    "Property value has type " + type + ", not " + expected + ".");
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
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

    private final class EnumView implements EnumProperty {
        public String typeName() {
            return enumDefinition.name();
        }
        public PropertyType storageType() {
            return enumDefinition.storageType();
        }
        public boolean isFlags() {
            return enumDefinition.isFlags();
        }
        public int choiceCount() {
            return enumDefinition.size();
        }
        public String choiceAt(int index) {
            return enumDefinition.choiceAt(index);
        }
        public boolean isSelected(int index) {
            enumDefinition.choiceAt(index);
            return isFlags() ? (enumSelection & (1 << index)) != 0 : enumSelection == index;
        }
        public String stringValue() {
            if (storageType() != PropertyType.STRING) {
                throw new IllegalStateException("Not a STRING enum.");
            }
            return stringValue;
        }
        public int intValue() {
            if (storageType() != PropertyType.INTEGER) {
                throw new IllegalStateException("Not an INTEGER enum.");
            }
            return integerValue;
        }
    }

    private final class ListView implements ListProperty {
        public int size() {
            return listValues.size;
        }
        private PropertyValue at(int index) {
            if (index < 0 || index >= listValues.size) {
                throw new IndexOutOfBoundsException("List index: " + index);
            }
            return listValues.get(index);
        }
        public PropertyType typeAt(int index) {
            return at(index).type();
        }
        public String getString(int index) {
            return at(index).asString();
        }
        public boolean getBoolean(int index) {
            return at(index).asBoolean();
        }
        public int getInt(int index) {
            return at(index).asInt();
        }
        public float getFloat(int index) {
            return at(index).asFloat();
        }
        public double getDouble(int index) {
            return at(index).asDouble();
        }
        public int getColorRgba8888(int index) {
            return at(index).asColorRgba8888();
        }
        public int getObjectStableId(int index) {
            return at(index).asObjectStableId();
        }
        public ClassProperty getClassValue(int index) {
            return at(index).asClass();
        }
        public ListProperty getListValue(int index) {
            return at(index).asList();
        }
        public EnumProperty getEnumValue(int index) {
            return at(index).asEnum();
        }
        public ImportedProperty getImportedValue(int index) {
            return at(index).asImported();
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

        @Override
        public int size() {
            return properties.size();
        }

        @Override
        public boolean isEmpty() {
            return properties.isEmpty();
        }

        @Override
        public boolean contains(String name) {
            return properties.contains(name);
        }

        @Override
        public PropertyType typeOf(String name) {
            return properties.typeOf(name);
        }

        @Override
        public String getString(String name, String fallback) {
            return properties.getString(name, fallback);
        }

        @Override
        public boolean getBoolean(String name, boolean fallback) {
            return properties.getBoolean(name, fallback);
        }

        @Override
        public int getInt(String name, int fallback) {
            return properties.getInt(name, fallback);
        }

        @Override
        public double getDouble(String name, double fallback) {
            return properties.getDouble(name, fallback);
        }
        @Override
        public ListProperty getListValue(String name) {
            return properties.getListValue(name);
        }
        @Override
        public EnumProperty getEnumValue(String name) {
            return properties.getEnumValue(name);
        }

        @Override
        public float getFloat(String name, float fallback) {
            return properties.getFloat(name, fallback);
        }

        @Override
        public int getColorRgba8888(String name, int fallback) {
            return properties.getColorRgba8888(name, fallback);
        }

        @Override
        public int getObjectStableId(String name, int fallback) {
            return properties.getObjectStableId(name, fallback);
        }

        @Override
        public ClassProperty getClassValue(String name) {
            return properties.getClassValue(name);
        }

        @Override
        public ImportedProperty getImportedValue(String name) {
            return properties.getImportedValue(name);
        }
    }
}
