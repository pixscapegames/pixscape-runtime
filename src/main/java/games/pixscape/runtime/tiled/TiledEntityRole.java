package games.pixscape.runtime.tiled;

import com.artemis.World;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.runtime.component.TiledMapLayerComponent;
import games.pixscape.runtime.component.TransformComponent;

/** Product roles independent of the ECS components reused for structural hierarchy and rendering. */
public enum TiledEntityRole {
    NONE, MAP, TILE_LAYER, OBJECT_LAYER, IMAGE_LAYER;

    /** Constant-time component lookup; never creates components or resolves descendants. */
    public static TiledEntityRole of(World world, int entity) {
        if (world == null || entity < 0) {
            return NONE;
        }
        if (world.getMapper(TiledMapComponent.class).has(entity)) {
            return MAP;
        }
        TiledMapLayerComponent layer = world.getMapper(TiledMapLayerComponent.class).getSafe(entity, null);
        if (layer != null && layer.kind != null) {
            switch (layer.kind) {
                case TILE: return TILE_LAYER;
                case OBJECT: return OBJECT_LAYER;
                case IMAGE: return IMAGE_LAYER;
                default: break;
            }
        }
        return world.getMapper(TiledLayerComponent.class).has(entity) ? TILE_LAYER : NONE;
    }

    public boolean translationOnly() {
        return this == MAP || this == TILE_LAYER;
    }

    public static void requireGenericMember(World world, int entity) {
        if (of(world, entity) != NONE) {
            throw new IllegalArgumentException("Tiled maps and layers are structural entities, not Game Object members or assets.");
        }
    }

    public static void requireObjectParent(World world, int entity) {
        TiledEntityRole role = of(world, entity);
        if (role != NONE && (role != OBJECT_LAYER || !world.getMapper(GameObjectComponent.class).has(entity))) {
            throw new IllegalArgumentException("Only a Tiled object layer can contain ordinary objects; Tiled layers cannot be promoted.");
        }
    }

    public static void requireActorCapability(World world, int entity, String capability) {
        if (of(world, entity) != NONE) {
            throw new IllegalArgumentException(capability + " is not an actor capability of Tiled maps or layers. Use the dedicated Tiled controls.");
        }
    }

    public static void requireTransform(World world, int entity, TransformComponent pose) {
        requireTransform(world, entity, pose.rotationRad, pose.scaleX, pose.scaleY, pose.originX, pose.originY);
    }

    public static void requireTransform(World world, int entity, float rotation, float scaleX, float scaleY,
                                        float originX, float originY) {
        requireTransform(of(world, entity), rotation, scaleX, scaleY, originX, originY);
    }

    public static void requireTransform(TiledEntityRole role, float rotation, float scaleX, float scaleY,
                                        float originX, float originY) {
        if (role.translationOnly()
                && (rotation != 0f || scaleX != 1f || scaleY != 1f || originX != 0f || originY != 0f)) {
            throw new IllegalArgumentException("Tiled maps and tile layers support translation only, without rotation, scale or pivot.");
        }
    }

    public static void requireStructuralRelation(World world, int child, int parent) {
        requireStructuralRelation(of(world, child), of(world, parent));
    }

    public static void requireStructuralRelation(TiledEntityRole childRole, TiledEntityRole parentRole) {
        if (childRole == MAP || (childRole != NONE && parentRole != MAP)
                || (parentRole == MAP && childRole == NONE)
                || parentRole == TILE_LAYER || parentRole == IMAGE_LAYER) {
            throw new IllegalArgumentException("Invalid Tiled ownership: maps are top-level, layers belong to maps, and objects belong to object layers.");
        }
    }
}
