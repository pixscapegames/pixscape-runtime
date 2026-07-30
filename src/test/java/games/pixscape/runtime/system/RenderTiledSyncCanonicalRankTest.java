package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.spatial.SpatialLayerFaceRuntime;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.spatial.SpatialTileOrderCache;
import games.pixscape.runtime.spatial.SpatialTileSyncInvariantException;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

public class RenderTiledSyncCanonicalRankTest {
    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void spatialOccupiedTilesUseCanonicalRanksIndependentOfInsertionOrder() {
        Fixture forward = fixture(true, new int[][]{{0, 1}, {2, 0}, {1, 2}});
        Fixture reverse = fixture(true, new int[][]{{1, 2}, {2, 0}, {0, 1}});

        forward.world.process();
        reverse.world.process();

        int[][] cells = {{0, 1}, {2, 0}, {1, 2}};
        for (int i = 0; i < cells.length; i++) {
            int gx = cells[i][0];
            int gy = cells[i][1];
            int forwardRank = forward.order().rank(gx, gy);
            int reverseRank = reverse.order().rank(gx, gy);
            Assert.assertTrue(forwardRank >= 0);
            Assert.assertEquals(forwardRank, reverseRank);
            Assert.assertEquals(canonicalKey(forwardRank), forward.key(gx, gy));
            Assert.assertEquals(forward.key(gx, gy), reverse.key(gx, gy));
        }
    }

    @Test
    public void spatialOccupiedTileWithoutRankFailsOnFullChunkBuild() throws Exception {
        Fixture fixture = fixture(true, new int[][]{{1, 1}});
        fixture.primeOrder();
        removeRank(fixture.order(), 1, 1, fixture.map.mapWidth);

        assertMissingRank(fixture, "missing-during-slot-write");
    }

    @Test
    public void spatialOccupiedTileWithoutRankFailsOnPartialChunkUpdate() throws Exception {
        Fixture fixture = fixture(true, new int[][]{{1, 1}});
        fixture.world.process();
        removeRank(fixture.order(), 1, 1, fixture.map.mapWidth);
        TileChunk chunk = fixture.map.getChunk(0, 0);
        chunk.markLocalDirty(chunk.localIndexFor(1, 1));

        assertMissingRank(fixture, "missing-during-slot-write");
    }

    @Test
    public void spatialOccupiedTileWithoutRankFailsOnCanonicalKeyRefresh() throws Exception {
        Fixture fixture = fixture(true, new int[][]{{1, 1}});
        fixture.world.process();
        removeRank(fixture.order(), 1, 1, fixture.map.mapWidth);
        Field applied = SpatialTileOrderCache.class.getDeclaredField("appliedOrderRevision");
        applied.setAccessible(true);
        applied.setInt(fixture.order(), Integer.MIN_VALUE);

        assertMissingRank(fixture, "missing-during-key-refresh");
    }

    @Test
    public void nonSpatialTileKeepsOrdinaryIsometricOrdering() {
        Fixture fixture = fixture(false, new int[][]{{1, 1}});

        fixture.world.process();

        Assert.assertEquals(SortKey64.packForBlend(1, BlendMode.ALPHA.id, 16, 3, -2, 1),
                fixture.key(1, 1));
    }

    @Test
    public void emptySpatialCellsDoNotRequireCanonicalRanks() {
        Fixture fixture = fixture(true, new int[0][0]);

        fixture.world.process();

        Assert.assertEquals(-1, fixture.order().rank(1, 1));
    }

    private static long canonicalKey(int rank) {
        return SortKey64.packForBlendOrder30(1, BlendMode.ALPHA.id, 16, 3, rank);
    }

    private static void removeRank(SpatialTileOrderCache order, int gx, int gy, int mapWidth)
            throws Exception {
        Field ranks = SpatialTileOrderCache.class.getDeclaredField("rankByCell");
        ranks.setAccessible(true);
        ((int[]) ranks.get(order))[gy * mapWidth + gx] = -1;
    }

    private static void assertMissingRank(Fixture fixture, String lookupState) {
        try {
            fixture.world.process();
            Assert.fail("Expected a missing canonical rank invariant");
        } catch (SpatialTileSyncInvariantException expected) {
            String message = expected.getMessage();
            Assert.assertTrue(message.contains("cell=(1,1)"));
            Assert.assertTrue(message.contains("layerEntity=" + fixture.entityId));
            Assert.assertTrue(message.contains("layerName=canonical-rank-layer"));
            Assert.assertTrue(message.contains("projection=ISO"));
            Assert.assertTrue(message.contains("mapRevision="));
            Assert.assertTrue(message.contains("canonicalRankState=" + lookupState));
        }
    }

    private static Fixture fixture(boolean spatial, int[][] occupiedCells) {
        OrthographicCamera camera = new OrthographicCamera(256f, 256f);
        camera.position.set(0f, 0f, 0f);
        TiledMapRenderState state = new TiledMapRenderState(32);
        SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();
        RenderTiledSyncSystem sync = new RenderTiledSyncSystem(camera, state, new TestAtlas(),
                1, null, profiles(), registry);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());

        Entity entity = world.createEntity();
        PixscapeIdentityComponent identity = entity.edit().create(PixscapeIdentityComponent.class);
        identity.name = "canonical-rank-layer";
        LayerComponent layer = entity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 3;
        layer.spatialEnabled = spatial;
        TiledLayerComponent tiled = entity.edit().create(TiledLayerComponent.class);
        tiled.atlasTag = "main";
        tiled.spatialEnabled = spatial;
        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 8, 2,
                SceneMetaRuntime.TiledProjection.ISO);
        map.spatialEnabled = spatial;
        for (int i = 0; i < occupiedCells.length; i++) {
            map.setTile(occupiedCells[i][0], occupiedCells[i][1], 1);
        }
        tiled.data = map;
        return new Fixture(world, state, registry, map, entity.getId());
    }

    private static RuntimeTilesetProfiles profiles() {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = 1;
        profile.referenceCellWidth = 16;
        profile.referenceCellHeight = 8;
        profile.projection = SceneMetaRuntime.TiledProjection.ISO;
        profile.anchor = RuntimeTilesetAnchor.TOP_CENTER;
        profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
        profile.tileAssetIds = new int[]{1};
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        profiles.add(profile);
        return profiles;
    }

    private static final class Fixture {
        final World world;
        final TiledMapRenderState state;
        final SpatialLayerRuntimeRegistry registry;
        final TiledMapLayerData map;
        final int entityId;

        Fixture(World world, TiledMapRenderState state, SpatialLayerRuntimeRegistry registry,
                TiledMapLayerData map, int entityId) {
            this.world = world;
            this.state = state;
            this.registry = registry;
            this.map = map;
            this.entityId = entityId;
        }

        SpatialTileOrderCache order() {
            return registry.forLayer(entityId, map).tileOrder;
        }

        void primeOrder() {
            SpatialLayerFaceRuntime runtime = registry.forLayer(entityId, map);
            runtime.compiled.ensure(null);
            runtime.projected.ensure(runtime.compiled, map);
            runtime.tileOrder.ensure(entityId, map, null, runtime.compiled);
        }

        long key(int gx, int gy) {
            return state.sortKey[map.tiledRenderRefForTile(gx, gy)];
        }
    }

    private static final class TestAtlas extends AtlasRuntimeService {
        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            if (assetId <= 0) return null;
            return AtlasBindingTestFactory.single(
                    assetId, "tile-" + assetId, 0f, 0f, 1f, 1f, 16, 8, 1);
        }
    }
}
