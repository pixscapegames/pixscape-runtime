package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class RenderOrderFacadeTest {

    @Test
    public void facadeIsExposedCachedAndReadsEntityIndexValues() throws Exception {
        Fixture fixture = fixture();
        EntityRef entity = fixture.target(2, 7);

        Assert.assertSame(entity.renderOrder(), entity.renderOrder());
        Assert.assertTrue(entity.renderOrder().exists());
        Assert.assertEquals(2, entity.renderOrder().layerIndex());
        Assert.assertEquals(7, entity.renderOrder().zIndex());
    }

    @Test
    public void layerIndexAcceptsEveryExportedSceneLayerType() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(1, LayerComponent.TYPE_CLASSIC);
        fixture.layer(2, LayerComponent.TYPE_PHYSICS);
        fixture.layer(3, LayerComponent.TYPE_LIGHT);
        fixture.layer(4, LayerComponent.TYPE_TILED);
        EntityRef entity = fixture.target(0, 9);

        Assert.assertEquals(1, entity.renderOrder().layerIndex(1).layerIndex());
        Assert.assertEquals(2, entity.renderOrder().layerIndex(2).layerIndex());
        Assert.assertEquals(3, entity.renderOrder().layerIndex(3).layerIndex());
        Assert.assertEquals(4, entity.renderOrder().layerIndex(4).layerIndex());
        Assert.assertEquals(9, entity.renderOrder().zIndex());
    }

    @Test
    public void unknownDuplicateAndActorOnlyLayerIndicesFail() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, LayerComponent.TYPE_CLASSIC);
        fixture.layer(2, LayerComponent.TYPE_LIGHT);
        fixture.actorMetadata(8, true);
        EntityRef entity = fixture.target(0, 3);

        expectIllegalArgument("ambiguous", new Action() {
            @Override
            public void run() {
                entity.renderOrder().layerIndex(2);
            }
        });
        expectIllegalArgument("No scene layer", new Action() {
            @Override
            public void run() {
                entity.renderOrder().layerIndex(8);
            }
        });
        expectIllegalArgument("No scene layer", new Action() {
            @Override
            public void run() {
                entity.renderOrder().layerIndex(99);
            }
        });
        Assert.assertEquals(0, entity.renderOrder().layerIndex());
        Assert.assertEquals(3, entity.renderOrder().zIndex());
    }

    @Test
    public void layerChangeSynchronizesFieldsPreservesMetadataAndPublishesLayerAndOrder() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(4, LayerComponent.TYPE_CLASSIC);
        EntityRef entity = fixture.target(0, 3);
        LayerComponent layer = fixture.world.getMapper(LayerComponent.class).get(entity.entityId());
        EntityIndexComponent index = fixture.world.getMapper(EntityIndexComponent.class).get(entity.entityId());
        layer.type = LayerComponent.TYPE_LIGHT;
        layer.spatialEnabled = true;
        fixture.dirty.clearAll();

        entity.renderOrder().layerIndex(4);

        Assert.assertEquals(4, layer.layerIndex);
        Assert.assertEquals(4, index.layerIndex);
        Assert.assertEquals(3, index.zIndex);
        Assert.assertEquals(LayerComponent.TYPE_LIGHT, layer.type);
        Assert.assertTrue(layer.spatialEnabled);
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER));
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.ORDER));
    }

    @Test
    public void zIndexBoundariesSucceedAndPreserveLayerWithOrderOnlyDirty() throws Exception {
        Fixture fixture = fixture();
        EntityRef entity = fixture.target(2, 3);
        LayerComponent layer = fixture.world.getMapper(LayerComponent.class).get(entity.entityId());

        fixture.dirty.clearAll();
        entity.renderOrder().zIndex(SortKey64.MIN_Z);
        Assert.assertEquals(SortKey64.MIN_Z, entity.renderOrder().zIndex());
        Assert.assertEquals(2, entity.renderOrder().layerIndex());
        Assert.assertEquals(2, layer.layerIndex);
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.ORDER));
        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER));

        fixture.dirty.clearAll();
        entity.renderOrder().zIndex(SortKey64.MAX_Z);
        Assert.assertEquals(SortKey64.MAX_Z, entity.renderOrder().zIndex());
        Assert.assertTrue(fixture.dirty.isDirty(entity.entityId(), DirtyBits.ORDER));
        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER));
    }

    @Test
    public void setUpdatesBothValuesAtomicallyAndUnchangedValuesPublishNothing() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(5, LayerComponent.TYPE_PHYSICS);
        EntityRef entity = fixture.target(0, 1);

        entity.renderOrder().set(5, 10);
        Assert.assertEquals(5, entity.renderOrder().layerIndex());
        Assert.assertEquals(10, entity.renderOrder().zIndex());

        fixture.dirty.clearAll();
        entity.renderOrder().set(5, 10);
        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER | DirtyBits.ORDER));
    }

    @Test
    public void invalidZIndicesFailWithoutMutationOrDirtyWork() throws Exception {
        final int[] invalid = {
                SortKey64.MIN_Z - 1,
                SortKey64.MAX_Z + 1,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE
        };
        Fixture fixture = fixture();
        fixture.layer(5, LayerComponent.TYPE_CLASSIC);
        final EntityRef entity = fixture.target(0, 7);

        for (int i = 0; i < invalid.length; i++) {
            final int value = invalid[i];
            fixture.dirty.clearAll();
            expectInvalidZ(value, "zIndex(int)", new Action() {
                @Override
                public void run() {
                    entity.renderOrder().zIndex(value);
                }
            });
            assertUnchanged(fixture, entity, 0, 7);

            fixture.dirty.clearAll();
            expectInvalidZ(value, "set(int, int)", new Action() {
                @Override
                public void run() {
                    entity.renderOrder().set(5, value);
                }
            });
            assertUnchanged(fixture, entity, 0, 7);
        }
    }

    @Test
    public void layerIndexRejectsInvalidPreservedZWithoutMutationOrDirtyWork() throws Exception {
        final int[] invalid = {SortKey64.MIN_Z - 1, SortKey64.MAX_Z + 1};
        Fixture fixture = fixture();
        fixture.layer(5, LayerComponent.TYPE_CLASSIC);

        for (int i = 0; i < invalid.length; i++) {
            final int value = invalid[i];
            final EntityRef entity = fixture.target(0, value);
            fixture.dirty.clearAll();

            expectInvalidZ(value, "layerIndex(int)", new Action() {
                @Override
                public void run() {
                    entity.renderOrder().layerIndex(5);
                }
            });

            assertUnchanged(fixture, entity, 0, value);
        }
    }

    @Test
    public void combinedValidationFailureNeverPartiallyMutates() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(5, LayerComponent.TYPE_CLASSIC);
        EntityRef entity = fixture.target(0, 7);

        expectIllegalArgument("No scene layer", new Action() {
            @Override
            public void run() {
                entity.renderOrder().set(99, 10);
            }
        });
        Assert.assertEquals(0, entity.renderOrder().layerIndex());
        Assert.assertEquals(7, entity.renderOrder().zIndex());
    }

    @Test
    public void missingCapabilityUsesDefaultsAndInertSettersWithoutCreatingComponents()
            throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, LayerComponent.TYPE_CLASSIC);
        int ordinary = fixture.world.create();
        fixture.world.process();

        RenderOrderFacade facade = fixture.engine.api().entities()
                .ofEntityId(ordinary).renderOrder();
        Assert.assertFalse(facade.exists());
        Assert.assertEquals(-1, facade.layerIndex());
        Assert.assertEquals(0, facade.zIndex());

        facade.layerIndex(99).zIndex(Integer.MAX_VALUE).set(99, Integer.MIN_VALUE);

        Assert.assertFalse(fixture.world.getMapper(LayerComponent.class).has(ordinary));
        Assert.assertFalse(fixture.world.getMapper(EntityIndexComponent.class).has(ordinary));
        Assert.assertFalse(fixture.dirty.isDirty(ordinary, DirtyBits.LAYER | DirtyBits.ORDER));
    }

    @Test
    public void partialCapabilitiesUseDefaultsAndInertSettersWithoutCompletion() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(2, LayerComponent.TYPE_CLASSIC);
        int indexOnly = fixture.world.create();
        EntityIndexComponent existingIndex =
                fixture.world.getMapper(EntityIndexComponent.class).create(indexOnly);
        existingIndex.layerIndex = 7;
        existingIndex.zIndex = 8;
        int layerOnly = fixture.world.create();
        LayerComponent existingLayer = fixture.world.getMapper(LayerComponent.class).create(layerOnly);
        existingLayer.layerIndex = 9;
        fixture.world.process();

        RenderOrderFacade indexOnlyFacade = fixture.engine.api().entities()
                .ofEntityId(indexOnly).renderOrder();
        Assert.assertFalse(indexOnlyFacade.exists());
        Assert.assertEquals(-1, indexOnlyFacade.layerIndex());
        Assert.assertEquals(0, indexOnlyFacade.zIndex());
        indexOnlyFacade.set(2, 1).layerIndex(2).zIndex(1);
        Assert.assertEquals(7, existingIndex.layerIndex);
        Assert.assertEquals(8, existingIndex.zIndex);
        Assert.assertFalse(fixture.world.getMapper(LayerComponent.class).has(indexOnly));

        RenderOrderFacade layerOnlyFacade = fixture.engine.api().entities()
                .ofEntityId(layerOnly).renderOrder();
        Assert.assertFalse(layerOnlyFacade.exists());
        Assert.assertEquals(-1, layerOnlyFacade.layerIndex());
        Assert.assertEquals(0, layerOnlyFacade.zIndex());
        layerOnlyFacade.set(2, 1).layerIndex(2).zIndex(1);
        Assert.assertEquals(9, existingLayer.layerIndex);
        Assert.assertFalse(fixture.world.getMapper(EntityIndexComponent.class).has(layerOnly));
        Assert.assertFalse(fixture.dirty.isDirty(
                indexOnly, DirtyBits.LAYER | DirtyBits.ORDER));
        Assert.assertFalse(fixture.dirty.isDirty(
                layerOnly, DirtyBits.LAYER | DirtyBits.ORDER));
    }

    @Test
    public void staleEntityUsesDefaultsAndCannotMutateReusedEntityId() throws Exception {
        Fixture fixture = fixture();
        EntityRef entity = fixture.target(0, 0);
        RenderOrderFacade facade = entity.renderOrder();
        int entityId = entity.entityId();
        fixture.world.delete(entityId);
        fixture.world.process();

        int replacement = fixture.world.create();
        Assert.assertEquals(entityId, replacement);
        LayerComponent replacementLayer =
                fixture.world.getMapper(LayerComponent.class).create(replacement);
        replacementLayer.layerIndex = 3;
        EntityIndexComponent replacementIndex =
                fixture.world.getMapper(EntityIndexComponent.class).create(replacement);
        replacementIndex.layerIndex = 3;
        replacementIndex.zIndex = 4;
        fixture.world.process();
        fixture.dirty.clearAll();

        Assert.assertFalse(facade.exists());
        Assert.assertEquals(-1, facade.layerIndex());
        Assert.assertEquals(0, facade.zIndex());

        facade.layerIndex(99).zIndex(Integer.MAX_VALUE).set(99, Integer.MIN_VALUE);

        Assert.assertEquals(3, replacementLayer.layerIndex);
        Assert.assertEquals(3, replacementIndex.layerIndex);
        Assert.assertEquals(4, replacementIndex.zIndex);
        Assert.assertFalse(fixture.dirty.isDirty(
                replacement, DirtyBits.LAYER | DirtyBits.ORDER));
    }

    @Test
    public void particleSpriteAndAnimationUseIndexFacadeImmediatelyAfterSpawn() throws Exception {
        Fixture fixture = fixture();
        fixture.layer(4, LayerComponent.TYPE_CLASSIC);
        setField(fixture.engine, "atlasRuntimeService", new TestAtlasRuntimeService());

        ParticleRef flame = fixture.engine.api().particles().spawn("Flame", 10f, 20f);
        flame.entity().renderOrder().layerIndex(4).zIndex(5);
        Assert.assertEquals(4, flame.entity().renderOrder().layerIndex());
        Assert.assertEquals(5, flame.entity().renderOrder().zIndex());
        flame.entity().renderOrder().set(4, 5);

        SpriteRef sprite = fixture.engine.api().sprites().spawn(42, 1f, 2f);
        sprite.entity().renderOrder().set(4, 6);
        Assert.assertEquals(4, sprite.entity().renderOrder().layerIndex());

        AnimationRef animation = fixture.engine.api().animations().spawn(42, 3f, 4f);
        animation.entity().renderOrder().set(4, 7);
        Assert.assertEquals(7, animation.entity().renderOrder().zIndex());
    }

    @Test
    public void prefabSpawnedEntityUsesIndexFacade() throws Exception {
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
        fixture.layer(4, LayerComponent.TYPE_CLASSIC);
        SpawnResult result = engine.api().prefabs().spawnFragment(fragment, 0f, 0f);
        EntityRef spawned = engine.api().entities().ofEntityId(result.createdEntityIds().get(0));

        spawned.renderOrder().set(4, 8);
        Assert.assertEquals(4, spawned.renderOrder().layerIndex());
        Assert.assertEquals(8, spawned.renderOrder().zIndex());
    }

    @Test
    public void existingTiledApiIndexLookupRemainsAvailable() throws Exception {
        Fixture fixture = fixture();
        int tiled = fixture.tiledLayer(3);

        Assert.assertEquals(tiled, fixture.engine.api().tiled().layer(3).entityId());
    }

    private static void assertUnchanged(Fixture fixture, EntityRef entity, int layerIndex, int zIndex) {
        Assert.assertEquals(layerIndex, entity.renderOrder().layerIndex());
        Assert.assertEquals(zIndex, entity.renderOrder().zIndex());
        Assert.assertEquals(layerIndex, fixture.world.getMapper(LayerComponent.class)
                .get(entity.entityId()).layerIndex);
        Assert.assertFalse(fixture.dirty.isDirty(entity.entityId(), DirtyBits.LAYER | DirtyBits.ORDER));
    }

    private static void expectInvalidZ(int value, String operation, Action action) {
        try {
            action.run();
            Assert.fail("Expected invalid zIndex " + value + " to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(String.valueOf(value)));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("[-32768, 32767]"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(operation));
        }
    }

    private static void expectIllegalArgument(String message, Action action) {
        try {
            action.run();
            Assert.fail("Expected IllegalArgumentException containing " + message);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
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

        int layer(int layerIndex, int type) {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.layerIndex = layerIndex;
            layer.type = type;
            world.process();
            return entityId;
        }

        int tiledLayer(int layerIndex) {
            int entityId = layer(layerIndex, LayerComponent.TYPE_TILED);
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entityId);
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
            world.process();
            return entityId;
        }

        int actorMetadata(int layerIndex, boolean spatialEnabled) {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.layerIndex = layerIndex;
            layer.spatialEnabled = spatialEnabled;
            world.getMapper(EntityIndexComponent.class).create(entityId).layerIndex = layerIndex;
            world.process();
            return entityId;
        }

        EntityRef target(int layerIndex, int zIndex) {
            int entityId = actorMetadata(layerIndex, false);
            world.getMapper(EntityIndexComponent.class).get(entityId).zIndex = zIndex;
            return engine.api().entities().ofEntityId(entityId);
        }
    }

    private static final class TestAtlasRuntimeService extends AtlasRuntimeService {
        @Override
        public games.pixscape.runtime.service.AtlasAssetBinding resolveBinding(int assetId, String atlasTag) {
            if (assetId != 42) return null;
            return games.pixscape.runtime.service.AtlasBindingTestFactory.single(
                    42, "sprite__a42", 0f, 0f, 1f, 1f, 7, 16, 16);
        }
    }
}
