package games.pixscape.runtime.system;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;

import java.util.Arrays;

public final class Box2dSyncSystem extends BaseSystem {

    private Box2dWorldService box2d;
    private SceneMetaRuntime sceneMeta;

    private ComponentMapper<TransformComponent>             mT;
    private ComponentMapper<PhysicsBodyComponent>           mBodyDef;
    private ComponentMapper<PhysicsFixturesComponent>       mFixDefs;
    private ComponentMapper<PhysicsRuntimeBodyComponent>    mRuntime;

    private ComponentMapper<PhysicsJointComponent>          mJointBase;
    private ComponentMapper<PhysicsRuntimeJointComponent>   mJointRt;

    private ComponentMapper<PhysicsDistanceJointComponent>  mJointDist;
    private ComponentMapper<PhysicsRevoluteJointComponent>  mJointRev;
    private ComponentMapper<PhysicsPrismaticJointComponent> mJointPrism;
    private ComponentMapper<PhysicsWheelJointComponent>     mJointWheel;
    private ComponentMapper<PhysicsFrictionJointComponent>  mFriction;
    private ComponentMapper<PhysicsMotorJointComponent>     mMotor;
    private ComponentMapper<PhysicsWeldJointComponent>      mWeld;
    private ComponentMapper<PhysicsPulleyJointComponent>    mPulley;
    private ComponentMapper<PhysicsGearJointComponent>      mGear;


    private final DirtyTrackerSystem dirty;

    private EntitySubscription subWanted;   // T + Body + Fixtures
    private EntitySubscription subRuntime;  // RuntimeBody
    private EntitySubscription jointsSub;   // JointBase insert/remove only

    private final Vector2 tmp = new Vector2();
    private static final int MAX_POLYGON_VERTICES = 8;
    private final float[] polygonVertsScratch = new float[MAX_POLYGON_VERTICES * 2];

    private float lastGx = Float.NaN;
    private float lastGy = Float.NaN;
    private boolean stepEnabled = false;
    private boolean fullRebuildPending = true;
    private boolean bootstrapStepPending = false;

    // bodyEid -> jointEids
    private IntArray[] jointsByBody;
    private int jointsByBodyCap = 0;

    // jointEid -> last indexed (a,b)
    private int[] jointAByJoint;
    private int[] jointBByJoint;
    private int jointsByJointCap = 0;

    private final Array<Body> bodyScratch = new Array<>(false, 64);

    public Box2dSyncSystem(Box2dWorldService box2d, DirtyTrackerSystem dirty) {
        this.box2d = box2d;
        this.dirty = dirty;
    }

    public void setBox2d(Box2dWorldService box2d) {
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
        mT       = world.getMapper(TransformComponent.class);
        mBodyDef = world.getMapper(PhysicsBodyComponent.class);
        mFixDefs = world.getMapper(PhysicsFixturesComponent.class);
        mRuntime = world.getMapper(PhysicsRuntimeBodyComponent.class);

        mJointBase  = world.getMapper(PhysicsJointComponent.class);
        mJointRt    = world.getMapper(PhysicsRuntimeJointComponent.class);

        mJointDist  = world.getMapper(PhysicsDistanceJointComponent.class);
        mJointRev   = world.getMapper(PhysicsRevoluteJointComponent.class);
        mJointPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        mJointWheel = world.getMapper(PhysicsWheelJointComponent.class);
        mFriction   = world.getMapper(PhysicsFrictionJointComponent.class);
        mMotor      = world.getMapper(PhysicsMotorJointComponent.class);
        mWeld       = world.getMapper(PhysicsWeldJointComponent.class);
        mPulley     = world.getMapper(PhysicsPulleyJointComponent.class);
        mGear       = world.getMapper(PhysicsGearJointComponent.class);


        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();

        subWanted = asm.get(Aspect.all(
                TransformComponent.class,
                PhysicsBodyComponent.class,
                PhysicsFixturesComponent.class
        ));

        subRuntime = asm.get(Aspect.all(
                PhysicsRuntimeBodyComponent.class
        ));
        subRuntime.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override public void inserted(IntBag entities) {}

            @Override public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    destroyRuntimeBodyByEntityId(data[i]);
                }
            }
        });

        // Insert/remove => index/unindex + rebuild joint
        jointsSub = asm.get(Aspect.all(PhysicsJointComponent.class));
        jointsSub.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override public void inserted(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int jEid = data[i];
                    indexJoint(jEid);
                    if (dirty != null) dirty.joint(jEid, JointDirtyBits.ALL);
                }
            }

            @Override public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int jEid = data[i];
                    unindexJoint(jEid);
                    destroyRuntimeJointIfAny(jEid);
                }
            }
        });
    }

    @Override
    protected void processSystem() {
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
        if (dirty != null && fullRebuildPending) {
            IntBag wanted = subWanted.getEntities();
            int[] wantedData = wanted.getData();
            for (int i = 0, n = wanted.size(); i < n; i++) {
                int e = wantedData[i];
                if (isWantedEntity(e)) dirty.physics(e, PhysicsDirtyBits.ALL);
            }

            IntBag joints = jointsSub.getEntities();
            int[] jointsData = joints.getData();
            for (int i = 0, n = joints.size(); i < n; i++) {
                int jEid = jointsData[i];
                indexJoint(jEid);
                dirty.joint(jEid, JointDirtyBits.ALL);
            }
            fullRebuildPending = false;
        }

        if (dirty != null) {
            dirty.consumePhysics(e -> {
                boolean stillWanted = isWantedEntity(e);
                if (!stillWanted) {
                    destroyRuntimeBody(e);
                    return;
                }

                PhysicsRuntimeBodyComponent rt = mRuntime.has(e) ? mRuntime.get(e) : mRuntime.create(e);
                boolean hadBody = rt.body != null;
                if (rt.body == null) {
                    buildBody(e, rt);
                    if (rt.body == null) return;
                }

                int mask = dirty.physicsSub(e);

                if ((mask & (PhysicsDirtyBits.BODY | PhysicsDirtyBits.FIXTURE)) != 0) {
                    if (hadBody) {
                        rebuildBody(e, rt);
                    } else if ((mask & PhysicsDirtyBits.FILTER) != 0) {
                        updateFixtureFilters(e, rt.body);
                    }
                    return;
                }

                if ((mask & PhysicsDirtyBits.FILTER) != 0) {
                    updateFixtureFilters(e, rt.body);
                }
            });
        }

        // 3) Ensure wanted bodies exist
        IntBag wanted = subWanted.getEntities();
        int[] w = wanted.getData();
        for (int i = 0, n = wanted.size(); i < n; i++) {
            int e = w[i];
            if (!isWantedEntity(e)) continue;

            PhysicsRuntimeBodyComponent rt = mRuntime.has(e) ? mRuntime.get(e) : mRuntime.create(e);
            if (rt.body == null) buildBody(e, rt);

            if (rt.body != null) {
                PhysicsBodyComponent bd = mBodyDef.get(e);
                if (bd != null) rt.body.setActive(bd.enabled);
                rt.body.setAwake(true);
            }
        }

        // 4) Destroy runtime bodies no longer wanted
        IntBag runtime = subRuntime.getEntities();
        int[] r = runtime.getData();
        for (int i = 0, n = runtime.size(); i < n; i++) {
            int e = r[i];
            boolean stillWanted = isWantedEntity(e);
            if (!stillWanted) destroyRuntimeBody(e);
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

        // 5) Joints: only dirty
        if (dirty != null) {
            dirty.consumeJoints(this::syncOneJoint);
        }

        // 6) Step
        if (stepEnabled) {
            box2d.step(world.getDelta());
        }

        // 7) Sync transform <-> body
        for (int i = 0, n = wanted.size(); i < n; i++) {
            int e = w[i];
            PhysicsRuntimeBodyComponent rt = mRuntime.get(e);
            if (rt == null || rt.body == null) continue;

            TransformComponent t = mT.get(e);
            if (t == null) continue;

            if (stepEnabled) {
                Vector2 p = rt.body.getPosition();
                t.x = box2d.mToPx(p.x);
                t.y = box2d.mToPx(p.y);
                t.rotationRad = rt.body.getAngle();
                if (dirty != null) dirty.geometry(e, GeometryDirty.POSITION | GeometryDirty.ROTATION);
            } else {
                float targetX = box2d.pxToM(t.x);
                float targetY = box2d.pxToM(t.y);
                float targetAngle = t.rotationRad;

                Vector2 p = rt.body.getPosition();
                boolean movedByAuthoring = Math.abs(p.x - targetX) > 1e-6f
                        || Math.abs(p.y - targetY) > 1e-6f
                        || Math.abs(rt.body.getAngle() - targetAngle) > 1e-6f;

                rt.body.setTransform(
                        targetX,
                        targetY,
                        targetAngle
                );

                if (movedByAuthoring) {
                    refreshConnectedDistanceJointLengths(e);
                    markJointsDirtyForBody(e);
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Inverse cache (body -> joints)
    // -------------------------------------------------------------

    private void ensureJointsByBodyCapacity(int eid) {
        if (eid < 0) return;
        if (jointsByBody == null) {
            jointsByBodyCap = 1024;
            while (eid >= jointsByBodyCap) jointsByBodyCap <<= 1;
            jointsByBody = new IntArray[jointsByBodyCap];
            return;
        }
        if (eid < jointsByBodyCap) return;

        int next = jointsByBodyCap;
        while (eid >= next) next <<= 1;

        IntArray[] newArr = new IntArray[next];
        System.arraycopy(jointsByBody, 0, newArr, 0, jointsByBodyCap);
        jointsByBody = newArr;
        jointsByBodyCap = next;
    }

    private void addJointRef(int bodyEid, int jointEid) {
        if (bodyEid < 0 || jointEid < 0) return;
        ensureJointsByBodyCapacity(bodyEid);
        IntArray list = jointsByBody[bodyEid];
        if (list == null) jointsByBody[bodyEid] = list = new IntArray(false, 4);

        for (int i = 0; i < list.size; i++) if (list.get(i) == jointEid) return;
        list.add(jointEid);
    }

    private void removeJointRef(int bodyEid, int jointEid) {
        if (bodyEid < 0 || jointEid < 0) return;
        if (jointsByBody == null || bodyEid >= jointsByBodyCap) return;
        IntArray list = jointsByBody[bodyEid];
        if (list == null) return;

        for (int i = 0; i < list.size; i++) {
            if (list.get(i) == jointEid) {
                list.removeIndex(i);
                return;
            }
        }
    }

    // -------------------------------------------------------------
    // Endpoint cache (joint -> a/b) for correct unindex/reindex
    // -------------------------------------------------------------

    private void ensureJointEndpointCacheCapacity(int jEid) {
        if (jEid < 0) return;

        if (jointAByJoint == null) {
            jointsByJointCap = 1024;
            while (jEid >= jointsByJointCap) jointsByJointCap <<= 1;

            jointAByJoint = new int[jointsByJointCap];
            jointBByJoint = new int[jointsByJointCap];
            Arrays.fill(jointAByJoint, -1);
            Arrays.fill(jointBByJoint, -1);
            return;
        }

        if (jEid < jointsByJointCap) return;

        int next = jointsByJointCap;
        while (jEid >= next) next <<= 1;

        int old = jointsByJointCap;

        jointAByJoint = Arrays.copyOf(jointAByJoint, next);
        jointBByJoint = Arrays.copyOf(jointBByJoint, next);
        Arrays.fill(jointAByJoint, old, next, -1);
        Arrays.fill(jointBByJoint, old, next, -1);

        jointsByJointCap = next;
    }

    private void indexJoint(int jEid) {
        ensureJointEndpointCacheCapacity(jEid);

        PhysicsJointComponent j = mJointBase.getSafe(jEid, null);
        if (j == null) return;

        int newA = j.aEid;
        int newB = j.bEid;

        int oldA = jointAByJoint[jEid];
        int oldB = jointBByJoint[jEid];

        if (oldA == newA && oldB == newB && oldA != -1 && oldB != -1) return;

        if (oldA != -1) removeJointRef(oldA, jEid);
        if (oldB != -1) removeJointRef(oldB, jEid);

        addJointRef(newA, jEid);
        addJointRef(newB, jEid);

        jointAByJoint[jEid] = newA;
        jointBByJoint[jEid] = newB;
    }

    private void unindexJoint(int jEid) {
        if (jointAByJoint == null || jEid < 0 || jEid >= jointsByJointCap) return;

        int a = jointAByJoint[jEid];
        int b = jointBByJoint[jEid];

        if (a != -1) removeJointRef(a, jEid);
        if (b != -1) removeJointRef(b, jEid);

        jointAByJoint[jEid] = -1;
        jointBByJoint[jEid] = -1;
    }

    private void markJointsDirtyForBody(int bodyEid) {
        if (dirty == null) return;
        if (jointsByBody == null || bodyEid < 0 || bodyEid >= jointsByBodyCap) return;

        IntArray list = jointsByBody[bodyEid];
        if (list == null || list.size == 0) return;

        for (int i = 0; i < list.size; i++) {
            dirty.joint(list.get(i), JointDirtyBits.ALL);
        }
    }

    private void refreshConnectedDistanceJointLengths(int bodyEid) {
        if (jointsByBody == null || bodyEid < 0 || bodyEid >= jointsByBodyCap) return;

        IntArray list = jointsByBody[bodyEid];
        if (list == null || list.size == 0) return;

        for (int i = 0; i < list.size; i++) {
            int jEid = list.get(i);

            PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
            if (base == null || base.type != PhysicsJointComponent.TYPE_DISTANCE) continue;

            PhysicsDistanceJointComponent dist = mJointDist.getSafe(jEid, null);
            if (dist == null) continue;

            TransformComponent aT = mT.getSafe(base.aEid, null);
            TransformComponent bT = mT.getSafe(base.bEid, null);
            if (aT == null || bT == null) continue;

            float ax = anchorWorldX(aT, base.anchorAx, base.anchorAy);
            float ay = anchorWorldY(aT, base.anchorAx, base.anchorAy);
            float bx = anchorWorldX(bT, base.anchorBx, base.anchorBy);
            float by = anchorWorldY(bT, base.anchorBx, base.anchorBy);

            float lenWU = Vector2.dst(ax, ay, bx, by);
            dist.lengthM = Math.max(0.001f, box2d.pxToM(lenWU));
        }
    }

    private float anchorWorldX(TransformComponent t, float localAx, float localAy) {
        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);
        float rx_m = localAx * cos - localAy * sin;
        return t.x + box2d.mToPx(rx_m);
    }

    private float anchorWorldY(TransformComponent t, float localAx, float localAy) {
        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);
        float ry_m = localAx * sin + localAy * cos;
        return t.y + box2d.mToPx(ry_m);
    }

    private void detachRuntimeJointsForBody(int bodyEid) {
        if (jointsByBody == null || bodyEid < 0 || bodyEid >= jointsByBodyCap) return;
        IntArray list = jointsByBody[bodyEid];
        if (list == null || list.size == 0) return;

        for (int i = 0; i < list.size; i++) {
            int jEid = list.get(i);

            PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
            if (rt != null) {
                rt.joint = null;
                if (mJointRt.has(jEid)) mJointRt.remove(jEid);
            }
            if (dirty != null) dirty.joint(jEid, JointDirtyBits.ALL);
        }
    }

    // -------------------------------------------------------------
    // Bodies
    // -------------------------------------------------------------

    private boolean hasValidFixtures(int e) {
        if (!mFixDefs.has(e)) return false;
        PhysicsFixturesComponent fixtures = mFixDefs.get(e);
        return fixtures != null && fixtures.hasFixtures();
    }

    private boolean isWantedEntity(int e) {
        return e >= 0 && mT.has(e) && mBodyDef.has(e) && hasValidFixtures(e);
    }

    private void updateFixtureFilters(int e, Body body) {
        if (body == null) return;

        PhysicsFixturesComponent defs = mFixDefs.getSafe(e, null);
        if (defs == null || !defs.hasFixtures()) return;

        Array<Fixture> runtimeFixtures = body.getFixtureList();
        int count = Math.min(runtimeFixtures.size, defs.fixtures.size);
        for (int i = 0; i < count; i++) {
            FixtureDefData fd = defs.fixtures.get(i);
            if (fd == null) continue;

            Filter filter = new Filter();
            filter.categoryBits = fd.categoryBits;
            filter.maskBits = fd.maskBits;
            filter.groupIndex = fd.groupIndex;
            runtimeFixtures.get(i).setFilterData(filter);
        }
    }

    private void buildBody(int e, PhysicsRuntimeBodyComponent rt) {
        rt.body = createBodyFromComponents(e);
        if (rt.body != null) {
            rt.body.setUserData(e);
            rt.gen++;
            markJointsDirtyForBody(e);
        }
    }

    private void rebuildBody(int e, PhysicsRuntimeBodyComponent rt) {
        if (rt.body != null) {
            detachRuntimeJointsForBody(e);
            box2d.world.destroyBody(rt.body);
            rt.body = null;
        }
        rt.body = createBodyFromComponents(e);
        if (rt.body != null) {
            rt.body.setUserData(e);
            rt.gen++;
            markJointsDirtyForBody(e);
        }
    }

    private void destroyRuntimeBody(int e) {
        if (!mRuntime.has(e)) return;

        PhysicsRuntimeBodyComponent rt = mRuntime.get(e);
        if (rt != null && rt.body != null && box2d != null && box2d.world != null) {
            detachRuntimeJointsForBody(e);
            box2d.world.destroyBody(rt.body);
            rt.body = null;
            rt.gen++;
        }

        markJointsDirtyForBody(e);
        mRuntime.remove(e);
    }

    private void destroyRuntimeBodyByEntityId(int e) {
        if (box2d == null || box2d.world == null) {
            if (mRuntime.has(e)) mRuntime.remove(e);
            return;
        }

        detachRuntimeJointsForBody(e);

        boolean destroyed = false;

        if (mRuntime.has(e)) {
            PhysicsRuntimeBodyComponent rt = mRuntime.get(e);
            if (rt != null && rt.body != null) {
                box2d.world.destroyBody(rt.body);
                rt.body = null;
                rt.gen++;
                destroyed = true;
            }
            mRuntime.remove(e);
        }

        if (!destroyed) {
            bodyScratch.clear();
            box2d.world.getBodies(bodyScratch);
            for (int i = 0; i < bodyScratch.size; i++) {
                Body body = bodyScratch.get(i);
                if (body == null) continue;
                Object userData = body.getUserData();
                if (!(userData instanceof Integer) || ((Integer) userData).intValue() != e) continue;
                box2d.world.destroyBody(body);
                break;
            }
        }

        markJointsDirtyForBody(e);
    }

    private Body createBodyFromComponents(int e) {
        if (box2d == null || box2d.world == null) return null;
        if (!isWantedEntity(e)) return null;

        TransformComponent t = mT.get(e);
        PhysicsBodyComponent bd = mBodyDef.get(e);
        PhysicsFixturesComponent defs = mFixDefs.get(e);
        if (t == null || bd == null || defs == null || !defs.hasFixtures()) return null;

        BodyDef def = new BodyDef();
        def.type = (bd.type == 0) ? BodyDef.BodyType.StaticBody
                : (bd.type == 1) ? BodyDef.BodyType.KinematicBody
                : BodyDef.BodyType.DynamicBody;

        def.position.set(box2d.pxToM(t.x), box2d.pxToM(t.y));
        def.angle = t.rotationRad;
        def.fixedRotation = bd.fixedRotation;
        def.bullet = bd.bullet;
        def.allowSleep = box2d.isDoSleep() && bd.allowSleep;
        def.gravityScale = bd.gravityScale;
        def.linearDamping = bd.linearDamping;
        def.angularDamping = bd.angularDamping;

        Body body = box2d.world.createBody(def);
        int createdFixtures = 0;

        Array<FixtureDefData> fixtures = defs.fixtures;
        for (int i = 0, n = fixtures.size; i < n; i++) {
            FixtureDefData fd = fixtures.get(i);
            if (fd == null) continue;

            Shape shape = null;
            try {
                shape = createShape(fd);
                if (shape == null) continue;

                FixtureDef fdef = new FixtureDef();
                fdef.shape = shape;
                fdef.density = fd.density;
                fdef.friction = fd.friction;
                fdef.restitution = fd.restitution;
                fdef.isSensor = fd.isSensor;
                fdef.filter.categoryBits = fd.categoryBits;
                fdef.filter.maskBits = fd.maskBits;
                fdef.filter.groupIndex = fd.groupIndex;

                body.createFixture(fdef);
                createdFixtures++;
            } catch (Throwable ignore) {
                // One invalid fixture does not cancel the others.
            } finally {
                if (shape != null) shape.dispose();
            }
        }

        if (createdFixtures == 0) {
            box2d.world.destroyBody(body);
            return null;
        }

        return body;
    }

    private Shape createShape(FixtureDefData fd) {
        switch (fd.shapeType) {
            case FixtureDefData.SHAPE_CIRCLE:
                return createCircleShape(fd);
            case FixtureDefData.SHAPE_POLYGON:
                return createPolygonShape(fd);
            default:
                return createBoxShape(fd);
        }
    }

    private Shape createBoxShape(FixtureDefData fd) {
        PolygonShape shape = new PolygonShape();
        float angleRad = (float) Math.toRadians(fd.angleDeg);
        shape.setAsBox(fd.halfW, fd.halfH, tmp.set(fd.offsetX, fd.offsetY), angleRad);
        return shape;
    }

    private Shape createCircleShape(FixtureDefData fd) {
        CircleShape shape = new CircleShape();
        shape.setRadius(fd.radius);
        shape.setPosition(tmp.set(fd.offsetX, fd.offsetY));
        return shape;
    }

    private Shape createPolygonShape(FixtureDefData fd) {
        int n = fd.polyCount;
        float[] vertices = fd.polyVerts;
        boolean valid = vertices != null && n >= 3 && n <= MAX_POLYGON_VERTICES && vertices.length >= n * 2;
        if (!valid) return createBoxShape(fd);

        float angleRad = (float) Math.toRadians(fd.angleDeg);
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);

        for (int i = 0; i < n; i++) {
            float lx = vertices[i * 2];
            float ly = vertices[i * 2 + 1];
            if (!Float.isFinite(lx) || !Float.isFinite(ly)) {
                return createBoxShape(fd);
            }

            float x = lx * cos - ly * sin + fd.offsetX;
            float y = lx * sin + ly * cos + fd.offsetY;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                return createBoxShape(fd);
            }

            int base = i * 2;
            polygonVertsScratch[base] = x;
            polygonVertsScratch[base + 1] = y;
        }

        PolygonShape shape = new PolygonShape();
        try {
            shape.set(polygonVertsScratch, 0, n * 2);
            return shape;
        } catch (Throwable ex) {
            shape.dispose();
            return createBoxShape(fd);
        }
    }

    // -------------------------------------------------------------
    // Joints (dirty-driven)
    // -------------------------------------------------------------

    private void syncOneJoint(int jEid) {
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
        def.frequencyHz  = Math.max(0f, dist.frequencyHz);
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

        float len2 = ax*ax + ay*ay;
        if (len2 < 1e-12f) { // fallback critique
            ax = 0f;
            ay = 1f;
        } else {
            float inv = (float)(1.0 / Math.sqrt(len2));
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

    private void destroyRuntimeJointIfAny(int jEid) {
        PhysicsRuntimeJointComponent rt = mJointRt.getSafe(jEid, null);
        if (rt != null && rt.joint != null && box2d != null && box2d.world != null) {
            box2d.world.destroyJoint(rt.joint);
            rt.joint = null;
        }
        if (mJointRt.has(jEid)) mJointRt.remove(jEid);

        markDependentGearJointsDirty(jEid);
    }

    private void markDependentGearJointsDirty(int sourceJointEid) {
        if (dirty == null || sourceJointEid < 0) return;

        IntBag bag = jointsSub.getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];
            PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
            if (base == null || base.type != PhysicsJointComponent.TYPE_GEAR) continue;

            PhysicsGearJointComponent gear = mGear.getSafe(jEid, null);
            if (gear == null) continue;

            if (gear.joint1Eid == sourceJointEid || gear.joint2Eid == sourceJointEid) {
                dirty.joint(jEid, JointDirtyBits.ALL);
            }
        }
    }

    // -------------------------------------------------------------
    // API
    // -------------------------------------------------------------

    public void setStepEnabled(boolean v) { this.stepEnabled = v; }
    public boolean isStepEnabled() { return stepEnabled; }
}
