package games.pixscape.runtime.hierarchy;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.PhysicsPoseAuthority;
import games.pixscape.runtime.service.IdentityRegistry;

/** Explicit cold hierarchy mutation, with candidate transforms prepared before any authored write. */
public final class GameObjectHierarchyMutation {
    private GameObjectHierarchyMutation() {
    }

    /** Preserves the published world pose; a negative parent detaches. Requires current topology. */
    public static void reparent(World world, int child, int parent) {
        TiledEntityRole.requireGenericMember(world, child);
        if (parent >= 0) {
            TiledEntityRole.requireObjectParent(world, parent);
        }
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        if (hierarchy == null) {
            throw new IllegalStateException("Composition mutation requires a published hierarchy.");
        }
        GameObjectTopologyState topology = hierarchy.topologyForRemoval();
        requireNode(world, hierarchy, child);
        if (parent >= 0) {
            requireNode(world, hierarchy, parent);
        }
        int oldParent = topology.parentEntityId[child];
        if (oldParent == parent) {
            return;
        }
        for (int ancestor = parent; ancestor >= 0; ancestor = topology.parentEntityId[ancestor]) {
            if (ancestor == child) {
                throw new IllegalArgumentException("A composition cannot contain a cycle.");
            }
        }
        Box2dSyncSystem physics = world.getSystem(Box2dSyncSystem.class);
        if (physics != null && physics.getBox2d() != null && physics.getBox2d().world.isLocked()) {
            throw new IllegalStateException("Reparenting requires an unlocked physics world.");
        }
        boolean promote = parent >= 0 && !world.getMapper(GameObjectComponent.class).has(parent);
        TransformComponent childBefore = copy(world.getMapper(TransformComponent.class).get(child));
        TransformComponent childWorld = resolved(world, hierarchy.worldTransforms(), child);
        TransformComponent parentBefore = parent < 0 ? null : copy(world.getMapper(TransformComponent.class).get(parent));
        TransformComponent parentWorld = parent < 0 ? null : resolved(world, hierarchy.worldTransforms(), parent);
        TransformComponent parentAfter = parentBefore;
        if (parent >= 0) {
            GameObjectTransformMath.requirePositiveUniformParentScale(parentBefore);
            if (promote && topology.parentEntityId[parent] >= 0) {
                parentAfter = GameObjectTransformMath.worldToLocal(
                        resolved(world, hierarchy.worldTransforms(), topology.parentEntityId[parent]),
                        parentWorld, true, new TransformComponent());
            }
            boolean physical = hierarchy.containsPhysicsInSubtree(child)
                    || world.getMapper(PhysicsBodyComponent.class).has(parent);
            if (physical) {
                GameObjectTransformMath.requireUnitParentScale(parentAfter, "Physics");
                for (int ancestor = topology.parentEntityId[parent]; ancestor >= 0; ancestor = topology.parentEntityId[ancestor]) {
                    GameObjectTransformMath.requireUnitParentScale(world.getMapper(TransformComponent.class).get(ancestor), "Physics");
                }
            }
        }
        TransformComponent childAfter = parent < 0 ? childWorld
                : GameObjectTransformMath.worldToLocal(parentWorld, childWorld,
                world.getMapper(GameObjectComponent.class).has(child), new TransformComponent());
        if (world.getMapper(GameObjectComponent.class).has(child)) {
            GameObjectTransformMath.requirePositiveUniformParentScale(childAfter);
        }
        GameObjectMemberComponent membership = world.getMapper(GameObjectMemberComponent.class).getSafe(child, null);
        int oldStableParent = membership == null ? -1 : membership.parentStableId;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(child);
        int previousLayer = index.layerIndex;
        GameObjectCompositionSystem composition = world.getSystem(GameObjectCompositionSystem.class);
        int effectiveLayer = composition == null ? previousLayer
                : composition.state().effectiveLayer[parent < 0 ? child : parent];
        games.pixscape.runtime.service.SpatialRepresentationAuthoring.PreparedSubtree spatial =
                parent >= 0 && composition != null && composition.spatialOwner(parent) >= 0
                        ? games.pixscape.runtime.service.SpatialRepresentationAuthoring.prepareSubtree(world, child, topology) : null;
        try {
            if (spatial != null) {
                spatial.apply();
            }
            if (promote) {
                world.getMapper(GameObjectComponent.class).create(parent);
                apply(world.getMapper(TransformComponent.class).get(parent), parentAfter);
            }
            applyPreparedLocalPose(world, child, parent < 0 ? 0
                    : world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId, childAfter);
            index.layerIndex = effectiveLayer;
            hierarchy.publishAuthoredHierarchyMutation();
        } catch (RuntimeException | Error failure) {
            if (spatial != null) {
                spatial.restore();
            }
            applyPreparedLocalPose(world, child, oldStableParent < 0 ? 0 : oldStableParent, childBefore);
            index.layerIndex = previousLayer;
            if (promote) {
                world.getMapper(GameObjectComponent.class).remove(parent);
                apply(world.getMapper(TransformComponent.class).get(parent), parentBefore);
            }
            try {
                hierarchy.publishAuthoredHierarchyMutation();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /**
     * Captures the pose used to prepare a world-preserving hierarchy edit. Under authored authority,
     * reads current authored relations, including changes not yet published by the caller.
     * Runtime physics instead requires the published pose, whose Body boundaries are native-owned.
     * Does not publish ECS state or step Physics.
     */
    public static TransformComponent captureWorldPose(World world, int entity) {
        PhysicsPoseAuthority authority = world.getSystem(PhysicsPoseAuthority.class);
        if (authority != null && authority.isRuntimePhysics()) {
            GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
            if (hierarchy == null || !hierarchy.worldTransforms().isResolved(entity)) {
                throw new IllegalStateException("Hierarchy mutation requires a published world pose.");
            }
            return resolved(world, hierarchy.worldTransforms(), entity);
        }
        return captureAuthoredWorldPose(world, IdentityRegistry.boundTo(world), entity, 0);
    }

    private static TransformComponent captureAuthoredWorldPose(
            World world, IdentityRegistry identities, int entity, int depth) {
        if (depth > 1024 || entity < 0 || !world.getEntityManager().isActive(entity)) {
            throw new IllegalArgumentException("Hierarchy requires live parents and bounded depth.");
        }
        TransformComponent authored = world.getMapper(TransformComponent.class).getSafe(entity, null);
        if (authored == null) {
            throw new IllegalArgumentException("Hierarchy entity requires TransformComponent.");
        }
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
        if (member == null) {
            return copy(authored);
        }
        if (identities == null) {
            throw new IllegalStateException("Hierarchy conversion requires bound identities.");
        }
        TransformComponent parentWorld = captureAuthoredWorldPose(world, identities,
                identities.findByStableId(member.parentStableId), depth + 1);
        return GameObjectTransformMath.localToWorld(parentWorld, authored,
                world.getMapper(GameObjectComponent.class).has(entity), new TransformComponent());
    }

    /**
     * Applies an already validated local pose and relationship (zero means detached).
     * This deliberately performs NO world-preserving conversion. Reparent preparation computes
     * that conversion first; history restores its recorded local pose without recomputing it.
     * Callers validate the complete operation before writes and own promotion, layers, dirty/event
     * publication and rollback. No native Body/Joint is moved, rebuilt or stepped here.
     */
    public static void applyPreparedLocalPose(
            World world, int entity, int parentStableId, TransformComponent localPose) {
        apply(world.getMapper(TransformComponent.class).get(entity), localPose);
        if (parentStableId == 0) {
            world.getMapper(GameObjectMemberComponent.class).remove(entity);
        } else {
            world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = parentStableId;
        }
    }

    private static void requireNode(World world, GameObjectHierarchySystem hierarchy, int entity) {
        if (entity < 0 || !world.getEntityManager().isActive(entity)
                || !hierarchy.worldTransforms().isResolved(entity)
                || !world.getMapper(TransformComponent.class).has(entity)
                || !world.getMapper(EntityIndexComponent.class).has(entity)
                || !world.getMapper(PixscapeIdentityComponent.class).has(entity)
                || world.getMapper(PixscapeIdentityComponent.class).get(entity).stableId <= 0
                || world.getMapper(ParticleEmitterComponent.class).has(entity)
                || world.getMapper(TiledMapComponent.class).has(entity)
                || (world.getMapper(TiledMapLayerComponent.class).has(entity)
                    && TiledEntityRole.of(world, entity)
                    != TiledEntityRole.OBJECT_LAYER)
                || world.getMapper(TiledLayerComponent.class).has(entity)
                || world.getMapper(SpatialBlocksComponent.class).has(entity)
                || world.getMapper(SpatialShapesComponent.class).has(entity)
                || world.getMapper(PhysicsJointComponent.class).has(entity)) {
            throw new IllegalArgumentException("Reparenting requires live, published composition entities.");
        }
    }

    private static TransformComponent resolved(World world, WorldTransformState poses, int entity) {
        TransformComponent result = copy(world.getMapper(TransformComponent.class).get(entity));
        result.x = poses.x[entity];
        result.y = poses.y[entity];
        result.rotationRad = poses.rotationRad[entity];
        result.scaleX = poses.scaleX[entity];
        result.scaleY = poses.scaleY[entity];
        result.refreshCaches();
        return result;
    }

    private static TransformComponent copy(TransformComponent source) {
        TransformComponent result = new TransformComponent();
        apply(result, source);
        return result;
    }

    private static void apply(TransformComponent target, TransformComponent source) {
        target.x = source.x;
        target.y = source.y;
        target.rotationRad = source.rotationRad;
        target.scaleX = source.scaleX;
        target.scaleY = source.scaleY;
        target.originX = source.originX;
        target.originY = source.originY;
        target.refreshCaches();
    }
}
