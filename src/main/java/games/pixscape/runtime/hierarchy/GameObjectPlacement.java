package games.pixscape.runtime.hierarchy;

import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.LongMap;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.PhysicsPoseAuthority;

/**
 * Prepared rigid placement of current composition subtrees, using an owned history snapshot
 * or a reusable gameplay buffer. This is not another hierarchy or physics authority. Preparation visits the selected subtrees, their
 * ancestors and incident joints only; it never steps or processes the World.
 */
public final class GameObjectPlacement {
    private final World world;
    private final IdentityRegistry identities;
    private final GameObjectHierarchySystem hierarchy;
    private final Box2dSyncSystem physics;
    private final Array<Node> nodes = new Array<Node>();
    private final Array<Pulley> pulleys = new Array<Pulley>();
    private final IntArray rootStableIds = new IntArray();
    private final LongMap<Joint> jointKeys = new LongMap<Joint>();
    private final Array<Node> nodePool = new Array<Node>();
    private final Array<Pulley> pulleyPool = new Array<Pulley>();
    private final Array<TransformComponent> posePool = new Array<TransformComponent>();
    private int usedPoses;
    private final IntSet selected = new IntSet();
    private final IntSet included = new IntSet();
    private final IntSet currentScope = new IntSet();
    private final IntSet joints = new IntSet();
    private final IntArray ordered = new IntArray();
    private final IntArray stack = new IntArray();
    private final IntArray path = new IntArray();
    private final IntBag entities = new IntBag();
    private final IntMap<TransformComponent> current = new IntMap<TransformComponent>();
    private final IntMap<TransformComponent> target = new IntMap<TransformComponent>();
    private final Array<Joint> replacements = new Array<Joint>();
    private boolean prepared;
    private boolean busy;
    private final JointIdentities jointIdentities;
    private final boolean retainUndo;

    /** Adapter to an existing history identity registry; no second registry is created. */
    public interface JointIdentities {
        long capture(int entity);
        long keyOf(int entity);
        int resolve(long key);
    }

    private GameObjectPlacement(World world, JointIdentities jointIdentities, boolean retainUndo) {
        this.retainUndo = retainUndo;
        this.jointIdentities = jointIdentities;
        this.world = world;
        identities = IdentityRegistry.boundTo(world);
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        physics = world.getSystem(Box2dSyncSystem.class);
        if (identities == null || hierarchy == null) {
            throw new IllegalStateException("Composition placement requires published identities and hierarchy.");
        }
    }

    /**
     * Rotates around the supplied world pivot, then translates, in Pixscape world units/radians.
     * Every configured Body must be realized. Root selections may overlap and are normalized.
     */
    public static GameObjectPlacement prepare(World world, IntArray roots, float pivotX, float pivotY,
                                               float dx, float dy, float radians, boolean resetVelocities) {
        return prepare(world, roots, pivotX, pivotY, dx, dy, radians, resetVelocities, null);
    }

    public static GameObjectPlacement prepare(World world, IntArray roots, float pivotX, float pivotY,
                                               float dx, float dy, float radians, boolean resetVelocities,
                                               JointIdentities jointIdentities) {
        if (world == null || roots == null || roots.size == 0) {
            throw new IllegalArgumentException("Composition placement requires a World and selected roots.");
        }
        GameObjectPlacement result = new GameObjectPlacement(world, jointIdentities, true);
        return result.prepareNext(roots, pivotX, pivotY, dx, dy, radians, resetVelocities);
    }

    /** Creates reusable gameplay storage without undo. Do not retain it beyond its World's lifetime. */
    public static GameObjectPlacement buffer(World world, int nodes, int joints) {
        GameObjectPlacement result = new GameObjectPlacement(world, null, false);
        result.reserve(nodes, joints);
        return result;
    }

    /** Reserves scratch and snapshot storage; never creates entities or native resources. */
    public void reserve(int nodeCapacity, int jointCapacity) {
        if (busy || nodeCapacity < 0 || jointCapacity < 0) {
            throw new IllegalArgumentException("Placement capacities must be non-negative and the buffer idle.");
        }
        while (nodePool.size < nodeCapacity) {
            nodePool.add(new Node(retainUndo));
        }
        while (posePool.size < nodeCapacity) {
            posePool.add(new TransformComponent());
        }
        while (pulleyPool.size < jointCapacity) {
            pulleyPool.add(new Pulley());
        }
        nodes.ensureCapacity(Math.max(0, nodeCapacity - nodes.size));
        pulleys.ensureCapacity(Math.max(0, jointCapacity - pulleys.size));
        rootStableIds.ensureCapacity(nodeCapacity);
        selected.ensureCapacity(nodeCapacity);
        included.ensureCapacity(nodeCapacity);
        currentScope.ensureCapacity(nodeCapacity);
        ordered.ensureCapacity(nodeCapacity);
        stack.ensureCapacity(nodeCapacity);
        path.ensureCapacity(nodeCapacity);
        entities.ensureCapacity(nodeCapacity);
        current.ensureCapacity(nodeCapacity);
        target.ensureCapacity(nodeCapacity);
        joints.ensureCapacity(jointCapacity);
        jointKeys.ensureCapacity(jointCapacity);
        replacements.ensureCapacity(jointCapacity);
        // IntSet has no public indexed traversal. Each iterator() initializes its reusable pair;
        // these two sets are never iterated recursively or while another traversal of them is live.
        included.iterator();
        joints.iterator();
    }

    /**
     * Replaces the previous snapshot using current topology. Not reentrant or thread-safe.
     * History commands must own separate buffers; reusing one discards its previous undo state.
     */
    public GameObjectPlacement prepareNext(IntArray roots, float pivotX, float pivotY,
                                           float dx, float dy, float radians, boolean resetVelocities) {
        if (busy) {
            throw new IllegalStateException("Composition placement buffer is already in use.");
        }
        busy = true;
        prepared = false;
        try {
            prepareState(roots, pivotX, pivotY, dx, dy, radians, resetVelocities);
            prepared = true;
            return this;
        } finally {
            busy = false;
        }
    }

    /** Releases borrowed Joint references while retaining reusable storage. */
    public void clear() {
        if (busy) {
            throw new IllegalStateException("Composition placement buffer is already in use.");
        }
        prepared = false;
        jointKeys.clear();
        replacements.clear();
    }

    private void prepareState(IntArray roots, float pivotX, float pivotY,
                              float dx, float dy, float radians, boolean resetVelocities) {
        if (roots == null || roots.size == 0) {
            throw new IllegalArgumentException("Composition placement requires selected roots.");
        }
        requireFinite(pivotX, pivotY);
        requireFinite(dx, dy, radians);
        nodes.clear();
        entities.clear();
        pulleys.clear();
        rootStableIds.clear();
        jointKeys.clear();
        current.clear();
        target.clear();
        usedPoses = 0;
        requireUnlocked();
        GameObjectTopologyState topology = hierarchy.topologyForRemoval();
        selected.clear();
        for (int i = 0; i < roots.size; i++) {
            int entity = roots.get(i);
            requireEntity(entity);
            selected.add(entity);
        }
        ordered.clear();
        stack.clear();
        currentScope.clear();
        for (int i = 0; i < roots.size; i++) {
            int root = roots.get(i);
            if (!selected.contains(root)) {
                continue;
            }
            boolean covered = false;
            for (int parent = topology.parentEntityId[root]; parent >= 0;
                 parent = topology.parentEntityId[parent]) {
                if (selected.contains(parent)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                int stableId = identities.getStableId(root);
                if (currentScope.add(root)) {
                    rootStableIds.add(stableId);
                    stack.add(root);
                }
            }
        }
        included.clear();
        while (stack.size > 0) {
            int entity = stack.pop();
            if (!included.add(entity)) {
                continue;
            }
            ordered.add(entity);
            for (int child = topology.firstChildEntityId[entity]; child >= 0;
                 child = topology.nextSiblingEntityId[child]) {
                stack.add(child);
            }
        }
        IntSet joints = validateJoints(included);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (int i = 0; i < ordered.size; i++) {
            int entity = ordered.get(i);
            requireEntity(entity);
            if (nodes.size == nodePool.size) {
                nodePool.add(new Node(retainUndo));
            }
            Node node = nodePool.get(nodes.size);
            node.stableId = identities.getStableId(entity);
            node.parentStableId = parentStableId(entity);
            if (retainUndo) {
                assign(node.beforeLocal, world.getMapper(TransformComponent.class).get(entity));
            }
            entities.add(entity);
            node.beforeWorld = resolveCurrent(entity, topology, current);
            assign(node.afterWorld, node.beforeWorld);
            transformPoint(node.afterWorld, pivotX, pivotY, dx, dy, cos, sin);
            node.afterWorld.rotationRad += radians;
            requireFinite(node.afterWorld.x, node.afterWorld.y, node.afterWorld.rotationRad);
            target.put(entity, node.afterWorld);
            int parent = topology.parentEntityId[entity];
            TransformComponent parentWorld = parent < 0 ? null : target.get(parent);
            if (parent >= 0 && parentWorld == null) {
                parentWorld = resolveCurrent(parent, topology, current);
            }
            if (parentWorld == null) {
                assign(node.afterLocal, node.afterWorld);
            } else {
                GameObjectTransformMath.worldToLocal(parentWorld, node.afterWorld,
                        world.getMapper(GameObjectComponent.class).has(entity), node.afterLocal);
            }
            node.physical = world.getMapper(PhysicsBodyComponent.class).has(entity);
            if (node.physical) {
                Body body = requireBody(entity);
                node.vx = body.getLinearVelocity().x;
                node.vy = body.getLinearVelocity().y;
                node.omega = body.getAngularVelocity();
                node.afterVx = resetVelocities ? 0f : cos * node.vx - sin * node.vy;
                node.afterVy = resetVelocities ? 0f : sin * node.vx + cos * node.vy;
                node.afterOmega = resetVelocities ? 0f : node.omega;
            }
            nodes.add(node);
        }
        for (IntSet.IntSetIterator iterator = joints.iterator(); iterator.hasNext;) {
            int joint = iterator.next();
            PhysicsPulleyJointComponent source = world.getMapper(PhysicsPulleyJointComponent.class).getSafe(joint, null);
            if (source != null) {
                float ppm = physics.getBox2d().ppm;
                if (pulleys.size == pulleyPool.size) {
                    pulleyPool.add(new Pulley());
                }
                Pulley pulley = pulleyPool.get(pulleys.size);
                pulley.entity = joint;
                pulley.before[0] = source.groundAx;
                pulley.before[1] = source.groundAy;
                pulley.before[2] = source.groundBx;
                pulley.before[3] = source.groundBy;
                for (int j = 0; j < 4; j += 2) {
                    float x = pulley.before[j] * ppm - pivotX;
                    float y = pulley.before[j + 1] * ppm - pivotY;
                    pulley.after[j] = (pivotX + cos * x - sin * y + dx) / ppm;
                    pulley.after[j + 1] = (pivotY + sin * x + cos * y + dy) / ppm;
                    requireFinite(pulley.after[j], pulley.after[j + 1]);
                }
                pulleys.add(pulley);
            }
        }
        // Allocate optional history bindings only after every refusal-prone validation.
        for (IntSet.IntSetIterator iterator = joints.iterator(); iterator.hasNext;) {
            int joint = iterator.next();
            long key = jointIdentities == null ? jointKey(joint) : jointIdentities.capture(joint);
            jointKeys.put(key, world.getMapper(PhysicsRuntimeJointComponent.class).get(joint).joint);
        }
        for (int i = 0; i < pulleys.size; i++) {
            Pulley pulley = pulleys.get(i);
            pulley.key = jointKey(pulley.entity);
        }
    }

    public void applyAfter() {
        apply(true);
    }

    /** Restores the exact captured state, resolving stable identities again for Studio undo. */
    public void applyBefore() {
        if (!retainUndo) {
            throw new IllegalStateException("Gameplay placement buffers do not retain undo state.");
        }
        apply(false);
    }

    /**
     * Validates and publishes synchronously. No user callback or publication boundary intervenes,
     * so the freshly checked membership need not be traversed a second time. This never exposes
     * the snapshot; deferred/history apply paths still revalidate all identities and membership.
     */
    public void placeNow(IntArray roots, float pivotX, float pivotY, float dx, float dy,
                         float radians, boolean resetVelocities) {
        if (busy) {
            throw new IllegalStateException("Composition placement buffer is already in use.");
        }
        busy = true;
        prepared = false;
        try {
            prepareState(roots, pivotX, pivotY, dx, dy, radians, resetVelocities);
            applyState(true, false);
        } finally {
            busy = false;
            clear();
        }
    }

    private void apply(boolean after) {
        if (busy || !prepared) {
            throw new IllegalStateException("Composition placement requires an idle prepared snapshot.");
        }
        busy = true;
        try {
            applyState(after, true);
        } finally {
            replacements.clear();
            busy = false;
        }
    }

    private void applyState(boolean after, boolean revalidate) {
        requireUnlocked();
        if (revalidate) {
            included.clear();
            entities.clear();
            for (int i = 0; i < nodes.size; i++) {
                Node node = nodes.get(i);
                int entity = identities.findByStableId(node.stableId);
                requireEntity(entity);
                if (parentStableId(entity) != node.parentStableId
                        || world.getMapper(PhysicsBodyComponent.class).has(entity) != node.physical) {
                    throw new IllegalStateException("Composition changed since placement preparation: stableId " + node.stableId);
                }
                if (node.physical) {
                    requireBody(entity);
                }
                included.add(entity);
                entities.add(entity);
            }
            GameObjectTopologyState topology = hierarchy.topologyForRemoval();
            currentScope.clear();
            stack.clear();
            IntArray pending = stack;
            for (int i = 0; i < rootStableIds.size; i++) {
                pending.add(identities.findByStableId(rootStableIds.get(i)));
            }
            while (pending.size > 0) {
                int entity = pending.pop();
                requireEntity(entity);
                if (!currentScope.add(entity)) {
                    continue;
                }
                for (int child = topology.firstChildEntityId[entity]; child >= 0;
                     child = topology.nextSiblingEntityId[child]) {
                    pending.add(child);
                }
            }
            if (!currentScope.equals(included)) {
                throw new IllegalStateException("Composition membership changed since placement preparation.");
            }
            IntSet joints = validateJoints(included);
            if (joints.size != jointKeys.size) {
                throw new IllegalStateException("Composition joints changed since placement preparation.");
            }
            for (IntSet.IntSetIterator iterator = joints.iterator(); iterator.hasNext;) {
                int entity = iterator.next();
                long key = jointKey(entity);
                Joint captured = jointKeys.get(key);
                if (captured == null || (jointIdentities == null && key < 0
                        && captured != world.getMapper(PhysicsRuntimeJointComponent.class).get(entity).joint)) {
                    throw new IllegalStateException("Composition joints changed since placement preparation.");
                }
            }
        }
        // Prepare replacement pulleys while retaining the originals. No simulation runs here.
        replacements.clear();
        try {
            for (int i = 0; i < pulleys.size; i++) {
                Pulley pulley = pulleys.get(i);
                float[] anchors = after ? pulley.after : pulley.before;
                replacements.add(physics.preparePulleyPlacement(resolveJoint(pulley.key), anchors));
            }
        } catch (RuntimeException failure) {
            for (int i = 0; i < replacements.size; i++) {
                physics.getBox2d().world.destroyJoint(replacements.get(i));
            }
            throw failure;
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        for (int i = 0; i < nodes.size; i++) {
            Node node = nodes.get(i);
            int entity = entities.get(i);
            assign(world.getMapper(TransformComponent.class).get(entity), after ? node.afterLocal : node.beforeLocal);
            if (node.physical) {
                Body body = requireBody(entity);
                TransformComponent pose = after ? node.afterWorld : node.beforeWorld;
                Box2dWorldService box2d = physics.getBox2d();
                body.setTransform(box2d.pxToM(pose.x), box2d.pxToM(pose.y), pose.rotationRad);
                body.setLinearVelocity(after ? node.afterVx : node.vx, after ? node.afterVy : node.vy);
                body.setAngularVelocity(after ? node.afterOmega : node.omega);
                body.setAwake(true);
            }
            if (dirty != null) {
                dirty.geometry(entity, GeometryDirty.ALL);
            }
        }
        for (int i = 0; i < pulleys.size; i++) {
            Pulley pulley = pulleys.get(i);
            int entity = resolveJoint(pulley.key);
            float[] anchors = after ? pulley.after : pulley.before;
            PhysicsPulleyJointComponent value = world.getMapper(PhysicsPulleyJointComponent.class).get(entity);
            value.groundAx = anchors[0];
            value.groundAy = anchors[1];
            value.groundBx = anchors[2];
            value.groundBy = anchors[3];
            physics.publishPulleyPlacement(entity, replacements.get(i));
            jointKeys.put(pulley.key, replacements.get(i));
        }
        hierarchy.resolvePreparedSpawn(entities);
        PhysicsPoseAuthority authority = world.getSystem(PhysicsPoseAuthority.class);
        if (authority == null || !authority.isRuntimePhysics()) {
            WorldTransformState state = hierarchy.worldTransforms();
            for (int i = 0; i < nodes.size; i++) {
                if (nodes.get(i).physical) {
                    int entity = entities.get(i);
                    PhysicsRuntimeBodyComponent runtime = world.getMapper(PhysicsRuntimeBodyComponent.class).get(entity);
                    runtime.placedDesignPose = true;
                    runtime.placedDesignX = physics.getBox2d().pxToM(state.x[entity]);
                    runtime.placedDesignY = physics.getBox2d().pxToM(state.y[entity]);
                    runtime.placedDesignAngle = state.rotationRad[entity];
                }
            }
        }
    }

    private long jointKey(int entity) {
        if (jointIdentities != null) {
            return jointIdentities.keyOf(entity);
        }
        int stableId = identities.getStableId(entity);
        return stableId > 0 ? stableId : -(entity + 1L);
    }

    private int resolveJoint(long key) {
        if (jointIdentities != null) {
            return jointIdentities.resolve(key);
        }
        return key > 0 ? identities.findByStableId((int) key) : (int) (-key - 1);
    }

    private IntSet validateJoints(IntSet included) {
        joints.clear();
        if (physics == null) {
            return joints;
        }
        for (IntSet.IntSetIterator iterator = included.iterator(); iterator.hasNext;) {
            int entity = iterator.next();
            IntArray incident = physics.jointDependencies().jointsForBody(entity);
            if (incident != null) {
                for (int i = 0; i < incident.size; i++) {
                    joints.add(incident.get(i));
                }
            }
        }
        for (IntSet.IntSetIterator iterator = joints.iterator(); iterator.hasNext;) {
            int entity = iterator.next();
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(entity);
            if (!included.contains(base.aEid) || !included.contains(base.bEid)) {
                throw new IllegalArgumentException("Placement crosses joint stableId " + identities.getStableId(entity)
                        + "; include both compositions explicitly.");
            }
            PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).getSafe(entity, null);
            if (gear != null && (!joints.contains(gear.joint1Eid) || !joints.contains(gear.joint2Eid))) {
                throw new IllegalArgumentException("Placement crosses Gear dependencies at stableId " + identities.getStableId(entity));
            }
            PhysicsRuntimeJointComponent runtime = world.getMapper(PhysicsRuntimeJointComponent.class).getSafe(entity, null);
            if (runtime == null || runtime.joint == null) {
                throw new IllegalStateException("Placement requires realized joint stableId " + identities.getStableId(entity));
            }
            if (base.type == PhysicsJointComponent.TYPE_MOUSE) {
                throw new IllegalArgumentException("Composition placement does not support mouse joints.");
            }
        }
        return joints;
    }

    private TransformComponent resolveCurrent(int entity, GameObjectTopologyState topology,
                                               IntMap<TransformComponent> resolved) {
        TransformComponent existing = resolved.get(entity);
        if (existing != null) {
            return existing;
        }
        path.clear();
        int current = entity;
        while (current >= 0 && !resolved.containsKey(current)) {
            path.add(current);
            current = topology.parentEntityId[current];
        }
        for (int i = path.size - 1; i >= 0; i--) {
            current = path.get(i);
            TransformComponent local = world.getMapper(TransformComponent.class).get(current);
            int parent = topology.parentEntityId[current];
            if (usedPoses == posePool.size) {
                posePool.add(new TransformComponent());
            }
            TransformComponent pose = posePool.get(usedPoses++);
            if (parent < 0) {
                assign(pose, local);
            } else {
                GameObjectTransformMath.localToWorld(resolved.get(parent), local,
                        world.getMapper(GameObjectComponent.class).has(current), pose);
            }
            PhysicsPoseAuthority authority = world.getSystem(PhysicsPoseAuthority.class);
            PhysicsRuntimeBodyComponent runtime = world.getMapper(PhysicsRuntimeBodyComponent.class).getSafe(current, null);
            if (authority != null && authority.isRuntimePhysics() && runtime != null && runtime.body != null) {
                com.badlogic.gdx.math.Vector2 position = runtime.body.getPosition();
                pose.x = physics.getBox2d().mToPx(position.x);
                pose.y = physics.getBox2d().mToPx(position.y);
                pose.rotationRad = runtime.body.getAngle();
            }
            resolved.put(current, pose);
        }
        return resolved.get(entity);
    }

    private void requireUnlocked() {
        if (physics != null && physics.getBox2d() != null && physics.getBox2d().world != null
                && physics.getBox2d().world.isLocked()) {
            throw new IllegalStateException("Composition placement is unavailable while Box2D is locked.");
        }
    }

    private Body requireBody(int entity) {
        PhysicsRuntimeBodyComponent runtime = world.getMapper(PhysicsRuntimeBodyComponent.class).getSafe(entity, null);
        if (physics == null || physics.getBox2d() == null || physics.getBox2d().world == null
                || runtime == null || runtime.body == null || runtime.body.getWorld() != physics.getBox2d().world) {
            throw new IllegalStateException("Placement requires realized Body stableId " + identities.getStableId(entity));
        }
        return runtime.body;
    }

    private void requireEntity(int entity) {
        if (entity < 0 || !world.getEntityManager().isActive(entity)
                || identities.getStableId(entity) <= 0 || !world.getMapper(TransformComponent.class).has(entity)) {
            throw new IllegalArgumentException("Placement requires an active, indexed composition entity: " + entity);
        }
    }

    private int parentStableId(int entity) {
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
        return member == null ? -1 : member.parentStableId;
    }

    private static void transformPoint(TransformComponent pose, float px, float py,
                                       float dx, float dy, float cos, float sin) {
        float x = pose.x - px;
        float y = pose.y - py;
        pose.x = px + cos * x - sin * y + dx;
        pose.y = py + sin * x + cos * y + dy;
    }

    private static void assign(TransformComponent target, TransformComponent source) {
        target.x = source.x;
        target.y = source.y;
        target.rotationRad = source.rotationRad;
        target.originX = source.originX;
        target.originY = source.originY;
        target.scaleX = source.scaleX;
        target.scaleY = source.scaleY;
        target.refreshCaches();
    }

    private static void requireFinite(float x, float y) {
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) {
            throw new IllegalArgumentException("Composition placement requires finite coordinates and angles.");
        }
    }

    private static void requireFinite(float x, float y, float angle) {
        requireFinite(x, y);
        requireFinite(angle, 0f);
    }

    private static final class Node {
        int stableId, parentStableId;
        boolean physical;
        final TransformComponent beforeLocal;
        final TransformComponent afterLocal = new TransformComponent();
        TransformComponent beforeWorld;
        final TransformComponent afterWorld = new TransformComponent();
        float vx, vy, omega, afterVx, afterVy, afterOmega;

        Node(boolean retainUndo) {
            beforeLocal = retainUndo ? new TransformComponent() : null;
        }
    }

    private static final class Pulley {
        int entity;
        long key;
        final float[] before = new float[4];
        final float[] after = new float[4];
    }
}
