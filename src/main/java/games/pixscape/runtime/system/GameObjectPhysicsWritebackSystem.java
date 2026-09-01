package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.physics.box2d.Body;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.Box2dWorldService;

import java.util.Arrays;

/**
 * Post-Physics phase that reconstructs authored hierarchy transforms from native Body world poses.
 *
 * <p>Native poses are snapshotted before authored state changes, then consumed in the hierarchy's
 * deterministic parent-first traversal. This preserves independent physical children: a parent
 * Body never transports a child Body merely because the entities are compositionally related.</p>
 */
public final class GameObjectPhysicsWritebackSystem extends BaseSystem {
    private static final float EPSILON = 1e-6f;

    private GameObjectHierarchySystem hierarchy;
    private PhysicsPoseAuthority poseAuthority;
    private Box2dSyncSystem box2dSync;
    private DirtyTrackerSystem dirty;

    private ComponentMapper<TransformComponent> transforms;
    private ComponentMapper<GameObjectComponent> gameObjects;
    private ComponentMapper<PhysicsRuntimeBodyComponent> runtimeBodies;
    private EntitySubscription realizedBodies;

    private int[] poseFrame = new int[16];
    private float[] poseX = new float[16];
    private float[] poseY = new float[16];
    private float[] poseRotation = new float[16];
    private int frame = 1;

    private final Affine2 parentFrame = new Affine2();
    private final Affine2 desiredWorldFrame = new Affine2();
    private final Affine2 localFrame = new Affine2();
    private final TransformComponent desiredWorldTransform = new TransformComponent();
    private final TransformComponent localTransform = new TransformComponent();

    @Override
    protected void initialize() {
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        poseAuthority = world.getSystem(PhysicsPoseAuthority.class);
        box2dSync = world.getSystem(Box2dSyncSystem.class);
        dirty = world.getSystem(DirtyTrackerSystem.class);
        if (hierarchy == null || poseAuthority == null || box2dSync == null) {
            throw new IllegalStateException(
                    "GameObjectPhysicsWritebackSystem requires hierarchy, pose authority, and Box2D sync.");
        }

        transforms = world.getMapper(TransformComponent.class);
        gameObjects = world.getMapper(GameObjectComponent.class);
        runtimeBodies = world.getMapper(PhysicsRuntimeBodyComponent.class);
        realizedBodies = world.getAspectSubscriptionManager().get(
                Aspect.all(TransformComponent.class, PhysicsRuntimeBodyComponent.class));
    }

    @Override
    protected void processSystem() {
        if (!poseAuthority.isRuntimePhysics()) return;
        Box2dWorldService box2d = box2dSync.getBox2d();
        if (box2d == null || box2d.world == null) return;

        nextFrame();
        captureNativePoses(box2d);

        hierarchy.ensureCurrentTopology();
        GameObjectTopologyState topology = hierarchy.topology();
        WorldTransformState worldTransforms = hierarchy.worldTransforms();
        for (int i = 0, n = topology.traversal.size; i < n; i++) {
            int entityId = topology.traversal.get(i);
            boolean hasNativePose = hasPose(entityId);
            if (hasNativePose) {
                writeAuthoredPose(entityId, topology.parentEntityId[entityId], worldTransforms);
            }
            hierarchy.resolveEntityFromCurrentAuthoredTransform(entityId, !hasNativePose);
        }
    }

    private void captureNativePoses(Box2dWorldService box2d) {
        IntBag entities = realizedBodies.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            PhysicsRuntimeBodyComponent runtime = runtimeBodies.getSafe(entityId, null);
            Body body = runtime != null ? runtime.body : null;
            if (body == null) continue;
            ensureCapacity(entityId);
            poseFrame[entityId] = frame;
            poseX[entityId] = box2d.mToPx(body.getPosition().x);
            poseY[entityId] = box2d.mToPx(body.getPosition().y);
            poseRotation[entityId] = body.getAngle();
        }
    }

    private void writeAuthoredPose(int entityId, int parentEntityId,
                                   WorldTransformState worldTransforms) {
        TransformComponent authored = transforms.getSafe(entityId, null);
        if (authored == null) return;

        float targetX;
        float targetY;
        float targetRotation;
        if (parentEntityId < 0) {
            targetX = poseX[entityId];
            targetY = poseY[entityId];
            targetRotation = poseRotation[entityId];
        } else {
            if (!worldTransforms.isResolved(parentEntityId)) {
                throw new IllegalStateException("Physics writeback requires a resolved parent frame at entityId "
                        + entityId + ".");
            }
            parentFrame.m00 = worldTransforms.m00[parentEntityId];
            parentFrame.m01 = worldTransforms.m01[parentEntityId];
            parentFrame.m02 = worldTransforms.m02[parentEntityId];
            parentFrame.m10 = worldTransforms.m10[parentEntityId];
            parentFrame.m11 = worldTransforms.m11[parentEntityId];
            parentFrame.m12 = worldTransforms.m12[parentEntityId];

            desiredWorldTransform.x = poseX[entityId];
            desiredWorldTransform.y = poseY[entityId];
            desiredWorldTransform.rotationRad = poseRotation[entityId];
            desiredWorldTransform.scaleX = authored.scaleX;
            desiredWorldTransform.scaleY = authored.scaleY;
            desiredWorldTransform.originX = authored.originX;
            desiredWorldTransform.originY = authored.originY;
            GameObjectTransformMath.toMemberFrame(
                    desiredWorldTransform, gameObjects.has(entityId), desiredWorldFrame);
            GameObjectTransformMath.worldToLocal(parentFrame, desiredWorldFrame, localFrame);
            GameObjectTransformMath.extract(
                    localFrame, authored.originX, authored.originY, localTransform);
            targetX = localTransform.x;
            targetY = localTransform.y;
            targetRotation = localTransform.rotationRad;
        }

        int mask = GeometryDirty.NONE;
        if (Math.abs(authored.x - targetX) > EPSILON || Math.abs(authored.y - targetY) > EPSILON) {
            authored.x = targetX;
            authored.y = targetY;
            mask |= GeometryDirty.POSITION;
        }
        if (Math.abs(authored.rotationRad - targetRotation) > EPSILON) {
            authored.rotationRad = targetRotation;
            mask |= GeometryDirty.ROTATION;
        }
        if (mask != GeometryDirty.NONE) {
            authored.refreshCaches();
            if (dirty != null) dirty.geometry(entityId, mask);
        }
    }

    private boolean hasPose(int entityId) {
        return entityId >= 0 && entityId < poseFrame.length && poseFrame[entityId] == frame;
    }

    private void nextFrame() {
        if (frame == Integer.MAX_VALUE) {
            Arrays.fill(poseFrame, 0);
            frame = 1;
            return;
        }
        frame++;
    }

    private void ensureCapacity(int entityId) {
        if (entityId < poseFrame.length) return;
        int next = poseFrame.length;
        while (next <= entityId) next <<= 1;
        poseFrame = Arrays.copyOf(poseFrame, next);
        poseX = Arrays.copyOf(poseX, next);
        poseY = Arrays.copyOf(poseY, next);
        poseRotation = Arrays.copyOf(poseRotation, next);
    }
}
