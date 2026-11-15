package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.io.SaveFileFormat;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledMapHierarchy;

/** One explicit schema-3 conversion, executed only in the isolated load candidate. */
final class TiledMapSceneUpgrade {
    private TiledMapSceneUpgrade() {
    }

    static void bindConfigurations(World world, SaveFileFormat format, SceneMetaRuntime metadata) {
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, metadata);
        try {
            identities.rebuild();
            for (int i = 0; i < format.entities.size(); i++) {
                int entity = format.entities.get(i);
                if (!world.getMapper(TiledMapLayerComponent.class).has(entity)) {
                    continue;
                }
                GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
                int parent = member != null ? identities.findByStableId(member.parentStableId) : -1;
                TiledMapComponent map = parent >= 0
                        ? world.getMapper(TiledMapComponent.class).getSafe(parent, null) : null;
                if (map == null) {
                    throw new IllegalArgumentException("Tiled layer requires a map with a stable identity.");
                }
                map.validate();
                TiledLayerComponent tiles = world.getMapper(TiledLayerComponent.class).getSafe(entity, null);
                if (tiles != null) {
                    tiles.projection = map.projection;
                    tiles.tileWidth = map.tileWidth;
                    tiles.tileHeight = map.tileHeight;
                    TransformComponent transform = world.getMapper(TransformComponent.class).getSafe(entity, null);
                    if (transform == null) {
                        throw new IllegalArgumentException("Tiled layer requires its local transform.");
                    }
                    tiles.originX = transform.x;
                    tiles.originY = transform.y;
                }
            }
        } finally {
            identities.bind(null, null);
        }
    }

    static void convert(World world, SaveFileFormat format, SceneMetaRuntime metadata) {
        if (metadata.sceneSchemaVersion != 3) {
            return;
        }
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, metadata);
        try {
            identities.rebuild();
            int oldCount = format.entities.size();
            for (int i = 0; i < oldCount; i++) {
                int entity = format.entities.get(i);
                TiledLayerComponent tiles = world.getMapper(TiledLayerComponent.class).getSafe(entity, null);
                if (tiles == null || world.getMapper(TiledMapLayerComponent.class).has(entity)) {
                    continue;
                }
                EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entity);
                PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                        .getSafe(entity, null);
                int map = TiledMapHierarchy.createMap(world, identities,
                        identity != null && identity.name != null ? identity.name : "Map",
                        index.layerIndex, index.zIndex, tiles.projection, tiles.tileWidth,
                        tiles.tileHeight, tiles.mapWidthCells, tiles.mapHeightCells);
                TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
                transform.x = tiles.originX;
                transform.y = tiles.originY;
                transform.refreshCaches();
                TiledMapHierarchy.attachLayer(world, identities, map, entity,
                        TiledMapLayerComponent.Kind.TILE, 0);
                format.entities.add(map);
            }
            // The isolated candidate contains no simulation/render systems. Publish its
            // newly authored component compositions before serializing the upgraded scene.
            world.process();
            metadata.sceneSchemaVersion = SceneMetaRuntime.CURRENT_SCENE_SCHEMA_VERSION;
        } finally {
            identities.bind(null, null);
        }
    }
}
