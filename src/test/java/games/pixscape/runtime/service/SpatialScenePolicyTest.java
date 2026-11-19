package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapHierarchy;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialScenePolicyTest {
    @Test
    public void mixedMapsAllowSeveralDepthLayersOfOneMapAtEveryActorLayerPosition() {
        for (TiledProjection participating : TiledProjection.values()) {
            for (int actorLayer = 0; actorLayer < 3; actorLayer++) {
                try (Fixture f = new Fixture()) {
                    int sceneLayer = f.world.create();
                    LayerComponent layer = f.world.getMapper(LayerComponent.class).create(sceneLayer);
                    layer.layerIndex = actorLayer;
                    layer.spatialEnabled = true;
                    int ortho = f.map(TiledProjection.ORTHO, 0);
                    int iso = f.map(TiledProjection.ISO, 2);
                    int chosen = participating == TiledProjection.ORTHO ? ortho : iso;
                    int other = chosen == ortho ? iso : ortho;
                    int lower = f.tiles(chosen, 0);
                    int upper = f.tiles(chosen, 1);
                    int inactive = f.tiles(other, 0);
                    f.world.process();
                    SpatialScenePolicy.requireTiledActivation(f.world, lower);
                    f.enable(lower, true);
                    SpatialScenePolicy.requireTiledActivation(f.world, upper);
                    f.enable(upper, true);
                    f.validate();
                    assertNotNull(SpatialScenePolicy.tiledActivationRejection(f.world, inactive));
                    assertFalse(f.world.getMapper(TiledLayerComponent.class).get(inactive).spatialEnabled);
                    f.enable(lower, false);
                    assertNotNull(SpatialScenePolicy.tiledActivationRejection(f.world, inactive));
                    f.enable(upper, false);
                    SpatialScenePolicy.requireTiledActivation(f.world, inactive);
                    f.enable(inactive, true);
                    f.validate();
                    // No projection is selected from actor layer ordering: only explicit Depth activation counts.
                    assertEquals(participating == TiledProjection.ORTHO ? TiledProjection.ISO : TiledProjection.ORTHO,
                            f.world.getMapper(TiledLayerComponent.class).get(inactive).projection);
                }
            }
        }
    }

    @Test
    public void invalidPublishedCandidatesRejectTwoMapsAndTwoActorLayers() {
        try (Fixture f = new Fixture()) {
            int a = f.tiles(f.map(TiledProjection.ORTHO, 0), 0);
            int b = f.tiles(f.map(TiledProjection.ISO, 1), 0);
            f.enable(a, true);
            f.enable(b, true);
            f.world.process();
            assertThrows(IllegalArgumentException.class, f::validate);
            f.enable(b, false);
            f.validate();
            for (int i = 0; i < 2; i++) {
                LayerComponent layer = f.world.getMapper(LayerComponent.class).create(f.world.create());
                layer.layerIndex = i;
                layer.spatialEnabled = true;
            }
            f.world.process();
            assertThrows(IllegalArgumentException.class, f::validate);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World();
        final IdentityRegistry ids = new IdentityRegistry();

        Fixture() {
            ids.bind(world, new SceneMetaRuntime());
        }

        int map(TiledProjection projection, int sceneLayer) {
            return TiledMapHierarchy.createMap(world, ids, projection.name(), sceneLayer, 0,
                    projection, 32, 16, 8, 8);
        }

        int tiles(int map, int order) {
            int entity = world.create();
            TiledMapComponent config = world.getMapper(TiledMapComponent.class).get(map);
            TiledLayerComponent tiles = world.getMapper(TiledLayerComponent.class).create(entity);
            tiles.projection = config.projection;
            tiles.tileWidth = config.tileWidth;
            tiles.tileHeight = config.tileHeight;
            tiles.mapWidthCells = tiles.mapHeightCells = 8;
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(TransformComponent.class).create(entity);
            TiledMapHierarchy.attachLayer(world, ids, map, entity, TiledMapLayerComponent.Kind.TILE, order);
            return entity;
        }

        void enable(int tiles, boolean enabled) {
            world.getMapper(TiledLayerComponent.class).get(tiles).spatialEnabled = enabled;
        }

        void validate() {
            SpatialScenePolicy.validateScene(world, ids,
                    world.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
        }

        @Override
        public void close() {
            ids.bind(null, null);
            world.dispose();
        }
    }
}
