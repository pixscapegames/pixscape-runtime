package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class EntityLayerNameQueryApiTest {
    private World world;
    private PixscapeEngine engine;
    private EntitiesAPI api;
    private DirtyTrackerSystem dirty;

    @Before
    public void setUp() throws Exception {
        dirty = new DirtyTrackerSystem(64);
        world = new World(new WorldConfiguration().setSystem(dirty));
        engine = new PixscapeEngine();
        bindEngine();
        api = engine.api().entities();
    }

    private void bindEngine() throws Exception {
        Field field = PixscapeEngine.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(engine, world);
        engine.getIdentityRegistry().bind(world, new SceneMetaRuntime());
    }

    @After
    public void tearDown() {
        engine.getIdentityRegistry().bind(null, null);
        world.dispose();
    }

    @Test
    public void exactNamesCombineDirectMembersAndReturnIndependentSnapshots() {
        layer(1, "spawn-points");
        layer(2, "spawn-points");
        layer(3, "empty");
        layer(4, "Spawn-points");
        layer(5, " spawn-points ");
        int first = point(1);
        int second = point(2);
        int upper = point(4);
        int padded = point(5);
        int child = point(1);
        world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 123;
        world.process();

        assertIds(api.findAllByLayerName("spawn-points"), first, second);
        assertIds(api.findAllByLayerName("Spawn-points"), upper);
        assertIds(api.findAllByLayerName(" spawn-points "), padded);
        assertIds(api.findAllByLayerName("empty"));
        assertIds(api.findAllByLayerName("missing"));
        assertIds(api.findAllByLayerName(null));
        EntityRef[] snapshot = api.findAllByLayerName("spawn-points");
        assertNotSame(snapshot, api.findAllByLayerName("spawn-points"));
        snapshot[0] = null;
        assertIds(api.findAllByLayerName("spawn-points"), first, second);
    }

    @Test
    public void pointWithoutRenderingExposesRoleAndTransform() {
        layer(1, "spawn-points");
        int point = point(1);
        world.process();
        EntityRef ref = api.findAllByLayerName("spawn-points")[0];
        assertEquals(point, ref.entityId());
        assertEquals(7, ref.properties().getInt("role", -1));
        assertEquals(12f, ref.transform().x(), 0f);
        assertEquals(34f, ref.transform().y(), 0f);
        assertFalse(world.getMapper(RenderMaterialComponent.class).has(point));
    }

    @Test
    public void mutationsRespectSubscriptionSynchronizationAndDirtyLayerMarks() {
        int layer = layer(1, "spawn-points");
        layer(2, "other");
        int first = point(1);
        world.process();
        EntityRef captured = api.findAllByLayerName("spawn-points")[0];
        api.ofEntityId(first).renderOrder().layerIndex(2);
        assertIds(api.findAllByLayerName("spawn-points"));
        assertIds(api.findAllByLayerName("other"), first);

        world.getMapper(LayerNameComponent.class).get(layer).name = "renamed";
        dirty.layer(layer);
        int second = point(1);
        assertIds(api.findAllByLayerName("renamed")); // Pending insertion.
        world.process();
        assertIds(api.findAllByLayerName("renamed"), second);
        world.getMapper(LayerComponent.class).get(layer).layerIndex = 8;
        dirty.layer(layer); // Still dirty from above: repeated marks must invalidate.
        assertIds(api.findAllByLayerName("renamed"));
        world.getMapper(EntityIndexComponent.class).get(second).layerIndex = 8;
        dirty.layer(second);
        assertIds(api.findAllByLayerName("renamed"), second);

        world.delete(layer);
        world.process();
        assertIds(api.findAllByLayerName("renamed"));
        layer(8, "renamed");
        world.process();
        assertIds(api.findAllByLayerName("renamed"), second);
        world.delete(first);
        world.process();
        assertFalse(captured.exists());
        assertIds(api.findAllByLayerName("other"));
        int recycled = point(2);
        world.process();
        assertFalse(captured.exists());
        assertIds(api.findAllByLayerName("other"), recycled);
    }

    @Test
    public void componentRemovalAndReplacementWorldDropOldMembership() throws Exception {
        int layer = layer(1, "spawn-points");
        int point = point(1);
        world.process();
        EntityRef old = api.findAllByLayerName("spawn-points")[0];
        world.getMapper(LayerNameComponent.class).remove(layer);
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"));
        world.getMapper(LayerNameComponent.class).create(layer).name = "spawn-points";
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"), point);
        world.getMapper(EntityIndexComponent.class).remove(point);
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"));

        world.getMapper(EntityIndexComponent.class).create(point).layerIndex = 1;
        world.getMapper(GameObjectMemberComponent.class).create(point);
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"));
        world.getMapper(GameObjectMemberComponent.class).remove(point);
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"), point);

        World previous = world;
        world = new World(new WorldConfiguration().setSystem(new DirtyTrackerSystem(64)));
        bindEngine();
        layer(1, "replacement");
        int replacement = point(1);
        world.process();
        assertIds(api.findAllByLayerName("spawn-points"));
        assertIds(api.findAllByLayerName("replacement"), replacement);
        assertFalse(old.exists());
        previous.dispose();
    }

    @Test
    public void unchangedCallsAndGameplayEditsNeverRevisitSourceSubscriptions() throws Exception {
        layer(1, "spawn-points");
        int point = point(1);
        for (int i = 0; i < 5000; i++) point(2);
        world.process();
        Field field = PixscapeApiImpl.class.getDeclaredField("sceneLayers");
        field.setAccessible(true);
        SceneLayerResolver resolver = (SceneLayerResolver) field.get(engine.api());
        assertEquals(0, resolver.memberIndexBuildCount());
        assertEquals(1, api.findAllByLayerName("spawn-points").length);
        int visits = resolver.memberIndexVisitCount();
        assertEquals(5002, visits);
        for (int i = 0; i < 100; i++) {
            api.ofEntityId(point).transform().setX(i);
            world.getMapper(CustomPropertiesComponent.class).get(point).properties.putInt("role", i);
            world.process();
            assertEquals(1, api.findAllByLayerName("spawn-points").length);
            assertEquals(0, api.findAllByLayerName("absent").length);
        }
        assertEquals(1, resolver.memberIndexBuildCount());
        assertEquals(visits, resolver.memberIndexVisitCount());
        world.getMapper(EntityIndexComponent.class).get(point).layerIndex = 2;
        dirty.layer(point);
        assertEquals(0, api.findAllByLayerName("spawn-points").length);
        assertEquals(2, resolver.memberIndexBuildCount());
    }

    private int layer(int index, String name) {
        int entity = world.create();
        world.getMapper(LayerComponent.class).create(entity).layerIndex = index;
        world.getMapper(LayerNameComponent.class).create(entity).name = name;
        return entity;
    }

    private int point(int layer) {
        int entity = world.create();
        world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = layer;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = 12;
        transform.y = 34;
        world.getMapper(CustomPropertiesComponent.class).create(entity).properties.putInt("role", 7);
        return entity;
    }

    private static void assertIds(EntityRef[] refs, int... ids) {
        Set<Integer> actual = new HashSet<Integer>();
        for (EntityRef ref : refs) assertTrue("duplicate entity", actual.add(ref.entityId()));
        assertEquals(ids.length, actual.size());
        for (int id : ids) assertTrue(actual.contains(id));
    }
}
