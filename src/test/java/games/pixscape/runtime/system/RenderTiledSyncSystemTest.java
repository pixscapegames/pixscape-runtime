package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RenderTiledSyncSystemTest {

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void hideShowCleanChunkSkipsFullRebuildAndPreservesSlotValidity() {
        Fixture fixture = createSingleChunkFixture();

        // Initial frame in view => FULL build.
        fixture.world.process();
        Assert.assertEquals("Initial build should resolve only non-empty tiles", 3, fixture.atlas.resolveCalls);
        Assert.assertEquals(2, fixture.drawList.size);
        Assert.assertEquals("Visible chunk slots should be published for draw-list extraction", 4, fixture.state.tiledVisibleSlotCount);

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        int slotValidA = chunk.slotFor(0, 0);
        int slotEmpty = chunk.slotFor(1, 0);
        int slotInvalidAtlas = chunk.slotFor(0, 1);
        int slotValidB = chunk.slotFor(1, 1);

        Assert.assertTrue(fixture.state.visible[slotValidA]);
        Assert.assertTrue(fixture.state.visible[slotValidB]);
        Assert.assertFalse(fixture.state.visible[slotEmpty]);
        Assert.assertFalse(fixture.state.visible[slotInvalidAtlas]);

        long sortKeyBeforeHide = fixture.state.sortKey[slotValidA];
        int textureBeforeHide = fixture.state.textureHandle[slotValidA];

        // Move camera out of chunk view => hide only.
        fixture.camera.position.set(96f, 16f, 0f);
        fixture.world.process();

        Assert.assertFalse(fixture.state.visible[slotValidA]);
        Assert.assertFalse(fixture.state.visible[slotValidB]);
        Assert.assertEquals("Out-of-view chunk should publish zero tiled candidates", 0, fixture.state.tiledVisibleSlotCount);
        Assert.assertEquals("Hidden tiled slots must not be extracted", 0, fixture.drawList.size);

        // Move camera back => re-activate without FULL rebuild.
        int resolveCallsBeforeShow = fixture.atlas.resolveCalls;
        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();

        Assert.assertEquals(
                "Clean chunk re-activation should not trigger atlas resolves",
                resolveCallsBeforeShow,
                fixture.atlas.resolveCalls
        );
        Assert.assertEquals("Only valid slots should be rendered after show", 2, fixture.drawList.size);

        Assert.assertTrue(fixture.state.visible[slotValidA]);
        Assert.assertTrue(fixture.state.visible[slotValidB]);
        Assert.assertFalse("Empty tile must stay hidden", fixture.state.visible[slotEmpty]);
        Assert.assertFalse("Invalid atlas tile must stay hidden", fixture.state.visible[slotInvalidAtlas]);

        Assert.assertEquals("Sort key should be preserved across hide/show", sortKeyBeforeHide, fixture.state.sortKey[slotValidA]);
        Assert.assertEquals("Texture handle should be preserved across hide/show", textureBeforeHide, fixture.state.textureHandle[slotValidA]);
    }

    @Test
    public void hiddenDirtyChunkUsesPartialThenFullPathsWhenVisibleAgain() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);

        // Hide chunk.
        fixture.camera.position.set(96f, 16f, 0f);
        fixture.world.process();

        // Dirty while hidden (PARTIAL path).
        fixture.map.setTile(0, 0, 3);
        Assert.assertEquals(TileChunk.DirtyState.PARTIAL, chunk.dirtyState);

        int callsBeforePartial = fixture.atlas.resolveCalls;
        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();

        Assert.assertTrue("PARTIAL path should resolve changed tile", fixture.atlas.resolveCalls > callsBeforePartial);
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, chunk.dirtyState);
        Assert.assertEquals(
                "Changed tile must be visible after partial refresh",
                203,
                fixture.state.textureHandle[chunk.slotFor(0, 0)]
        );

        // Hide again.
        fixture.camera.position.set(96f, 16f, 0f);
        fixture.world.process();

        // Force FULL while hidden.
        chunk.dirtyState = TileChunk.DirtyState.FULL;
        chunk.dirtyLocalIndices.clear();
        chunk.contentDirty = true;

        int callsBeforeFull = fixture.atlas.resolveCalls;
        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();

        Assert.assertTrue(
                "FULL path should resolve all non-empty tiles when chunk comes back",
                fixture.atlas.resolveCalls >= callsBeforeFull + 3
        );
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, chunk.dirtyState);
        Assert.assertEquals("Chunk should render valid tiles after FULL rebuild", 2, fixture.drawList.size);
    }

    @Test
    public void repeatedChunkBoundaryCrossingDoesNotCorruptStateOrTriggerExtraCleanRebuilds() {
        Fixture fixture = createTwoChunksFixture();

        // Build left chunk once.
        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();
        int callsAfterLeftBuild = fixture.atlas.resolveCalls;

        // Build right chunk once.
        fixture.camera.position.set(48f, 16f, 0f);
        fixture.world.process();
        int callsAfterRightBuild = fixture.atlas.resolveCalls;

        for (int i = 0; i < 6; i++) {
            fixture.camera.position.set(16f, 16f, 0f);
            fixture.world.process();
            Assert.assertEquals("Left chunk should expose exactly one valid tile", 1, fixture.drawList.size);

            fixture.camera.position.set(48f, 16f, 0f);
            fixture.world.process();
            Assert.assertEquals("Right chunk should expose exactly one valid tile", 1, fixture.drawList.size);
        }

        Assert.assertEquals(
                "Clean hide/show crossings should not trigger additional atlas resolves",
                callsAfterRightBuild,
                fixture.atlas.resolveCalls
        );
        Assert.assertTrue("Initial right build should add resolves", callsAfterRightBuild > callsAfterLeftBuild);
    }

    @Test
    public void largeMapSmallViewTraversesOnlyWindowChunks() {
        Fixture fixture = createLargeMapFixture();
        fixture.world.process();

        Assert.assertTrue("Windowed traversal must avoid full-map scan", fixture.tiledSync.getTestedChunkCount() < 16);
        Assert.assertEquals("Only one visible tile should be extracted", 1, fixture.drawList.size);
        Assert.assertEquals(1, fixture.tiledSync.getVisibleChunkCount());
    }

    @Test
    public void hiddenPartialUpdateThatBecomesEmptyOrInvalidDoesNotLeaveStaleVisibleSlots() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        int slotValidA = chunk.slotFor(0, 0);
        int slotValidB = chunk.slotFor(1, 1);

        Assert.assertTrue(fixture.state.visible[slotValidA]);
        Assert.assertTrue(fixture.state.visible[slotValidB]);
        Assert.assertEquals(2, fixture.drawList.size);

        // Hide the chunk.
        fixture.camera.position.set(96f, 16f, 0f);
        fixture.world.process();

        // Dirty while hidden: valid -> empty, valid -> invalid atlas.
        fixture.map.setTile(0, 0, 0);
        fixture.map.setTile(1, 1, 99);
        Assert.assertEquals(TileChunk.DirtyState.PARTIAL, chunk.dirtyState);

        // Show chunk again: partial update must clear both slots without stale visibility.
        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();

        Assert.assertFalse("Slot changed to empty must remain hidden", fixture.state.visible[slotValidA]);
        Assert.assertFalse("Slot changed to invalid atlas must remain hidden", fixture.state.visible[slotValidB]);
        Assert.assertFalse("Slot changed to empty must be disabled", fixture.state.enabled[slotValidA]);
        Assert.assertFalse("Slot changed to invalid atlas must be disabled", fixture.state.enabled[slotValidB]);
        Assert.assertEquals("No stale tiled slot must be extracted", 0, fixture.drawList.size);
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, chunk.dirtyState);
    }

    @Test
    public void chunksLeavingPreviousWindowAreExplicitlyHidden() {
        Fixture fixture = createTwoChunksFixture();
        TileChunk left = findChunk(fixture.map, 0, 0);
        TileChunk right = findChunk(fixture.map, 1, 0);

        fixture.camera.position.set(16f, 16f, 0f);
        fixture.world.process();
        Assert.assertTrue(left.visibleLastFrame);
        Assert.assertFalse(right.visibleLastFrame);

        fixture.camera.position.set(48f, 16f, 0f);
        fixture.world.process();

        Assert.assertFalse("Left chunk should be hidden when leaving previous window", left.visibleLastFrame);
        Assert.assertTrue("Right chunk should now be visible", right.visibleLastFrame);
        Assert.assertTrue("Hide metric should count leaving chunk", fixture.tiledSync.getHiddenChunkCount() > 0);
    }

    @Test
    public void chunkInsideConservativeWindowButFailingFinalOverlapIsHiddenImmediately() {
        Fixture fixture = createTwoChunksFixture();
        TileChunk right = findChunk(fixture.map, 1, 0);
        int rightSlot = right.slotFor(0, 0);

        // Frame N: right chunk is truly visible.
        fixture.camera.position.set(48f, 16f, 0f);
        fixture.world.process();
        Assert.assertTrue(right.visibleLastFrame);
        Assert.assertTrue(fixture.state.visible[rightSlot]);
        Assert.assertEquals(1, fixture.drawList.size);

        // Frame N+1: right chunk remains in conservative window (due expansion),
        // but final overlap check should fail and force immediate hide.
        fixture.camera.position.set(8f, 16f, 0f);
        fixture.world.process();

        Assert.assertFalse("Chunk must be hidden when final overlap fails", right.visibleLastFrame);
        Assert.assertFalse("No stale visible slot should remain", fixture.state.visible[rightSlot]);
        Assert.assertFalse("No stale enabled slot should remain", fixture.state.enabled[rightSlot]);
        Assert.assertTrue("Hidden counter should include this case", fixture.tiledSync.getHiddenChunkCount() > 0);
        Assert.assertEquals("Draw list must not include stale right chunk tile", 1, fixture.drawList.size);
    }

    @Test
    public void orthographicWindowClampingKeepsEdgeChunksVisible() {
        Fixture fixture = createLargeMapFixture();
        fixture.camera.position.set(2f, 2f, 0f);
        fixture.world.process();
        Assert.assertEquals("Top-left edge chunk tile should render", 1, fixture.drawList.size);

        fixture.camera.position.set(158f, 158f, 0f);
        fixture.world.process();
        Assert.assertEquals("Bottom-right edge chunk tile should render", 1, fixture.drawList.size);
        Assert.assertTrue("Edge window should remain clamped and non-empty", fixture.tiledSync.getTestedChunkCount() >= 1);
    }

    @Test
    public void isometricWindowTraversalUsesConservativeWindowAndOverlapSafety() {
        Fixture fixture = createIsometricFixture();
        fixture.world.process();

        Assert.assertEquals("Exactly one center diamond tile should be visible", 1, fixture.drawList.size);
        Assert.assertTrue("ISO path should test only a small window", fixture.tiledSync.getTestedChunkCount() <= 9);
        Assert.assertTrue("At least one chunk should be visible", fixture.tiledSync.getVisibleChunkCount() >= 1);
    }

    @Test
    public void multiLayerWindowsRemainIsolated() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        RenderStateSOA state = new RenderStateSOA(512);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;
        layerState.enabled[1] = true;

        DrawList drawList = new DrawList(512);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(camera, state, atlas, 7, 64, 512);

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync, new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64))
                .build());

        Entity layerA = world.createEntity();
        LayerComponent layerCompA = layerA.edit().create(LayerComponent.class);
        layerCompA.type = LayerComponent.TYPE_TILED;
        layerCompA.layerIndex = 0;
        TiledLayerComponent tiledA = layerA.edit().create(TiledLayerComponent.class);
        tiledA.atlasTag = "main";
        tiledA.data = new TiledMapLayerData(4, 2, 16, 16, 2);
        tiledA.data.initSlotRange(96, 160);
        tiledA.data.setTile(0, 0, 1);

        Entity layerB = world.createEntity();
        LayerComponent layerCompB = layerB.edit().create(LayerComponent.class);
        layerCompB.type = LayerComponent.TYPE_TILED;
        layerCompB.layerIndex = 1;
        TiledLayerComponent tiledB = layerB.edit().create(TiledLayerComponent.class);
        tiledB.atlasTag = "main";
        tiledB.data = new TiledMapLayerData(4, 2, 16, 16, 2);
        tiledB.data.originX = 128f;
        tiledB.data.initSlotRange(192, 256);
        tiledB.data.setTile(2, 0, 2);

        // First camera position: layer A visible, layer B offscreen
        camera.position.set(16f, 16f, 0f);
        world.process();

        TileChunk aChunk = findChunk(tiledA.data, 0, 0);
        TileChunk bChunk = findChunk(tiledB.data, 1, 0);

        Assert.assertTrue(aChunk.visibleLastFrame);
        Assert.assertFalse(bChunk.visibleLastFrame);

        // Second camera position: move toward layer B
        camera.position.set(176f, 16f, 0f);
        world.process();

        Assert.assertFalse("Layer A chunk should now be hidden", aChunk.visibleLastFrame);
        Assert.assertTrue("Layer B chunk should now be visible", bChunk.visibleLastFrame);
    }

    private static Fixture createSingleChunkFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        RenderStateSOA state = new RenderStateSOA(256);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();

        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(camera, state, atlas, 7, 64, 256);

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        tiledSync,
                        new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64)
                )
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;

        TiledLayerComponent tiled = layerEntity.edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(2, 2, 16, 16, 2);
        map.initSlotRange(96, 112);
        map.setTile(0, 0, 1);
        map.setTile(1, 0, 0);
        map.setTile(0, 1, 99);
        map.setTile(1, 1, 2);
        tiled.data = map;

        return new Fixture(world, camera, state, drawList, atlas, map, tiledSync);
    }

    private static Fixture createTwoChunksFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        RenderStateSOA state = new RenderStateSOA(256);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();

        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(camera, state, atlas, 7, 64, 256);

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        tiledSync,
                        new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64)
                )
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;

        TiledLayerComponent tiled = layerEntity.edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(4, 2, 16, 16, 2);
        map.initSlotRange(96, 128);

        // Left chunk has a single valid tile.
        map.setTile(0, 0, 1);

        // Right chunk has one valid and one invalid tile.
        map.setTile(2, 0, 2);
        map.setTile(3, 0, 99);

        tiled.data = map;

        return new Fixture(world, camera, state, drawList, atlas, map, tiledSync);
    }

    private static Fixture createLargeMapFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        RenderStateSOA state = new RenderStateSOA(4096);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(4096);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(camera, state, atlas, 7, 64, 4096);

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync, new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;

        TiledLayerComponent tiled = layerEntity.edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(10, 10, 16, 16, 2);
        map.initSlotRange(96, 4096);
        map.setTile(0, 0, 1);
        map.setTile(9, 9, 2);
        tiled.data = map;

        return new Fixture(world, camera, state, drawList, atlas, map, tiledSync);
    }

    private static Fixture createIsometricFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        RenderStateSOA state = new RenderStateSOA(512);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(512);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(camera, state, atlas, 7, 64, 512);

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync, new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;
        TiledLayerComponent tiled = layerEntity.edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";
        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 16, 2,
                games.pixscape.runtime.loading.SceneMetaRuntime.TiledProjection.ISO);
        map.initSlotRange(96, 512);
        map.setTile(1, 1, 1);
        tiled.data = map;

        return new Fixture(world, camera, state, drawList, atlas, map, tiledSync);
    }

    private static TileChunk findChunk(TiledMapLayerData map, int cx, int cy) {
        IntMap.Values<TileChunk> values = map.getChunks();
        while (values.hasNext()) {
            TileChunk chunk = values.next();
            if (chunk.chunkX == cx && chunk.chunkY == cy) {
                return chunk;
            }
        }
        throw new AssertionError("Chunk not found: (" + cx + "," + cy + ")");
    }

    private static final class Fixture {
        final World world;
        final OrthographicCamera camera;
        final RenderStateSOA state;
        final DrawList drawList;
        final CountingAtlasRuntimeService atlas;
        final TiledMapLayerData map;
        final RenderTiledSyncSystem tiledSync;

        Fixture(World world,
                OrthographicCamera camera,
                RenderStateSOA state,
                DrawList drawList,
                CountingAtlasRuntimeService atlas,
                TiledMapLayerData map,
                RenderTiledSyncSystem tiledSync) {
            this.world = world;
            this.camera = camera;
            this.state = state;
            this.drawList = drawList;
            this.atlas = atlas;
            this.map = map;
            this.tiledSync = tiledSync;
        }
    }

    private static final class CountingAtlasRuntimeService extends AtlasRuntimeService {
        int resolveCalls = 0;

        @Override
        public CachedRegion resolveCached(int assetId, String tag) {
            resolveCalls++;
            if (assetId == 1 || assetId == 2 || assetId == 3) {
                return new CachedRegion(
                        "tile-" + assetId,
                        0f,
                        0f,
                        1f,
                        1f,
                        200 + assetId,
                        16,
                        16
                );
            }
            return null;
        }
    }
}
