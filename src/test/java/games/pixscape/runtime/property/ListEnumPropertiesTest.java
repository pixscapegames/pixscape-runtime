package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.api.CustomProperties;
import games.pixscape.runtime.api.ListProperty;
import org.junit.Test;
import static org.junit.Assert.*;

public class ListEnumPropertiesTest {
    @Test
    public void mixedListsPreserveOrderDuplicatesEmptyValuesAndDoublePrecision() {
        Array<PropertyValue> items = new Array<>();
        items.add(PropertyValue.ofString("7"));
        items.add(PropertyValue.ofInt(7));
        items.add(PropertyValue.ofInt(7));
        items.add(PropertyValue.ofFloat(16777217d));
        items.add(PropertyValue.ofFloat(0.1234567890123456));
        items.add(PropertyValue.ofList(new Array<>()));
        items.add(PropertyValue.ofClass("Config", new PropertySet().putList("nested", new Array<>())));
        PropertySet properties = new PropertySet().putList("items", items);
        items.clear();
        Json json = new Json();
        PropertySet loaded = json.fromJson(PropertySet.class, json.toJson(properties));
        loaded.validate();
        CustomProperties api = loaded.readOnlyView();
        assertNull(api.getListValue("absent"));
        ListProperty list = api.getListValue("items");
        assertSame(list, api.getListValue("items"));
        assertEquals(7, list.size());
        assertEquals("7", list.getString(0));
        assertEquals(PropertyType.INTEGER, list.typeAt(1));
        assertEquals(7, list.getInt(1));
        assertEquals(7, list.getInt(2));
        assertEquals(16777217d, list.getDouble(3), 0);
        assertEquals(0.1234567890123456, list.getDouble(4), 0);
        assertEquals(0, list.getListValue(5).size());
        assertEquals(0, list.getClassValue(6).properties().getListValue("nested").size());
        assertThrows(IllegalStateException.class, () -> list.getString(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.typeAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.typeAt(7));
        assertThrows(IllegalStateException.class, () -> api.getDouble("items", 0));
    }

    @Test
    public void copiesAndReferenceRemappingVisitNestedListsAndClassesIndependently() {
        PropertySet source = new PropertySet().putList("targets", Array.with(
                PropertyValue.ofObjectStableId(12),
                PropertyValue.ofClass("Link", new PropertySet().putList("more", Array.with(
                        PropertyValue.ofObjectStableId(12), PropertyValue.ofObjectStableId(-1))))));
        PropertySet copy = source.remapObjectReferences(id -> id == -1 ? -1 : id + 100);
        assertEquals(12, source.getListValue("targets").getObjectStableId(0));
        assertEquals(112, copy.getListValue("targets").getObjectStableId(0));
        ListProperty nested = copy.getListValue("targets").getClassValue(1).properties().getListValue("more");
        assertEquals(112, nested.getObjectStableId(0));
        assertEquals(-1, nested.getObjectStableId(1));
        Array<PropertyValue> editable = copy.valueCopy("targets").listValuesCopy();
        editable.removeIndex(0);
        copy.putList("targets", editable);
        assertEquals(2, source.getListValue("targets").size());
        assertEquals(1, copy.getListValue("targets").size());
    }

    @Test
    public void enumDefinitionsAndExactStorageSurviveCopyAndSerialization() {
        for (PropertyType storage : new PropertyType[]{PropertyType.STRING, PropertyType.INTEGER}) {
            for (boolean flags : new boolean[]{false, true}) {
                EnumDefinition definition = new EnumDefinition("Direction", storage, flags, "North", "South", "West");
                PropertyValue value = storage == PropertyType.STRING
                        ? PropertyValue.ofEnum(definition, flags ? "West,North" : "West")
                        : PropertyValue.ofEnum(definition, flags ? 5 : 2);
                PropertySet properties = new PropertySet().putList("options", Array.with(value));
                Json json = new Json();
                PropertySet loaded = json.fromJson(PropertySet.class, json.toJson(properties.copy()));
                loaded.validate();
                var option = loaded.readOnlyView().getListValue("options").getEnumValue(0);
                assertEquals("Direction", option.typeName());
                assertEquals(storage, option.storageType());
                assertEquals(flags, option.isFlags());
                assertTrue(option.isSelected(2));
                assertEquals(flags, option.isSelected(0));
                assertFalse(option.isSelected(1));
                if (storage == PropertyType.STRING) {
                    assertEquals(flags ? "West,North" : "West", option.stringValue());
                } else {
                    assertEquals(flags ? 5 : 2, option.intValue());
                }
            }
        }
        var definition = new EnumDefinition("Flags", PropertyType.INTEGER, true, "A", "B");
        assertThrows(IllegalArgumentException.class, () -> PropertyValue.ofEnum(definition, 4));
        assertThrows(IllegalArgumentException.class, () -> PropertyValue.ofEnum(
                new EnumDefinition("Mode", PropertyType.STRING, false, "A"), "unknown"));
    }

    @Test
    public void invalidValuesAndCyclesFailWithoutPublishingPartialCopies() {
        PropertySet target = new PropertySet().putInt("keep", 7);
        PropertyValue invalid = new PropertyValue();
        invalid.type = PropertyType.LIST;
        invalid.listValues = Array.with(invalid);
        assertThrows(IllegalStateException.class, () -> target.put("cycle", invalid));
        assertEquals(1, target.size());
        assertEquals(7, target.getInt("keep", 0));
        assertThrows(IllegalStateException.class, () -> PropertyValue.ofList(Array.with((PropertyValue) null)));
        assertThrows(IllegalArgumentException.class, () -> PropertyValue.ofFloat(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PropertyValue.ofFloat(Double.POSITIVE_INFINITY));
    }

    @Test
    public void largeAndDeepListsHaveNoInventedProductLimit() {
        Array<PropertyValue> items = new Array<>();
        for (int i = 0; i < 20000; i++) {
            items.add(PropertyValue.ofInt(i));
        }
        PropertyValue value = PropertyValue.ofList(items);
        assertEquals(19999, value.asList().getInt(19999));
        // Build a serializer-style tree to exercise validation/copy without quadratic authoring setup.
        for (int i = 0; i < 4000; i++) {
            PropertyValue parent = new PropertyValue();
            parent.type = PropertyType.LIST;
            parent.listValues = Array.with(value);
            value = parent;
        }
        PropertyValue copied = value.copy();
        for (int i = 0; i < 4000; i++) {
            copied = copied.listValues.get(0);
        }
        assertEquals(20000, copied.asList().size());
    }
}
