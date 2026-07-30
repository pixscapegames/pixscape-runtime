package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Assert;
import org.junit.Test;

public class AnimationSystemIndexedBindingTest {

    @Test
    public void usesIndexedFramesAndObservesReloadAndAssetSwitchesWithoutCache() {
        MutableAtlas atlas = new MutableAtlas();
        AtlasAssetBinding assetA = AtlasBindingTestFactory.frames(
                1, "asset-a__a1", 3, 10, 16, 16);
        AtlasAssetBinding assetB = AtlasBindingTestFactory.frames(
                2, "asset-b__a2", 2, 20, 16, 16);
        atlas.assetA = assetA;
        atlas.assetB = assetB;
        World world = world(atlas);
        int entity = animatedEntity(world, 1);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entity);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).get(entity);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).get(entity);

        world.setDelta(1.1f);
        world.process();
        Assert.assertEquals(1, animation.frame);
        Assert.assertEquals(assetA.regionAt(1).getU(), region.u1, 0f);
        Assert.assertEquals(assetA.regionAt(1).getU2(), region.u2, 0f);

        AtlasAssetBinding reloadedA = AtlasBindingTestFactory.frames(
                1, "asset-a-reloaded__a1", 4, 30, 16, 16);
        atlas.assetA = reloadedA;
        resetAnimation(animation);
        world.process();
        Assert.assertEquals(reloadedA.regionAt(1).getU(), region.u1, 0f);
        Assert.assertEquals(reloadedA.regionAt(1).getU2(), region.u2, 0f);

        assetRef.assetId = 2;
        resetAnimation(animation);
        world.setDelta(0f);
        world.process();
        Assert.assertEquals(assetB.regionAt(0).getU2(), region.u2, 0f);

        assetRef.assetId = 1;
        resetAnimation(animation);
        world.process();
        Assert.assertEquals(reloadedA.regionAt(0).getU2(), region.u2, 0f);
        Assert.assertEquals(4, atlas.resolveCalls);
    }

    @Test
    public void repeatedEntitiesAndMissesPerformOneBindingLookupPerProcessing() {
        MutableAtlas atlas = new MutableAtlas();
        atlas.assetA = AtlasBindingTestFactory.frames(
                1, "shared__a1", 2, 10, 16, 16);
        World world = world(atlas);
        for (int i = 0; i < 4; i++) {
            animatedEntity(world, 1);
        }

        world.setDelta(0.1f);
        for (int i = 0; i < 1000; i++) {
            world.process();
        }
        Assert.assertEquals(4000, atlas.resolveCalls);

        MutableAtlas missingAtlas = new MutableAtlas();
        World missingWorld = world(missingAtlas);
        animatedEntity(missingWorld, 99);
        missingWorld.setDelta(0.1f);
        for (int i = 0; i < 2000; i++) {
            missingWorld.process();
        }
        Assert.assertEquals(2000, missingAtlas.resolveCalls);
    }

    private static World world(AtlasRuntimeService atlas) {
        return new World(new WorldConfigurationBuilder()
                .with(
                        new DirtyTrackerSystem(32),
                        new AnimationSystem(atlas))
                .build());
    }

    private static int animatedEntity(World world, int assetId) {
        int entity = world.create();
        AnimationComponent animation =
                world.edit(entity).create(AnimationComponent.class);
        animation.clips.put("default", new AnimationComponent.Clip(0, 10));
        animation.currentClip = "default";
        animation.fps = 1f;
        animation.playing = true;
        animation.loop = true;
        animation.frame = -1;

        AssetRefComponent assetRef =
                world.edit(entity).create(AssetRefComponent.class);
        assetRef.assetId = assetId;
        assetRef.atlasTag = "main";
        world.edit(entity).create(TextureRegionComponent.class);
        world.edit(entity).create(RenderMaterialComponent.class);
        return entity;
    }

    private static void resetAnimation(AnimationComponent animation) {
        animation.stateTime = 0f;
        animation.frame = -1;
    }

    private static final class MutableAtlas extends AtlasRuntimeService {
        AtlasAssetBinding assetA;
        AtlasAssetBinding assetB;
        int resolveCalls;

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveCalls++;
            if (assetId == 1) return assetA;
            if (assetId == 2) return assetB;
            return null;
        }
    }
}
