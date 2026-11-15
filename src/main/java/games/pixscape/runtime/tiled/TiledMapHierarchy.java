package games.pixscape.runtime.tiled;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.IdentityRegistry;

/** Cold authored mutations; stable identities come exclusively from IdentityRegistry. */
public final class TiledMapHierarchy {
    private TiledMapHierarchy() {
    }

    public static int createMap(World world, IdentityRegistry identities, String name,
                                int sceneLayer, int zIndex, TiledProjection projection,
                                int tileWidth, int tileHeight, int width, int height) {
        TiledMapComponent configuration = new TiledMapComponent();
        configuration.projection = projection;
        configuration.tileWidth = tileWidth;
        configuration.tileHeight = tileHeight;
        configuration.width = width;
        configuration.height = height;
        configuration.validate();
        int entity = world.create();
        world.getMapper(TiledMapComponent.class).create(entity).copyFrom(configuration);
        world.getMapper(GameObjectComponent.class).create(entity);
        world.getMapper(TransformComponent.class).create(entity).refreshCaches();
        world.getMapper(VisibilityComponent.class).create(entity).visible = true;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = sceneLayer;
        index.zIndex = zIndex;
        identities.ensureStableId(entity);
        identities.setName(entity, name);
        identities.indexEntityImmediately(entity);
        return entity;
    }

    public static void attachLayer(World world, IdentityRegistry identities, int mapEntity,
                                    int layerEntity, TiledMapLayerComponent.Kind kind, int order) {
        TiledMapComponent map = world.getMapper(TiledMapComponent.class).get(mapEntity);
        map.validate();
        TiledLayerComponent tiles = world.getMapper(TiledLayerComponent.class).getSafe(layerEntity, null);
        if ((kind == TiledMapLayerComponent.Kind.TILE) != (tiles != null)) {
            throw new IllegalArgumentException("Tiled layer kind does not match its contents.");
        }
        if (tiles != null && (tiles.projection != map.projection || tiles.tileWidth != map.tileWidth
                || tiles.tileHeight != map.tileHeight)) {
            throw new IllegalArgumentException("Tile layer grid does not match its map.");
        }
        identities.ensureStableId(layerEntity);
        world.getMapper(TiledMapLayerComponent.class).create(layerEntity).kind = kind;
        world.getMapper(GameObjectMemberComponent.class).create(layerEntity).parentStableId =
                world.getMapper(PixscapeIdentityComponent.class).get(mapEntity).stableId;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(layerEntity);
        index.layerIndex = world.getMapper(EntityIndexComponent.class).get(mapEntity).layerIndex;
        index.zIndex = order;
        identities.indexEntityImmediately(layerEntity);
    }
}
