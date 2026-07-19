package games.pixscape.runtime.system;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.spatial.SpatialTileOrderCache;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RenderTiledSyncSpatialEditTest {
    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void adjacentUnanchoredBrushPiecesRenderDuringOpenMutationAndMatchFullRebuild() {
        OrthographicCamera camera = new OrthographicCamera(256f, 256f);
        camera.position.set(32f, 48f, 0f);
        TiledMapRenderState state = new TiledMapRenderState(64);
        SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();
        RenderTiledSyncSystem sync = new RenderTiledSyncSystem(camera, state,
                new TestAtlas(), 1, null, profiles(1, 2, 3), registry);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());

        Entity entity = world.createEntity();
        LayerComponent layer = entity.edit().create(LayerComponent.class);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;
        layer.spatialEnabled = true;
        TiledLayerComponent tiled = entity.edit().create(TiledLayerComponent.class);
        tiled.spatialEnabled = true;
        tiled.atlasTag = "main";
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 16, 8, 4, SceneMetaRuntime.TiledProjection.ISO);
        map.spatialEnabled = true;
        map.setTile(2, 2, 1);
        map.setTile(3, 2, 1);
        tiled.data = map;

        SpatialBlocksComponent blocks = entity.edit().create(SpatialBlocksComponent.class);
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 9;
        wall.structureId = 4;
        wall.x = 2;
        wall.y = 2;
        wall.width = 2;
        wall.depth = 1;
        wall.height = 16;
        wall.addLinkedTileRef(2, 2, 1);
        wall.addLinkedTileRef(3, 2, 1);
        blocks.blocks.add(wall);
        blocks.revision = 1;

        world.process();
        SpatialTileOrderCache order = registry.forLayer(entity.getId(), map).tileOrder;
        Assert.assertTrue(order.rank(2, 2) >= 0);

        map.beginContentMutation();
        try {
            map.setTile(2, 2, 2); // replace a wall piece with a corner asset
            world.process();
            map.setTile(4, 2, 2); // adjacent candidate
            world.process();
            map.setTile(5, 2, 3); // third candidate, not owned by any wall
            world.process();

            Assert.assertTrue(state.enabled[map.tiledRenderRefForTile(5, 2)]);
            Assert.assertFalse(order.requiresCanonicalRank(map, 5, 2));
            Assert.assertTrue("open mutation must refresh static ordering", order.rank(5, 2) >= 0);
            Assert.assertEquals("replacement and both adjacent edits invalidate stale tile occupancy",
                    4, order.tileOrderCompileCount);
        } finally {
            map.endContentMutation();
        }

        long[] partialKeys = keys(map, state, new int[]{2, 3, 4, 5}, 2);
        map.markAllChunksContentDirty();
        world.process();
        Assert.assertArrayEquals(partialKeys, keys(map, state, new int[]{2, 3, 4, 5}, 2));
        Assert.assertTrue(order.requiresCanonicalRank(map, 2, 2));
        Assert.assertTrue(order.rank(2, 2) >= 0);
    }

    private static long[] keys(TiledMapLayerData map, TiledMapRenderState state, int[] xs, int gy) {
        long[] keys = new long[xs.length];
        for (int i = 0; i < xs.length; i++) keys[i] = state.sortKey[map.tiledRenderRefForTile(xs[i], gy)];
        return keys;
    }

    private static RuntimeTilesetProfiles profiles(int... assetIds) {
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        for (int i = 0; i < assetIds.length; i++) {
            RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
            profile.tilesetId = i + 1;
            profile.referenceCellWidth = 16;
            profile.referenceCellHeight = 8;
            profile.projection = SceneMetaRuntime.TiledProjection.ISO;
            profile.anchor = RuntimeTilesetAnchor.TOP_CENTER;
            profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
            profile.tileAssetIds = new int[]{assetIds[i]};
            profiles.add(profile);
        }
        return profiles;
    }

    private static final class TestAtlas extends AtlasRuntimeService {
        @Override
        public CachedRegion resolveCached(int assetId, String tag) {
            if (assetId <= 0) return null;
            return new CachedRegion("tile-" + assetId, 0f, 0f, 1f, 1f,
                    16, 8, 1);
        }
    }
}
