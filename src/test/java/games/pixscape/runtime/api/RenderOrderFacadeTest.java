package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class RenderOrderFacadeTest {

    @Test
    public void facadeIsExposedCachedAndReadsCanonicalEntityIndexValues() throws Exception {
        Fixture fixture = fixture();
        EntityRef entity = fixture.target(2, 7);

        Assert.assertSame(entity.renderOrder(), entity.renderOrder());
        Assert.assertEquals(2, entity.renderOrder().layerIndex());
        Assert.assertEquals(7, entity.renderOrder().zIndex());
    }

    @Test
    public void layerIndexUpdatesBothComponentsAndPublishesLayerAndOrderDirty() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(4, "Effects", LayerComponent.TYPE_CLASSIC);
        EntityRef entity = fixture.target(0, 3);
        LayerComponent layer = fixture.world.getMapper(LayerComponent.class).get(entity.entityId());
        EntityIndexComponent index = fixture.world.getMapper(EntityIndexComponent.class).get(entity.entityId());
        fixture.dirty.clearAll();

        entity.renderOrder().layerIndex(4);

        Assert.assertEquals(4, layer.layerIndex);
        Assert.assertEquals(4, index.layerIndex);
        Assert.assertEquals(3, index.zIndex);
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER));
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.ORDER));
    }

    @Test
    public void namesResolveAllSceneLayerTypes() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(1, "Classic", LayerComponent.TYPE_CLASSIC);
        fixture.layer(2, "Physics", LayerComponent.TYPE_PHYSICS);
        fixture.layer(3, "Light", LayerComponent.TYPE_LIGHT);
        fixture.layer(4, "Tiled", LayerComponent.TYPE_TILED);
        EntityRef entity = fixture.target(0, 0);

        Assert.assertEquals(1, entity.renderOrder().layer("Classic").layerIndex());
        Assert.assertEquals(2, entity.renderOrder().layer("Physics").layerIndex());
        Assert.assertEquals(3, entity.renderOrder().layer("Light").layerIndex());
        Assert.assertEquals(4, entity.renderOrder().layer("Tiled").layerIndex());
    }

    @Test
    public void actorNamesNeverResolveAsSceneLayers() throws Exception {
        Fixture fixture = fixture();
        fixture.namedActor(8, "Impostor");
        EntityRef entity = fixture.target(0, 0);

        expectIllegalArgument("No scene layer", new Action() {
            @Override
            public void run() {
                entity.renderOrder().layer("Impostor");
            }
        });
    }

    @Test
    public void invalidNamesAndIndicesFailWithoutMutation() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, "Duplicate", LayerComponent.TYPE_CLASSIC);
        fixture.layer(3, "Duplicate", LayerComponent.TYPE_LIGHT);
        EntityRef entity = fixture.target(0, 9);

        expectIllegalArgument("blank", actionLayer(entity, "  "));
        expectIllegalArgument("No scene layer", actionLayer(entity, "Missing"));
        expectIllegalArgument("ambiguous", actionLayer(entity, "Duplicate"));
        expectIllegalArgument("layerIndex(int)", actionLayer(entity, "Duplicate"));
        expectIllegalArgument("No scene layer", new Action() {
            @Override
            public void run() {
                entity.renderOrder().layerIndex(99);
            }
        });
        Assert.assertEquals(0, entity.renderOrder().layerIndex());
        Assert.assertEquals(9, entity.renderOrder().zIndex());
    }

    @Test
    public void zIndexAndCombinedSetHaveAtomicPreservingSemantics() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(5, "Effects", LayerComponent.TYPE_CLASSIC);
        EntityRef entity = fixture.target(0, 1);
        LayerComponent layer = fixture.world.getMapper(LayerComponent.class).get(entity.entityId());
        layer.type = LayerComponent.TYPE_LIGHT;
        layer.spatialEnabled = true;

        entity.renderOrder().zIndex(Integer.MIN_VALUE);
        Assert.assertEquals(0, entity.renderOrder().layerIndex());
        Assert.assertEquals(Integer.MIN_VALUE, entity.renderOrder().zIndex());

        entity.renderOrder().set(5, Integer.MAX_VALUE);
        Assert.assertEquals(5, entity.renderOrder().layerIndex());
        Assert.assertEquals(Integer.MAX_VALUE, entity.renderOrder().zIndex());
        Assert.assertEquals(LayerComponent.TYPE_LIGHT, layer.type);
        Assert.assertTrue(layer.spatialEnabled);

        entity.renderOrder().set("Effects", 5);
        Assert.assertEquals(5, entity.renderOrder().layerIndex());
        Assert.assertEquals(5, entity.renderOrder().zIndex());

        try {
            entity.renderOrder().set("Missing", 123);
            Assert.fail("Expected unknown layer to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(5, entity.renderOrder().layerIndex());
            Assert.assertEquals(5, entity.renderOrder().zIndex());
        }
    }

    @Test
    public void unchangedValuesPublishNoDirtyWork() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, "Effects", LayerComponent.TYPE_CLASSIC);
        EntityRef entity = fixture.target(2, 10);
        fixture.dirty.clearAll();

        entity.renderOrder().set("Effects", 10);

        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER | DirtyBits.ORDER));
    }

    @Test
    public void zIndexChangesOnlyOrderState() throws Exception {
        Fixture fixture = fixture();
        EntityRef entity = fixture.target(2, 3);
        LayerComponent layer = fixture.world.getMapper(LayerComponent.class).get(entity.entityId());
        fixture.dirty.clearAll();

        entity.renderOrder().zIndex(4);

        Assert.assertEquals(2, layer.layerIndex);
        Assert.assertEquals(4, entity.renderOrder().zIndex());
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.ORDER));
        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER));
    }

    @Test
    public void missingComponentsFailClearlyAndAreNotCreated() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, "Effects", LayerComponent.TYPE_CLASSIC);
        int noLayer = fixture.world.create();
        fixture.world.getMapper(EntityIndexComponent.class).create(noLayer);
        int noIndex = fixture.world.create();
        fixture.world.getMapper(LayerComponent.class).create(noIndex);
        fixture.world.process();

        EntityRef first = fixture.engine.api().entities().ofEntityId(noLayer);
        expectIllegalState("entityId=" + noLayer, "LayerComponent", "set", new Action() {
            @Override
            public void run() {
                first.renderOrder().set(2, 1);
            }
        });
        Assert.assertFalse(fixture.world.getMapper(LayerComponent.class).has(noLayer));

        EntityRef second = fixture.engine.api().entities().ofEntityId(noIndex);
        expectIllegalState("entityId=" + noIndex, "EntityIndexComponent", "zIndex", new Action() {
            @Override
            public void run() {
                second.renderOrder().zIndex(1);
            }
        });
        Assert.assertFalse(fixture.world.getMapper(EntityIndexComponent.class).has(noIndex));
    }

    @Test
    public void removedAndRecycledEntityCannotBeMutatedThroughOldFacade() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, "Effects", LayerComponent.TYPE_CLASSIC);
        EntityRef stale = fixture.target(0, 0);
        RenderOrderFacade facade = stale.renderOrder();
        int recycledId = stale.entityId();
        fixture.world.delete(recycledId);
        fixture.world.process();
        int replacement = fixture.world.create();
        Assert.assertEquals(recycledId, replacement);
        fixture.world.getMapper(LayerComponent.class).create(replacement);
        fixture.world.getMapper(EntityIndexComponent.class).create(replacement);
        fixture.world.process();

        expectIllegalState("entityId=" + recycledId, "no longer exists", "zIndex", new Action() {
            @Override
            public void run() {
                facade.zIndex(12);
            }
        });
        Assert.assertEquals(0, fixture.world.getMapper(EntityIndexComponent.class).get(replacement).zIndex);
    }

    @Test
    public void particleSpriteAndAnimationUseCommonFacadeImmediatelyAfterSpawn() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(4, "Effects", LayerComponent.TYPE_CLASSIC);
        setField(fixture.engine, "atlasRuntimeService", new TestAtlasRuntimeService());

        ParticleRef particle = fixture.engine.api().particles().spawn("Flame", 10f, 20f);
        particle.entity().renderOrder().layer("Effects").zIndex(5);
        Assert.assertEquals(4, particle.entity().renderOrder().layerIndex());
        Assert.assertEquals(5, particle.entity().renderOrder().zIndex());
        particle.entity().renderOrder().set("Effects", 5);

        SpriteRef sprite = fixture.engine.api().sprites().spawn(42, 1f, 2f);
        sprite.entity().renderOrder().set("Effects", 6);
        Assert.assertEquals(4, sprite.entity().renderOrder().layerIndex());

        AnimationRef animation = fixture.engine.api().animations().spawn(42, 3f, 4f);
        animation.entity().renderOrder().set("Effects", 7);
        Assert.assertEquals(7, animation.entity().renderOrder().zIndex());
    }

    @Test
    public void prefabSpawnedEntityUsesCommonFacade() throws Exception {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, new WorldSerializationManager())
                .build());
        PixscapeEngine engine = new PixscapeEngine();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        setField(engine, "world", world);
        setField(engine, "sceneLoaded", true);
        setField(engine, "activeSceneMeta", meta);
        setField(engine, "atlasRuntimeService", new TestAtlasRuntimeService());
        engine.getIdentityRegistry().bind(world, meta);
        engine.getTagRegistry().bind(world);

        int source = world.create();
        world.getMapper(LayerComponent.class).create(source);
        world.getMapper(EntityIndexComponent.class).create(source);
        world.getMapper(PixscapeIdentityComponent.class).create(source).stableId = 1;
        meta.nextEntityStableId = 2;
        IntBag sourceEntities = new IntBag();
        sourceEntities.add(source);
        RuntimePrefabFragment fragment = new RuntimePrefabFragment(sourceEntities);
        world.process();

        Fixture fixture = new Fixture(engine, world, dirty);
        fixture.layer(4, "Effects", LayerComponent.TYPE_CLASSIC);
        SpawnResult result = engine.api().prefabs().spawnFragment(fragment, 0f, 0f);
        EntityRef spawned = engine.api().entities().ofEntityId(result.createdEntityIds().get(0));

        spawned.renderOrder().set("Effects", 8);
        Assert.assertEquals(4, spawned.renderOrder().layerIndex());
        Assert.assertEquals(8, spawned.renderOrder().zIndex());
    }

    @Test
    public void existingTiledApiLookupBehaviorRemainsAvailable() throws Exception {
        Fixture fixture = fixture();
        int tiled = fixture.layer(3, "maps/ground.tmx", LayerComponent.TYPE_TILED);
        games.pixscape.runtime.component.TiledLayerComponent tiledLayer = fixture.world
                .getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).create(tiled);
        tiledLayer.data = new games.pixscape.runtime.tiled.TiledMapLayerData(1, 1, 16, 16, 1);
        fixture.world.process();

        Assert.assertEquals(tiled, fixture.engine.api().tiled().layer(3).entityId());
        Assert.assertEquals(tiled, fixture.engine.api().tiled().layer("ground").entityId());
    }

    private static Action actionLayer(final EntityRef entity, final String name) {
        return new Action() {
            @Override
            public void run() {
                entity.renderOrder().layer(name);
            }
        };
    }

    private static void expectIllegalArgument(String message, Action action) {
        try {
            action.run();
            Assert.fail("Expected IllegalArgumentException containing " + message);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static void expectIllegalState(String first, String second, String third, Action action) {
        try {
            action.run();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(first));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(second));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(third));
        }
    }

    private static Fixture fixture() throws Exception {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        World world = new World(new WorldConfigurationBuilder().with(dirty).build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        engine.getIdentityRegistry().bind(world, meta);
        engine.getTagRegistry().bind(world);
        return new Fixture(engine, world, dirty);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private interface Action {
        void run();
    }

    private static final class Fixture {
        final PixscapeEngine engine;
        final World world;
        final DirtyTrackerSystem dirty;

        Fixture(PixscapeEngine engine, World world, DirtyTrackerSystem dirty) {
            this.engine = engine;
            this.world = world;
            this.dirty = dirty;
        }

        int layer(int layerIndex, String name, int type) {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.layerIndex = layerIndex;
            layer.type = type;
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entityId);
            identity.name = name;
            world.process();
            return entityId;
        }

        EntityRef target(int layerIndex, int zIndex) {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.layerIndex = layerIndex;
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entityId);
            index.layerIndex = layerIndex;
            index.zIndex = zIndex;
            world.process();
            return engine.api().entities().ofEntityId(entityId);
        }

        void namedActor(int layerIndex, String name) {
            EntityRef actor = target(layerIndex, 0);
            world.getMapper(PixscapeIdentityComponent.class).create(actor.entityId()).name = name;
            world.process();
        }
    }

    private static final class TestAtlasRuntimeService extends AtlasRuntimeService {
        TestAtlasRuntimeService() {
            super();
        }

        @Override
        public games.pixscape.runtime.service.AtlasAssetBinding resolveBinding(int assetId, String atlasTag) {
            if (assetId != 42) return null;
            return games.pixscape.runtime.service.AtlasBindingTestFactory.single(
                    42, "sprite__a42", 0f, 0f, 1f, 1f, 7, 16, 16);
        }
    }
}
