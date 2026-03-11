package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsPrismaticJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRevoluteJointComponent;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/**
 * Centralise la logique "métier" Physique (Editor/Runtime):
 * - Crée/supprime body+fixture
 * - Crée/supprime joints
 * - Marque les dirty bits
 * - Helpers anchors (local meters) -> world WU(px)
 * <p>
 * Le runtime Box2D (Body/Joint) est créé/détruit par Box2dSyncSystem.
 */
public final class PhysicsService {

    private final World world;
    private Box2dWorldService box2d;
    private final DirtyTrackerSystem dirty;

    private final ComponentMapper<TransformComponent> mT;
    private final ComponentMapper<PhysicsBodyComponent> mBody;
    private final ComponentMapper<PhysicsFixturesComponent> mFixtures;

    private final ComponentMapper<PhysicsJointComponent> mJoint;
    private final ComponentMapper<PhysicsDistanceJointComponent> mDist;
    private final ComponentMapper<PhysicsRevoluteJointComponent> mRev;
    private final ComponentMapper<PhysicsPrismaticJointComponent> mPrism;
    private final ComponentMapper<PhysicsWheelJointComponent> mWheel;

    private Vector2 tmpA = new Vector2();
    private Vector2 tmpB = new Vector2();

    public PhysicsService(World world, Box2dWorldService box2d) {
        this.world = world;
        this.box2d = box2d;
        this.dirty = world.getSystem(DirtyTrackerSystem.class);

        this.mT    = world.getMapper(TransformComponent.class);
        this.mBody = world.getMapper(PhysicsBodyComponent.class);
        this.mFixtures  = world.getMapper(PhysicsFixturesComponent.class);

        this.mJoint = world.getMapper(PhysicsJointComponent.class);
        this.mDist  = world.getMapper(PhysicsDistanceJointComponent.class);
        this.mRev   = world.getMapper(PhysicsRevoluteJointComponent.class);
        this.mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        this.mWheel = world.getMapper(PhysicsWheelJointComponent.class);
    }

    public void setBox2d(Box2dWorldService box2d) {
        this.box2d = box2d;
    }

    /** Box2D disponible (world créé). */
    public boolean isAvailable() {
        return box2d != null;
    }

    // ---------------------------------------------------------------------
    // Body / Fixture
    // ---------------------------------------------------------------------

    public boolean hasBody(int eid)      { return eid >= 0 && mBody.has(eid); }
    public boolean hasFixtures(int eid)  { return eid >= 0 && mFixtures.has(eid) && mFixtures.get(eid).hasFixtures(); }
    public boolean hasPhysics(int eid)   { return hasBody(eid) && hasFixtures(eid); }

    public void ensurePhysics(int eid) {
        if (eid < 0) return;

        if (!mBody.has(eid)) initDefaultBody(mBody.create(eid));

        PhysicsFixturesComponent fixtures = mFixtures.has(eid) ? mFixtures.get(eid) : mFixtures.create(eid);
        if (!fixtures.hasFixtures()) {
            fixtures.fixtures.clear();
            fixtures.fixtures.add(createDefaultFixture());
        }

        markPhysicsDirty(eid);
    }

    public void removePhysics(int eid) {
        if (eid < 0) return;

        // garder runtime cohérent: pas de joint orphelin
        deleteAllJointsReferencingBody(eid);

        if (mFixtures.has(eid))  mFixtures.remove(eid);
        if (mBody.has(eid)) mBody.remove(eid);

        markPhysicsDirty(eid);
    }

    private void markPhysicsDirty(int eid) {
        if (dirty != null && eid >= 0) dirty.physics(eid, PhysicsDirtyBits.ALL);
    }

    public PhysicsFixturesComponent getFixturesComponent(int eid) {
        return eid >= 0 ? mFixtures.getSafe(eid, null) : null;
    }

    public void ensureFixtureIds(int eid) {
        PhysicsFixturesComponent fixtures = getFixturesComponent(eid);
        if (fixtures == null) return;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData f = fixtures.fixtures.get(i);
            FixtureIdSequence.i().ensure(f);
        }
    }

    public int fixtureCount(int eid) {
        PhysicsFixturesComponent fixtures = getFixturesComponent(eid);
        return fixtures != null ? fixtures.fixtures.size : 0;
    }

    public int countCircleFixtures(int eid) {
        return countFixturesByType(eid, FixtureDefData.SHAPE_CIRCLE);
    }

    public int countQuadFixtures(int eid) {
        return countFixturesByType(eid, FixtureDefData.SHAPE_BOX);
    }

    public int countPolygonFixtures(int eid) {
        return countFixturesByType(eid, FixtureDefData.SHAPE_POLYGON);
    }

    private int countFixturesByType(int eid, int shapeType) {
        PhysicsFixturesComponent fixtures = getFixturesComponent(eid);
        if (fixtures == null) return 0;
        int count = 0;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData f = fixtures.fixtures.get(i);
            if (f != null && f.shapeType == shapeType) count++;
        }
        return count;
    }

    public FixtureDefData getFixtureById(int eid, long fixtureId) {
        if (fixtureId <= 0L) return null;
        PhysicsFixturesComponent fixtures = getFixturesComponent(eid);
        if (fixtures == null) return null;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData f = fixtures.fixtures.get(i);
            if (f == null) continue;
            FixtureIdSequence.i().ensure(f);
            if (f.fixtureId == fixtureId) return f;
        }
        return null;
    }

    public long pickFixtureId(int bodyEid, float worldX, float worldY, float toleranceWU) {
        if (!hasPhysics(bodyEid) || !isAvailable()) return -1L;
        PhysicsFixturesComponent fixtures = getFixturesComponent(bodyEid);
        if (fixtures == null || fixtures.fixtures.size == 0) return -1L;

        float[] verts = new float[32];
        for (int i = fixtures.fixtures.size - 1; i >= 0; i--) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;
            FixtureIdSequence.i().ensure(fixture);
            if (hitTestFixture(bodyEid, fixture, worldX, worldY, toleranceWU, verts)) {
                return fixture.fixtureId;
            }
        }
        return -1L;
    }

    public boolean computeFixtureCenterWU(int bodyEid, FixtureDefData fixture, Vector2 outCenterWU) {
        return transformFixturePointWU(bodyEid, fixture, 0f, 0f, outCenterWU);
    }

    public float computeFixtureRadiusWU(FixtureDefData fixture) {
        if (fixture == null || !isAvailable()) return 0f;
        return box2d.mToPx(Math.max(0f, fixture.radius));
    }

    public int computeFixtureVerticesWU(int bodyEid, FixtureDefData fixture, float[] outVertsWU) {
        if (fixture == null || outVertsWU == null || !isAvailable()) return 0;

        if (fixture.shapeType == FixtureDefData.SHAPE_BOX) {
            if (outVertsWU.length < 8) return 0;
            if (!transformFixturePointWU(bodyEid, fixture, -fixture.halfW, -fixture.halfH, outVertsWU, 0)) return 0;
            transformFixturePointWU(bodyEid, fixture, fixture.halfW, -fixture.halfH, outVertsWU, 2);
            transformFixturePointWU(bodyEid, fixture, fixture.halfW, fixture.halfH, outVertsWU, 4);
            transformFixturePointWU(bodyEid, fixture, -fixture.halfW, fixture.halfH, outVertsWU, 6);
            return 4;
        }

        if (fixture.shapeType == FixtureDefData.SHAPE_POLYGON) {
            int count = safePolyCount(fixture);
            if (count < 3) return 0;
            if (outVertsWU.length < count * 2) return 0;
            for (int i = 0; i < count; i++) {
                float lx = fixture.polyVerts[i * 2];
                float ly = fixture.polyVerts[i * 2 + 1];
                transformFixturePointWU(bodyEid, fixture, lx, ly, outVertsWU, i * 2);
            }
            return count;
        }

        return 0;
    }

    private boolean hitTestFixture(int bodyEid,
                                   FixtureDefData fixture,
                                   float worldX,
                                   float worldY,
                                   float toleranceWU,
                                   float[] scratchVerts) {
        if (fixture == null) return false;

        if (fixture.shapeType == FixtureDefData.SHAPE_CIRCLE) {
            if (!computeFixtureCenterWU(bodyEid, fixture, tmpA)) return false;
            float r = computeFixtureRadiusWU(fixture) + Math.max(0f, toleranceWU);
            return tmpA.dst2(worldX, worldY) <= r * r;
        }

        int neededFloats = fixture.shapeType == FixtureDefData.SHAPE_BOX ? 8 : safePolyCount(fixture) * 2;
        if (neededFloats <= 0) return false;

        float[] verts = scratchVerts;
        if (verts.length < neededFloats) {
            verts = new float[neededFloats];
        }
        int vertexCount = computeFixtureVerticesWU(bodyEid, fixture, verts);
        if (vertexCount < 3) return false;

        int floatCount = vertexCount * 2;
        if (Intersector.isPointInPolygon(verts, 0, floatCount, worldX, worldY)) {
            return true;
        }
        return isNearClosedPolyline(verts, vertexCount, worldX, worldY, toleranceWU);
    }

    private boolean isNearClosedPolyline(float[] verts, int vertexCount, float worldX, float worldY, float toleranceWU) {
        if (vertexCount < 2) return false;
        float tol2 = toleranceWU * toleranceWU;
        for (int i = 0; i < vertexCount; i++) {
            int j = (i + 1) % vertexCount;
            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[j * 2];
            float by = verts[j * 2 + 1];
            if (pointSegmentDst2(worldX, worldY, ax, ay, bx, by) <= tol2) return true;
        }
        return false;
    }

    private static float pointSegmentDst2(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float apx = px - ax;
        float apy = py - ay;

        float abLen2 = abx * abx + aby * aby;
        if (abLen2 <= 1e-12f) {
            float dx = px - ax;
            float dy = py - ay;
            return dx * dx + dy * dy;
        }

        float t = (apx * abx + apy * aby) / abLen2;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;

        float cx = ax + abx * t;
        float cy = ay + aby * t;
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy;
    }

    private int safePolyCount(FixtureDefData fixture) {
        if (fixture == null || fixture.polyVerts == null) return 0;
        return Math.max(0, Math.min(fixture.polyCount, fixture.polyVerts.length / 2));
    }

    private boolean transformFixturePointWU(int bodyEid, FixtureDefData fixture, float localX_m, float localY_m, Vector2 outWU) {
        if (outWU == null) return false;
        outWU.set(0f, 0f);
        if (!isAvailable() || bodyEid < 0 || fixture == null) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float fixtureAngle = (float) Math.toRadians(fixture.angleDeg);
        float fcos = (float) Math.cos(fixtureAngle);
        float fsin = (float) Math.sin(fixtureAngle);
        float fx = localX_m * fcos - localY_m * fsin + fixture.offsetX;
        float fy = localX_m * fsin + localY_m * fcos + fixture.offsetY;

        float bcos = (float) Math.cos(t.rotationRad);
        float bsin = (float) Math.sin(t.rotationRad);
        float bx = fx * bcos - fy * bsin;
        float by = fx * bsin + fy * bcos;

        outWU.set(
                t.x + box2d.mToPx(bx),
                t.y + box2d.mToPx(by)
        );
        return true;
    }

    private boolean transformFixturePointWU(int bodyEid, FixtureDefData fixture, float localX_m, float localY_m, float[] outVertsWU, int outIndex) {
        if (!isAvailable() || outVertsWU == null || outIndex < 0 || outIndex + 1 >= outVertsWU.length) return false;
        if (bodyEid < 0 || fixture == null) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float fixtureAngle = (float) Math.toRadians(fixture.angleDeg);
        float fcos = (float) Math.cos(fixtureAngle);
        float fsin = (float) Math.sin(fixtureAngle);
        float fx = localX_m * fcos - localY_m * fsin + fixture.offsetX;
        float fy = localX_m * fsin + localY_m * fcos + fixture.offsetY;

        float bcos = (float) Math.cos(t.rotationRad);
        float bsin = (float) Math.sin(t.rotationRad);
        float bx = fx * bcos - fy * bsin;
        float by = fx * bsin + fy * bcos;

        outVertsWU[outIndex] = t.x + box2d.mToPx(bx);
        outVertsWU[outIndex + 1] = t.y + box2d.mToPx(by);
        return true;
    }

    // ---------------------------------------------------------------------
    // Joints
    // ---------------------------------------------------------------------

    public boolean isJoint(int eid) {
        return eid >= 0 && mJoint.has(eid);
    }

    public boolean isDistanceJoint(int eid) {
        if (eid < 0) return false;
        PhysicsJointComponent base = mJoint.getSafe(eid, null);
        return base != null
                && base.type == PhysicsJointComponent.TYPE_DISTANCE
                && mDist.has(eid);
    }

    public boolean isPrismaticJoint(int eid) {
        if (eid < 0) return false;
        PhysicsJointComponent base = mJoint.getSafe(eid, null);
        return base != null
                && base.type == PhysicsJointComponent.TYPE_PRISMATIC
                && mPrism.has(eid);
    }

    /**
     * Crée un joint Distance entre deux bodies.
     * Anchors au centre local (0,0).
     */
    public int createDistanceJoint(int aEid, int bEid) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_DISTANCE;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;

        // anchors local meters (center)
        base.anchorAx = 0f;
        base.anchorAy = 0f;
        base.anchorBx = 0f;
        base.anchorBy = 0f;

        PhysicsDistanceJointComponent dist = mDist.create(jEid);

        float lenM = 1f;
        if (isAvailable()) {
            if (computeDistanceJointEndpointsWU(jEid, tmpA, tmpB)) {
                float lenWU = tmpA.dst(tmpB);
                lenM = Math.max(0.001f, box2d.pxToM(lenWU));
            }
        }
        dist.lengthM = lenM;

        dist.frequencyHz  = Math.max(0f, dist.frequencyHz);
        dist.dampingRatio = Math.max(0f, Math.min(1f, dist.dampingRatio));

        markJointDirty(jEid);
        return jEid;
    }

    public float pxToM(float value) {
        return box2d.pxToM(value);
    }

    /**
     * Crée un joint Revolute entre deux bodies, pivot world (WU/pixels).
     */
    public int createRevoluteJoint(int aEid, int bEid, float pivotWuX, float pivotWuY) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, pivotWuX, pivotWuY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, pivotWuX, pivotWuY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_REVOLUTE;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        mRev.create(jEid);

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Crée un joint Prismatic entre deux bodies, pivot world (WU/pixels).
     */
    public int createPrismaticJoint(int aEid, int bEid, float pivotWuX, float pivotWuY) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, pivotWuX, pivotWuY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, pivotWuX, pivotWuY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_PRISMATIC;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        PhysicsPrismaticJointComponent prism = mPrism.create(jEid);
        prism.axisX = 1f;
        prism.axisY = 0f;

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Crée un joint Wheel entre deux bodies, pivot world (WU/pixels).
     */
    public int createWheelJoint(int aEid, int bEid, float worldX, float worldY) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, worldX, worldY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, worldX, worldY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_WHEEL;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        mWheel.create(jEid);

        markJointDirty(jEid);
        return jEid;
    }


    public void deleteJoint(int jointEid) {
        if (jointEid < 0) return;

        // world.delete => Box2dSyncSystem removed() => destroy runtime joint + unindex
        try {
            world.delete(jointEid);
        } catch (Throwable ignore) {
            // fallback: remove components
            if (mDist.has(jointEid))  mDist.remove(jointEid);
            if (mJoint.has(jointEid)) mJoint.remove(jointEid);
        }
    }

    /**
     * Liste (scan) des joints reliés à un body.
     * OK pour UI. (Pour l’instant, on renvoie tous les joints, peu importe le type.)
     */
    public IntArray listJointsForBody(int bodyEid, IntArray out) {
        if (out == null) out = new IntArray(false, 8);
        out.clear();
        if (bodyEid < 0) return out;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];
            PhysicsJointComponent j = mJoint.getSafe(jEid, null);
            if (j == null) continue;
            if (j.aEid == bodyEid || j.bEid == bodyEid) out.add(jEid);
        }
        return out;
    }

    private void deleteAllJointsReferencingBody(int bodyEid) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];
            PhysicsJointComponent j = mJoint.getSafe(jEid, null);
            if (j == null) continue;
            if (j.aEid == bodyEid || j.bEid == bodyEid) deleteJoint(jEid);
        }
    }

    private void markJointDirty(int jointEid) {
        if (dirty != null && jointEid >= 0) dirty.joint(jointEid, JointDirtyBits.ALL);
    }

    // ---------------------------------------------------------------------
    // Anchors helpers (local meters -> world WU(px))
    // ---------------------------------------------------------------------

    /**
     * Anchor local (m) -> world (WU/pixels), rotation incluse.
     * Retourne false si Box2D n’est pas dispo (évite NPE).
     */
    public boolean computeAnchorWorldWU(int bodyEid, float localAx_m, float localAy_m, Vector2 outWU) {
        if (outWU == null) return false;
        outWU.set(0f, 0f);

        if (!isAvailable()) return false;
        if (bodyEid < 0) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);

        float rx_m = localAx_m * cos - localAy_m * sin;
        float ry_m = localAx_m * sin + localAy_m * cos;

        outWU.set(
                t.x + box2d.mToPx(rx_m),
                t.y + box2d.mToPx(ry_m)
        );
        return true;
    }

    /**
     * Endpoints world (WU) d’un joint Distance.
     * Retourne false si pas dispo (box2d null, joint invalide, bodies invalides…).
     */
    public boolean computeDistanceJointEndpointsWU(int jointEid, Vector2 outA, Vector2 outB) {
        if (outA == null || outB == null) return false;
        outA.set(0f, 0f);
        outB.set(0f, 0f);

        if (!isAvailable()) return false;

        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        if (base.type != PhysicsJointComponent.TYPE_DISTANCE) return false;
        if (!mDist.has(jointEid)) return false;

        int aEid = base.aEid;
        int bEid = base.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        if (!computeAnchorWorldWU(aEid, base.anchorAx, base.anchorAy, outA)) return false;
        if (!computeAnchorWorldWU(bEid, base.anchorBx, base.anchorBy, outB)) return false;
        return true;
    }


    /**
     * Pivot world (WU/pixels) d’un joint Revolute.
     */
    public boolean computeRevoluteJointPivotWU(int jointEid, Vector2 outPivotWU) {
        if (outPivotWU == null) return false;
        outPivotWU.set(0f, 0f);

        if (!isAvailable()) return false;

        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        if (base.type != PhysicsJointComponent.TYPE_REVOLUTE) return false;
        if (!mRev.has(jointEid)) return false; // invariant

        int aEid = base.aEid;
        int bEid = base.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        return computeAnchorWorldWU(aEid, base.anchorAx, base.anchorAy, outPivotWU);
    }

    /**
     * Pivot world (WU/pixels) d’un joint Prismatic.
     */
    public boolean computePrismaticJointPivotWU(int jointEid, Vector2 outPivotWU) {
        if (outPivotWU == null) return false;
        outPivotWU.set(0f, 0f);

        if (!isAvailable()) return false;

        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        if (base.type != PhysicsJointComponent.TYPE_PRISMATIC) return false;
        if (!mPrism.has(jointEid)) return false; // invariant

        int aEid = base.aEid;
        int bEid = base.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        return computeAnchorWorldWU(aEid, base.anchorAx, base.anchorAy, outPivotWU);
    }

    /**
     * Pivot world (WU/pixels) d’un joint Wheel.
     */
    public boolean computeWheelJointPivotWU(int jointEid, Vector2 outPivotWU) {
        if (outPivotWU == null) return false;
        outPivotWU.set(0f, 0f);

        if (!isAvailable()) return false;

        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        if (base.type != PhysicsJointComponent.TYPE_WHEEL) return false;
        if (!mWheel.has(jointEid)) return false;

        int aEid = base.aEid;
        int bEid = base.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        return computeAnchorWorldWU(aEid, base.anchorAx, base.anchorAy, outPivotWU);
    }

    /**
     * Pivot + axe world (WU/pixels) d’un joint Prismatic.
     */
    public boolean computePrismaticJointGizmoWU(int jointEid, Vector2 outPivotWU, Vector2 outAxisEndWU) {
        if (outPivotWU == null || outAxisEndWU == null) return false;
        outPivotWU.set(0f, 0f);
        outAxisEndWU.set(0f, 0f);

        if (!isAvailable()) return false;

        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        if (base.type != PhysicsJointComponent.TYPE_PRISMATIC) return false;

        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        if (prism == null) return false;

        int aEid = base.aEid;
        int bEid = base.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        TransformComponent t = mT.getSafe(aEid, null);
        if (t == null) return false;

        if (!computeAnchorWorldWU(aEid, base.anchorAx, base.anchorAy, outPivotWU)) return false;

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

        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);
        float worldAxisX = axisX * cos - axisY * sin;
        float worldAxisY = axisX * sin + axisY * cos;

        float axisLenWu = 60f;
        outAxisEndWU.set(
                outPivotWU.x + worldAxisX * axisLenWu,
                outPivotWU.y + worldAxisY * axisLenWu
        );

        return true;
    }

    /**
     * World pivot (WU) -> local anchor (m) for a body.
     */
    private boolean computeLocalAnchorMetersFromWorldPivot(int bodyEid, float pivotWuX, float pivotWuY, Vector2 outLocalMeters) {
        if (outLocalMeters == null) return false;
        outLocalMeters.set(0f, 0f);

        if (!isAvailable()) return false;
        if (bodyEid < 0) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float dxWu = pivotWuX - t.x;
        float dyWu = pivotWuY - t.y;

        float dxM = box2d.pxToM(dxWu);
        float dyM = box2d.pxToM(dyWu);

        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);

        float localAx = dxM * cos + dyM * sin;
        float localAy = -dxM * sin + dyM * cos;

        outLocalMeters.set(localAx, localAy);
        return true;
    }

    // ---------------------------------------------------------------------
    // Defaults (alignés avec PhysicsPanel)
    // ---------------------------------------------------------------------

    public static void initDefaultBody(PhysicsBodyComponent b) {
        b.type = PhysicsBodyComponent.DYNAMIC;
        b.fixedRotation = false;
        b.bullet = false;
        b.allowSleep = true;
        b.awake = true;
        b.gravityScale = 1f;
        b.linearDamping = 0f;
        b.angularDamping = 0f;
        b.enabled = true;
    }

    public static FixtureDefData createDefaultFixture() {
        FixtureDefData f = new FixtureDefData();
        initDefaultFixture(f);
        return f;
    }

    public static void initDefaultFixture(FixtureDefData f) {
        f.shapeType = FixtureDefData.SHAPE_BOX;

        f.polyVerts = new float[0];
        f.polyCount = 0;

        f.halfW = 0.5f;
        f.halfH = 0.5f;
        f.angleDeg = 0f;

        f.radius = 0.5f;

        f.offsetX = 0f;
        f.offsetY = 0f;

        f.density = 1f;
        f.friction = 0.2f;
        f.restitution = 0f;
        f.isSensor = false;

        f.categoryBits = 0x0001;
        f.maskBits = (short) 0xFFFF;
        f.groupIndex = 0;

        FixtureIdSequence.i().ensure(f);
    }
}

