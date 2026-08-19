package games.pixscape.runtime.component;

import com.artemis.World;
import org.junit.Assert;
import org.junit.Test;

public class CustomPropertiesComponentTest {
    @Test
    public void componentIsOptionalAndStoresNormalEcsValues() {
        World world = new World();
        int entity = world.create();

        Assert.assertFalse(world.getMapper(CustomPropertiesComponent.class).has(entity));

        CustomPropertiesComponent component = world
                .getMapper(CustomPropertiesComponent.class)
                .create(entity);
        component.properties.putBoolean("locked", true).putInt("damage", 20);

        Assert.assertTrue(component.properties.getBoolean("locked", false));
        Assert.assertEquals(20, component.properties.getInt("damage", 0));
        world.dispose();
    }

    @Test
    public void recycledComponentDoesNotLeakPreviousEntityProperties() {
        World world = new World();
        int first = world.create();
        CustomPropertiesComponent firstComponent = world
                .getMapper(CustomPropertiesComponent.class)
                .create(first);
        firstComponent.properties.putString("owner", "first").putInt("damage", 20);
        world.process();

        world.delete(first);
        world.process();

        int replacement = world.create();
        Assert.assertEquals("The test must exercise Artemis entity-id reuse", first, replacement);
        CustomPropertiesComponent recycled = world
                .getMapper(CustomPropertiesComponent.class)
                .create(replacement);

        Assert.assertSame("The test must exercise the pooled component instance",
                firstComponent, recycled);
        Assert.assertTrue(recycled.properties.isEmpty());
        Assert.assertFalse(recycled.properties.contains("owner"));
        Assert.assertEquals(0, recycled.properties.getInt("damage", 0));
        world.dispose();
    }
}
