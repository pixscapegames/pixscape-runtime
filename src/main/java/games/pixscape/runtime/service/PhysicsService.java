package games.pixscape.runtime.service;

import games.pixscape.runtime.physics.PhysicsDirectGeometryData;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.physics.*;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/**
 * Centralizes Physics business logic (Editor/Runtime):
 * - Creates/removes bodies and logical shapes
 * - Creates/removes joints
 * - Marks dirty bits
 * - Helpers anchors (local meters) -> world WU(px)
 * <p>
 * The Box2D runtime objects (Body/Joint) are created/destroyed by Box2dSyncSystem.
 */
public final class PhysicsService {
    private static final PhysicsShapeResolver SHAPE_RESOLVER = new PhysicsShapeResolver();
    private static final PhysicsBodyCompiler BODY_COMPILER = new PhysicsBodyCompiler();
    private static final PhysicsCompiledFixtureCachePublisher CACHE_PUBLISHER =
            new PhysicsCompiledFixtureCachePublisher();

    public static void requireNoAuthoredPhysics(
            World world, IntBag entityIds, String context) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (entityIds == null) {
            throw new IllegalArgumentException("entityIds must not be null");
        }

        String label = context == null || context.trim().isEmpty()
                ? "Content"
                : context;
        ComponentMapper<PhysicsBodyComponent> bodies =
                world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsShapesComponent> shapes =
                world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsJointComponent> joints =
                world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsDistanceJointComponent> distances =
                world.getMapper(PhysicsDistanceJointComponent.class);
        ComponentMapper<PhysicsRevoluteJointComponent> revolutes =
                world.getMapper(PhysicsRevoluteJointComponent.class);
        ComponentMapper<PhysicsPrismaticJointComponent> prismatics =
                world.getMapper(PhysicsPrismaticJointComponent.class);
        ComponentMapper<PhysicsWheelJointComponent> wheels =
                world.getMapper(PhysicsWheelJointComponent.class);
        ComponentMapper<PhysicsFrictionJointComponent> frictions =
                world.getMapper(PhysicsFrictionJointComponent.class);
        ComponentMapper<PhysicsMotorJointComponent> motors =
                world.getMapper(PhysicsMotorJointComponent.class);
        ComponentMapper<PhysicsWeldJointComponent> welds =
                world.getMapper(PhysicsWeldJointComponent.class);
        ComponentMapper<PhysicsPulleyJointComponent> pulleys =
                world.getMapper(PhysicsPulleyJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> gears =
                world.getMapper(PhysicsGearJointComponent.class);

        int[] data = entityIds.getData();
        for (int i = 0; i < entityIds.size(); i++) {
            int entityId = data[i];
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
                continue;
            }

            String componentName = null;
            if (bodies.has(entityId)) {
                componentName = "PhysicsBodyComponent";
            } else if (shapes.has(entityId)) {
                componentName = "PhysicsShapesComponent";
            } else if (joints.has(entityId)) {
                componentName = "PhysicsJointComponent";
            } else if (distances.has(entityId)) {
                componentName = "PhysicsDistanceJointComponent";
            } else if (revolutes.has(entityId)) {
                componentName = "PhysicsRevoluteJointComponent";
            } else if (prismatics.has(entityId)) {
                componentName = "PhysicsPrismaticJointComponent";
            } else if (wheels.has(entityId)) {
                componentName = "PhysicsWheelJointComponent";
            } else if (frictions.has(entityId)) {
                componentName = "PhysicsFrictionJointComponent";
            } else if (motors.has(entityId)) {
                componentName = "PhysicsMotorJointComponent";
            } else if (welds.has(entityId)) {
                componentName = "PhysicsWeldJointComponent";
            } else if (pulleys.has(entityId)) {
                componentName = "PhysicsPulleyJointComponent";
            } else if (gears.has(entityId)) {
                componentName = "PhysicsGearJointComponent";
            }
            if (componentName != null) {
                throw new IllegalArgumentException(
                        label + " has physicsEnabled=false but contains "
                                + componentName + " on entity " + entityId + ".");
            }
        }
    }

    private final World world;
    private Box2dWorldService box2d;
    private final DirtyTrackerSystem dirty;

    private final ComponentMapper<TransformComponent> mT;
    private final ComponentMapper<PhysicsBodyComponent> mBody;
    private final ComponentMapper<PhysicsShapesComponent> mShapes;
    private final ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled;
    private final ComponentMapper<PhysicsRuntimeBodyComponent> mRuntimeBody;
    private PhysicsShapeIdAllocator shapeIdAllocator;

    private final ComponentMapper<PhysicsJointComponent> mJoint;
    private final ComponentMapper<PhysicsDistanceJointComponent> mDist;
    private final ComponentMapper<PhysicsRevoluteJointComponent> mRev;
    private final ComponentMapper<PhysicsPrismaticJointComponent> mPrism;
    private final ComponentMapper<PhysicsWheelJointComponent> mWheel;
    private final ComponentMapper<PhysicsFrictionJointComponent> mFriction;
    private final ComponentMapper<PhysicsMotorJointComponent> mMotor;
    private final ComponentMapper<PhysicsWeldJointComponent> mWeld;
    private final ComponentMapper<PhysicsPulleyJointComponent> mPulley;
    private final ComponentMapper<PhysicsGearJointComponent> mGear;

    private final Vector2 tmpA = new Vector2();
    private final Vector2 tmpB = new Vector2();

    public PhysicsService(World world, Box2dWorldService box2d) {
        this.world = world;
        this.box2d = box2d;
        this.dirty = world.getSystem(DirtyTrackerSystem.class);

        this.mT = world.getMapper(TransformComponent.class);
        this.mBody = world.getMapper(PhysicsBodyComponent.class);
        this.mShapes = world.getMapper(PhysicsShapesComponent.class);
        this.mCompiled = world.getMapper(PhysicsCompiledFixturesComponent.class);
        this.mRuntimeBody = world.getMapper(PhysicsRuntimeBodyComponent.class);
        this.mJoint = world.getMapper(PhysicsJointComponent.class);

        this.mDist = world.getMapper(PhysicsDistanceJointComponent.class);
        this.mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
        this.mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        this.mWheel = world.getMapper(PhysicsWheelJointComponent.class);
        this.mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
        this.mMotor = world.getMapper(PhysicsMotorJointComponent.class);
        this.mWeld = world.getMapper(PhysicsWeldJointComponent.class);
        this.mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
        this.mGear = world.getMapper(PhysicsGearJointComponent.class);
    }

    public PhysicsService(
            World world, Box2dWorldService box2d, PhysicsShapeIdState physicsShapeIdState) {
        this(world, box2d);
        setPhysicsShapeIdState(physicsShapeIdState);
    }

    public void setPhysicsShapeIdState(PhysicsShapeIdState physicsShapeIdState) {
        shapeIdAllocator = physicsShapeIdState != null
                ? new PhysicsShapeIdAllocator(physicsShapeIdState)
                : null;
    }

    public int allocateNewPhysicsShapeId() {
        if (shapeIdAllocator == null) {
            throw new IllegalStateException(
                    "Scene physics shape ID authority is not configured.");
        }
        return shapeIdAllocator.allocateNewPhysicsShapeId();
    }

    public void setBox2d(Box2dWorldService box2d) {
        this.box2d = box2d;
    }

    /**
     * Box2D available (world created).
     */
    public boolean isAvailable() {
        return box2d != null;
    }

    // ---------------------------------------------------------------------
    // Body / logical shape
    // ---------------------------------------------------------------------

    public boolean hasBody(int eid) {
        return eid >= 0 && mBody.has(eid);
    }

    public boolean hasShapes(int eid) {
        return eid >= 0 && mShapes.has(eid) && mShapes.get(eid).hasShapes();
    }

    public boolean hasPhysics(int eid) {
        return hasBody(eid);
    }

    public void ensurePhysics(int eid) {
        if (eid < 0) return;

        if (!mBody.has(eid)) initDefaultBody(mBody.create(eid));

        PhysicsShapesComponent shapes =
                mShapes.has(eid) ? mShapes.get(eid) : mShapes.create(eid);
        if (!shapes.hasShapes()) {
            shapes.add(createDefaultShape(allocateNewPhysicsShapeId()));
        }

        PreparedPhysicsBodyCandidate prepared = prepareBodyCandidate(shapes.shapes);
        PhysicsCompiledFixturesComponent compiled =
                mCompiled.has(eid) ? mCompiled.get(eid) : mCompiled.create(eid);
        publishPreparedCandidate(shapes, compiled, prepared);
        markPhysicsDirty(eid);
    }

    public void removePhysics(int eid) {
        if (eid < 0) return;

        IntArray affectedJoints =
                collectJointsAffectedByBodyRemoval(eid, new IntArray(false, 8));
        for (int i = 0; i < affectedJoints.size; i++) {
            deleteJoint(affectedJoints.get(i));
        }

        if (mShapes.has(eid)) mShapes.remove(eid);
        if (mCompiled.has(eid)) mCompiled.remove(eid);
        if (mRuntimeBody.has(eid)) mRuntimeBody.remove(eid);
        if (mBody.has(eid)) mBody.remove(eid);

        markPhysicsDirty(eid);
    }

    private void markPhysicsDirty(int eid) {
        if (dirty != null && eid >= 0) dirty.physics(eid, PhysicsDirtyBits.ALL);
    }

    public PhysicsShapesComponent getShapesComponent(int eid) {
        return eid >= 0 ? mShapes.getSafe(eid, null) : null;
    }

    public int shapeCount(int eid) {
        PhysicsShapesComponent shapes = getShapesComponent(eid);
        return shapes != null && shapes.shapes != null ? shapes.shapes.size : 0;
    }

    public int countCircleShapes(int eid) {
        return countShapesByType(eid, PhysicsDirectGeometryData.SHAPE_CIRCLE);
    }

    public int countBoxShapes(int eid) {
        return countShapesByType(eid, PhysicsDirectGeometryData.SHAPE_BOX);
    }

    public int countPolygonShapes(int eid) {
        return countShapesByType(eid, PhysicsDirectGeometryData.SHAPE_POLYGON);
    }

    private int countShapesByType(int eid, int shapeType) {
        PhysicsShapesComponent shapes = getShapesComponent(eid);
        if (shapes == null || shapes.shapes == null) return 0;
        int count = 0;
        for (int i = 0, n = shapes.shapes.size; i < n; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null
                    && shape.directGeometry != null
                    && shape.directGeometry.shapeType == shapeType) count++;
        }
        return count;
    }

    public PhysicsShapeData getShapeById(int eid, int physicsShapeId) {
        PhysicsShapesComponent shapes = getShapesComponent(eid);
        return shapes != null ? shapes.getById(physicsShapeId) : null;
    }

    public PhysicsCompiledFixturesComponent getCompiledFixturesComponent(int eid) {
        return eid >= 0 ? mCompiled.getSafe(eid, null) : null;
    }

    public boolean computeShapeCenterWU(
            int bodyEid, PhysicsShapeData shape, Vector2 outCenterWU) {
        return computeShapeOriginWU(bodyEid, shape, outCenterWU);
    }

    public float computeShapeRadiusWU(PhysicsShapeData shape) {
        if (shape == null || shape.directGeometry == null || !isAvailable()) return 0f;
        return box2d.mToPx(Math.max(0f, shape.directGeometry.radius));
    }

    public boolean computeCompiledFixtureCenterWU(
            int bodyEid, CompiledFixtureData fixture, Vector2 outCenterWU) {
        if (fixture == null) return false;
        return computeLocalOriginWU(bodyEid, fixture.offsetX, fixture.offsetY, outCenterWU);
    }

    public float computeCompiledFixtureRadiusWU(CompiledFixtureData fixture) {
        if (fixture == null || !isAvailable()) return 0f;
        return box2d.mToPx(Math.max(0f, fixture.radius));
    }

    public int computeCompiledFixtureVerticesWU(
            int bodyEid, CompiledFixtureData fixture, float[] outVertsWU) {
        if (fixture == null || outVertsWU == null || !isAvailable()) return 0;
        if (fixture.shapeType == PhysicsDirectGeometryData.SHAPE_BOX) {
            if (outVertsWU.length < 8) return 0;
            if (!transformLocalPointWU(bodyEid, fixture.offsetX, fixture.offsetY,
                    fixture.angleDegrees, -fixture.halfWidth, -fixture.halfHeight,
                    outVertsWU, 0)) return 0;
            transformLocalPointWU(bodyEid, fixture.offsetX, fixture.offsetY,
                    fixture.angleDegrees, fixture.halfWidth, -fixture.halfHeight,
                    outVertsWU, 2);
            transformLocalPointWU(bodyEid, fixture.offsetX, fixture.offsetY,
                    fixture.angleDegrees, fixture.halfWidth, fixture.halfHeight,
                    outVertsWU, 4);
            transformLocalPointWU(bodyEid, fixture.offsetX, fixture.offsetY,
                    fixture.angleDegrees, -fixture.halfWidth, fixture.halfHeight,
                    outVertsWU, 6);
            return 4;
        }
        if (fixture.shapeType == PhysicsDirectGeometryData.SHAPE_POLYGON) {
            int count = Math.max(0, Math.min(
                    fixture.polygonVertexCount, fixture.polygonVertices.length / 2));
            if (count < 3 || outVertsWU.length < count * 2) return 0;
            for (int i = 0; i < count; i++) {
                transformLocalPointWU(bodyEid, fixture.offsetX, fixture.offsetY,
                        fixture.angleDegrees,
                        fixture.polygonVertices[i * 2],
                        fixture.polygonVertices[i * 2 + 1],
                        outVertsWU, i * 2);
            }
            return count;
        }
        return 0;
    }

    public int computeShapeVerticesWU(
            int bodyEid, PhysicsShapeData shape, float[] outVertsWU) {
        if (shape == null || outVertsWU == null || !isAvailable()) return 0;
        PhysicsDirectGeometryData geometry = shape.directGeometry;
        if (geometry == null) return 0;

        if (geometry.shapeType == PhysicsDirectGeometryData.SHAPE_BOX) {
            if (outVertsWU.length < 8) return 0;
            if (!transformShapePointWU(bodyEid, geometry, -geometry.halfWidth,
                    -geometry.halfHeight, outVertsWU, 0)) return 0;
            transformShapePointWU(bodyEid, geometry, geometry.halfWidth,
                    -geometry.halfHeight, outVertsWU, 2);
            transformShapePointWU(bodyEid, geometry, geometry.halfWidth,
                    geometry.halfHeight, outVertsWU, 4);
            transformShapePointWU(bodyEid, geometry, -geometry.halfWidth,
                    geometry.halfHeight, outVertsWU, 6);
            return 4;
        }

        if (geometry.shapeType == PhysicsDirectGeometryData.SHAPE_POLYGON) {
            int count = safePolygonVertexCount(geometry);
            if (count < 3) return 0;
            if (outVertsWU.length < count * 2) return 0;
            for (int i = 0; i < count; i++) {
                float lx = geometry.polygonVertices[i * 2];
                float ly = geometry.polygonVertices[i * 2 + 1];
                transformShapePointWU(bodyEid, geometry, lx, ly, outVertsWU, i * 2);
            }
            return count;
        }

        return 0;
    }

    private int safePolygonVertexCount(PhysicsDirectGeometryData geometry) {
        if (geometry == null || geometry.polygonVertices == null) return 0;
        return Math.max(
                0,
                Math.min(
                        geometry.polygonVertexCount,
                        geometry.polygonVertices.length / 2));
    }

    private boolean computeShapeOriginWU(
            int bodyEid, PhysicsShapeData shape, Vector2 outWU) {
        if (shape == null) return false;
        PhysicsDirectGeometryData geometry = shape.directGeometry;
        return geometry != null
                && computeLocalOriginWU(bodyEid, geometry.offsetX, geometry.offsetY, outWU);
    }

    private boolean computeLocalOriginWU(
            int bodyEid, float localOffsetX, float localOffsetY, Vector2 outWU) {
        if (outWU == null) return false;
        outWU.set(0f, 0f);
        if (!isAvailable() || bodyEid < 0) return false;
        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float bcos = MathUtils.cos(t.rotationRad);
        float bsin = MathUtils.sin(t.rotationRad);
        float bx = localOffsetX * bcos - localOffsetY * bsin;
        float by = localOffsetX * bsin + localOffsetY * bcos;

        outWU.set(
                t.x + box2d.mToPx(bx),
                t.y + box2d.mToPx(by)
        );
        return true;
    }

    private boolean transformShapePointWU(
            int bodyEid,
            PhysicsDirectGeometryData geometry,
            float localX_m,
            float localY_m,
            float[] outVertsWU,
            int outIndex) {
        if (geometry == null) return false;
        return transformLocalPointWU(bodyEid, geometry.offsetX, geometry.offsetY,
                geometry.angleDegrees, localX_m, localY_m, outVertsWU, outIndex);
    }

    private boolean transformLocalPointWU(
            int bodyEid,
            float offsetX,
            float offsetY,
            float angleDegrees,
            float localX_m,
            float localY_m,
            float[] outVertsWU,
            int outIndex) {
        if (!isAvailable() || outVertsWU == null
                || outIndex < 0 || outIndex + 1 >= outVertsWU.length) return false;
        if (bodyEid < 0) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float fixtureAngle = angleDegrees * MathUtils.degreesToRadians;
        float fcos = MathUtils.cos(fixtureAngle);
        float fsin = MathUtils.sin(fixtureAngle);
        float fx = localX_m * fcos - localY_m * fsin + offsetX;
        float fy = localX_m * fsin + localY_m * fcos + offsetY;

        float bcos = MathUtils.cos(t.rotationRad);
        float bsin = MathUtils.sin(t.rotationRad);
        float bx = fx * bcos - fy * bsin;
        float by = fx * bsin + fy * bcos;

        outVertsWU[outIndex] = t.x + box2d.mToPx(bx);
        outVertsWU[outIndex + 1] = t.y + box2d.mToPx(by);
        return true;
    }

    public float pxToM(float value) {
        return box2d.pxToM(value);
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

    private boolean isGearSourceJointType(int jointEid) {
        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return false;
        return base.type == PhysicsJointComponent.TYPE_REVOLUTE
                || base.type == PhysicsJointComponent.TYPE_PRISMATIC;
    }

    private int getGearDynamicBodyEid(int jointEid) {
        PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
        if (base == null) return -1;
        return base.bEid;
    }

    /**
     * Creates a Distance joint between two bodies.
     * Anchors at the local center (0,0).
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

        dist.frequencyHz = Math.max(0f, dist.frequencyHz);
        dist.dampingRatio = Math.max(0f, Math.min(1f, dist.dampingRatio));

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Creates a Revolute joint between two bodies, world pivot (WU/pixels).
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
     * Creates a Prismatic joint between two bodies, world pivot (WU/pixels).
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
     * Creates a Wheel joint between two bodies, world pivot (WU/pixels).
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

    /**
     * Creates a Friction joint between two bodies, world pivot (WU/pixels).
     */
    public int createFrictionJoint(int aEid, int bEid, float pivotWuX, float pivotWuY) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, pivotWuX, pivotWuY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, pivotWuX, pivotWuY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_FRICTION;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        PhysicsFrictionJointComponent friction = mFriction.create(jEid);
        friction.maxForce = Math.max(0f, friction.maxForce);
        friction.maxTorque = Math.max(0f, friction.maxTorque);

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Creates a Motor joint between two bodies.
     * Offsets are initialized from the current relative transform of bodyB in bodyA frame.
     */
    public int createMotorJoint(int aEid, int bEid) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_MOTOR;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;

        // Unused for motor joint, keep neutral.
        base.anchorAx = 0f;
        base.anchorAy = 0f;
        base.anchorBx = 0f;
        base.anchorBy = 0f;

        PhysicsMotorJointComponent motor = mMotor.create(jEid);

        // Default offsets = current relative transform of B in A frame.
        if (isAvailable()) {
            TransformComponent bT = mT.getSafe(bEid, null);
            if (bT != null) {
                Vector2 localBInA = new Vector2();
                if (computeLocalAnchorMetersFromWorldPivot(aEid, bT.x, bT.y, localBInA)) {
                    motor.linearOffsetX = localBInA.x;
                    motor.linearOffsetY = localBInA.y;
                }
            }

            TransformComponent aT = mT.getSafe(aEid, null);
            TransformComponent bT2 = mT.getSafe(bEid, null);
            if (aT != null && bT2 != null) {
                motor.angularOffsetRad = bT2.rotationRad - aT.rotationRad;
            }
        }

        motor.maxForce = Math.max(0f, motor.maxForce);
        motor.maxTorque = Math.max(0f, motor.maxTorque);
        motor.correctionFactor = Math.max(0f, Math.min(1f, motor.correctionFactor));

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Creates a Weld joint between two bodies, world pivot (WU/pixels).
     */
    public int createWeldJoint(int aEid, int bEid, float pivotWuX, float pivotWuY) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, pivotWuX, pivotWuY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, pivotWuX, pivotWuY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_WELD;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        PhysicsWeldJointComponent weld = mWeld.create(jEid);

        TransformComponent aT = mT.getSafe(aEid, null);
        TransformComponent bT = mT.getSafe(bEid, null);
        if (aT != null && bT != null) {
            weld.referenceAngleRad = bT.rotationRad - aT.rotationRad;
        }

        weld.frequencyHz = Math.max(0f, weld.frequencyHz);
        weld.dampingRatio = Math.max(0f, Math.min(1f, weld.dampingRatio));

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Creates a Pulley joint between two bodies.
     * <p>
     * bodyAnchorA/B and groundAnchorA/B are given in world WU/pixels.
     * Stored values are converted to meters for Box2D compatibility.
     */
    public int createPulleyJoint(int aEid, int bEid,
                                 float bodyAnchorAWuX, float bodyAnchorAWuY,
                                 float bodyAnchorBWuX, float bodyAnchorBWuY,
                                 float groundAnchorAWuX, float groundAnchorAWuY,
                                 float groundAnchorBWuX, float groundAnchorBWuY,
                                 float ratio) {
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;
        if (!hasPhysics(aEid) || !hasPhysics(bEid)) return -1;

        Vector2 localA = new Vector2();
        Vector2 localB = new Vector2();
        if (!computeLocalAnchorMetersFromWorldPivot(aEid, bodyAnchorAWuX, bodyAnchorAWuY, localA)) return -1;
        if (!computeLocalAnchorMetersFromWorldPivot(bEid, bodyAnchorBWuX, bodyAnchorBWuY, localB)) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_PULLEY;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;
        base.anchorAx = localA.x;
        base.anchorAy = localA.y;
        base.anchorBx = localB.x;
        base.anchorBy = localB.y;

        PhysicsPulleyJointComponent pulley = mPulley.create(jEid);

        // Ground anchors are world-space in meters.
        pulley.groundAx = box2d.pxToM(groundAnchorAWuX);
        pulley.groundAy = box2d.pxToM(groundAnchorAWuY);
        pulley.groundBx = box2d.pxToM(groundAnchorBWuX);
        pulley.groundBy = box2d.pxToM(groundAnchorBWuY);

        // Reference lengths are segment lengths from ground anchors to body anchors.
        float lenAWu = Vector2.dst(groundAnchorAWuX, groundAnchorAWuY, bodyAnchorAWuX, bodyAnchorAWuY);
        float lenBWu = Vector2.dst(groundAnchorBWuX, groundAnchorBWuY, bodyAnchorBWuX, bodyAnchorBWuY);

        pulley.lengthAM = Math.max(0.001f, box2d.pxToM(lenAWu));
        pulley.lengthBM = Math.max(0.001f, box2d.pxToM(lenBWu));
        pulley.ratio = ratio > 0f ? ratio : 1f;

        markJointDirty(jEid);
        return jEid;
    }

    /**
     * Creates a Gear joint from two existing revolute/prismatic joints.
     */
    public int createGearJoint(int joint1Eid, int joint2Eid, float ratio) {
        if (joint1Eid < 0 || joint2Eid < 0 || joint1Eid == joint2Eid) return -1;

        if (!isGearSourceJointType(joint1Eid) || !isGearSourceJointType(joint2Eid)) return -1;

        int aEid = getGearDynamicBodyEid(joint1Eid);
        int bEid = getGearDynamicBodyEid(joint2Eid);
        if (aEid < 0 || bEid < 0 || aEid == bEid) return -1;

        int jEid = world.create();

        PhysicsJointComponent base = mJoint.create(jEid);
        base.type = PhysicsJointComponent.TYPE_GEAR;
        base.aEid = aEid;
        base.bEid = bEid;
        base.collideConnected = false;

        // Unused for gear joint, keep neutral.
        base.anchorAx = 0f;
        base.anchorAy = 0f;
        base.anchorBx = 0f;
        base.anchorBy = 0f;

        PhysicsGearJointComponent gear = mGear.create(jEid);
        gear.joint1Eid = joint1Eid;
        gear.joint2Eid = joint2Eid;
        gear.ratio = ratio;

        markJointDirty(jEid);
        return jEid;
    }

    public void deleteJoint(int jointEid) {
        if (jointEid < 0) return;

        try {
            world.delete(jointEid);
        } catch (Throwable ignore) {
            if (mDist.has(jointEid)) mDist.remove(jointEid);
            if (mRev.has(jointEid)) mRev.remove(jointEid);
            if (mPrism.has(jointEid)) mPrism.remove(jointEid);
            if (mWheel.has(jointEid)) mWheel.remove(jointEid);
            if (mFriction.has(jointEid)) mFriction.remove(jointEid);
            if (mMotor.has(jointEid)) mMotor.remove(jointEid);
            if (mWeld.has(jointEid)) mWeld.remove(jointEid);
            if (mPulley.has(jointEid)) mPulley.remove(jointEid);
            if (mGear.has(jointEid)) mGear.remove(jointEid);
            if (mJoint.has(jointEid)) mJoint.remove(jointEid);
        }
    }

    /**
     * Collects joints that must be deleted before removing a body.
     * Gear joints are returned before the source joints they depend on.
     */
    public IntArray collectJointsAffectedByBodyRemoval(int bodyEid, IntArray out) {
        IntArray removedEntityIds = new IntArray(1);
        removedEntityIds.add(bodyEid);
        return collectJointsAffectedByEntityRemoval(world, removedEntityIds, out);
    }

    /**
     * Collects joints affected by removing the supplied entities.
     * Dependent gear joints are returned before directly affected joints.
     */
    public static IntArray collectJointsAffectedByEntityRemoval(
            World world, IntArray removedEntityIds, IntArray out) {
        if (world == null) {
            throw new IllegalArgumentException("world is null");
        }
        if (out == null) out = new IntArray(false, 8);
        out.clear();
        if (removedEntityIds == null || removedEntityIds.size == 0) return out;

        IntSet removedIds = new IntSet();
        for (int i = 0; i < removedEntityIds.size; i++) {
            int entityId = removedEntityIds.get(i);
            if (entityId >= 0 && world.getEntityManager().isActive(entityId)) {
                removedIds.add(entityId);
            }
        }
        if (removedIds.size == 0) return out;

        return collectJointsAffectedByEntityRemoval(world, removedIds, out);
    }

    private static IntArray collectJointsAffectedByEntityRemoval(
            World world, IntSet removedIds, IntArray out) {
        ComponentMapper<PhysicsJointComponent> joints =
                world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> gears =
                world.getMapper(PhysicsGearJointComponent.class);
        IntSet directJointIds = new IntSet();
        IntSet gearSourceJointIds = new IntSet();
        IntSet emitted = new IntSet();
        IntArray direct = new IntArray(false, 8);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];
            if (!world.getEntityManager().isActive(jEid)) continue;
            PhysicsJointComponent j = joints.getSafe(jEid, null);
            if (j == null) continue;
            if (removedIds.contains(jEid)
                    || removedIds.contains(j.aEid)
                    || removedIds.contains(j.bEid)) {
                if (directJointIds.add(jEid)) {
                    direct.add(jEid);
                    if (j.type == PhysicsJointComponent.TYPE_REVOLUTE
                            || j.type == PhysicsJointComponent.TYPE_PRISMATIC) {
                        gearSourceJointIds.add(jEid);
                    }
                }
            }
        }

        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];
            if (!world.getEntityManager().isActive(jEid)) continue;
            PhysicsJointComponent joint = joints.getSafe(jEid, null);
            if (joint == null || joint.type != PhysicsJointComponent.TYPE_GEAR) continue;
            PhysicsGearJointComponent gear = gears.getSafe(jEid, null);
            if (gear == null) continue;
            if (directJointIds.contains(jEid)
                    || gearSourceJointIds.contains(gear.joint1Eid)
                    || gearSourceJointIds.contains(gear.joint2Eid)) {
                if (emitted.add(jEid)) out.add(jEid);
            }
        }

        for (int i = 0; i < direct.size; i++) {
            int jointEid = direct.get(i);
            if (emitted.add(jointEid)) out.add(jointEid);
        }
        return out;
    }

    private void markJointDirty(int jointEid) {
        if (dirty != null && jointEid >= 0) dirty.joint(jointEid, JointDirtyBits.ALL);
    }

    // ---------------------------------------------------------------------
    // Anchors helpers (local meters -> world WU(px))
    // ---------------------------------------------------------------------

    /**
     * Local anchor (m) -> world (WU/pixels), including rotation.
     * Returns false if Box2D is unavailable (avoids NPE).
     */
    public boolean computeAnchorWorldWU(int bodyEid, float localAx_m, float localAy_m, Vector2 outWU) {
        if (outWU == null) return false;
        outWU.set(0f, 0f);

        if (!isAvailable()) return false;
        if (bodyEid < 0) return false;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return false;

        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);

        float rx_m = localAx_m * cos - localAy_m * sin;
        float ry_m = localAx_m * sin + localAy_m * cos;

        outWU.set(
                t.x + box2d.mToPx(rx_m),
                t.y + box2d.mToPx(ry_m)
        );
        return true;
    }

    /**
     * World endpoints (WU) of a Distance joint.
     * Returns false if unavailable (box2d null, invalid joint, invalid bodies...).
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
        return computeAnchorWorldWU(bEid, base.anchorBx, base.anchorBy, outB);
    }


    /**
     * World pivot (WU/pixels) of a Revolute joint.
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
     * World pivot (WU/pixels) of a Prismatic joint.
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
     * World pivot (WU/pixels) of a Wheel joint.
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
     * World pivot + axis (WU/pixels) of a Prismatic joint.
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

        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);
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

        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);

        float localAx = dxM * cos + dyM * sin;
        float localAy = -dxM * sin + dyM * cos;

        outLocalMeters.set(localAx, localAy);
        return true;
    }

    // ---------------------------------------------------------------------
    // Defaults (aligned with PhysicsPanel)
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
    }

    public static PhysicsShapeData createDefaultShape(int physicsShapeId) {
        PhysicsShapeData shape = new PhysicsShapeData();
        initDefaultShape(shape, physicsShapeId);
        return shape;
    }

    public static void initDefaultShape(PhysicsShapeData shape, int physicsShapeId) {
        PhysicsShapeIdAllocator.validatePhysicsShapeId(physicsShapeId);
        shape.physicsShapeId = physicsShapeId;
        shape.directGeometry = new PhysicsDirectGeometryData();

        shape.density = 1f;
        shape.friction = 0.2f;
        shape.restitution = 0f;
        shape.sensor = false;

        shape.categoryBits = 0x0001;
        shape.maskBits = (short) 0xFFFF;
        shape.groupIndex = 0;
        shape.enabled = true;
    }

    public static PreparedPhysicsBodyCandidate prepareBodyCandidate(
            Array<PhysicsShapeData> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("Physics shape sources cannot be null.");
        }
        Array<PhysicsShapeData> detached =
                new Array<>(true, sources.size, PhysicsShapeData.class);
        Array<ResolvedPhysicsShape> resolved =
                new Array<>(true, sources.size, ResolvedPhysicsShape.class);
        for (int i = 0; i < sources.size; i++) {
            PhysicsShapeData source = sources.get(i);
            if (source == null) {
                throw new IllegalArgumentException(
                        "Physics shape source at index " + i + " is null.");
            }
            PhysicsShapeData copy = source.copy();
            detached.add(copy);
            resolved.add(SHAPE_RESOLVER.resolve(copy));
        }
        return new PreparedPhysicsBodyCandidate(detached, BODY_COMPILER.compile(resolved));
    }

    public static void rebuildPreparedBodyCaches(World world) {
        if (world == null) {
            throw new IllegalArgumentException("World is required.");
        }
        IntBag bodies = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsBodyComponent.class))
                .getEntities();
        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                world.getMapper(PhysicsShapesComponent.class);
        IntArray entityIds = new IntArray(bodies.size());
        Array<PreparedPhysicsBodyCandidate> preparedBodies =
                new Array<>(true, bodies.size(), PreparedPhysicsBodyCandidate.class);

        int[] bodyIds = bodies.getData();
        for (int i = 0; i < bodies.size(); i++) {
            int entityId = bodyIds[i];
            PhysicsShapesComponent shapes = shapesMapper.getSafe(entityId, null);
            Array<PhysicsShapeData> sources = shapes != null
                    ? shapes.shapes
                    : new Array<>(true, 0, PhysicsShapeData.class);
            entityIds.add(entityId);
            preparedBodies.add(prepareBodyCandidate(sources));
        }

        ComponentMapper<PhysicsCompiledFixturesComponent> compiledMapper =
                world.getMapper(PhysicsCompiledFixturesComponent.class);
        for (int i = 0; i < entityIds.size; i++) {
            int entityId = entityIds.get(i);
            PhysicsShapesComponent shapes = shapesMapper.has(entityId)
                    ? shapesMapper.get(entityId)
                    : shapesMapper.create(entityId);
            PhysicsCompiledFixturesComponent compiled = compiledMapper.has(entityId)
                    ? compiledMapper.get(entityId)
                    : compiledMapper.create(entityId);
            publishPreparedCandidate(shapes, compiled, preparedBodies.get(i));
        }
    }

    public static void publishPreparedCandidate(
            PhysicsShapesComponent targetShapes,
            PhysicsCompiledFixturesComponent targetCompiled,
            PreparedPhysicsBodyCandidate prepared) {
        if (targetShapes == null || targetCompiled == null || prepared == null) {
            throw new IllegalArgumentException(
                    "Physics source target, cache target and prepared candidate are required.");
        }
        Array<PhysicsShapeData> shapes = prepared.takeShapes();
        Array<CompiledFixtureData> fixtures =
                prepared.takeCompiledFixtures().takeFixtures();
        targetShapes.shapes = shapes;
        CACHE_PUBLISHER.publish(targetCompiled, fixtures);
    }
}

