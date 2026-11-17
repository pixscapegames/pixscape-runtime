package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.io.SaveFileFormat;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.IdentityRegistry;

/** Binds current-schema Tiled layer data to its authored Map configuration. */
final class TiledMapSceneBindings {
    private TiledMapSceneBindings() {
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

}
