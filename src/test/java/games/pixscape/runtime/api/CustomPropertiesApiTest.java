package games.pixscape.runtime.api;

import com.artemis.World;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertySet;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CustomPropertiesApiTest {
    @Test
    public void entityRefExposesReadOnlyTypedProperties() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int entity = world.create();
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putString("name", "door")
                .putBoolean("locked", true)
                .putInt("damage", 20)
                .putFloat("rate", 0.5f);
        world.process();

        CustomProperties properties = engine.api().entities().ofEntityId(entity).properties();

        Assert.assertEquals(4, properties.size());
        Assert.assertFalse(properties.isEmpty());
        Assert.assertTrue(properties.contains("damage"));
        Assert.assertEquals(PropertyType.INTEGER, properties.typeOf("damage"));
        Assert.assertEquals("door", properties.getString("name", ""));
        Assert.assertTrue(properties.getBoolean("locked", false));
        Assert.assertEquals(20, properties.getInt("damage", 0));
        Assert.assertEquals(0.5f, properties.getFloat("rate", 0f), 0f);
        world.dispose();
    }

    @Test
    public void absentComponentBehavesLikeAnEmptySet() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int entity = world.create();
        world.process();

        CustomProperties properties = engine.api().entities().ofEntityId(entity).properties();

        Assert.assertTrue(properties.isEmpty());
        Assert.assertFalse(properties.contains("missing"));
        Assert.assertNull(properties.typeOf("missing"));
        Assert.assertEquals("fallback", properties.getString("missing", "fallback"));
        Assert.assertTrue(properties.getBoolean("missing", true));
        Assert.assertEquals(10, properties.getInt("missing", 10));
        Assert.assertEquals(2f, properties.getFloat("missing", 2f), 0f);
        world.dispose();
    }

    @Test
    public void wrongTypeThrowsInsteadOfReturningFallback() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int entity = world.create();
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putString("damage", "20");
        world.process();

        try {
            engine.api().entities().ofEntityId(entity).properties().getInt("damage", 10);
            Assert.fail("Expected a type mismatch");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("damage"));
        }
        world.dispose();
    }

    @Test
    public void entityRefExposesReadOnlyNestedClassValues() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int entity = world.create();
        PropertySet modifier = new PropertySet().putBoolean("critical", true);
        PropertySet attack = new PropertySet()
                .putInt("damage", 10)
                .putClass("modifier", "Critical", modifier);
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putClass("attack", "Attack", attack);
        world.process();

        CustomProperties properties = engine.api().entities().ofEntityId(entity).properties();
        ClassProperty classValue = properties.getClassValue("attack");

        Assert.assertNotNull(classValue);
        Assert.assertEquals("Attack", classValue.typeName());
        Assert.assertEquals(10, classValue.properties().getInt("damage", 0));
        ClassProperty nested = classValue.properties().getClassValue("modifier");
        Assert.assertEquals("Critical", nested.typeName());
        Assert.assertTrue(nested.properties().getBoolean("critical", false));
        Assert.assertNull(properties.getClassValue("missing"));
        Assert.assertSame(classValue, properties.getClassValue("attack"));
        world.dispose();
    }

    @Test
    public void classGetterRejectsPrimitiveProperties() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int entity = world.create();
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putInt("attack", 10);
        world.process();

        try {
            engine.api().entities().ofEntityId(entity).properties().getClassValue("attack");
            Assert.fail("Expected a type mismatch");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("attack"));
            Assert.assertTrue(expected.getMessage().contains("CLASS"));
        }
        world.dispose();
    }

    @Test
    public void cachedViewDoesNotFollowARecycledEntityId() throws Exception {
        World world = new World();
        PixscapeEngine engine = engineWithWorld(world);
        int first = world.create();
        world.getMapper(CustomPropertiesComponent.class).create(first).properties
                .putString("owner", "first");
        world.process();
        EntityRef oldEntity = engine.api().entities().ofEntityId(first);
        CustomProperties oldProperties = oldEntity.properties();

        world.delete(first);
        world.process();
        int replacement = world.create();
        Assert.assertEquals(first, replacement);
        world.getMapper(CustomPropertiesComponent.class).create(replacement).properties
                .putString("owner", "replacement");
        world.process();

        Assert.assertFalse(oldEntity.exists());
        Assert.assertTrue(oldProperties.isEmpty());
        Assert.assertEquals("missing", oldProperties.getString("owner", "missing"));
        Assert.assertEquals("replacement", engine.api().entities().ofEntityId(replacement)
                .properties().getString("owner", ""));
        world.dispose();
    }

    @Test
    public void publicViewDoesNotExposeMutationOrEcsInternals() {
        for (Method method : CustomProperties.class.getMethods()) {
            String name = method.getName();
            Assert.assertFalse("Mutation method leaked: " + name,
                    "put".equals(name) || "remove".equals(name) || "clear".equals(name));
            Class<?> returnType = method.getReturnType();
            Assert.assertFalse(returnType.getName().startsWith("com.artemis"));
            Assert.assertFalse(returnType.getName().startsWith("com.badlogic.gdx.utils"));
            Assert.assertFalse(returnType.getName().endsWith("PropertySet"));
            Assert.assertFalse(returnType.getName().endsWith("CustomPropertiesComponent"));
        }
        for (Method method : ClassProperty.class.getMethods()) {
            Class<?> returnType = method.getReturnType();
            Assert.assertFalse(returnType.getName().endsWith("PropertySet"));
            Assert.assertFalse(returnType.getName().endsWith("CustomPropertiesComponent"));
        }
    }

    private static PixscapeEngine engineWithWorld(World world) throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        Field field = PixscapeEngine.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(engine, world);
        return engine;
    }
}
