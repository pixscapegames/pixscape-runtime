package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledAnimationComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import org.junit.Assert;
import org.junit.Test;

public class TiledEntityAnimationSystemTest {

    @Test
    public void firstPassAppliesFrameZeroEvenWithZeroDeltaAndKeepsAuthoredState() {
        Fixture fixture = fixture(new int[]{101, 102}, new int[]{40, 70});
        int entity = fixture.createEntity();
        TransformComponent transform = fixture.world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = fixture.world.getMapper(DimensionsComponent.class).get(entity);

        fixture.world.setDelta(0f);
        fixture.world.process();

        TiledAnimationComponent animation = fixture.animation(entity);
        AssetRefComponent assetRef = fixture.world.getMapper(AssetRefComponent.class).get(entity);
        TextureRegionComponent region = fixture.world.getMapper(TextureRegionComponent.class).get(entity);
        RenderMaterialComponent material = fixture.world.getMapper(RenderMaterialComponent.class).get(entity);
        Assert.assertEquals(0, animation.frameIndex);
        Assert.assertEquals(0, animation.frameElapsedMs);
        Assert.assertEquals(101, animation.appliedFrameAssetId);
        Assert.assertEquals(77, assetRef.assetId);
        Assert.assertEquals(0.1f, region.u1, 0f);
        Assert.assertEquals(11, region.pixW);
        Assert.assertEquals(1001, material.textureHandle);
        Assert.assertTrue(fixture.dirty.isDirty(entity, DirtyBits.MATERIAL));
        assertAuthoredGeometry(transform, dimensions);
        fixture.dispose();
    }

    @Test
    public void unequalDurationsLargeDeltaAndLoopRemainderAreExact() {
        Fixture fixture = fixture(new int[]{101, 102, 103}, new int[]{40, 70, 90});
        int entity = fixture.createEntity();

        fixture.world.setDelta(0.325f);
        fixture.world.process();

        TiledAnimationComponent animation = fixture.animation(entity);
        Assert.assertEquals(2, animation.frameIndex);
        Assert.assertEquals(15, animation.frameElapsedMs);
        Assert.assertEquals(103, animation.appliedFrameAssetId);
        Assert.assertEquals(77,
                fixture.world.getMapper(AssetRefComponent.class).get(entity).assetId);
        assertAuthoredGeometry(
                fixture.world.getMapper(TransformComponent.class).get(entity),
                fixture.world.getMapper(DimensionsComponent.class).get(entity));
        fixture.dispose();
    }

    @Test
    public void independentEntitiesCarryFractionalMillisecondsSeparatelyFromPlaybackState() {
        Fixture fixture = fixture(new int[]{101, 102}, new int[]{1, 3});
        int first = fixture.createEntity();
        int second = fixture.createEntity();
        fixture.animation(second).frameElapsedMs = 1;

        fixture.world.setDelta(0.0005f);
        fixture.world.process();
        Assert.assertEquals(0, fixture.animation(first).frameIndex);
        Assert.assertEquals(0, fixture.animation(first).frameElapsedMs);
        Assert.assertEquals(0, fixture.animation(second).frameIndex);
        Assert.assertEquals(1, fixture.animation(second).frameElapsedMs);

        fixture.world.setDelta(0.0005f);
        fixture.world.process();
        Assert.assertEquals(1, fixture.animation(first).frameIndex);
        Assert.assertEquals(0, fixture.animation(first).frameElapsedMs);
        Assert.assertEquals(1, fixture.animation(second).frameIndex);
        Assert.assertEquals(1, fixture.animation(second).frameElapsedMs);
        fixture.dispose();
    }

    @Test
    public void materialIsDirtiedOnlyWhenConcreteVisualAssetChanges() {
        Fixture fixture = fixture(new int[]{101, 102}, new int[]{40, 70});
        int entity = fixture.createEntity();
        fixture.world.setDelta(0f);
        fixture.world.process();
        fixture.dirty.clearFrame();
        int callsAfterInitialFrame = fixture.atlas.resolveCalls;

        fixture.world.setDelta(0.039f);
        fixture.world.process();
        Assert.assertFalse(fixture.dirty.isDirty(entity, DirtyBits.MATERIAL));
        Assert.assertEquals(callsAfterInitialFrame, fixture.atlas.resolveCalls);

        fixture.world.setDelta(0.001f);
        fixture.world.process();
        Assert.assertTrue(fixture.dirty.isDirty(entity, DirtyBits.MATERIAL));
        Assert.assertEquals(102, fixture.animation(entity).appliedFrameAssetId);
        Assert.assertEquals(1002,
                fixture.world.getMapper(RenderMaterialComponent.class).get(entity).textureHandle);
        Assert.assertEquals(0.2f,
                fixture.world.getMapper(TextureRegionComponent.class).get(entity).u1, 0f);
        fixture.dispose();
    }

    @Test
    public void repeatedFrameAssetAdvancesPlaybackWithoutRedundantVisualWork() {
        Fixture fixture = fixture(new int[]{101, 101}, new int[]{40, 70});
        int entity = fixture.createEntity();
        fixture.world.setDelta(0f);
        fixture.world.process();
        fixture.dirty.clearFrame();
        int callsAfterInitialFrame = fixture.atlas.resolveCalls;

        fixture.world.setDelta(0.04f);
        fixture.world.process();

        Assert.assertEquals(1, fixture.animation(entity).frameIndex);
        Assert.assertEquals(101, fixture.animation(entity).appliedFrameAssetId);
        Assert.assertEquals(callsAfterInitialFrame, fixture.atlas.resolveCalls);
        Assert.assertFalse(fixture.dirty.isDirty(entity, DirtyBits.MATERIAL));
        fixture.dispose();
    }

    @Test
    public void oneFrameDefinitionAppliesOnceAndThenRemainsStable() {
        Fixture fixture = fixture(new int[]{103}, new int[]{25});
        int entity = fixture.createEntity();
        fixture.world.setDelta(0f);
        fixture.world.process();
        int calls = fixture.atlas.resolveCalls;
        fixture.dirty.clearFrame();

        fixture.world.setDelta(1f);
        fixture.world.process();

        Assert.assertEquals(103, fixture.animation(entity).appliedFrameAssetId);
        Assert.assertEquals(calls, fixture.atlas.resolveCalls);
        Assert.assertFalse(fixture.dirty.isDirty(entity, DirtyBits.MATERIAL));
        fixture.dispose();
    }

    @Test
    public void unknownDefinitionLeavesBaseVisualAndAuthoredAssetUntouched() {
        Fixture fixture = fixture(new int[]{101, 102}, new int[]{40, 70});
        int entity = fixture.createEntity();
        TiledAnimationComponent animation = fixture.animation(entity);
        animation.animationId = 999;
        TextureRegionComponent region = fixture.world.getMapper(TextureRegionComponent.class).get(entity);
        region.u1 = 0.75f;

        fixture.world.setDelta(0.2f);
        fixture.world.process();

        Assert.assertEquals(77,
                fixture.world.getMapper(AssetRefComponent.class).get(entity).assetId);
        Assert.assertEquals(0.75f, region.u1, 0f);
        Assert.assertEquals(-1, animation.appliedFrameAssetId);
        Assert.assertEquals(0, animation.frameIndex);
        Assert.assertEquals(0, animation.frameElapsedMs);
        fixture.dispose();
    }

    private static void assertAuthoredGeometry(TransformComponent transform,
                                               DimensionsComponent dimensions) {
        Assert.assertEquals(12f, transform.x, 0f);
        Assert.assertEquals(34f, transform.y, 0f);
        Assert.assertEquals(5f, transform.originX, 0f);
        Assert.assertEquals(6f, transform.originY, 0f);
        Assert.assertEquals(0.75f, transform.rotationRad, 0f);
        Assert.assertEquals(-2f, transform.scaleX, 0f);
        Assert.assertEquals(3f, transform.scaleY, 0f);
        Assert.assertEquals(80f, dimensions.width, 0f);
        Assert.assertEquals(96f, dimensions.height, 0f);
    }

    private static Fixture fixture(int[] assets, int[] durations) {
        TileAnimationRegistry registry = new TileAnimationRegistry();
        TileAnimationDefData definition = new TileAnimationDefData();
        definition.id = 42;
        definition.frameAssetIds = assets;
        definition.frameDurationsMs = durations;
        registry.put(definition);

        TestAtlasRuntimeService atlas = new TestAtlasRuntimeService();
        atlas.put(77, 0.77f, 707, 7, 9);
        atlas.put(101, 0.1f, 1001, 11, 13);
        atlas.put(102, 0.2f, 1002, 21, 23);
        atlas.put(103, 0.3f, 1003, 31, 33);
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        TiledEntityAnimationSystem system = new TiledEntityAnimationSystem(registry, atlas);
        World world = new World(new WorldConfigurationBuilder().with(dirty, system).build());
        return new Fixture(world, dirty, atlas);
    }

    private static final class Fixture {
        final World world;
        final DirtyTrackerSystem dirty;
        final TestAtlasRuntimeService atlas;

        Fixture(World world, DirtyTrackerSystem dirty, TestAtlasRuntimeService atlas) {
            this.world = world;
            this.dirty = dirty;
            this.atlas = atlas;
        }

        int createEntity() {
            int entity = world.create();
            AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entity);
            assetRef.assetId = 77;
            assetRef.atlasTag = "scene";
            world.getMapper(TextureRegionComponent.class).create(entity);
            world.getMapper(RenderMaterialComponent.class).create(entity);
            TiledAnimationComponent animation = world.getMapper(TiledAnimationComponent.class)
                    .create(entity);
            animation.animationId = 42;
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = 12f;
            transform.y = 34f;
            transform.originX = 5f;
            transform.originY = 6f;
            transform.rotationRad = 0.75f;
            transform.scaleX = -2f;
            transform.scaleY = 3f;
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
            dimensions.width = 80f;
            dimensions.height = 96f;
            return entity;
        }

        TiledAnimationComponent animation(int entity) {
            return world.getMapper(TiledAnimationComponent.class).get(entity);
        }

        void dispose() {
            world.dispose();
        }
    }

    private static final class TestAtlasRuntimeService extends AtlasRuntimeService {
        private final IntMap<AtlasAssetBinding> bindings = new IntMap<AtlasAssetBinding>();
        int resolveCalls;

        void put(int assetId, float u1, int textureHandle, int width, int height) {
            bindings.put(assetId, AtlasBindingTestFactory.single(
                    assetId, "asset_" + assetId, u1, 0f, u1 + 0.05f, 1f,
                    textureHandle, width, height));
        }

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveCalls++;
            return bindings.get(assetId);
        }
    }
}
