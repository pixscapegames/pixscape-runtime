package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.profile.*;

import java.util.Arrays;

/** Actual tiled publication, draw-list build/sort and spatial system, without GL submission. */
final class SpatialSharedSearchFixture implements AutoCloseable {
    final DynamicEntityRenderState ecs;
    final TiledMapRenderState tiled = new TiledMapRenderState(64);
    final DrawList draws = new DrawList(64);
    final SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();
    final SpatialSharedAnchorIndex search;
    final OrthographicCamera camera;
    final World world;
    final SpatialRenderOrderSystem spatial;
    final int[] owners, actors;
    final TiledMapLayerData[] maps;
    final float[] offsetX = new float[16], offsetY = new float[16];
    final LayerDisplayOffsetResolver offsets = new LayerDisplayOffsetResolver() {
        public void resolveLayer(int layer, Vector2 out) {
            out.set(offsetX[layer], offsetY[layer]);
        }

        public void resolvePhysics(Vector2 out) {
            out.setZero();
        }
    };

    SpatialSharedSearchFixture(int count, int actorCount, boolean nativeSearch) {
        GdxNativesLoader.load();
        tiled.lightingEnabled = false;
        camera = new OrthographicCamera(8192, 8192);
        ecs = new DynamicEntityRenderState(actorCount);
        owners = new int[count];
        maps = new TiledMapLayerData[count];
        actors = new int[actorCount];
        camera.update();
        LayerStateSOA layers = new LayerStateSOA(16);
        Arrays.fill(layers.enabled, true);
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = 1;
        profile.referenceCellWidth = 64;
        profile.referenceCellHeight = 32;
        profile.projection = TiledProjection.ISO;
        profile.anchor = RuntimeTilesetAnchor.BOTTOM_CENTER;
        profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
        profile.tileAssetIds = new int[]{1, 2, 3};
        profiles.add(profile);
        spatial = nativeSearch
                ? new SpatialRenderOrderSystem(ecs, tiled, draws, registry, offsets, null)
                : new SpatialRenderOrderSystem(ecs, tiled, draws, registry, offsets);
        search = searchOf(spatial);
        world = new World(new WorldConfigurationBuilder().with(
                new RenderTiledSyncSystem(camera, tiled, new TestAtlas(), 1, null, profiles, registry, offsets),
                new RenderBuildDrawListSystem(ecs, tiled, layers, draws, new RenderStats(), 128, -1, -1),
                new RenderSortSystem(ecs, tiled, draws), spatial).build());
        for (int m = 0; m <= count; m++) {
            int layer = world.create();
            LayerComponent component = world.getMapper(LayerComponent.class).create(layer);
            component.layerIndex = m;
            component.spatialEnabled = true;
        }
        for (int m = 0; m < count; m++) {
            owners[m] = addMap(m);
        }
        for (int a = 0; a < actorCount; a++) {
            int actor = world.create();
            actors[a] = actor;
            world.getMapper(TransformComponent.class).create(actor);
            world.getMapper(EntityIndexComponent.class).create(actor).layerIndex = count;
            SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(actor);
            SpatialRectangleTestData.setHeight(height, 64);
            height.altitude = 32 * (a % count);
            SpatialPhysicsFootprintComponent footprint = world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
            footprint.valid = true;
            footprint.radiusPx = 2;
            int slot = ecs.acquireSlotForEntity(actor);
            ecs.kind[slot] = RenderKind.SPRITE;
            ecs.enabled[slot] = ecs.visible[slot] = true;
            ecs.textureHandle[slot] = ecs.shader[slot] = 1;
            ecs.blend[slot] = BlendMode.ALPHA.id;
            ecs.layerIndex[slot] = count;
            ecs.runtimeOrder[slot] = a;
            ecs.x2[slot] = ecs.x3[slot] = ecs.y3[slot] = ecs.y4[slot] = 1;
            ecs.a[slot] = 1;
            ecs.sortKey[slot] = SortKey64.packForBlend(1, BlendMode.ALPHA.id, 1, count, 0, a);
        }
        move(0);
        world.process();
    }

    int addMap(int m) {
        int owner = world.create();
        world.getMapper(EntityIndexComponent.class).create(owner).layerIndex = m;
        TiledLayerComponent component = world.getMapper(TiledLayerComponent.class).create(owner);
        component.spatialEnabled = true;
        component.atlasTag = "main";
        TiledMapLayerData map = new TiledMapLayerData(64, 64, 64, 32, 64, TiledProjection.ISO);
        component.data = maps[m] = map;
        map.spatialEnabled = true;
        map.defaultTileAltitude = 32 * m;
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owner);
        SpatialBlockData block = new SpatialBlockData();
        block.id = blocks.allocateNextSpatialBlockId();
        block.structureId = block.id;
        block.x = block.y = 4 + m;
        block.width = 8;
        block.depth = 1;
        block.altitude = 32 * m;
        block.height = 32;
        for (int x = 0; x < 8; x++) {
            map.setTile(4 + m + x, 4 + m, 1);
            block.addLinkedTileRef(4 + m + x, 4 + m, 1);
        }
        blocks.blocks.add(block);
        return owner;
    }

    private static SpatialSharedAnchorIndex searchOf(SpatialRenderOrderSystem system) {
        try {
            java.lang.reflect.Field field = SpatialRenderOrderSystem.class.getDeclaredField("sharedAnchorSearch");
            field.setAccessible(true);
            return (SpatialSharedAnchorIndex) field.get(system);
        } catch (NoSuchFieldException baseline) {
            return null;
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        }
    }

    void move(int frame) {
        for (int a = 0; a < actors.length; a++) {
            TransformComponent t = world.getMapper(TransformComponent.class).get(actors[a]);
            t.x = 16 + a % 8 * 32;
            t.y = 108 + a % 8 * 16 + frame % 128 * .5f + 32 * (a % owners.length);
        }
    }

    String evidence() {
        return spatial.diagnosticSummary() + Arrays.toString(Arrays.copyOf(draws.data(), draws.size))
                + Arrays.toString(Arrays.copyOf(draws.domainData(), draws.size));
    }

    @Override
    public void close() {
        world.dispose();
    }

    private static final class TestAtlas extends AtlasRuntimeService {
        private final AtlasAssetBinding[] bindings = new AtlasAssetBinding[]{null,
                AtlasBindingTestFactory.single(1, "ordinary", 0, 0, 1, 1, 64, 64, 1),
                AtlasBindingTestFactory.single(2, "replaced", 0, 0, 1, 1, 48, 32, 1),
                AtlasBindingTestFactory.single(3, "overhang", 0, 0, 1, 1, 1088, 544, 1)};

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            return assetId > 0 && assetId < bindings.length ? bindings[assetId] : null;
        }
    }
}
