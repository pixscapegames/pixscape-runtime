package games.pixscape.runtime.property;

import org.junit.Assert;
import org.junit.Test;

public class PropertySetTest {
    @Test
    public void storesAndRetrievesAllSupportedTypes() {
        PropertySet properties = new PropertySet();

        Assert.assertTrue(properties.isEmpty());
        properties.putString("Name", "first\nsecond")
                .putBoolean("locked", true)
                .putInt("damage", -20)
                .putFloat("spawnRate", 0.5f);

        Assert.assertEquals(4, properties.size());
        Assert.assertTrue(properties.contains("Name"));
        Assert.assertFalse(properties.contains("name"));
        Assert.assertEquals(PropertyType.STRING, properties.typeOf("Name"));
        Assert.assertEquals(PropertyType.BOOLEAN, properties.typeOf("locked"));
        Assert.assertEquals(PropertyType.INTEGER, properties.typeOf("damage"));
        Assert.assertEquals(PropertyType.FLOAT, properties.typeOf("spawnRate"));
        Assert.assertEquals("first\nsecond", properties.getString("Name", "fallback"));
        Assert.assertTrue(properties.getBoolean("locked", false));
        Assert.assertEquals(-20, properties.getInt("damage", 0));
        Assert.assertEquals(0.5f, properties.getFloat("spawnRate", 0f), 0f);
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
}
