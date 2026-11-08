package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import org.junit.Assert;
import org.junit.Test;

public class TiledAnimationSystemTest {

    @Test
    public void animationMembershipSurvivesClearRemovalAndGridReplacement() {
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(definition(10, new int[]{101, 102}, new int[]{40, 40}));
        TiledAnimationSystem system = new TiledAnimationSystem(registry);
        system.setAdvanceOnlyVisibleChunks(false);
        World world = new World(new WorldConfigurationBuilder().with(system).build());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity);
            TiledMapLayerData map = new TiledMapLayerData(3, 1, 16, 16, 1);
            world.getMapper(TiledLayerComponent.class).create(entity).data = map;
            for (int x = 0; x < 3; x++) {
                map.setTile(x, 0, 10);
                TileAnimationStateSupport.syncWorldCell(map.getChunk(x, 0), 0, 0, registry);
            }
            Assert.assertEquals(3, map.animatedChunkCount());
            map.getChunk(1, 0).clearAnimationState(0);
            Assert.assertEquals(2, map.animatedChunkCount());
            world.setDelta(0.016f);
            world.process();
            Assert.assertEquals(16, map.getChunk(0, 0).getAnimFrameElapsedMs(0));
            Assert.assertEquals(16, map.getChunk(2, 0).getAnimFrameElapsedMs(0));
            com.badlogic.gdx.utils.IntMap.Values<TileChunk> chunks = map.getChunks();
            while (chunks.hasNext()) { chunks.next(); chunks.remove(); }
            world.process();
            Assert.assertEquals(0, map.animatedChunkCount());
            map.initializeChunks();
            map.setTile(0, 0, 10);
            TileChunk old = map.getChunk(0, 0);
            TileAnimationStateSupport.syncWorldCell(old, 0, 0, registry);
            map.initializeChunks();
            TileAnimationStateSupport.syncWorldCell(old, 0, 0, registry);
            Assert.assertEquals(0, map.animatedChunkCount());
            map.setTile(2, 0, 10);
            TileAnimationStateSupport.syncWorldCell(map.getChunk(2, 0), 0, 0, registry);
            world.process();
            Assert.assertEquals(16, map.getChunk(2, 0).getAnimFrameElapsedMs(0));
            registry.clear();
            world.process();
            Assert.assertEquals(0, map.animatedChunkCount());
        } finally { world.dispose(); }
    }

    @Test
    public void largeMapVisitsOnlyAnimatedChunksIncludingAnIsolatedNewAnimation() {
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(definition(10, new int[]{101, 102}, new int[]{40, 40}));
        TiledAnimationSystem system = new TiledAnimationSystem(registry);
        system.setAdvanceOnlyVisibleChunks(false);
        int[] visits = {0};
        system.setChunkVisitObserver(() -> visits[0]++);
        World world = new World(new WorldConfigurationBuilder().with(system).build());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity);
            TiledMapLayerData map = new TiledMapLayerData(1000, 1, 16, 16, 1);
            world.getMapper(TiledLayerComponent.class).create(entity).data = map;
            world.setDelta(0.016f);
            for (int i = 0; i < 10; i++) world.process();
            Assert.assertEquals("No animation means no chunk search", 0, visits[0]);
            map.setTile(999, 0, 10);
            TileAnimationStateSupport.syncWorldCell(map.getChunk(999, 0), 0, 0, registry);
            world.process();
            Assert.assertEquals(1, visits[0]);
            Assert.assertEquals(16, map.getChunk(999, 0).getAnimFrameElapsedMs(0));
            visits[0] = 0;
            for (int i = 0; i < 10; i++) world.process();
            Assert.assertEquals(10, visits[0]);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void variableDurationsCrossMultipleFramesAndDirtyOnlyForVisualChange() {
        Fixture fixture = fixture(new int[]{101, 102, 103}, new int[]{40, 70, 90});
        fixture.chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        fixture.chunk.contentDirty = false;
        fixture.chunk.dirtyLocalIndices.clear();

        fixture.world.setDelta(0.125f);
        fixture.world.process();

        Assert.assertEquals(2, fixture.chunk.getAnimFrameIndex(0));
        Assert.assertEquals(15, fixture.chunk.getAnimFrameElapsedMs(0));
        Assert.assertEquals(TileChunk.DirtyState.PARTIAL, fixture.chunk.dirtyState);
        Assert.assertEquals(1, fixture.chunk.dirtyLocalIndices.size);
        fixture.dispose();
    }

    @Test
    public void repeatedVisualAssetDoesNotDirtyChunkWhenLogicalFrameChanges() {
        Fixture fixture = fixture(new int[]{101, 101}, new int[]{40, 70});
        fixture.chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        fixture.chunk.contentDirty = false;
        fixture.chunk.dirtyLocalIndices.clear();

        fixture.world.setDelta(0.04f);
        fixture.world.process();

        Assert.assertEquals(1, fixture.chunk.getAnimFrameIndex(0));
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, fixture.chunk.dirtyState);
        Assert.assertEquals(0, fixture.chunk.dirtyLocalIndices.size);
        fixture.dispose();
    }

    @Test
    public void visibleChunkGateAndDisabledGatePreserveCurrentBehavior() {
        Fixture fixture = fixture(new int[]{101, 102}, new int[]{40, 70});
        fixture.chunk.visibleLastFrame = false;

        fixture.world.setDelta(0.05f);
        fixture.world.process();
        Assert.assertEquals(0, fixture.chunk.getAnimFrameIndex(0));
        Assert.assertEquals(0, fixture.chunk.getAnimFrameElapsedMs(0));

        fixture.system.setAdvanceOnlyVisibleChunks(false);
        fixture.world.setDelta(0.05f);
        fixture.world.process();
        Assert.assertEquals(1, fixture.chunk.getAnimFrameIndex(0));
        Assert.assertEquals(10, fixture.chunk.getAnimFrameElapsedMs(0));
        fixture.dispose();
    }

    @Test
    public void singleFrameAndUnknownDefinitionsDoNotRetainAnimationState() {
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(definition(10, new int[]{101}, new int[]{40}));
        TiledMapLayerData map = new TiledMapLayerData(2, 1, 16, 16, 2);
        TileChunk chunk = map.getChunk(0, 0);

        map.setTile(0, 0, 10);
        TileAnimationStateSupport.syncWorldCell(chunk, 0, 0, registry);
        Assert.assertEquals(TileAnimationPlayback.NONE, chunk.getAnimPlaybackState(0));

        map.setTile(1, 0, 999);
        TileAnimationStateSupport.syncWorldCell(chunk, 1, 0, registry);
        Assert.assertEquals(TileAnimationPlayback.NONE, chunk.getAnimPlaybackState(1));
    }

    private static Fixture fixture(int[] assets, int[] durations) {
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(definition(10, assets, durations));
        TiledAnimationSystem system = new TiledAnimationSystem(registry);
        World world = new World(new WorldConfigurationBuilder().with(system).build());

        int layerEntity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        int mapEntity = world.create();
        world.getMapper(EntityIndexComponent.class).create(mapEntity).layerIndex = 0;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(mapEntity);
        tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        tiled.data.setTile(0, 0, 10);
        TileChunk chunk = tiled.data.getChunk(0, 0);
        TileAnimationStateSupport.syncWorldCell(chunk, 0, 0, registry);
        chunk.visibleLastFrame = true;
        world.process();
        return new Fixture(world, system, chunk);
    }

    private static TileAnimationDefData definition(int id, int[] assets, int[] durations) {
        TileAnimationDefData data = new TileAnimationDefData();
        data.id = id;
        data.frameAssetIds = assets;
        data.frameDurationsMs = durations;
        return data;
    }

    private static final class Fixture {
        final World world;
        final TiledAnimationSystem system;
        final TileChunk chunk;

        Fixture(World world, TiledAnimationSystem system, TileChunk chunk) {
            this.world = world;
            this.system = system;
            this.chunk = chunk;
        }

        void dispose() {
            world.dispose();
        }
    }
}
