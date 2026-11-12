package games.pixscape.runtime.system;

import com.artemis.*;
import com.artemis.annotations.SkipWire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectIntMap;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.JointDependencyIndex;
import games.pixscape.runtime.physics.PhysicsFixtureProvenance;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;


/**
 * {@code SUPPORTED_EXPERT} synchronization boundary from authored physics ECS state to native
 * Box2D state.
 *
 * <p>In authoring mode, the authored ECS hierarchy owns native Body pose. In Runtime Physics mode,
 * the native Body owns physical world pose and {@link GameObjectPhysicsWritebackSystem} reconstructs
 * authored world/local transforms. Fixtures and Body definitions remain authored ECS state; native
 * bodies and joints are derived resources that may be rebuilt. Replacing or re-enabling the bound
 * {@link Box2dWorldService} schedules the required body/fixture bootstrap and joint rebuild before
 * normal stepping. The system is World-owned, phase-sensitive, and not thread-safe.</p>
 *
 * <p>Body membership changes are observed through ECS subscriptions. In-place edits to authored
 * physics fields or compiled fixture publication must mark the entity through
 * {@link DirtyTrackerSystem#physics(int, int)}. Stable frames do not scan bodies to discover
 * unannounced changes. Native ownership is retained independently of pooled ECS components.</p>
 */
public final class Box2dSyncSystem extends BaseSystem implements ProfiledSystem {
    private static final float TRANSFORM_SYNC_EPSILON = 1e-6f;

    private Box2dWorldService box2d;
    private SceneMetaRuntime sceneMeta;

    private ComponentMapper<TransformComponent> mT;
    private ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private ComponentMapper<TiledLayerComponent> mTiled;
    private ComponentMapper<PhysicsBodyComponent> mBodyDef;
    private ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled;
    private ComponentMapper<PhysicsRuntimeBodyComponent> mRuntime;

    private ComponentMapper<PhysicsJointComponent> mJointBase;
    private ComponentMapper<PhysicsRuntimeJointComponent> mJointRt;

    private ComponentMapper<PhysicsDistanceJointComponent> mJointDist;
    private ComponentMapper<PhysicsRevoluteJointComponent> mJointRev;
    private ComponentMapper<PhysicsPrismaticJointComponent> mJointPrism;
    private ComponentMapper<PhysicsWheelJointComponent> mJointWheel;
    private ComponentMapper<PhysicsFrictionJointComponent> mFriction;
    private ComponentMapper<PhysicsMotorJointComponent> mMotor;
    private ComponentMapper<PhysicsWeldJointComponent> mWeld;
    private ComponentMapper<PhysicsPulleyJointComponent> mPulley;
    private ComponentMapper<PhysicsGearJointComponent> mGear;


    private DirtyTrackerSystem dirty;
    private final DirtyTrackerSystem.IntConsumer physicsDirtyConsumer = this::synchronizeDirtyBody;
    private final DirtyTrackerSystem.IntConsumer jointDirtyConsumer = this::syncOneJoint;
    @SkipWire
    private GameObjectHierarchySystem hierarchy;
    @SkipWire
    private PhysicsPoseAuthority poseAuthority;

    private EntitySubscription subWanted;   // T + Body
    private EntitySubscription subRuntime;  // RuntimeBody
    private EntitySubscription jointsSub;   // JointBase insert/remove only
    private EntitySubscription allEntities;

    private final Vector2 tmp = new Vector2();
    private static final int MAX_POLYGON_VERTICES = 8;
    private final float[] polygonVertsScratch = new float[MAX_POLYGON_VERTICES * 2];
    private transient TestObserver testObserver;

    private float lastGx = Float.NaN;
    private float lastGy = Float.NaN;
    private boolean stepEnabled = false;
    private boolean fullRebuildPending = true;
    private boolean bootstrapStepPending = false;

    // Structural notifications survive disabled frames and are reconciled before stepping.
    private final IntArray pendingBodies = new IntArray(false, 16);
    private final IntSet pendingBodySet = new IntSet();

    private final JointDependencyIndex jointDependencies = new JointDependencyIndex();
    // Native ownership can lag authored endpoints until ECS synchronization.
    private final ObjectIntMap<Joint> nativeJointEntities = new ObjectIntMap<>();
    private final IntArray removalQueue = new IntArray(false, 16);
    private final IntSet removalSeen = new IntSet();

    // Sole native ownership authority for this binding. Runtime components only mirror it;
    // pooling or delayed subscription observation must not abandon a live body.
    private final IntMap<Body> nativeBodies = new IntMap<>();
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public Box2dSyncSystem(Box2dWorldService box2d) {
        this.box2d = box2d;
    }

    public void setBox2d(Box2dWorldService box2d) {
        if (this.box2d != box2d) {
            nativeJointEntities.clear();
            nativeBodies.clear();
        }
        this.box2d = box2d;
        lastGx = Float.NaN;
        lastGy = Float.NaN;
        fullRebuildPending = true;
        if (box2d != null && box2d.world != null) {
            bootstrapStepPending = true;
        }
    }

    public Box2dWorldService getBox2d() {
        return box2d;
    }

    void setTestObserver(TestObserver observer) {
        this.testObserver = observer;
    }

    public void setSceneMeta(SceneMetaRuntime meta) {
        this.sceneMeta = meta;
        lastGx = Float.NaN;
        lastGy = Float.NaN;
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = isEnabled();
        super.setEnabled(enabled);
        if (enabled && !wasEnabled) {
            fullRebuildPending = true;
            bootstrapStepPending = true;
        }
    }

    @Override
    protected void initialize() {
        mT = world.getMapper(TransformComponent.class);
        mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        mTiled = world.getMapper(TiledLayerComponent.class);
        mBodyDef = world.getMapper(PhysicsBodyComponent.class);
        mCompiled = world.getMapper(PhysicsCompiledFixturesComponent.class);
        mRuntime = world.getMapper(PhysicsRuntimeBodyComponent.class);

        mJointBase = world.getMapper(PhysicsJointComponent.class);
        mJointRt = world.getMapper(PhysicsRuntimeJointComponent.class);

        mJointDist = world.getMapper(PhysicsDistanceJointComponent.class);
        mJointRev = world.getMapper(PhysicsRevoluteJointComponent.class);
        mJointPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        mJointWheel = world.getMapper(PhysicsWheelJointComponent.class);
        mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
        mMotor = world.getMapper(PhysicsMotorJointComponent.class);
        mWeld = world.getMapper(PhysicsWeldJointComponent.class);
        mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
        mGear = world.getMapper(PhysicsGearJointComponent.class);


        dirty = world.getSystem(DirtyTrackerSystem.class);
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        poseAuthority = world.getSystem(PhysicsPoseAuthority.class);

        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();

        subWanted = asm.get(Aspect.all(
                TransformComponent.class,
                PhysicsBodyComponent.class
        ));
        EntitySubscription.SubscriptionListener bodyChanges = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                for (int i = 0; i < entities.size(); i++) {
                    int entity = entities.get(i);
                    Body owner = nativeBodies.get(entity);
                    PhysicsRuntimeBodyComponent rt = mRuntime.getSafe(entity, null);
                    // Prepared spawns publish before Artemis observes their components.
                    // Real subsequent mutations still have their own dirty/removal tickets.
                    if (owner == null || rt == null || rt.body != owner || !isWantedEntity(entity)) {
                        queueBody(entity);
                    }
                }
            }

            @Override
            public void removed(IntBag entities) {
                enqueue(entities);
            }

            private void enqueue(IntBag entities) {
                for (int i = 0; i < entities.size(); i++) queueBody(entities.get(i));
            }
        };
        subWanted.addSubscriptionListener(bodyChanges);
        // Authored body removal must also remove its joints in the same mutation.
        // Inspect only indexed dependents; native Physics may be disabled or unavailable.
        asm.get(Aspect.all(PhysicsBodyComponent.class)).addSubscriptionListener(
                new EntitySubscription.SubscriptionListener() {
                    @Override
                    public void inserted(IntBag entities) {
                    }

                    @Override
                    public void removed(IntBag entities) {
                        for (int i = 0; i < entities.size(); i++) {
                            int body = entities.get(i);
                            if (allEntities.getActiveEntityIds().get(body) && mBodyDef.has(body)) continue;
                            IntArray dependents = jointDependencies.jointsForBody(body);
                            if (dependents == null) continue;
                            for (int j = 0; j < dependents.size; j++) {
                                int joint = dependents.get(j);
                                // Artemis retains deleted components until after these callbacks.
                                // Aspect.all has already published the complete batch of deletions.
                                if (!allEntities.getActiveEntityIds().get(joint)) continue;
                                PhysicsJointComponent base = mJointBase.getSafe(joint, null);
                                if (base == null) continue;
                                PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).getSafe(joint, null);
                                throw new IllegalArgumentException("Body removal left an authored joint: entityId="
                                        + joint + ", stableId=" + (identity != null ? identity.stableId : -1)
                                        + ", removedBody=" + body + ".");
                            }
                        }
                    }
                });
        asm.get(Aspect.all(PhysicsCompiledFixturesComponent.class)).addSubscriptionListener(bodyChanges);
        asm.get(Aspect.all(TiledLayerComponent.class)).addSubscriptionListener(bodyChanges);

        subRuntime = asm.get(Aspect.all(
                PhysicsRuntimeBodyComponent.class
        ));
        subRuntime.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    destroyRuntimeBodyByEntityId(data[i]);
                    queueBody(data[i]);
                }
            }
        });

        // Aspect.all also reports entities created/deleted before their first insertion.
        allEntities = asm.get(Aspect.all());
        allEntities.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                for (int i = 0; i < entities.size(); i++) {
                    int entity = entities.get(i);
                    // All authored components may have disappeared before any narrower
                    // subscription observed the synchronously materialized spawn.
                    if (nativeBodies.get(entity) != null && !isWantedEntity(entity)) queueBody(entity);
                }
            }

            @Override
            public void removed(IntBag entities) {
                for (int i = 0; i < entities.size(); i++) {
                    int entity = entities.get(i);
                    destroyRuntimeBodyByEntityId(entity);
                    // Regular joint removals belong to the joint subscription below.
                    // This callback covers entities it has never observed.
                    if (!jointsSub.getActiveEntityIds().get(entity)) unindexJoint(entity);
                }
            }
        });
        asm.get(Aspect.all(PhysicsGearJointComponent.class)).addSubscriptionListener(
                new EntitySubscription.SubscriptionListener() {
                    @Override
                    public void inserted(IntBag entities) {
                        publish(entities);
                    }

                    @Override
                    public void removed(IntBag entities) {
                        for (int i = 0; i < entities.size(); i++) {
                            int entity = entities.get(i);
                            if (asm.get(Aspect.all()).getActiveEntityIds().get(entity)) publishJointRelations(entity);
                        }
                    }

                    private void publish(IntBag entities) {
                        for (int i = 0; i < entities.size(); i++) publishJointRelations(entities.get(i));
                    }
                });

        // Insert/remove => index/unindex + rebuild joint
        jointsSub = asm.get(Aspect.all(PhysicsJointComponent.class));
        jointsSub.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int jEid = data[i];
                    indexJoint(jEid);
                    PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                    if (dirty != null && (rt == null || rt.joint == null
                            || nativeJointEntities.get(rt.joint, -1) != jEid)) {
                        dirty.joint(jEid, JointDirtyBits.ALL);
                    }
                }
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int jEid = data[i];
                    unindexJoint(jEid);
                    destroyRuntimeJointIfAny(jEid);
                    // Body teardown can have queued this now-deleted joint for rebuild.
                    if (dirty != null) dirty.acknowledgeJoint(jEid);
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.BOX2D_SYNC);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.BOX2D_SYNC, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        if (box2d == null || box2d.world == null) return;

        // 1) Gravity
        float gx = (sceneMeta != null) ? sceneMeta.gravityX : 0f;
        float gy = (sceneMeta != null) ? sceneMeta.gravityY : -9.81f;
        if (gx != lastGx || gy != lastGy) {
            lastGx = gx;
            lastGy = gy;
            tmp.set(gx, gy);
            box2d.world.setGravity(tmp);
        }

        // 2) Physics dirty (bodies)
        if (fullRebuildPending) {
            IntBag wanted = subWanted.getEntities();
            int[] wantedData = wanted.getData();
            for (int i = 0, n = wanted.size(); i < n; i++) {
                int e = wantedData[i];
                queueBody(e);
            }

            // A whole-world reconciliation is only needed at bootstrap/rebind/re-enable.
            IntBag runtime = subRuntime.getEntities();
            for (int i = 0; i < runtime.size(); i++) queueBody(runtime.get(i));

            IntBag joints = jointsSub.getEntities();
            int[] jointsData = joints.getData();
            for (int i = 0, n = joints.size(); i < n; i++) {
                int jEid = jointsData[i];
                indexJoint(jEid);
                if (dirty != null) dirty.joint(jEid, JointDirtyBits.ALL);
            }
            fullRebuildPending = false;
        }

        flushBodyNotifications();
        if (dirty != null) {
            dirty.consumePhysics(physicsDirtyConsumer);
        }

        if (bootstrapStepPending && !fullRebuildPending) {
            box2d.world.step(1f / 60f, 6, 2);
            if (dirty != null) {
                IntBag joints = jointsSub.getEntities();
                int[] jointsData = joints.getData();
                for (int i = 0, n = joints.size(); i < n; i++) {
                    dirty.joint(jointsData[i], JointDirtyBits.ALL);
                }
            }
            bootstrapStepPending = false;
        }

        // 3) Joints: only dirty
        if (dirty != null) {
            dirty.consumeJoints(jointDirtyConsumer);
        }

        // 4) Step
        if (stepEnabled) {
            box2d.step(world.getDelta());
        }

        // 5) Authoring owns native Body poses. Runtime-owned poses are reconstructed by the
        // dedicated post-Physics hierarchy writeback phase, even while stepping is paused.
        if (isRuntimePhysicsAuthoritative()) return;
        IntBag wanted = subWanted.getEntities();
        int[] w = wanted.getData();
        for (int i = 0, n = wanted.size(); i < n; i++) {
            int e = w[i];
            PhysicsRuntimeBodyComponent rt = mRuntime.get(e);
            if (rt == null || rt.body == null) continue;

            TransformComponent t = mT.get(e);
            if (t == null) continue;

            float targetX = box2d.pxToM(authoredWorldX(e, t));
            float targetY = box2d.pxToM(authoredWorldY(e, t));
            float targetAngle = authoredWorldRotation(e, t);

            Vector2 p = rt.body.getPosition();
            boolean movedByAuthoring =
                    Math.abs(p.x - targetX) > TRANSFORM_SYNC_EPSILON
                            || Math.abs(p.y - targetY) > TRANSFORM_SYNC_EPSILON
                            || Math.abs(rt.body.getAngle() - targetAngle)
                            > TRANSFORM_SYNC_EPSILON;

            if (movedByAuthoring) {
                rt.body.setTransform(targetX, targetY, targetAngle);
                rt.body.setAwake(true);
                refreshConnectedDistanceJointLengths(e);
                markJointsDirtyForBody(e);
            }
        }
    }

    private void queueBody(int entityId) {
        if (pendingBodySet.add(entityId)) pendingBodies.add(entityId);
    }

    private void flushBodyNotifications() {
        while (pendingBodies.size > 0) {
            int entityId = pendingBodies.peek();
            // EntityManager still reports deleted entities active during notification delivery.
            if (allEntities.getActiveEntityIds().get(entityId)) {
                if (dirty != null) dirty.physics(entityId, PhysicsDirtyBits.ALL);
                else synchronizeDirtyBody(entityId);
            }
            pendingBodies.pop();
            pendingBodySet.remove(entityId);
        }
    }

    private void synchronizeDirtyBody(int entityId) {
        if (!allEntities.getActiveEntityIds().get(entityId)) return;
        synchronizeBody(entityId);
    }

    private void synchronizeBody(int entityId) {
        if (hasAuthoredBody(entityId)) {
            requireCompiledCache(entityId);
        }
        if (!isWantedEntity(entityId)) {
            destroyRuntimeBody(entityId);
            return;
        }

        int mask = dirty != null ? dirty.physicsSub(entityId) : PhysicsDirtyBits.ALL;
        PhysicsRuntimeBodyComponent runtimeBody =
                mRuntime.has(entityId) ? mRuntime.get(entityId) : mRuntime.create(entityId);
        if ((mask & PhysicsDirtyBits.ALL) != 0 || runtimeBody.body == null) {
            rebuildBody(entityId, runtimeBody);
        }
    }

    /**
     * Builds bodies, fixtures and joints, including the mandatory bootstrap step,
     * while leaving normal simulation stepping disabled.
     */
    public void prepareRuntimeAvailability() {
        setStepEnabled(false);
        setEnabled(true);
        if (hierarchy != null) hierarchy.prepareRuntimeAvailability();
        processSystemInternal();
        if (fullRebuildPending || bootstrapStepPending) {
            throw new IllegalStateException("Box2D Runtime availability did not close.");
        }
    }

    /**
     * Checks the native boundary before a synchronous spawn allocates any entities or identities.
     */
    public void requireSynchronousSpawnAvailable() {
        if (box2d == null || box2d.world == null || box2d.isDisposed()) {
            throw new IllegalStateException("Cannot spawn physical Game Object: Physics is unavailable.");
        }
        if (box2d.world.isLocked()) {
            throw new IllegalStateException("Cannot spawn physical Game Object while Box2D is locked; use spawnDeferred().");
        }
    }

    /**
     * Materializes only a freshly created instance with resolved poses. Shares the normal
     * body/joint creation and ownership paths, without subscriptions, a World pass or a step.
     * All authored joints must be realized; the caller rolls the instance back on failure.
     */
    public void materializePreparedSpawn(IntBag entities) {
        requireSynchronousSpawnAvailable();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (mJointBase.has(entity)) indexJoint(entity);
        }
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (hasAuthoredBody(entity)) synchronizeBody(entity);
        }
        // Gears depend only on revolute/prismatic joints, never on another gear.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < entities.size(); i++) {
                int entity = entities.get(i);
                PhysicsJointComponent base = mJointBase.getSafe(entity, null);
                if (base == null || (base.type == PhysicsJointComponent.TYPE_GEAR) != (pass == 1)) continue;
                syncOneJoint(entity);
                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(entity, null);
                if (rt == null || rt.joint == null) {
                    throw new IllegalStateException("Cannot complete Game Object joint entityId " + entity
                            + ": endpoints or source joints are not materializable.");
                }
            }
        }
        if (dirty != null) {
            for (int i = 0; i < entities.size(); i++) {
                dirty.acknowledgePhysics(entities.get(i));
                dirty.acknowledgeJoint(entities.get(i));
            }
        }
    }

    /**
     * Releases only resources belonging to a failed new instance, including unobserved components.
     */
    public void discardPreparedSpawn(IntBag entities) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < entities.size(); i++) {
                int entity = entities.get(i);
                PhysicsJointComponent base = mJointBase.getSafe(entity, null);
                if (base != null && (base.type == PhysicsJointComponent.TYPE_GEAR) == (pass == 0)) {
                    destroyRuntimeJointIfAny(entity);
                    unindexJoint(entity);
                }
            }
        }
        for (int i = 0; i < entities.size(); i++) destroyRuntimeBodyByEntityId(entities.get(i));
    }

    // -------------------------------------------------------------
    // Inverse cache (body -> joints)
    // -------------------------------------------------------------

    /**
     * INTERNAL shared index; no native world is required for authored dependency lookup.
     */
    public JointDependencyIndex jointDependencies() {
        return jointDependencies;
    }

    /**
     * Publishes one complete authored joint after its fields have been assigned.
     */
    public void publishJointRelations(int joint) {
        if (joint < 0 || mJointBase == null) return;
        // Native teardown may dirty a joint while Artemis still retains its deleted
        // components. The all-entity subscription publishes deletions before joint callbacks.
        if (jointsSub.getActiveEntityIds().get(joint) && !allEntities.getActiveEntityIds().get(joint)) {
            jointDependencies.remove(joint);
            return;
        }
        PhysicsJointComponent base = mJointBase.getSafe(joint, null);
        if (base == null) {
            jointDependencies.remove(joint);
            return;
        }
        PhysicsService.requireJointBodies(world, joint, "Joint publication");
        PhysicsGearJointComponent gear = mGear.getSafe(joint, null);
        jointDependencies.publish(joint, base.aEid, base.bEid,
                gear != null ? gear.joint1Eid : -1, gear != null ? gear.joint2Eid : -1);
    }

    private void indexJoint(int joint) {
        publishJointRelations(joint);
    }

    private void unindexJoint(int joint) {
        jointDependencies.remove(joint);
    }

    /**
     * Visits only the removal closure. Caller owns out; valid gear dependencies precede sources.
     */
    public IntArray collectJointsForRemoval(IntArray entities, IntArray out) {
        if (out == null) out = new IntArray(false, 8);
        out.clear();
        removalQueue.clear();
        removalSeen.clear();
        if (entities == null) return out;
        for (int i = 0; i < entities.size; i++) addRemovalCandidate(entities.get(i));
        for (int i = 0; i < removalQueue.size; i++) {
            int entity = removalQueue.get(i);
            addRemovalCandidates(jointDependencies.jointsForBody(entity));
            addRemovalCandidates(jointDependencies.gearsForSource(entity));
        }
        // Valid gears depend only on revolute/prismatic sources. Emit all gears first,
        // including when the caller explicitly supplied a gear before its source.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = removalQueue.size - 1; i >= 0; i--) {
                int entity = removalQueue.get(i);
                PhysicsJointComponent base = mJointBase.getSafe(entity, null);
                if (base != null && (base.type == PhysicsJointComponent.TYPE_GEAR) == (pass == 0)) out.add(entity);
            }
        }
        return out;
    }

    private void addRemovalCandidates(IntArray entities) {
        if (entities == null) return;
        for (int i = 0; i < entities.size; i++) addRemovalCandidate(entities.get(i));
    }

    private void addRemovalCandidate(int entity) {
        if (entity >= 0 && world.getEntityManager().isActive(entity) && removalSeen.add(entity)) {
            removalQueue.add(entity);
        }
    }

    private void markJointsDirtyForBody(int bodyEid) {
        if (dirty == null) return;

        IntArray list = jointDependencies.jointsForBody(bodyEid);
        if (list == null || list.size == 0) return;

        for (int i = 0; i < list.size; i++) {
            dirty.joint(list.get(i), JointDirtyBits.ALL);
        }
    }

    private void refreshConnectedDistanceJointLengths(int bodyEid) {

        IntArray list = jointDependencies.jointsForBody(bodyEid);
        if (list == null || list.size == 0) return;

        for (int i = 0; i < list.size; i++) {
            int jEid = list.get(i);

            PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
            if (base == null || base.type != PhysicsJointComponent.TYPE_DISTANCE) continue;

            PhysicsDistanceJointComponent dist = mJointDist.getSafe(jEid, null);
            if (dist == null) continue;

            PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(base.aEid, null);
            PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(base.bEid, null);
            if (aRt == null || aRt.body == null || bRt == null || bRt.body == null) continue;

            float ax = anchorWorldX(aRt.body, base.anchorAx, base.anchorAy);
            float ay = anchorWorldY(aRt.body, base.anchorAx, base.anchorAy);
            float bx = anchorWorldX(bRt.body, base.anchorBx, base.anchorBy);
            float by = anchorWorldY(bRt.body, base.anchorBx, base.anchorBy);

            float lenWU = Vector2.dst(ax, ay, bx, by);
            dist.lengthM = Math.max(0.001f, box2d.pxToM(lenWU));
        }
    }

    private float anchorWorldX(Body body, float localAx, float localAy) {
        float cos = MathUtils.cos(body.getAngle());
        float sin = MathUtils.sin(body.getAngle());
        float rx_m = localAx * cos - localAy * sin;
        return box2d.mToPx(body.getPosition().x + rx_m);
    }

    private float anchorWorldY(Body body, float localAx, float localAy) {
        float cos = MathUtils.cos(body.getAngle());
        float sin = MathUtils.sin(body.getAngle());
        float ry_m = localAx * sin + localAy * cos;
        return box2d.mToPx(body.getPosition().y + ry_m);
    }

    private void detachRuntimeJointsForBody(Body body) {
        // Box2D owns this adjacency: authored endpoints may already refer to another body.
        Array<JointEdge> edges = body.getJointList();
        for (int i = 0; i < edges.size; i++) {
            Joint joint = edges.get(i).joint;
            int entity = nativeJointEntities.remove(joint, -1);
            if (entity < 0) continue;
            PhysicsRuntimeJointComponent rt = mJointRt.getSafe(entity, null);
            if (rt != null && rt.joint == joint) {
                rt.joint = null;
                mJointRt.remove(entity);
            }
            if (dirty != null) dirty.joint(entity, JointDirtyBits.ALL);
        }
    }

    // -------------------------------------------------------------
    // Bodies
    // -------------------------------------------------------------

    private boolean isWantedEntity(int e) {
        if (testObserver != null) testObserver.onBodyEligibilityCheck();
        return hasAuthoredBody(e)
                && isTiledCollisionEnabled(e)
                && hasMaterializableCompiledCache(e);
    }

    private boolean isRuntimePhysicsAuthoritative() {
        return poseAuthority != null && poseAuthority.isRuntimePhysics();
    }

    private float authoredWorldX(int entityId, TransformComponent authored) {
        if (hierarchy != null && hierarchy.worldTransforms().isResolved(entityId)) {
            return hierarchy.worldTransforms().x[entityId];
        }
        return authored.x;
    }

    private float authoredWorldY(int entityId, TransformComponent authored) {
        if (hierarchy != null && hierarchy.worldTransforms().isResolved(entityId)) {
            return hierarchy.worldTransforms().y[entityId];
        }
        return authored.y;
    }

    private float authoredWorldRotation(int entityId, TransformComponent authored) {
        if (hierarchy != null && hierarchy.worldTransforms().isResolved(entityId)) {
            return hierarchy.worldTransforms().rotationRad[entityId];
        }
        return authored.rotationRad;
    }

    private boolean isTiledCollisionEnabled(int e) {
        TiledLayerComponent tiled = mTiled.getSafe(e, null);
        return tiled == null || tiled.data == null || tiled.data.collisionEnabled;
    }

    private boolean hasAuthoredBody(int e) {
        return e >= 0 && mT.has(e) && mBodyDef.has(e);
    }

    private boolean hasPreparedCompiledCache(int e) {
        PhysicsCompiledFixturesComponent compiled = mCompiled.getSafe(e, null);
        return compiled != null && compiled.valid && compiled.fixtures != null;
    }

    private boolean hasMaterializableCompiledCache(int e) {
        PhysicsCompiledFixturesComponent compiled = mCompiled.getSafe(e, null);
        return compiled != null
                && compiled.valid
                && compiled.fixtures != null
                && compiled.fixtures.size > 0;
    }

    private PhysicsCompiledFixturesComponent requireCompiledCache(int e) {
        if (testObserver != null) testObserver.onBodyCacheCheck();
        PhysicsCompiledFixturesComponent compiled = mCompiled.getSafe(e, null);
        if (!hasPreparedCompiledCache(e)) {
            PixscapeIdentityComponent identity = mIdentity.getSafe(e, null);
            String stable = identity != null && identity.stableId > 0
                    ? ", stableId " + identity.stableId
                    : "";
            throw new IllegalStateException(
                    "Physics body entityId " + e + stable
                            + " has no valid PhysicsCompiledFixturesComponent; "
                            + "prepare and publish the compiled cache before Box2D materialization.");
        }
        return compiled;
    }

    private void rebuildBody(int e, PhysicsRuntimeBodyComponent rt) {
        replaceBodyAtomically(e, rt);
    }

    private void replaceBodyAtomically(int e, PhysicsRuntimeBodyComponent rt) {
        if (box2d == null || box2d.world == null || !isWantedEntity(e)) return;

        PhysicsCompiledFixturesComponent compiled = requireCompiledCache(e);
        if (testObserver != null) testObserver.onBodyRebuild();
        Body previousBody = nativeBodies.get(e);
        // Restore the mirror if its pooled component was removed/reset. Keep the old
        // owner and its joints intact until the complete candidate has been created.
        rt.body = previousBody;
        float worldX = previousBody != null && isRuntimePhysicsAuthoritative()
                ? box2d.mToPx(previousBody.getPosition().x)
                : authoredWorldX(e, mT.get(e));
        float worldY = previousBody != null && isRuntimePhysicsAuthoritative()
                ? box2d.mToPx(previousBody.getPosition().y)
                : authoredWorldY(e, mT.get(e));
        float worldRotation = previousBody != null && isRuntimePhysicsAuthoritative()
                ? previousBody.getAngle()
                : authoredWorldRotation(e, mT.get(e));
        Body candidateBody = createBodyFromCompiled(
                e, compiled.fixtures, previousBody != null, worldX, worldY, worldRotation);

        if (previousBody != null) {
            detachRuntimeJointsForBody(previousBody);
            box2d.world.destroyBody(previousBody);
        }

        rt.body = candidateBody;
        nativeBodies.put(e, candidateBody);
        rt.gen++;
        markJointsDirtyForBody(e);
    }

    private void destroyRuntimeBody(int e) {
        destroyRuntimeBodyByEntityId(e);
    }

    private void destroyRuntimeBodyByEntityId(int e) {
        Body body = nativeBodies.get(e);
        PhysicsRuntimeBodyComponent rt = mRuntime.getSafe(e, null);
        if (body != null && box2d != null && box2d.world != null) {
            detachRuntimeJointsForBody(body);
            box2d.world.destroyBody(body);
        }
        nativeBodies.remove(e);
        if (rt != null) {
            rt.body = null;
            rt.gen++;
            mRuntime.remove(e);
        }
        if (body != null || rt != null) markJointsDirtyForBody(e);
    }

    private Body createBodyFromCompiled(
            int e,
            Array<CompiledFixtureData> fixtures,
            boolean wakeForMutation,
            float worldX,
            float worldY,
            float worldRotation) {
        if (box2d == null || box2d.world == null) return null;
        if (!isWantedEntity(e)) return null;

        PhysicsBodyComponent bd = mBodyDef.get(e);
        if (bd == null || fixtures == null) return null;

        BodyDef def = new BodyDef();
        def.type = (bd.type == 0) ? BodyDef.BodyType.StaticBody
                : (bd.type == 1) ? BodyDef.BodyType.KinematicBody
                : BodyDef.BodyType.DynamicBody;

        def.position.set(box2d.pxToM(worldX), box2d.pxToM(worldY));
        def.angle = worldRotation;
        def.fixedRotation = bd.fixedRotation;
        def.bullet = bd.bullet;
        def.allowSleep = box2d.isDoSleep() && bd.allowSleep;
        def.awake = !def.allowSleep || wakeForMutation || bd.awake;
        def.gravityScale = bd.gravityScale;
        def.linearDamping = bd.linearDamping;
        def.angularDamping = bd.angularDamping;

        Body body = box2d.world.createBody(def);
        body.setUserData(Integer.valueOf(e));
        CompiledFixtureData materializing = null;
        try {
            for (int i = 0, n = fixtures.size; i < n; i++) {
                CompiledFixtureData fd = fixtures.get(i);
                materializing = fd;
                fd.validate();
                Shape shape = null;
                try {
                    shape = createShape(fd);
                    FixtureDef fdef = new FixtureDef();
                    fdef.shape = shape;
                    fdef.density = fd.density;
                    fdef.friction = fd.friction;
                    fdef.restitution = fd.restitution;
                    fdef.isSensor = fd.sensor;
                    fdef.filter.categoryBits = fd.categoryBits;
                    fdef.filter.maskBits = fd.maskBits;
                    fdef.filter.groupIndex = fd.groupIndex;

                    if (testObserver != null) {
                        testObserver.beforeCreateFixture(e, fd);
                    }
                    Fixture fixture = body.createFixture(fdef);
                    fixture.setUserData(new PhysicsFixtureProvenance(
                            e, fd.physicsShapeId, fd.partIndex));
                    if (testObserver != null) {
                        testObserver.onFixtureProvenanceCreated(e, fd);
                    }
                } finally {
                    if (shape != null) shape.dispose();
                }
            }
            return body;
        } catch (Throwable ex) {
            box2d.world.destroyBody(body);
            throw materializationFailure(e, materializing, ex);
        }
    }

    private IllegalStateException materializationFailure(
            int entityId, CompiledFixtureData fixture, Throwable cause) {
        PixscapeIdentityComponent identity = mIdentity != null
                ? mIdentity.getSafe(entityId, null)
                : null;
        StringBuilder message = new StringBuilder(160);
        message.append("Failed to materialize Box2D fixture for body entityId ")
                .append(entityId);
        if (identity != null && identity.stableId > 0) {
            message.append(", stableId ").append(identity.stableId);
        }
        if (fixture != null) {
            message.append(", physicsShapeId ").append(fixture.physicsShapeId)
                    .append(", partIndex ").append(fixture.partIndex)
                    .append(", fixtureType ").append(fixture.shapeType);
        } else {
            message.append(", physicsShapeId unavailable, partIndex unavailable, fixtureType unavailable");
        }
        message.append('.');
        return new IllegalStateException(message.toString(), cause);
    }

    private Shape createShape(CompiledFixtureData fd) {
        switch (fd.shapeType) {
            case PhysicsGeometryData.SHAPE_BOX:
                return createBoxShape(fd);
            case PhysicsGeometryData.SHAPE_CIRCLE:
                return createCircleShape(fd);
            case PhysicsGeometryData.SHAPE_POLYGON:
                return createPolygonShape(fd);
            default:
                throw new IllegalArgumentException(
                        "Unsupported compiled shapeType " + fd.shapeType
                                + " for physicsShapeId " + fd.physicsShapeId + ".");
        }
    }

    private Shape createBoxShape(CompiledFixtureData fd) {
        PolygonShape shape = new PolygonShape();
        float angleRad = fd.angleDegrees * MathUtils.degreesToRadians;
        shape.setAsBox(
                fd.halfWidth, fd.halfHeight, tmp.set(fd.offsetX, fd.offsetY), angleRad);
        return shape;
    }

    private Shape createCircleShape(CompiledFixtureData fd) {
        CircleShape shape = new CircleShape();
        shape.setRadius(fd.radius);
        shape.setPosition(tmp.set(fd.offsetX, fd.offsetY));
        return shape;
    }

    private Shape createPolygonShape(CompiledFixtureData fd) {
        int n = fd.polygonVertexCount;
        float[] vertices = fd.polygonVertices;
        boolean valid = vertices != null
                && n >= 3
                && n <= MAX_POLYGON_VERTICES
                && vertices.length >= n * 2;
        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid compiled polygon for physicsShapeId " + fd.physicsShapeId
                            + ", partIndex " + fd.partIndex + ".");
        }

        float angleRad = fd.angleDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(angleRad);
        float sin = MathUtils.sin(angleRad);

        for (int i = 0; i < n; i++) {
            float lx = vertices[i * 2];
            float ly = vertices[i * 2 + 1];
            if (!Float.isFinite(lx) || !Float.isFinite(ly)) {
                throw new IllegalArgumentException(
                        "Non-finite compiled polygon vertex for physicsShapeId "
                                + fd.physicsShapeId + ", partIndex " + fd.partIndex + ".");
            }

            float x = lx * cos - ly * sin + fd.offsetX;
            float y = lx * sin + ly * cos + fd.offsetY;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException(
                        "Non-finite transformed polygon vertex for physicsShapeId "
                                + fd.physicsShapeId + ", partIndex " + fd.partIndex + ".");
            }

            int base = i * 2;
            polygonVertsScratch[base] = x;
            polygonVertsScratch[base + 1] = y;
        }

        PolygonShape shape = new PolygonShape();
        shape.set(polygonVertsScratch, 0, n * 2);
        return shape;
    }

    // -------------------------------------------------------------
    // Joints (dirty-driven)
    // -------------------------------------------------------------

    private void syncOneJoint(int jEid) {
        try {
            if (testObserver != null) {
                testObserver.beforeCreateOrRebuildJoint(jEid);
            }
            syncOneJointInternal(jEid);
            PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
            if (rt != null && rt.joint != null) nativeJointEntities.put(rt.joint, jEid);
        } catch (Throwable failure) {
            destroyRuntimeJointIfAny(jEid);
            if (dirty != null) {
                dirty.joint(jEid, JointDirtyBits.ALL);
            }
            PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
            String endpoints = base != null
                    ? ", bodyA " + base.aEid + ", bodyB " + base.bEid
                    : "";
            throw new IllegalStateException(
                    "Failed to recreate Box2D joint entityId " + jEid + endpoints
                            + "; the native joint remains invalid and dirty.",
                    failure);
        }
    }

    private void syncOneJointInternal(int jEid) {
        // IMPORTANT: read submask BEFORE any early return (consumeJoints ACKs after callback)
        final int sub = (dirty != null) ? dirty.jointSub(jEid) : JointDirtyBits.ALL;

        // ---- base component existence ----
        PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
        if (base == null) {
            unindexJoint(jEid);
            destroyRuntimeJointIfAny(jEid);
            return;
        }

        // Keep inverse cache in sync even if UI changed endpoints
        indexJoint(jEid);

        // ---- dispatch by type (fill later) ----
        switch (base.type) {

            case PhysicsJointComponent.TYPE_DISTANCE: {
                PhysicsDistanceJointComponent dist = mJointDist.getSafe(jEid, null);
                if (dist == null) {
                    // Invalid entity: base says distance but missing the type component
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                // bodies rebuilt => force recreate
                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                // dirty => recreate (simple/safe)
                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createDistanceJoint(base, dist, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_REVOLUTE: {
                PhysicsRevoluteJointComponent rev = mJointRev.getSafe(jEid, null);
                if (rev == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createRevoluteJoint(base, rev, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_PRISMATIC: {
                PhysicsPrismaticJointComponent prism = mJointPrism.getSafe(jEid, null);
                if (prism == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createPrismaticJoint(base, prism, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_WHEEL: {
                PhysicsWheelJointComponent wheel = mJointWheel.getSafe(jEid, null);
                if (wheel == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createWheelJoint(base, wheel, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }
            case PhysicsJointComponent.TYPE_FRICTION: {
                PhysicsFrictionJointComponent friction = mFriction.getSafe(jEid, null);
                if (friction == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createFrictionJoint(base, friction, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_MOTOR: {
                PhysicsMotorJointComponent motor = mMotor.getSafe(jEid, null);
                if (motor == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createMotorJoint(base, motor, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_WELD: {
                PhysicsWeldJointComponent weld = mWeld.getSafe(jEid, null);
                if (weld == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createWeldJoint(base, weld, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_PULLEY: {
                PhysicsPulleyJointComponent pulley = mPulley.getSafe(jEid, null);
                if (pulley == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createPulleyJoint(base, pulley, aRt.body, bRt.body);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;

                    markDependentGearJointsDirty(jEid);
                }
                break;
            }

            case PhysicsJointComponent.TYPE_GEAR: {
                PhysicsGearJointComponent gear = mGear.getSafe(jEid, null);
                if (gear == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                if (gear.joint1Eid < 0 || gear.joint2Eid < 0 || gear.joint1Eid == gear.joint2Eid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                if (gear.joint1Eid == jEid || gear.joint2Eid == jEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                int aEid = base.aEid;
                int bEid = base.bEid;
                if (aEid < 0 || bEid < 0 || aEid == bEid) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeBodyComponent aRt = mRuntime.getSafe(aEid, null);
                PhysicsRuntimeBodyComponent bRt = mRuntime.getSafe(bEid, null);
                if (aRt == null || bRt == null || aRt.body == null || bRt.body == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent src1Rt = mJointRt.getSafe(gear.joint1Eid, null);
                PhysicsRuntimeJointComponent src2Rt = mJointRt.getSafe(gear.joint2Eid, null);
                if (src1Rt == null || src2Rt == null || src1Rt.joint == null || src2Rt.joint == null) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                Joint src1 = src1Rt.joint;
                Joint src2 = src2Rt.joint;
                boolean src1Ok = src1 instanceof RevoluteJoint || src1 instanceof PrismaticJoint;
                boolean src2Ok = src2 instanceof RevoluteJoint || src2 instanceof PrismaticJoint;
                if (!src1Ok || !src2Ok) {
                    destroyRuntimeJointIfAny(jEid);
                    return;
                }

                PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
                boolean needsRebuild = (rt == null || rt.joint == null);

                if (!needsRebuild) {
                    if (rt.aGen != aRt.gen || rt.bGen != bRt.gen) needsRebuild = true;
                }

                if (!needsRebuild && sub != 0) needsRebuild = true;
                if (!needsRebuild) return;

                destroyRuntimeJointIfAny(jEid);

                Joint newJoint = createGearJoint(base, gear, aRt.body, bRt.body, src1, src2);
                if (newJoint != null) {
                    PhysicsRuntimeJointComponent newRt = mJointRt.create(jEid);
                    newRt.joint = newJoint;
                    newRt.aGen = aRt.gen;
                    newRt.bGen = bRt.gen;
                }
                break;
            }

            default: {
                // Unknown type => no runtime joint
                destroyRuntimeJointIfAny(jEid);
            }
        }
    }

    private Joint createDistanceJoint(PhysicsJointComponent base, PhysicsDistanceJointComponent dist, Body bodyA, Body bodyB) {
        DistanceJointDef def = new DistanceJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        def.length = Math.max(0.001f, dist.lengthM);
        def.frequencyHz = Math.max(0f, dist.frequencyHz);
        def.dampingRatio = Math.max(0f, Math.min(1f, dist.dampingRatio));

        return box2d.world.createJoint(def);
    }

    private Joint createRevoluteJoint(PhysicsJointComponent base, PhysicsRevoluteJointComponent rev, Body bodyA, Body bodyB) {
        RevoluteJointDef def = new RevoluteJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        def.referenceAngle = bodyB.getAngle() - bodyA.getAngle();

        def.enableLimit = rev.enableLimit;
        def.lowerAngle = rev.lowerAngleRad;
        def.upperAngle = rev.upperAngleRad;

        def.enableMotor = rev.enableMotor;
        def.motorSpeed = rev.motorSpeedRad;
        def.maxMotorTorque = rev.maxMotorTorque;

        return box2d.world.createJoint(def);
    }

    private Joint createPrismaticJoint(PhysicsJointComponent base, PhysicsPrismaticJointComponent prism, Body bodyA, Body bodyB) {
        PrismaticJointDef def = new PrismaticJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        float axisX = prism.axisX;
        float axisY = prism.axisY;
        float len = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (len < 1e-6f) {
            axisX = 1f;
            axisY = 0f;
        } else {
            axisX /= len;
            axisY /= len;
        }
        def.localAxisA.set(axisX, axisY);

        def.referenceAngle = bodyB.getAngle() - bodyA.getAngle();

        def.enableLimit = prism.enableLimit;
        def.lowerTranslation = prism.lowerTranslationM;
        def.upperTranslation = prism.upperTranslationM;

        def.enableMotor = prism.enableMotor;
        def.motorSpeed = prism.motorSpeedMps;
        def.maxMotorForce = Math.max(0f, prism.maxMotorForce);

        return box2d.world.createJoint(def);
    }

    private Joint createWheelJoint(PhysicsJointComponent base, PhysicsWheelJointComponent wheel, Body bodyA, Body bodyB) {
        WheelJointDef def = new WheelJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        float ax = wheel.axisX;
        float ay = wheel.axisY;

        float len2 = ax * ax + ay * ay;
        if (len2 < 1e-12f) { // fallback critique
            ax = 0f;
            ay = 1f;
        } else {
            float inv = (float) (1.0 / Math.sqrt(len2));
            ax *= inv;
            ay *= inv;
        }
        def.localAxisA.set(ax, ay).nor();

        def.frequencyHz = wheel.frequencyHz;
        def.dampingRatio = wheel.dampingRatio;

        def.enableMotor = wheel.enableMotor;
        def.motorSpeed = wheel.motorSpeedRad;
        def.maxMotorTorque = wheel.maxMotorTorque;

        return box2d.world.createJoint(def);
    }

    private Joint createFrictionJoint(PhysicsJointComponent base,
                                      PhysicsFrictionJointComponent friction,
                                      Body bodyA,
                                      Body bodyB) {
        FrictionJointDef def = new FrictionJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        def.maxForce = Math.max(0f, friction.maxForce);
        def.maxTorque = Math.max(0f, friction.maxTorque);

        return box2d.world.createJoint(def);
    }

    private Joint createMotorJoint(PhysicsJointComponent base,
                                   PhysicsMotorJointComponent motor,
                                   Body bodyA,
                                   Body bodyB) {
        MotorJointDef def = new MotorJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.linearOffset.set(motor.linearOffsetX, motor.linearOffsetY);
        def.angularOffset = motor.angularOffsetRad;
        def.maxForce = Math.max(0f, motor.maxForce);
        def.maxTorque = Math.max(0f, motor.maxTorque);
        def.correctionFactor = Math.max(0f, Math.min(1f, motor.correctionFactor));

        return box2d.world.createJoint(def);
    }

    private Joint createWeldJoint(PhysicsJointComponent base,
                                  PhysicsWeldJointComponent weld,
                                  Body bodyA,
                                  Body bodyB) {
        WeldJointDef def = new WeldJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);
        def.referenceAngle = weld.referenceAngleRad;
        def.frequencyHz = Math.max(0f, weld.frequencyHz);
        def.dampingRatio = Math.max(0f, Math.min(1f, weld.dampingRatio));

        return box2d.world.createJoint(def);
    }

    private Joint createPulleyJoint(PhysicsJointComponent base,
                                    PhysicsPulleyJointComponent pulley,
                                    Body bodyA,
                                    Body bodyB) {
        PulleyJointDef def = new PulleyJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.groundAnchorA.set(pulley.groundAx, pulley.groundAy);
        def.groundAnchorB.set(pulley.groundBx, pulley.groundBy);

        def.localAnchorA.set(base.anchorAx, base.anchorAy);
        def.localAnchorB.set(base.anchorBx, base.anchorBy);

        def.lengthA = Math.max(0.001f, pulley.lengthAM);
        def.lengthB = Math.max(0.001f, pulley.lengthBM);
        def.ratio = pulley.ratio > 0f ? pulley.ratio : 1f;

        return box2d.world.createJoint(def);
    }

    private Joint createGearJoint(PhysicsJointComponent base,
                                  PhysicsGearJointComponent gear,
                                  Body bodyA,
                                  Body bodyB,
                                  Joint joint1,
                                  Joint joint2) {
        GearJointDef def = new GearJointDef();
        def.bodyA = bodyA;
        def.bodyB = bodyB;
        def.collideConnected = base.collideConnected;

        def.joint1 = joint1;
        def.joint2 = joint2;
        def.ratio = gear.ratio;

        return box2d.world.createJoint(def);
    }

    interface TestObserver {
        default void onBodyEligibilityCheck() {
        }

        default void onBodyCacheCheck() {
        }

        void onBodyCompile();

        void onShapeCompile();

        void onPolygonDecomposition();

        void onBodyRebuild();

        void beforeCreateFixture(int bodyEntityId, CompiledFixtureData fixture);

        void onFixtureProvenanceCreated(int bodyEntityId, CompiledFixtureData fixture);

        void beforeCreateOrRebuildJoint(int jointEntityId);
    }

    private void destroyRuntimeJointIfAny(int jEid) {
        PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
        if (rt != null && rt.joint != null && box2d != null && box2d.world != null) {
            nativeJointEntities.remove(rt.joint, -1);
            box2d.world.destroyJoint(rt.joint);
            rt.joint = null;
        }
        if (mJointRt.has(jEid)) mJointRt.remove(jEid);

        markDependentGearJointsDirty(jEid);
    }

    private void markDependentGearJointsDirty(int sourceJointEid) {
        if (dirty == null || sourceJointEid < 0) return;

        IntArray dependents = jointDependencies.gearsForSource(sourceJointEid);
        if (dependents == null) return;
        for (int i = 0; i < dependents.size; i++) {
            dirty.joint(dependents.get(i), JointDirtyBits.ALL);
        }
    }

    // -------------------------------------------------------------
    // API
    // -------------------------------------------------------------

    public void setStepEnabled(boolean v) {
        this.stepEnabled = v;
    }

    public boolean isStepEnabled() {
        return stepEnabled;
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
