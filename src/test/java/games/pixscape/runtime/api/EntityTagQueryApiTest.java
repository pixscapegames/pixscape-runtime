package games.pixscape.runtime.api;

import com.artemis.World;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class EntityTagQueryApiTest {
    private PixscapeEngine engine;
    private World world;

    @Before
    public void setUp() throws Exception {
        world = new World();
        engine = new PixscapeEngine();
        setField(engine, "world", world);
        engine.getIdentityRegistry().bind(world, new SceneMetaRuntime());
        engine.getTagRegistry().bind(world);
    }

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void findAllReturnsIndependentSnapshotsOfEveryCurrentMatch() {
        int first = tagged("EnemySpawn", "EnemySpawn");
        int second = tagged("EnemySpawn");
        int unrelated = tagged("Pickup");
        world.process();

        EntitiesAPI entities = engine.api().entities();
        Assert.assertEquals(0, entities.findAllByTag("missing").length);
        Assert.assertEquals(0, entities.findAllByTag(null).length);
        Assert.assertEquals(0, entities.findAllByTag("   ").length);
        EntityRef[] oneMatch = entities.findAllByTag("Pickup");
        Assert.assertEquals(1, oneMatch.length);
        Assert.assertEquals(unrelated, oneMatch[0].entityId());

        EntityRef[] matches = entities.findAllByTag("EnemySpawn");
        Assert.assertEquals(2, matches.length);
        assertIds(matches, first, second);
        Assert.assertNotEquals(unrelated, matches[0].entityId());
        Assert.assertNotEquals(unrelated, matches[1].entityId());

        EntityRef[] secondSnapshot = entities.findAllByTag("EnemySpawn");
        Assert.assertNotSame(matches, secondSnapshot);
        matches[0] = entities.ofEntityId(unrelated);
        assertIds(entities.findAllByTag("EnemySpawn"), first, second);

        EntityRef oneCurrentMatch = entities.requireTag("EnemySpawn");
        Assert.assertTrue(oneCurrentMatch.entityId() == first
                || oneCurrentMatch.entityId() == second);
    }

    @Test
    public void capturedMatchesBecomeStaleAndNeverRetargetRecycledIds() {
        int first = tagged("EnemySpawn");
        int survivor = tagged("EnemySpawn");
        world.process();
        EntityRef[] snapshot = engine.api().entities().findAllByTag("EnemySpawn");
        EntityRef old = find(snapshot, first);

        world.delete(first);
        world.process();
        Assert.assertFalse(old.exists());

        int replacement = tagged("EnemySpawn");
        Assert.assertEquals(first, replacement);
        world.process();

        Assert.assertFalse(old.exists());
        EntityRef[] current = engine.api().entities().findAllByTag("EnemySpawn");
        assertIds(current, survivor, replacement);
        Assert.assertTrue(find(current, replacement).exists());
    }

    @Test
    public void importedObjectStateUsesOrdinaryEntityCapabilities() {
        int first = importedObject(10f, 20f, "left");
        int second = importedObject(30f, 40f, "right");
        world.process();
        engine.getTagRegistry().rebuild();

        EntityRef[] objects = engine.api().entities().findAllByTag("EnemySpawn");
        Assert.assertEquals(2, objects.length);
        assertIds(objects, first, second);
        for (int i = 0; i < objects.length; i++) {
            EntityRef object = objects[i];
            Assert.assertTrue(object.transform().x() == 10f
                    || object.transform().x() == 30f);
            Assert.assertTrue(object.properties().contains("side"));
            Assert.assertEquals(AuthoredGeometryKind.RECTANGLE,
                    object.geometry().kind());
            Assert.assertEquals(16f, object.geometry().width(), 0f);
            Assert.assertEquals(24f, object.geometry().height(), 0f);
        }
    }

    private int importedObject(float x, float y, String side) {
        int entity = tagged("EnemySpawn");
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = x;
        transform.y = y;
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
        dimensions.width = 16f;
        dimensions.height = 24f;
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putString("side", side);
        return entity;
    }

    private int tagged(String... tags) {
        int entity = world.create();
        PixscapeTagComponent component =
                world.getMapper(PixscapeTagComponent.class).create(entity);
        for (int i = 0; i < tags.length; i++) {
            component.tags.add(tags[i]);
        }
        return entity;
    }

    private static EntityRef find(EntityRef[] refs, int entityId) {
        for (int i = 0; i < refs.length; i++) {
            if (refs[i].entityId() == entityId) return refs[i];
        }
        Assert.fail("Missing entityId=" + entityId);
        return null;
    }

    private static void assertIds(EntityRef[] refs, int first, int second) {
        Set<Integer> actual = new HashSet<Integer>();
        for (int i = 0; i < refs.length; i++) {
            actual.add(refs[i].entityId());
        }
        Assert.assertEquals(2, actual.size());
        Assert.assertTrue(actual.contains(first));
        Assert.assertTrue(actual.contains(second));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
