package games.pixscape.runtime.hierarchy;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;

/** Cold-boundary validator for authored Game Object hierarchy relations. */
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
    private final ComponentMapper<TextureRegionComponent> textureRegions;
    private final ComponentMapper<AnimationComponent> animations;
    private final ComponentMapper<PointLightComponent> pointLights;
    private final ComponentMapper<ConeLightComponent> coneLights;
    private final IntSet chainStableIds = new IntSet();
    private final IntArray path = new IntArray(false, 16);
    private final IntArray touchedStates = new IntArray(false, 16);
    private byte[] visitState = new byte[16];

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
        this.textureRegions = world.getMapper(TextureRegionComponent.class);
        this.animations = world.getMapper(AnimationComponent.class);
        this.pointLights = world.getMapper(PointLightComponent.class);
        this.coneLights = world.getMapper(ConeLightComponent.class);
    }

    /** Validates one relation and its complete ancestor chain in O(depth). */
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
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)) continue;
            if (gameObjects.has(entityId)) {
                requireStableIdentity(entityId);
                if (particles.has(entityId)) {
                    throw failure(entityId, "particles cannot be Game Object roots");
                }
                if (tiledMaps.has(entityId)) {
                    throw failure(entityId, "Tiled Maps cannot be Game Object roots");
                }
                if (textureRegions.has(entityId) || animations.has(entityId)
                        || pointLights.has(entityId) || coneLights.has(entityId)) {
                    throw failure(entityId,
                            "Game Object roots are composition-only and cannot be directly drawable");
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
                if (tiledMaps.has(entityId)) {
                    throw failure(entityId, "Tiled Maps cannot be Game Object members");
                }
                if (spatialBlocks.has(entityId) || spatialShapes.has(entityId)) {
                    throw failure(entityId,
                            "Scene-local Spatial structures cannot be Game Object members");
                }
                requireEntityIndex(entityId, "Game Object member");
                requireDirectParent(entityId);
            }
        }

        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)
                    || !members.has(entityId)
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
        for (int i = 0; i < path.size; i++) setState(path.get(i), (byte) 2);
    }

    /** Physics fixtures are Body-local, so every inherited Game Object frame must be unit scale. */
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
