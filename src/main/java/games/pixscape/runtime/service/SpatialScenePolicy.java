package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;

/**
 * Authored Spatial admission, independent of Physics and footprint ownership.
 * Scene validation and explicit activation are cold, linear operations. Rendering
 * publishes the actor layer once, then tests each drawable's effective layer in O(1).
 */
public final class SpatialScenePolicy {
    private SpatialScenePolicy() {
    }

    public static int actorLayerIndex(World world, IntBag entities) {
        int result = -1;
        int owner = -1;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(entity, null);
            if (layer != null && layer.spatialEnabled) {
                if (owner >= 0 && owner != entity) {
                    throw new IllegalArgumentException("Only one scene layer may enable spatial actors.");
                }
                owner = entity;
                result = layer.layerIndex;
            }
        }
        return result;
    }

    /** Validates a candidate scene before it is published, using its own identity index. */
    public static void validateScene(World world, IdentityRegistry identities, IntBag entities) {
        actorLayerIndex(world, entities);
        int participatingMap = -1;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            TiledLayerComponent tiles = world.getMapper(TiledLayerComponent.class).getSafe(entity, null);
            if (enabled(tiles)) {
                int map = mapOwner(world, identities, entity);
                if (participatingMap >= 0 && participatingMap != map) {
                    throw new IllegalArgumentException("Spatial Depth must belong to a single Tiled map.");
                }
                participatingMap = map;
            }
        }
    }

    public static String tiledActivationRejection(World world, int tileLayer) {
        try {
            requireTiledActivation(world, tileLayer);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    /** Cold activation preflight. Multiple enabled tile layers of the same map are allowed. */
    public static void requireTiledActivation(World world, int tileLayer) {
        IdentityRegistry identities = IdentityRegistry.boundTo(world);
        int map = mapOwner(world, identities, tileLayer);
        IntBag tiles = world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities();
        for (int i = 0; i < tiles.size(); i++) {
            int other = tiles.get(i);
            if (other != tileLayer && enabled(world.getMapper(TiledLayerComponent.class).get(other))
                    && mapOwner(world, identities, other) != map) {
                throw new IllegalArgumentException("Another Tiled map already uses Spatial Depth. Disable its Spatial Depth layers first.");
            }
        }
    }

    public static boolean enabled(TiledLayerComponent tiles) {
        return tiles != null && (tiles.spatialEnabled || tiles.data != null && tiles.data.spatialEnabled);
    }

    private static int mapOwner(World world, IdentityRegistry identities, int tileLayer) {
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(tileLayer, null);
        int map = identities != null && member != null ? identities.findByStableId(member.parentStableId) : -1;
        if (map < 0 || !world.getMapper(TiledMapComponent.class).has(map)) {
            throw new IllegalArgumentException("Spatial Depth requires a tile layer belonging to a Tiled map.");
        }
        return map;
    }
}
