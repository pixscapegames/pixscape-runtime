package games.pixscape.runtime.system.optional;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.MouseJoint;
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef;
import games.pixscape.runtime.api.PhysicsAPI;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;

public final class PhysicsMouseDragSystem extends BaseSystem {

    private final OrthographicCamera camera;
    private final PhysicsAPI physics;
    private LayerStateSOA layerState;
    private Box2dSyncSystem box2dSync;
    private Box2dWorldService box2d;
    private World lastWorld;

    private Body groundBody;
    private MouseJoint mouseJoint;

    private float maxForce = 2000f;
    private float frequencyHz = 5f;
    private float dampingRatio = 0.7f;
    private float grabRadiusMeters = 0.25f;
    private boolean allowStatic = false;

    private boolean wasPressed;

    private final Vector3 tmpScreen = new Vector3();
    private final Vector2 tmpTarget = new Vector2();
    private final Vector2 queryPoint = new Vector2();

    private Body pickedBody;
    private float pickedDist2;
    private boolean pickedDynamic;

    private final QueryCallback pickCallback = new QueryCallback() {
        @Override
        public boolean reportFixture(Fixture fixture) {
            if (fixture == null || fixture.isSensor()) return true;
            Body body = fixture.getBody();
            if (body == null) return true;

            boolean isDynamic = body.getType() == BodyDef.BodyType.DynamicBody;
            if (!allowStatic && !isDynamic) return true;
            if (!fixture.testPoint(queryPoint)) return true;

            if (pickedBody != null) {
                if (pickedDynamic && !isDynamic) return true;
                if (!pickedDynamic && isDynamic) {
                    pickedBody = body;
                    pickedDynamic = true;
                    pickedDist2 = distanceSquared(body.getPosition(), queryPoint);
                    return true;
                }
            }

            float dist2 = distanceSquared(body.getPosition(), queryPoint);
            if (pickedBody == null || dist2 < pickedDist2) {
                pickedBody = body;
                pickedDist2 = dist2;
                pickedDynamic = isDynamic;
            }
            return true;
        }
    };

    public PhysicsMouseDragSystem(OrthographicCamera camera) {
        this(camera, null);
    }

    /**
     * Creates a parallax-aware mouse drag system backed by the public Runtime physics API.
     *
     * @param camera world camera used to unproject pointer coordinates
     * @param physics Runtime physics facade used for lifecycle, parallax and scale conversion
     */
    public PhysicsMouseDragSystem(OrthographicCamera camera, PhysicsAPI physics) {
        this.camera = camera;
        this.physics = physics;
    }

    /**
     * Legacy late binding for parallax-aware runtime/Preview picking.
     * <p>
     * Prefer {@link #PhysicsMouseDragSystem(OrthographicCamera, PhysicsAPI)}. When this
     * legacy binding is unset, physics parallax defaults to {@code 1f}.
     */
    @Deprecated
    public void setLayerState(LayerStateSOA layerState) {
        this.layerState = layerState;
    }

    public void setMaxForce(float maxForce) {
        this.maxForce = maxForce;
    }

    public void setFrequencyHz(float frequencyHz) {
        this.frequencyHz = frequencyHz;
    }

    public void setDampingRatio(float dampingRatio) {
        this.dampingRatio = dampingRatio;
    }

    public void setGrabRadiusMeters(float grabRadiusMeters) {
        this.grabRadiusMeters = grabRadiusMeters;
    }

    public void setAllowStatic(boolean allowStatic) {
        this.allowStatic = allowStatic;
    }

    @Override
    protected void initialize() {
        box2dSync = world.getSystem(Box2dSyncSystem.class);
        wasPressed = false;
        resetJointState();
    }

    @Override
    protected void processSystem() {
        if (camera == null) return;

        if (physics != null) {
            World currentWorld = physics.box2dWorld();
            if (!physics.isRunning() || currentWorld == null) {
                clearStateForMissingWorld(currentWorld);
                wasPressed = false;
                return;
            }
            bindWorld(currentWorld);
            processInput();
            return;
        }

        if (box2dSync == null) return;

        Box2dWorldService current = box2dSync.getBox2d();
        if (current == null || current.world == null || current.isDisposed() || !box2dSync.isEnabled()) {
            clearStateForMissingWorld(current);
            wasPressed = false;
            return;
        }

        box2d = current;
        bindWorld(current.world);
        processInput();
    }

    private void bindWorld(World currentWorld) {
        if (currentWorld == lastWorld) return;
        resetJointState();
        lastWorld = currentWorld;
    }

    private void processInput() {
        if (Gdx.input.isTouched(1)) {
            destroyJoint();
            wasPressed = false;
            return;
        }

        boolean pressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (pressed && !wasPressed) {
            tryBeginDrag();
        } else if (!pressed && wasPressed) {
            destroyJoint();
        }

        if (pressed && mouseJoint != null) {
            updateTargetFromCursor();
        }

        wasPressed = pressed;
    }

    private void tryBeginDrag() {
        if (lastWorld == null) return;
        if (!updateTargetFromCursor()) return;

        Body hit = pickBodyAtCursor();
        if (hit == null) return;

        ensureGroundBody();
        if (groundBody == null) return;

        MouseJointDef def = new MouseJointDef();
        def.bodyA = groundBody;
        def.bodyB = hit;
        def.target.set(tmpTarget);
        def.maxForce = maxForce * hit.getMass();
        def.frequencyHz = frequencyHz;
        def.dampingRatio = dampingRatio;
        mouseJoint = (MouseJoint) lastWorld.createJoint(def);
    }

    private boolean updateTargetFromCursor() {
        if (lastWorld == null) return false;
        tmpScreen.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(tmpScreen);
        tmpTarget.set(tmpScreen.x, tmpScreen.y);
        if (physics != null) {
            toPhysicsMeters(physics, camera, tmpTarget, tmpTarget);
        } else {
            float logicalX = toLogicalPhysicsWorld(
                    tmpTarget.x, camera.position.x, physicsParallaxX());
            float logicalY = toLogicalPhysicsWorld(
                    tmpTarget.y, camera.position.y, physicsParallaxY());
            tmpTarget.set(box2d.pxToM(logicalX), box2d.pxToM(logicalY));
        }
        if (mouseJoint != null) {
            mouseJoint.setTarget(tmpTarget);
        }
        return true;
    }

    private Body pickBodyAtCursor() {
        pickedBody = null;
        pickedDist2 = Float.POSITIVE_INFINITY;
        pickedDynamic = false;

        queryPoint.set(tmpTarget);
        float r = grabRadiusMeters;
        lastWorld.QueryAABB(pickCallback, queryPoint.x - r, queryPoint.y - r, queryPoint.x + r, queryPoint.y + r);
        return pickedBody;
    }

    private float physicsParallaxX() {
        if (layerState == null) return 1f;
        float factor = layerState.physicsParallaxX;
        return Float.isNaN(factor) ? 1f : factor;
    }

    private float physicsParallaxY() {
        if (layerState == null) return 1f;
        float factor = layerState.physicsParallaxY;
        return Float.isNaN(factor) ? 1f : factor;
    }

    static float toLogicalPhysicsWorld(float renderedWorld, float cameraPosition, float physicsParallax) {
        float factor = Float.isNaN(physicsParallax) ? 1f : physicsParallax;
        return renderedWorld - (1f - factor) * cameraPosition;
    }

    static Vector2 toPhysicsMeters(
            PhysicsAPI physics,
            OrthographicCamera camera,
            Vector2 renderedWorldPosition,
            Vector2 out) {
        physics.removeParallax(renderedWorldPosition, camera, out);
        return out.scl(1f / physics.pixelsPerMeter());
    }

    private void ensureGroundBody() {
        if (lastWorld == null) return;
        if (groundBody != null) return;
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        groundBody = lastWorld.createBody(def);
    }

    private void destroyJoint() {
        if (mouseJoint == null) return;
        if (lastWorld != null) {
            lastWorld.destroyJoint(mouseJoint);
        }
        mouseJoint = null;
    }

    private void resetJointState() {
        destroyJoint();
        groundBody = null;
        pickedBody = null;
    }

    private void clearStateForMissingWorld(Box2dWorldService current) {
        clearStateForMissingWorld(current != null ? current.world : null);
        box2d = current;
    }

    private void clearStateForMissingWorld(World currentWorld) {
        if (mouseJoint != null && lastWorld != null && lastWorld == currentWorld) {
            lastWorld.destroyJoint(mouseJoint);
        }
        mouseJoint = null;
        groundBody = null;
        pickedBody = null;
        lastWorld = currentWorld;
    }

    private static float distanceSquared(Vector2 a, Vector2 b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
