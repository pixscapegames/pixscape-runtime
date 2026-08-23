package games.pixscape.runtime.property;

import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.api.ClassProperty;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class PropertySetTest {
    @Test
    public void storesAndRetrievesAllSupportedTypes() {
        PropertySet properties = new PropertySet();

        Assert.assertTrue(properties.isEmpty());
        properties.putString("Name", "first\nsecond")
                .putBoolean("locked", true)
                .putInt("damage", -20)
                .putFloat("spawnRate", 0.5f)
                .putColorRgba8888("tint", 0x80FF0066)
                .putObjectStableId("target", 42);

        Assert.assertEquals(6, properties.size());
        Assert.assertTrue(properties.contains("Name"));
        Assert.assertFalse(properties.contains("name"));
        Assert.assertEquals(PropertyType.STRING, properties.typeOf("Name"));
        Assert.assertEquals(PropertyType.BOOLEAN, properties.typeOf("locked"));
        Assert.assertEquals(PropertyType.INTEGER, properties.typeOf("damage"));
        Assert.assertEquals(PropertyType.FLOAT, properties.typeOf("spawnRate"));
        Assert.assertEquals(PropertyType.COLOR, properties.typeOf("tint"));
        Assert.assertEquals(PropertyType.OBJECT, properties.typeOf("target"));
        Assert.assertEquals("first\nsecond", properties.getString("Name", "fallback"));
        Assert.assertTrue(properties.getBoolean("locked", false));
        Assert.assertEquals(-20, properties.getInt("damage", 0));
        Assert.assertEquals(0.5f, properties.getFloat("spawnRate", 0f), 0f);
        Assert.assertEquals(0x80FF0066, properties.getColorRgba8888("tint", 0));
        Assert.assertEquals(42, properties.getObjectStableId("target", -1));
    }

    @Test
    public void storesEmptyPrimitiveAndRecursivelyNestedClassValues() {
        PropertySet nested = new PropertySet()
                .putBoolean("critical", true)
                .putColorRgba8888("flash", 0xFF000080);
        PropertySet attack = new PropertySet()
                .putFloat("range", 80f)
                .putInt("damage", 10)
                .putClass("modifier", "Critical", nested);
        PropertySet properties = new PropertySet()
                .putClass("empty", "Marker", new PropertySet())
                .putClass("attack", "Attack", attack);

        ClassProperty empty = properties.getClassValue("empty");
        ClassProperty value = properties.getClassValue("attack");

        Assert.assertEquals(PropertyType.CLASS, properties.typeOf("attack"));
        Assert.assertEquals("Marker", empty.typeName());
        Assert.assertTrue(empty.properties().isEmpty());
        Assert.assertEquals("Attack", value.typeName());
        Assert.assertEquals(80f, value.properties().getFloat("range", 0f), 0f);
        Assert.assertEquals(10, value.properties().getInt("damage", 0));
        ClassProperty modifier = value.properties().getClassValue("modifier");
        Assert.assertEquals("Critical", modifier.typeName());
        Assert.assertTrue(modifier.properties().getBoolean("critical", false));
        Assert.assertEquals(0xFF000080,
                modifier.properties().getColorRgba8888("flash", 0));
        Assert.assertSame(value, properties.getClassValue("attack"));
        Assert.assertSame(modifier, value.properties().getClassValue("modifier"));
    }

    @Test
    public void classValuesDeepCopyAcrossEveryOwnershipBoundaryAndReplaceNormally() {
        PropertySet authoredMembers = new PropertySet().putInt("damage", 10);
        PropertySet source = new PropertySet().putClass("attack", "Attack", authoredMembers);
        authoredMembers.putInt("damage", 99).putString("late", "ignored");

        PropertySet copy = source.copy();
        source.putClass("attack", "Replacement", new PropertySet().putInt("damage", 25));

        Assert.assertEquals("Replacement", source.getClassValue("attack").typeName());
        Assert.assertEquals(25,
                source.getClassValue("attack").properties().getInt("damage", 0));
        Assert.assertEquals("Attack", copy.getClassValue("attack").typeName());
        Assert.assertEquals(10, copy.getClassValue("attack").properties().getInt("damage", 0));
        Assert.assertFalse(copy.getClassValue("attack").properties().contains("late"));
    }

    @Test
    public void classConstructionRejectsInvalidTypeNamesAndNullMembers() {
        assertInvalidClass(null, new PropertySet(), "null");
        assertInvalidClass(" \t", new PropertySet(), "blank");
        assertInvalidClass("Attack", null, "members");
    }

    @Test
    public void classGetterUsesNormalMissingAndTypeMismatchSemantics() {
        PropertySet properties = new PropertySet().putInt("damage", 10);

        Assert.assertNull(properties.getClassValue("missing"));
        try {
            properties.getClassValue("damage");
            Assert.fail("Expected a type mismatch");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("damage"));
            Assert.assertTrue(expected.getMessage().contains("CLASS"));
        }
    }

    @Test
    public void recursiveValidationRejectsPathologicalDepth() {
        PropertySet nested = new PropertySet();
        try {
            for (int i = 0; i < 70; i++) {
                nested = new PropertySet().putClass("next", "Node", nested);
            }
            Assert.fail("Expected excessive nesting to fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("maximum depth"));
        }
    }

    @Test
    public void missingPropertiesUseFallbackAndHaveNoType() {
        PropertySet properties = new PropertySet();

        Assert.assertFalse(properties.contains("missing"));
        Assert.assertNull(properties.typeOf("missing"));
        Assert.assertEquals("fallback", properties.getString("missing", "fallback"));
        Assert.assertTrue(properties.getBoolean("missing", true));
        Assert.assertEquals(10, properties.getInt("missing", 10));
        Assert.assertEquals(2.5f, properties.getFloat("missing", 2.5f), 0f);
        Assert.assertEquals(0x12345678, properties.getColorRgba8888("missing", 0x12345678));
        Assert.assertEquals(-1, properties.getObjectStableId("missing", -1));
    }

    @Test
    public void objectPropertiesUseOnlyNoTargetOrPositiveStableIds() {
        PropertySet properties = new PropertySet()
                .putObjectStableId("none", -1)
                .putObjectStableId("target", 42);
        Assert.assertEquals(-1, properties.valueCopy("none").asObjectStableId());
        Assert.assertEquals(42, properties.getObjectStableId("target", -1));
        assertInvalidObjectStableId(0);
        assertInvalidObjectStableId(-2);
        try {
            properties.getInt("target", 0);
            Assert.fail("Expected OBJECT/INTEGER type mismatch");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("OBJECT"));
        }
    }

    @Test
    public void existingPropertyOfWrongTypeThrowsInsteadOfUsingFallback() {
        PropertySet properties = new PropertySet().putString("damage", "20");

        try {
            properties.getInt("damage", 10);
            Assert.fail("Expected a type mismatch");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("damage"));
            Assert.assertTrue(expected.getMessage().contains("STRING"));
            Assert.assertTrue(expected.getMessage().contains("INTEGER"));
        }
    }

    @Test
    public void puttingAnExistingNameDeterministicallyReplacesItsValueAndType() {
        PropertySet properties = new PropertySet().putInt("value", 1);

        properties.putString("value", "replacement");

        Assert.assertEquals(1, properties.size());
        Assert.assertEquals(PropertyType.STRING, properties.typeOf("value"));
        Assert.assertEquals("replacement", properties.getString("value", ""));
    }

    @Test
    public void rejectsInvalidNamesAndValues() {
        assertInvalidName(null);
        assertInvalidName("");
        assertInvalidName(" \t\n");

        PropertySet properties = new PropertySet();
        try {
            properties.put("valid", null);
            Assert.fail("Expected null value to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("value"));
        }

        try {
            properties.putString("valid", null);
            Assert.fail("Expected null string to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("String"));
        }
    }

    @Test
    public void rejectsEveryNonFiniteFloat() {
        assertInvalidFloat(Float.NaN);
        assertInvalidFloat(Float.POSITIVE_INFINITY);
        assertInvalidFloat(Float.NEGATIVE_INFINITY);
    }

    @Test
    public void clearRemovesAllProperties() {
        PropertySet properties = new PropertySet()
                .putString("a", "one")
                .putInt("b", 2);

        properties.clear();

        Assert.assertTrue(properties.isEmpty());
        Assert.assertEquals(0, properties.size());
    }

    @Test
    public void copiesHaveIndependentCollectionsAndValues() {
        PropertySet source = new PropertySet()
                .putString("name", "source")
                .putInt("damage", 20);

        PropertySet copy = source.copy();
        source.putString("name", "changed");
        source.clear();

        Assert.assertEquals(2, copy.size());
        Assert.assertEquals("source", copy.getString("name", ""));
        Assert.assertEquals(20, copy.getInt("damage", 0));
    }

    @Test
    public void authoringAccessorsEnumerateExactNamesAndDoNotExposeValues() {
        PropertySet source = new PropertySet()
                .putString("Health", "first")
                .putInt("health", 20);
        Array<String> names = new Array<>();

        source.copyNamesTo(names);
        Assert.assertEquals(2, names.size);
        Assert.assertTrue(names.contains("Health", false));
        Assert.assertTrue(names.contains("health", false));
        names.clear();

        Assert.assertEquals(2, source.size());
        PropertyValue copy = source.valueCopy("Health");
        Assert.assertNotNull(copy);
        Assert.assertEquals("first", copy.asString());
        Assert.assertNull(source.valueCopy("missing"));
    }

    @Test
    public void authoringRemovalIsDeterministicForPresentAndMissingNames() {
        PropertySet source = new PropertySet().putInt("damage", 20);

        Assert.assertFalse(source.remove("missing"));
        Assert.assertTrue(source.remove("damage"));
        Assert.assertFalse(source.remove("damage"));
        Assert.assertFalse(source.remove(null));
        Assert.assertTrue(source.isEmpty());
    }

    @Test
    public void classAuthoringCopiesAreDeepAndDoNotMutateTheOriginal() {
        PropertySet source = new PropertySet().putClass(
                "attack", "Attack", new PropertySet().putInt("damage", 20));

        PropertyValue copiedValue = source.valueCopy("attack");
        PropertySet copiedMembers = copiedValue.classPropertiesCopy();
        copiedMembers.putInt("damage", 99).putString("extra", "local");

        Assert.assertEquals("Attack", copiedValue.className());
        Assert.assertEquals(20,
                source.getClassValue("attack").properties().getInt("damage", 0));
        Assert.assertFalse(source.getClassValue("attack").properties().contains("extra"));
    }

    @Test
    public void defaultAndKnownSizeConstructionAvoidOversizedObjectMapAllocation()
            throws Exception {
        int libGdxDefaultCapacity = backingCapacity(new ObjectMap<String, PropertyValue>());

        PropertySet empty = new PropertySet();
        PropertySet knownSize = new PropertySet(3)
                .putString("a", "one")
                .putBoolean("b", true)
                .putInt("c", 3);

        Assert.assertTrue(backingCapacity(empty) < libGdxDefaultCapacity);
        Assert.assertTrue(backingCapacity(knownSize) < libGdxDefaultCapacity);
        Assert.assertEquals(3, knownSize.size());
    }

    @Test
    public void copyUsesCapacityBasedOnSourceSize() throws Exception {
        PropertySet source = new PropertySet(2)
                .putString("name", "source")
                .putInt("damage", 20);

        PropertySet copy = source.copy();

        Assert.assertTrue(backingCapacity(copy)
                < backingCapacity(new ObjectMap<String, PropertyValue>()));
        Assert.assertEquals("source", copy.getString("name", ""));
        Assert.assertEquals(20, copy.getInt("damage", 0));
    }

    @Test
    public void negativeExpectedSizeIsRejected() {
        try {
            new PropertySet(-1);
            Assert.fail("Expected negative size to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("negative"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void copyRejectsCorruptedSourceInsteadOfPublishingPartialState() throws Exception {
        PropertySet source = new PropertySet().putInt("valid", 1);
        Field values = PropertySet.class.getDeclaredField("values");
        values.setAccessible(true);
        ((ObjectMap<String, PropertyValue>) values.get(source)).put("broken", null);

        try {
            new PropertySet().copyFrom(source);
            Assert.fail("Expected corrupted source to fail validation");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("broken"));
        }
    }

    private static void assertInvalidName(String name) {
        try {
            new PropertySet().putInt(name, 1);
            Assert.fail("Expected invalid property name to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("name"));
        }
    }

    private static void assertInvalidFloat(float value) {
        try {
            new PropertySet().putFloat("value", value);
            Assert.fail("Expected non-finite float to fail: " + value);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("finite"));
        }
    }

    private static void assertInvalidClass(String className,
                                           PropertySet members,
                                           String expectedText) {
        try {
            new PropertySet().putClass("value", className, members);
            Assert.fail("Expected invalid class value to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedText));
        }
    }

    private static void assertInvalidObjectStableId(int stableId) {
        try {
            PropertyValue.ofObjectStableId(stableId);
            Assert.fail("Expected invalid OBJECT stable ID: " + stableId);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("stableId"));
        }
    }

    private static int backingCapacity(Object owner) throws Exception {
        ObjectMap<?, ?> map;
        if (owner instanceof PropertySet) {
            Field values = PropertySet.class.getDeclaredField("values");
            values.setAccessible(true);
            map = (ObjectMap<?, ?>) values.get(owner);
        } else {
            map = (ObjectMap<?, ?>) owner;
        }
        Field keyTable = ObjectMap.class.getDeclaredField("keyTable");
        keyTable.setAccessible(true);
        return ((Object[]) keyTable.get(map)).length;
    }
}
