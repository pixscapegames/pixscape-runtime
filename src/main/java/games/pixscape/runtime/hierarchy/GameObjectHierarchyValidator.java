package games.pixscape.runtime.hierarchy;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;

/**
 * Cold-boundary validator for authored Game Object hierarchy relations.
 */
public final class GameObjectHierarchyValidator {
    private final World world;
    private final IdentityRegistry identities;
    private final ComponentMapper<GameObjectComponent> gameObjects;
    private final ComponentMapper<GameObjectMemberComponent> members;
    private final ComponentMapper<PixscapeIdentityComponent> entityIdentities;
    private final ComponentMapper<TransformComponent> transforms;
    private final ComponentMapper<PhysicsBodyComponent> physicsBodies;
    private final ComponentMapper<ParticleEmitterComponent> particles;
    private final ComponentMapper<TiledLayerComponent> tiledMaps;
    private final ComponentMapper<SpatialBlocksComponent> spatialBlocks;
    private final ComponentMapper<SpatialShapesComponent> spatialShapes;
    private final ComponentMapper<EntityIndexComponent> entityIndices;
    private final IntSet chainStableIds = new IntSet();
    private final IntArray path = new IntArray(false, 16);
    private final IntArray touchedStates = new IntArray(false, 16);
    private byte[] visitState = new byte[16];
    private final IntSet spatialEntities = new IntSet();

    public GameObjectHierarchyValidator(World world, IdentityRegistry identities) {
        if (world == null || identities == null) {
            throw new IllegalArgumentException("World and IdentityRegistry are required.");
        }
        this.world = world;
        this.identities = identities;
        this.gameObjects = world.getMapper(GameObjectComponent.class);
        this.members = world.getMapper(GameObjectMemberComponent.class);
        this.entityIdentities = world.getMapper(PixscapeIdentityComponent.class);
        this.transforms = world.getMapper(TransformComponent.class);
        this.physicsBodies = world.getMapper(PhysicsBodyComponent.class);
        this.particles = world.getMapper(ParticleEmitterComponent.class);
        this.tiledMaps = world.getMapper(TiledLayerComponent.class);
        this.spatialBlocks = world.getMapper(SpatialBlocksComponent.class);
        this.spatialShapes = world.getMapper(SpatialShapesComponent.class);
        this.entityIndices = world.getMapper(EntityIndexComponent.class);
    }

    /**
     * Validates one relation and its complete ancestor chain in O(depth).
     */
    public void validateMember(int memberEntityId) {
        requireActive(memberEntityId, "member");
        chainStableIds.clear();
        int current = memberEntityId;
        while (members.has(current)) {
            PixscapeIdentityComponent identity = requireStableIdentity(current);
            if (!chainStableIds.add(identity.stableId)) {
                throw failure(memberEntityId, "hierarchy contains a cycle at stableId "
                        + identity.stableId);
            }
            current = requireDirectParent(current);
        }
        if (physicsBodies.has(memberEntityId)) validatePhysicsAncestry(memberEntityId);
    }

    /**
     * Validates a complete loaded/mutation candidate in O(entity count + relation count).
     * The supplied entities define the cold validation boundary; no validation is performed per frame.
     */
    public void validateEntities(IntBag entities) {
        if (entities == null) {
            throw new IllegalArgumentException("Hierarchy validation entity list is required.");
        }
        clearVisitState();
        spatialEntities.clear();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)) continue;
            TiledEntityRole role = TiledEntityRole.of(world, entityId);
            if (role != TiledEntityRole.NONE) {
                TransformComponent pose = transforms.getSafe(entityId, null);
                if (pose != null) {
                    try {
                        TiledEntityRole.requireTransform(world, entityId, pose);
                    } catch (IllegalArgumentException ex) {
                        throw failure(entityId, ex.getMessage());
                    }
                }
                if (world.getMapper(SpatialFootprintComponent.class).has(entityId)) {
                    throw failure(entityId, "Tiled maps and layers cannot own actor footprints");
                }
                if (physicsBodies.has(entityId) && role != TiledEntityRole.TILE_LAYER) {
                    throw failure(entityId, "Tiled maps and non-tile layers cannot own generic Bodies");
                }
                if (world.getMapper(TiledMapLayerComponent.class).has(entityId) && !members.has(entityId)) {
                    throw failure(entityId, "Tiled layer requires a Tiled map parent");
                }
                if (gameObjects.has(entityId) && role != TiledEntityRole.MAP && role != TiledEntityRole.OBJECT_LAYER) {
                    throw failure(entityId, "Tile and image layers cannot be promoted to composition parents");
                }
                if (gameObjects.has(entityId) && gameObjects.get(entityId).sourceAssetId != null
                        && !gameObjects.get(entityId).sourceAssetId.isEmpty()) {
                    throw failure(entityId, "Tiled structures cannot be Game Object asset instances");
                }
            }
            TiledMapComponent map = world.getMapper(TiledMapComponent.class).getSafe(entityId, null);
            if (map != null) {
                map.validate();
                if (!gameObjects.has(entityId) || members.has(entityId)) {
                    throw failure(entityId, "Tiled map must be a top-level composition root");
                }
                TransformComponent pose = transforms.getSafe(entityId, null);
                if (pose == null || pose.rotationRad != 0f || pose.scaleX != 1f || pose.scaleY != 1f) {
                    throw failure(entityId, "Tiled map supports translation with unit scale and no rotation");
                }
            }
            if (gameObjects.has(entityId)) {
                requireStableIdentity(entityId);
                if (particles.has(entityId)) {
                    throw failure(entityId, "particles cannot be Game Object roots");
                }
                if (tiledMaps.has(entityId)) {
                    throw failure(entityId, "Tiled Maps cannot be Game Object roots");
                }
                requireEntityIndex(entityId, "Game Object root");
                TransformComponent transform = transforms.getSafe(entityId, null);
                if (transform == null) {
                    throw failure(entityId, "Game Object root requires TransformComponent");
                }
                try {
                    GameObjectTransformMath.requirePositiveUniformParentScale(transform);
                } catch (IllegalArgumentException ex) {
                    throw failure(entityId, ex.getMessage());
                }
            }
            if (members.has(entityId)) {
                if (!transforms.has(entityId)) {
                    throw failure(entityId, "Game Object member requires TransformComponent");
                }
                if (particles.has(entityId)) {
                    throw failure(entityId, "particles cannot be Game Object members");
                }
                boolean tiledLayer = world.getMapper(TiledMapLayerComponent.class).has(entityId);
                if (tiledMaps.has(entityId) && !tiledLayer) {
                    throw failure(entityId, "Tiled Maps cannot be Game Object members");
                }
                if (!tiledLayer && (spatialBlocks.has(entityId) || spatialShapes.has(entityId))) {
                    throw failure(entityId,
                            "Scene-local Spatial structures cannot be Game Object members");
                }
                requireEntityIndex(entityId, "Game Object member");
                requireDirectParent(entityId);
                if (tiledLayer && !world.getMapper(TiledMapComponent.class)
                        .has(requireDirectParent(entityId))) {
                    throw failure(entityId, "Tiled layer requires a Tiled map parent");
                }
                if (tiledLayer && tiledMaps.has(entityId)) {
                    TiledMapComponent owner = world.getMapper(TiledMapComponent.class)
                            .get(requireDirectParent(entityId));
                    TiledLayerComponent tiles = tiledMaps.get(entityId);
                    if (tiles.projection != owner.projection || tiles.tileWidth != owner.tileWidth
                            || tiles.tileHeight != owner.tileHeight) {
                        throw failure(entityId, "Tile grid configuration differs from its map");
                    }
                }
            }
        }

        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)
                    || state(entityId) == 2) {
                continue;
            }
            validateChainLinear(entityId);
        }

        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (world.getEntityManager().isActive(entityId) && physicsBodies.has(entityId)) {
                validatePhysicsAncestry(entityId);
            }
        }
    }

    private EntityIndexComponent requireEntityIndex(int entityId, String role) {
        EntityIndexComponent index = entityIndices.getSafe(entityId, null);
        if (index == null) {
            throw failure(entityId, role + " requires EntityIndexComponent");
        }
        if (index.zIndex < SortKey64.MIN_Z || index.zIndex > SortKey64.MAX_Z) {
            throw failure(entityId, role + " zIndex " + index.zIndex
                    + " is outside supported range [" + SortKey64.MIN_Z
                    + ", " + SortKey64.MAX_Z + "]");
        }
        return index;
    }

    private void validateChainLinear(int startEntityId) {
        path.clear();
        int current = startEntityId;
        while (members.has(current)) {
            byte currentState = state(current);
            if (currentState == 1) {
                int stableId = requireStableIdentity(current).stableId;
                throw failure(startEntityId,
                        "hierarchy contains a cycle at stableId " + stableId);
            }
            if (currentState == 2) break;
            setState(current, (byte) 1);
            path.add(current);
            current = requireDirectParent(current);
        }
        if (state(current) != 2) {
            validateSpatialRepresentation(current, false);
            setState(current, (byte) 2);
        }
        boolean inherited = spatialEntities.contains(current);
        for (int i = path.size - 1; i >= 0; i--) {
            current = path.get(i);
            validateSpatialRepresentation(current, inherited);
            inherited = spatialEntities.contains(current);
            setState(current, (byte) 2);
        }
    }

    private void validateSpatialRepresentation(int entity, boolean inherited) {
        if (!inherited && !world.getMapper(SpatialFootprintComponent.class).has(entity)) {
            return;
        }
        spatialEntities.add(entity);
        if (!world.getMapper(TextureRegionComponent.class).has(entity)) {
            return;
        }
        games.pixscape.runtime.component.spatial.SpatialRepresentationComponent representation = world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class)
                .getSafe(entity, null);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).getSafe(entity, null);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).getSafe(entity, null);
        try {
            ActorSpatialRectangles.requireCurrent(
                    representation == null ? null : representation.rectangles,
                    asset == null ? 0 : asset.assetId, animation == null ? null : animation.currentClip);
        } catch (IllegalArgumentException ex) {
            throw failure(entity, ex.getMessage());
        }
    }

    /**
     * Physics fixtures are Body-local, so every inherited Game Object frame must be unit scale.
     */
    private void validatePhysicsAncestry(int bodyEntityId) {
        int current = bodyEntityId;
        while (members.has(current)) {
            int parentEntityId = requireDirectParent(current);
            TransformComponent parentTransform = transforms.getSafe(parentEntityId, null);
            try {
                GameObjectTransformMath.requireUnitParentScale(parentTransform, "Physics");
            } catch (IllegalArgumentException ex) {
                throw failure(bodyEntityId, ex.getMessage());
            }
            current = parentEntityId;
        }
    }

    private int requireDirectParent(int memberEntityId) {
        GameObjectMemberComponent member = members.getSafe(memberEntityId, null);
        if (member == null) {
            throw failure(memberEntityId, "member relation is missing");
        }
        PixscapeIdentityComponent memberIdentity = requireStableIdentity(memberEntityId);
        if (member.parentStableId <= 0) {
            throw failure(memberEntityId,
                    "parentStableId must be positive, found " + member.parentStableId);
        }
        if (member.parentStableId == memberIdentity.stableId) {
            throw failure(memberEntityId, "member cannot be its own parent");
        }
        int parentEntityId = identities.findByStableId(member.parentStableId);
        if (parentEntityId < 0) {
            throw failure(memberEntityId,
                    "parent stableId " + member.parentStableId + " does not resolve to an active entity");
        }
        requireActive(parentEntityId, "parent");
        TiledEntityRole.requireStructuralRelation(world, memberEntityId, parentEntityId);
        if (!gameObjects.has(parentEntityId)) {
            throw failure(memberEntityId,
                    "parent stableId " + member.parentStableId + " is not a Game Object");
        }
        TransformComponent parentTransform = transforms.getSafe(parentEntityId, null);
        if (parentTransform == null) {
            throw failure(memberEntityId,
                    "parent stableId " + member.parentStableId + " has no TransformComponent");
        }
        try {
            GameObjectTransformMath.requirePositiveUniformParentScale(parentTransform);
        } catch (IllegalArgumentException ex) {
            throw failure(memberEntityId,
                    "parent stableId " + member.parentStableId + ": " + ex.getMessage());
        }
        return parentEntityId;
    }

    private PixscapeIdentityComponent requireStableIdentity(int entityId) {
        PixscapeIdentityComponent identity = entityIdentities.getSafe(entityId, null);
        if (identity == null || identity.stableId <= 0) {
            throw failure(entityId, "entity requires a positive persistent stable ID");
        }
        return identity;
    }

    private void requireActive(int entityId, String role) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            throw failure(entityId, role + " entity is not active");
        }
    }

    private byte state(int entityId) {
        return entityId >= 0 && entityId < visitState.length ? visitState[entityId] : 0;
    }

    private void setState(int entityId, byte value) {
        ensureStateCapacity(entityId);
        if (visitState[entityId] == 0) touchedStates.add(entityId);
        visitState[entityId] = value;
    }

    private void ensureStateCapacity(int entityId) {
        if (entityId < visitState.length) return;
        int next = visitState.length;
        while (next <= entityId) next <<= 1;
        byte[] expanded = new byte[next];
        System.arraycopy(visitState, 0, expanded, 0, visitState.length);
        visitState = expanded;
    }

    private void clearVisitState() {
        for (int i = 0; i < touchedStates.size; i++) {
            visitState[touchedStates.get(i)] = 0;
        }
        touchedStates.clear();
    }

    private IllegalArgumentException failure(int entityId, String detail) {
        PixscapeIdentityComponent identity = entityId >= 0
                ? entityIdentities.getSafe(entityId, null) : null;
        String stable = identity != null && identity.stableId > 0
                ? ", stableId " + identity.stableId : "";
        return new IllegalArgumentException(
                "Invalid Game Object hierarchy at entityId " + entityId + stable
                        + ": " + detail + ".");
    }
}
