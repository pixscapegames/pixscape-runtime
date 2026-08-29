package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.profile.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RenderTiledSyncSystemTest {

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void runtimeAvailabilityCompilesAllPersistentChunksBeforeFirstPan() {
        Fixture fixture = createTwoChunksFixture();
        fixture.world.process();
        fixture.map.markAllChunksContentDirty();

        fixture.tiledSync.prepareRuntimeAvailability();

        Assert.assertEquals(2, fixture.tiledSync.preparedPersistentChunkCount());
        for (IntMap.Values<TileChunk> chunks =
                fixture.map.getChunks(); chunks.hasNext(); ) {
            Assert.assertEquals(TileChunk.DirtyState.CLEAN,
                    chunks.next().dirtyState);
        }
        int compilations = fixture.tiledSync.persistentChunkCompilationCount();

        fixture.camera.position.set(48f, 16f, 0f);
        fixture.world.process();
        Assert.assertEquals(compilations,
                fixture.tiledSync.persistentChunkCompilationCount());
    }

    @Test
    public void visibleRefsFromMultipleChunksPublishOneContiguousMapGroup() {
        Fixture fixture = createTwoChunksFixture();
        fixture.camera.viewportWidth = 64f;
        fixture.camera.position.set(32f, 16f, 0f);
        fixture.camera.update();

        fixture.world.process();

        Assert.assertEquals(2, fixture.drawList.size);
        Assert.assertEquals(1, fixture.tiledState.getVisibleMapCount());
        Assert.assertEquals(0, fixture.tiledState.visibleMapRefStart(0));
        Assert.assertEquals(2, fixture.tiledState.visibleMapRefCount(0));
        Assert.assertEquals(onlyMapEntity(fixture.world),
                fixture.tiledState.visibleMapEntityId(0));
    }

    @Test
    public void mapZChangeUpdatesOnlyGroupOrderAndDoesNotRecompileChunks() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.process();
        int mapEntity = onlyMapEntity(fixture.world);
        int compilations = fixture.tiledSync.persistentChunkCompilationCount();
        int firstRef = fixture.tiledState.getVisibleRefs()[0];
        long internalKey = fixture.tiledState.sortKey[firstRef];

        fixture.world.getMapper(EntityIndexComponent.class).get(mapEntity).zIndex = 25;
        fixture.world.process();

        Assert.assertEquals(1, fixture.tiledState.getVisibleMapCount());
        Assert.assertEquals(mapEntity, fixture.tiledState.visibleMapEntityId(0));
        Assert.assertEquals(25, fixture.tiledState.visibleMapZIndex(0));
        Assert.assertEquals(25, SortKey64.unpackZOrdered(
                fixture.tiledState.visibleMapCompositionKey(0)));
        Assert.assertEquals(internalKey, fixture.tiledState.sortKey[firstRef]);
        Assert.assertEquals(compilations, fixture.tiledSync.persistentChunkCompilationCount());
    }

    @Test
    public void mapCompositionAcceptsExactZBoundaries() {
        Fixture fixture = createSingleChunkFixture();
        int mapEntity = onlyMapEntity(fixture.world);
        EntityIndexComponent index = fixture.world.getMapper(EntityIndexComponent.class).get(mapEntity);

        index.zIndex = SortKey64.MIN_Z;
        fixture.world.process();
        Assert.assertEquals(SortKey64.MIN_Z, fixture.tiledState.visibleMapZIndex(0));

        index.zIndex = SortKey64.MAX_Z;
        fixture.world.process();
        Assert.assertEquals(SortKey64.MAX_Z, fixture.tiledState.visibleMapZIndex(0));
    }

    @Test(expected = IllegalStateException.class)
    public void mapCompositionRejectsZBelowSupportedRange() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.getMapper(EntityIndexComponent.class)
                .get(onlyMapEntity(fixture.world)).zIndex = SortKey64.MIN_Z - 1;
        fixture.world.process();
    }

    @Test(expected = IllegalStateException.class)
    public void mapCompositionRejectsZAboveSupportedRange() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.getMapper(EntityIndexComponent.class)
                .get(onlyMapEntity(fixture.world)).zIndex = SortKey64.MAX_Z + 1;
        fixture.world.process();
    }

    @Test
    public void hideShowCleanChunkSkipsFullRebuildAndPreservesSlotValidity() {
        Fixture fixture = createSingleChunkFixture();

        // Initial frame in view => FULL build.
        fixture.world.process();
        Assert.assertEquals("Initial build should resolve non-empty tiles for padding and slot build", 6, fixture.atlas.resolveCalls);
        Assert.assertEquals(2, fixture.drawList.size);
        Assert.assertEquals("Only renderable tiled refs should be published for draw-list extraction", 2, fixture.tiledState.getVisibleRefCount());
        Assert.assertEquals(fixture.tiledState.getVisibleRefCount(), fixture.stats.tiledRenderableRefsVisible);
        Assert.assertEquals(fixture.tiledState.getVisibleRefCount(), fixture.stats.buildDrawListScannedTiledSlots);

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        int refValidA = refFor(fixture.map, 0, 0);
        int refEmpty = refFor(fixture.map, 1, 0);
        int refInvalidAtlas = refFor(fixture.map, 0, 1);
        int refValidB = refFor(fixture.map, 1, 1);

        Assert.assertTrue(fixture.tiledState.visible[refValidA]);
        Assert.assertTrue(fixture.tiledState.visible[refValidB]);
        Assert.assertFalse(fixture.tiledState.visible[refEmpty]);
        Assert.assertFalse(fixture.tiledState.visible[refInvalidAtlas]);

        long sortKeyBeforeHide = fixture.tiledState.sortKey[refValidA];
        int textureBeforeHide = fixture.tiledState.textureHandle[refValidA];

        // Move camera out of chunk view => hide only.
        fixture.camera.position.set(96f, 16f, 0f);
        fixture.world.process();

        Assert.assertEquals("Out-of-view chunk should publish zero tiled candidates", 0, fixture.tiledState.getVisibleRefCount());
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

        Assert.assertTrue(fixture.tiledState.visible[refValidA]);
        Assert.assertTrue(fixture.tiledState.visible[refValidB]);
        Assert.assertFalse("Empty tile must stay hidden", fixture.tiledState.visible[refEmpty]);
        Assert.assertFalse("Invalid atlas tile must stay hidden", fixture.tiledState.visible[refInvalidAtlas]);

        Assert.assertEquals("Sort key should be preserved across hide/show", sortKeyBeforeHide, fixture.tiledState.sortKey[refValidA]);
        Assert.assertEquals("Texture handle should be preserved across hide/show", textureBeforeHide, fixture.tiledState.textureHandle[refValidA]);
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
                fixture.tiledState.textureHandle[refFor(fixture.map, 0, 0)]
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
        Assert.assertTrue("Initial traversal should resolve at least the visible left tile", callsAfterLeftBuild > 0);
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
    public void partialChunkPublishesOnlyOverlappingRenderableRefsAndExactStats() {
        Fixture fixture = createWideSingleChunkFixture();

        fixture.camera.position.set(8f, 8f, 0f);
        fixture.world.process();

        Assert.assertEquals(1, fixture.drawList.size);
        Assert.assertEquals(1, fixture.tiledState.getVisibleRefCount());
        Assert.assertTrue(containsVisibleRef(fixture.tiledState, refFor(fixture.map, 0, 0)));
        Assert.assertFalse(containsVisibleRef(fixture.tiledState, refFor(fixture.map, 3, 0)));

        Assert.assertEquals(1, fixture.stats.tiledChunksTested);
        Assert.assertEquals(0, fixture.stats.tiledChunksOutside);
        Assert.assertEquals(0, fixture.stats.tiledChunksFullyInside);
        Assert.assertEquals(1, fixture.stats.tiledChunksPartial);
        Assert.assertEquals(2, fixture.stats.tiledRenderableRefsConsidered);
        Assert.assertEquals(1, fixture.stats.tiledRenderableRefsVisible);
        Assert.assertEquals(1, fixture.stats.tiledRenderableRefsCulled);
        Assert.assertEquals(fixture.tiledState.getVisibleRefCount(), fixture.stats.buildDrawListScannedTiledSlots);
    }

    @Test
    public void emptyChunkHasNoVisualBoundsAndCountsAsOutside() {
        Fixture fixture = createEmptySingleChunkFixture();

        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        Assert.assertFalse(chunk.hasVisualBounds);
        Assert.assertEquals(0, chunk.getRenderableRefCount());
        Assert.assertEquals(0, fixture.tiledState.getVisibleRefCount());
        Assert.assertEquals(0, fixture.drawList.size);
        Assert.assertEquals(1, fixture.stats.tiledChunksOutside);
    }

    @Test
    public void removingExtremeTileShrinksRenderableCacheAndVisualBounds() {
        Fixture fixture = createWideSingleChunkFixture();

        fixture.camera.position.set(32f, 8f, 0f);
        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        Assert.assertEquals(2, chunk.getRenderableRefCount());
        Assert.assertEquals(0f, chunk.visualMinX, 0.0001f);
        Assert.assertEquals(64f, chunk.visualMaxX, 0.0001f);

        fixture.map.setTile(3, 0, 0);
        fixture.world.process();

        Assert.assertEquals(1, chunk.getRenderableRefCount());
        Assert.assertEquals(0f, chunk.visualMinX, 0.0001f);
        Assert.assertEquals(16f, chunk.visualMaxX, 0.0001f);
    }

    @Test
    public void repeatedPartialDirtyDoesNotDuplicateRenderableLocalIndices() {
        Fixture fixture = createWideSingleChunkFixture();
        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        chunk.markLocalDirty(0);
        chunk.markLocalDirty(0);
        fixture.world.process();

        Assert.assertEquals(2, chunk.getRenderableRefCount());
        Assert.assertEquals(0, chunk.renderableLocalIndices.get(0));
        Assert.assertEquals(3, chunk.renderableLocalIndices.get(1));
    }

    @Test
    public void hiddenPartialUpdateThatBecomesEmptyOrInvalidDoesNotLeaveStaleVisibleSlots() {
        Fixture fixture = createSingleChunkFixture();
        fixture.world.process();

        TileChunk chunk = findChunk(fixture.map, 0, 0);
        int refValidA = refFor(fixture.map, 0, 0);
        int refValidB = refFor(fixture.map, 1, 1);

        Assert.assertTrue(fixture.tiledState.visible[refValidA]);
        Assert.assertTrue(fixture.tiledState.visible[refValidB]);
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

        Assert.assertFalse("Tile changed to empty must remain hidden", fixture.tiledState.visible[refValidA]);
        Assert.assertFalse("Tile changed to invalid atlas must remain hidden", fixture.tiledState.visible[refValidB]);
        Assert.assertFalse("Tile changed to empty must be disabled", fixture.tiledState.enabled[refValidA]);
        Assert.assertFalse("Tile changed to invalid atlas must be disabled", fixture.tiledState.enabled[refValidB]);
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
        int rightRef;

        // Frame N: right chunk is truly visible.
        fixture.camera.position.set(48f, 16f, 0f);
        fixture.world.process();
        rightRef = refFor(fixture.map, 2, 0);
        Assert.assertTrue(right.visibleLastFrame);
        Assert.assertTrue(fixture.tiledState.visible[rightRef]);
        Assert.assertEquals(1, fixture.drawList.size);

        // Frame N+1: right chunk remains in conservative window (due expansion),
        // but final overlap check should fail and force immediate hide.
        fixture.camera.position.set(8f, 16f, 0f);
        fixture.world.process();

        Assert.assertFalse("Chunk must be hidden when final overlap fails", right.visibleLastFrame);
        Assert.assertFalse("No stale right chunk ref should be published", containsVisibleRef(fixture.tiledState, rightRef));
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
    public void isometricWindowIncludesTallSpritesNearViewportTopEdge() {
        Fixture fixture = createTallIsometricFixture(
                1920f,
                1000f,
                -1240f,
                600f,
                32,
                41
        );

        fixture.world.process();

        Assert.assertEquals("Tall ISO tile entering from the top edge should render immediately", 1, fixture.drawList.size);
        Assert.assertTrue("The owning chunk must be considered visible", fixture.tiledSync.getVisibleChunkCount() >= 1);
    }

    @Test
    public void isometricFinalOverlapAllowsTallSpritesExtendingPastChunkBounds() {
        Fixture fixture = createTallIsometricFixture(
                62f,
                8f,
                32f,
                -92f,
                0,
                0
        );

        fixture.world.process();

        Assert.assertEquals("Tall ISO tile extending past chunk bounds should not be culled", 1, fixture.drawList.size);
        Assert.assertTrue("The padded final overlap should keep the chunk visible", fixture.tiledSync.getVisibleChunkCount() >= 1);
    }

    @Test
    public void multiLayerWindowsRemainIsolated() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;
        layerState.enabled[1] = true;

        DrawList drawList = new DrawList(512);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1,
                        2
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList)
                )
                .build());

        Entity layerA = world.createEntity();
        LayerComponent layerCompA = layerA.edit().create(LayerComponent.class);
        layerCompA.layerIndex = 0;
        TiledLayerComponent tiledA = createTiledMapEntity(world, layerCompA.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiledA.atlasTag = "main";
        tiledA.data = new TiledMapLayerData(4, 2, 16, 16, 2);
        tiledA.data.setTile(0, 0, 1);

        Entity layerB = world.createEntity();
        LayerComponent layerCompB = layerB.edit().create(LayerComponent.class);
        layerCompB.layerIndex = 1;
        TiledLayerComponent tiledB = createTiledMapEntity(world, layerCompB.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiledB.atlasTag = "main";
        tiledB.data = new TiledMapLayerData(4, 2, 16, 16, 2);
        tiledB.data.originX = 128f;
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

    @Test
    public void missingProfileDisablesTileWithoutCrash() {
        Fixture fixture = createProfilePlacementFixture(
                RuntimeTilesetProfiles.empty(),
                games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                64,
                32,
                64,
                32,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        int ref = refFor(fixture.map, 0, 0);
        Assert.assertFalse(fixture.tiledState.enabled[ref]);
        Assert.assertFalse(fixture.tiledState.visible[ref]);
    }

    @Test
    public void topCenterProfileMatchesDefaultAnchorPlacementInRenderSync() {
        RuntimeTilesetProfiles profiles = profilesWith(profile(
                1,
                RuntimeTilesetAnchor.TOP_CENTER,
                64,
                32,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ORTHO
        ));
        Fixture fixture = createProfilePlacementFixture(
                profiles,
                games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                64,
                32,
                64,
                32,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        float[] expected = new float[8];
        TileProfilePlacement.buildTopCenterDefaultSpriteQuad(100f, 200f, 64, 32, 64, 32, expected);
        assertRefQuad(fixture.tiledState, refFor(fixture.map, 0, 0), expected);
    }

    @Test
    public void tallIsometricTopCenterProfileUsesProfilePlacement() {
        RuntimeTilesetProfile profile = profile(
                1,
                RuntimeTilesetAnchor.TOP_CENTER,
                256,
                128,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(profile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        float[] expected = expectedProfileQuad(fixture.map, profile, 256, 512);
        assertRefQuad(fixture.tiledState, refFor(fixture.map, 0, 0), expected);
        assertVisualPadding(fixture.map, 0f, 0f, 0f, 384f);
    }

    @Test
    public void bottomCenterProfileChangesRenderedPlacement() {
        RuntimeTilesetProfile profile = profile(
                1,
                RuntimeTilesetAnchor.BOTTOM_CENTER,
                256,
                128,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(profile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        float[] topCenterDefault = new float[8];
        TileProfilePlacement.buildTopCenterDefaultSpriteQuad(100f, 200f, 256, 128, 256, 512, topCenterDefault);
        float[] expected = expectedProfileQuad(fixture.map, profile, 256, 512);

        assertRefQuad(fixture.tiledState, refFor(fixture.map, 0, 0), expected);
        Assert.assertNotEquals("bottom-center should move the sprite away from top-center default", topCenterDefault[1], expected[1], 0.0001f);
        assertVisualPadding(fixture.map, 0f, 0f, 384f, 0f);
    }

    @Test
    public void profileOffsetAffectsRenderedQuad() {
        RuntimeTilesetProfile profile = profile(
                1,
                RuntimeTilesetAnchor.BOTTOM_CENTER,
                256,
                128,
                10,
                -20,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(profile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        float[] expected = expectedProfileQuad(fixture.map, profile, 256, 512);
        assertRefQuad(fixture.tiledState, refFor(fixture.map, 0, 0), expected);
        assertVisualPadding(fixture.map, 0f, 10f, 364f, 20f);
    }

    @Test
    public void missingProfileDoesNotContributeVisualPadding() {
        RuntimeTilesetProfile unrelatedProfile = profile(
                2,
                RuntimeTilesetAnchor.BOTTOM_CENTER,
                256,
                128,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(unrelatedProfile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.NONE
        );

        fixture.world.process();

        int ref = refFor(fixture.map, 0, 0);
        Assert.assertFalse(fixture.tiledState.enabled[ref]);
        Assert.assertFalse(fixture.tiledState.visible[ref]);
        assertVisualPadding(fixture.map, 0f, 0f, 0f, 0f);
    }

    @Test
    public void profileVisualPaddingKeepsChunkVisibleWhenViewOnlyOverlapsSprite() {
        RuntimeTilesetProfile profile = profile(
                1,
                RuntimeTilesetAnchor.TOP_CENTER,
                256,
                128,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(profile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.NONE,
                32f,
                32f,
                228f,
                -100f
        );

        fixture.world.process();

        Assert.assertEquals("View overlapping only the sprite should still render the tile", 1, fixture.drawList.size);
        Assert.assertTrue("Profile-expanded overlap should keep the owning chunk visible", fixture.tiledSync.getVisibleChunkCount() >= 1);
        assertVisualPadding(fixture.map, 0f, 0f, 0f, 384f);
    }

    @Test
    public void transformFlagsStillApplyAfterProfilePlacement() {
        RuntimeTilesetProfile profile = profile(
                1,
                RuntimeTilesetAnchor.BOTTOM_CENTER,
                256,
                128,
                0,
                0,
                games.pixscape.runtime.tiled.TiledProjection.ISO
        );
        Fixture fixture = createProfilePlacementFixture(
                profilesWith(profile),
                games.pixscape.runtime.tiled.TiledProjection.ISO,
                256,
                128,
                256,
                512,
                1,
                TileTransformFlags.FLIP_H
        );

        fixture.world.process();

        float[] expected = new float[8];
        games.pixscape.runtime.tiled.TileQuadTransforms.buildSpriteQuad(
                fixture.map,
                0,
                0,
                256,
                512,
                profile,
                TileTransformFlags.FLIP_H,
                expected
        );
        assertRefQuad(fixture.tiledState, refFor(fixture.map, 0, 0), expected);
    }

    private static Fixture createSingleChunkFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();

        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1,
                        2,
                        3
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList)
                )
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(2, 2, 16, 16, 2);
        map.setTile(0, 0, 1);
        map.setTile(1, 0, 0);
        map.setTile(0, 1, 99);
        map.setTile(1, 1, 2);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createTwoChunksFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();

        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1,
                        2
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1,-1),
                        new RenderSortSystem(null, tiledState, drawList)
                )
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(4, 2, 16, 16, 2);

        // Left chunk has a single valid tile.
        map.setTile(0, 0, 1);

        // Right chunk has one valid and one invalid tile.
        map.setTile(2, 0, 2);
        map.setTile(3, 0, 99);

        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createLargeMapFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(4096);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1,
                        2
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(10, 10, 16, 16, 2);
        map.setTile(0, 0, 1);
        map.setTile(9, 9, 2);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createWideSingleChunkFixture() {
        OrthographicCamera camera = new OrthographicCamera(16f, 16f);
        camera.position.set(8f, 8f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1,
                        2
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(4, 1, 16, 16, 4);
        map.setTile(0, 0, 1);
        map.setTile(3, 0, 2);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createEmptySingleChunkFixture() {
        OrthographicCamera camera = new OrthographicCamera(16f, 16f);
        camera.position.set(8f, 8f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ORTHO,
                        1
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";
        tiled.data = new TiledMapLayerData(2, 2, 16, 16, 2);

        return new Fixture(world, camera, tiledState, drawList, atlas, tiled.data, tiledSync, stats);
    }

    private static Fixture createIsometricFixture() {
        OrthographicCamera camera = new OrthographicCamera(32f, 32f);
        camera.position.set(16f, 16f, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(512);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new CountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        16,
                        16,
                        games.pixscape.runtime.tiled.TiledProjection.ISO,
                        1
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;
        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";
        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 16, 2,
                games.pixscape.runtime.tiled.TiledProjection.ISO);
        map.setTile(1, 1, 1);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createTallIsometricFixture(float viewportWidth,
                                                      float viewportHeight,
                                                      float cameraX,
                                                      float cameraY,
                                                      int tileX,
                                                      int tileY) {
        OrthographicCamera camera = new OrthographicCamera(viewportWidth, viewportHeight);
        camera.position.set(cameraX, cameraY, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(20000);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new TallCountingAtlasRuntimeService();
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                topCenterProfiles(
                        64,
                        32,
                        games.pixscape.runtime.tiled.TiledProjection.ISO,
                        1
                )
        );

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";
        TiledMapLayerData map = new TiledMapLayerData(100, 100, 64, 32, 16,
                games.pixscape.runtime.tiled.TiledProjection.ISO);
        map.setTile(tileX, tileY, 1);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
    }

    private static Fixture createProfilePlacementFixture(RuntimeTilesetProfiles profiles,
                                                         games.pixscape.runtime.tiled.TiledProjection projection,
                                                         int cellWidth,
                                                         int cellHeight,
                                                         int spriteWidth,
                                                         int spriteHeight,
                                                         int tileId,
                                                         byte transformFlags) {
        return createProfilePlacementFixture(
                profiles,
                projection,
                cellWidth,
                cellHeight,
                spriteWidth,
                spriteHeight,
                tileId,
                transformFlags,
                1024f,
                1024f,
                228f,
                264f
        );
    }

    private static Fixture createProfilePlacementFixture(RuntimeTilesetProfiles profiles,
                                                         games.pixscape.runtime.tiled.TiledProjection projection,
                                                         int cellWidth,
                                                         int cellHeight,
                                                         int spriteWidth,
                                                         int spriteHeight,
                                                         int tileId,
                                                         byte transformFlags,
                                                         float viewportWidth,
                                                         float viewportHeight,
                                                         float cameraX,
                                                         float cameraY) {
        OrthographicCamera camera = new OrthographicCamera(viewportWidth, viewportHeight);
        camera.position.set(cameraX, cameraY, 0f);

        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(4);
        layerState.enabled[0] = true;

        DrawList drawList = new DrawList(512);
        RenderStats stats = new RenderStats();
        CountingAtlasRuntimeService atlas = new SizedCountingAtlasRuntimeService(spriteWidth, spriteHeight);
        RenderTiledSyncSystem tiledSync = new RenderTiledSyncSystem(
                camera,
                tiledState,
                atlas,
                7,
                null,
                profiles);

        World world = new World(new WorldConfigurationBuilder()
                .with(tiledSync,
                        new RenderBuildDrawListSystem(new DynamicEntityRenderState(64), tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(null, tiledState, drawList))
                .build());

        Entity layerEntity = world.createEntity();
        LayerComponent layer = layerEntity.edit().create(LayerComponent.class);
        layer.layerIndex = 0;

        TiledLayerComponent tiled = createTiledMapEntity(world, layer.layerIndex)
                .edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";

        TiledMapLayerData map = new TiledMapLayerData(1, 1, cellWidth, cellHeight, 1, projection);
        map.originX = 100f;
        map.originY = 200f;
        map.setTile(0, 0, tileId, transformFlags);
        tiled.data = map;

        return new Fixture(world, camera, tiledState, drawList, atlas, map, tiledSync, stats);
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

    private static RuntimeTilesetProfile profile(int tileAssetId,
                                                 RuntimeTilesetAnchor anchor,
                                                 int referenceCellWidth,
                                                 int referenceCellHeight,
                                                 int offsetX,
                                                 int offsetY,
                                                 games.pixscape.runtime.tiled.TiledProjection projection) {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = 1;
        profile.referenceCellWidth = referenceCellWidth;
        profile.referenceCellHeight = referenceCellHeight;
        profile.projection = projection;
        profile.anchor = anchor;
        profile.offsetX = offsetX;
        profile.offsetY = offsetY;
        profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
        profile.tileAssetIds = new int[]{tileAssetId};
        return profile;
    }

    private static RuntimeTilesetProfiles profilesWith(RuntimeTilesetProfile profile) {
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        profiles.add(profile);
        return profiles;
    }

    private static RuntimeTilesetProfiles topCenterProfiles(int referenceCellWidth,
                                                            int referenceCellHeight,
                                                            games.pixscape.runtime.tiled.TiledProjection projection,
                                                            int... tileAssetIds) {
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        for (int tileAssetId : tileAssetIds) {
            profiles.add(profile(
                    tileAssetId,
                    RuntimeTilesetAnchor.TOP_CENTER,
                    referenceCellWidth,
                    referenceCellHeight,
                    0,
                    0,
                    projection
            ));
        }
        return profiles;
    }

    private static float[] expectedProfileQuad(TiledMapLayerData map,
                                               RuntimeTilesetProfile profile,
                                               int spriteWidth,
                                               int spriteHeight) {
        float[] expected = new float[8];
        TileProfilePlacement.buildSpriteQuad(
                map.tileToWorldX(0, 0),
                map.tileToWorldY(0, 0),
                map.tileWidth,
                map.tileHeight,
                spriteWidth,
                spriteHeight,
                profile,
                expected
        );
        return expected;
    }

    private static int refFor(TiledMapLayerData map, int gx, int gy) {
        int ref = map.tiledRenderRefForTile(gx, gy);
        Assert.assertTrue("tile should have a render ref", ref >= 0);
        return ref;
    }

    private static int onlyMapEntity(World world) {
        IntBag maps = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)).getEntities();
        Assert.assertEquals(1, maps.size());
        return maps.get(0);
    }

    private static Entity createTiledMapEntity(World world, int layerIndex) {
        Entity mapEntity = world.createEntity();
        EntityIndexComponent index = mapEntity.edit().create(EntityIndexComponent.class);
        index.layerIndex = layerIndex;
        index.zIndex = 0;
        return mapEntity;
    }

    private static boolean containsVisibleRef(TiledMapRenderState state, int ref) {
        int[] refs = state.getVisibleRefs();
        for (int i = 0; i < state.getVisibleRefCount(); i++) {
            if (refs[i] == ref) return true;
        }
        return false;
    }

    private static void assertRefQuad(TiledMapRenderState state, int ref, float[] expected) {
        Assert.assertTrue("ref should be enabled", state.enabled[ref]);
        Assert.assertTrue("ref should be visible", state.visible[ref]);
        Assert.assertEquals(expected[0], state.x1[ref], 0.0001f);
        Assert.assertEquals(expected[1], state.y1[ref], 0.0001f);
        Assert.assertEquals(expected[2], state.x2[ref], 0.0001f);
        Assert.assertEquals(expected[3], state.y2[ref], 0.0001f);
        Assert.assertEquals(expected[4], state.x3[ref], 0.0001f);
        Assert.assertEquals(expected[5], state.y3[ref], 0.0001f);
        Assert.assertEquals(expected[6], state.x4[ref], 0.0001f);
        Assert.assertEquals(expected[7], state.y4[ref], 0.0001f);
    }

    private static void assertVisualPadding(TiledMapLayerData map,
                                            float left,
                                            float right,
                                            float top,
                                            float bottom) {
        Assert.assertEquals(left, map.visualPaddingLeft, 0.0001f);
        Assert.assertEquals(right, map.visualPaddingRight, 0.0001f);
        Assert.assertEquals(top, map.visualPaddingTop, 0.0001f);
        Assert.assertEquals(bottom, map.visualPaddingBottom, 0.0001f);
        Assert.assertFalse("visual padding should be cached after processing", map.visualBoundsDirty);
    }

    private static final class Fixture {
        final World world;
        final OrthographicCamera camera;
        final TiledMapRenderState tiledState;
        final DrawList drawList;
        final CountingAtlasRuntimeService atlas;
        final TiledMapLayerData map;
        final RenderTiledSyncSystem tiledSync;
        final RenderStats stats;

        Fixture(World world,
                OrthographicCamera camera,
                TiledMapRenderState tiledState,
                DrawList drawList,
                CountingAtlasRuntimeService atlas,
                TiledMapLayerData map,
                RenderTiledSyncSystem tiledSync,
                RenderStats stats) {
            this.world = world;
            this.camera = camera;
            this.tiledState = tiledState;
            this.drawList = drawList;
            this.atlas = atlas;
            this.map = map;
            this.tiledSync = tiledSync;
            this.stats = stats;
        }
    }

    private static class CountingAtlasRuntimeService extends AtlasRuntimeService {
        int resolveCalls = 0;

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveCalls++;
            if (assetId == 1 || assetId == 2 || assetId == 3) {
                return AtlasBindingTestFactory.single(
                        assetId,
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

    private static final class TallCountingAtlasRuntimeService extends CountingAtlasRuntimeService {
        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveCalls++;
            if (assetId == 1) {
                return AtlasBindingTestFactory.single(
                        assetId,
                        "tall-tile",
                        0f,
                        0f,
                        1f,
                        1f,
                        301,
                        64,
                        128
                );
            }
            return null;
        }
    }

    private static final class SizedCountingAtlasRuntimeService extends CountingAtlasRuntimeService {
        private final int spriteWidth;
        private final int spriteHeight;

        private SizedCountingAtlasRuntimeService(int spriteWidth, int spriteHeight) {
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
        }

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveCalls++;
            if (assetId == 1) {
                return AtlasBindingTestFactory.single(
                        assetId,
                        "tile-" + assetId,
                        0f,
                        0f,
                        1f,
                        1f,
                        400 + assetId,
                        spriteWidth,
                        spriteHeight
                );
            }
            return null;
        }
    }
}
