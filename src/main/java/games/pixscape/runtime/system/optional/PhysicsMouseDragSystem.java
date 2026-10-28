package games.pixscape.runtime.system.optional;

import com.artemis.BaseSystem;
import com.artemis.annotations.SkipWire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
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
    @SkipWire private Box2dSyncSystem box2dSync;
    private Box2dWorldService box2d;
    private World lastWorld;

    private Body groundBody;
    private MouseJoint mouseJoint;

    private float maxForce = 2000f;
    private float frequencyHz = 5f;
    private float dampingRatio = 0.7f;
    private float grabRadiusMeters = 0.25f;
    private boolean allowStatic = false;
    private boolean inputEnabled = true;

    private int gesturePointer = -1;
    private int gestureButton = -1;
    private int gestureScreenX;
    private int gestureScreenY;
    private boolean beginRequested;
    private boolean releasePending;

    private final InputProcessor inputProcessor = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (!inputEnabled || button != Input.Buttons.LEFT || pointer != 0) {
                if (gesturePointer >= 0 && pointer != gesturePointer) cancelInput();
                return false;
            }
            if (gesturePointer >= 0) return false;

            gesturePointer = pointer;
            gestureButton = button;
            gestureScreenX = screenX;
            gestureScreenY = screenY;
            beginRequested = true;
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (pointer != gesturePointer) return false;
            gestureScreenX = screenX;
            gestureScreenY = screenY;
            return mouseJoint != null;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer != gesturePointer || button != gestureButton) return false;
            boolean handled = mouseJoint != null;
            gestureScreenX = screenX;
            gestureScreenY = screenY;
            finishGesture();
            return handled;
        }

        @Override
        public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
            if (pointer != gesturePointer || button != gestureButton) return false;
            boolean handled = mouseJoint != null;
            finishGesture();
            return handled;
        }
    };

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

    /** Returns the stable World-drag input endpoint to place after higher-priority UI processors. */
    public InputProcessor inputProcessor() {
        return inputProcessor;
    }

    /** Enables routed input; disabling it releases any active World drag. */
    public void setInputEnabled(boolean inputEnabled) {
        this.inputEnabled = inputEnabled;
        if (!inputEnabled) cancelInput();
    }

    /** Cancels the accepted World gesture and releases its MouseJoint when Box2D is unlocked. */
    public void cancelInput() {
        clearGesture();
        destroyJoint();
    }

    @Override
    protected void initialize() {
        box2dSync = world.getSystem(Box2dSyncSystem.class);
        clearGesture();
        resetJointState();
    }

    @Override
    protected void processSystem() {
        if (camera == null) return;

        if (physics != null) {
            World currentWorld = physics.box2dWorld();
            if (!physics.isRunning() || currentWorld == null) {
                clearStateForMissingWorld(currentWorld);
                clearGesture();
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
            clearGesture();
            return;
        }

        box2d = current;
        bindWorld(current.world);
        processInput();
    }

    private void bindWorld(World currentWorld) {
        if (currentWorld == lastWorld) return;
        if (lastWorld != null) {
            resetJointState();
            clearGesture();
        }
        lastWorld = currentWorld;
    }

    private void processInput() {
        if (releasePending) destroyJoint();
        if (!inputEnabled) {
            cancelInput();
            return;
        }
        if (gesturePointer < 0) return;

        // Polling is termination-only recovery for a routed gesture whose release was missed.
        if (!beginRequested && Gdx.input != null
                && (!Gdx.input.isButtonPressed(gestureButton) || Gdx.input.isTouched(1))) {
            finishGesture();
            return;
        }

        if (beginRequested) {
            beginRequested = false;
            tryBeginDrag(gestureScreenX, gestureScreenY);
        }

        if (mouseJoint != null) {
            updateTargetFromScreen(gestureScreenX, gestureScreenY);
        }
    }

    private void tryBeginDrag(int screenX, int screenY) {
        if (lastWorld == null) return;
        if (!updateTargetFromScreen(screenX, screenY)) return;

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

    private boolean updateTargetFromScreen(int screenX, int screenY) {
        if (lastWorld == null) return false;
        tmpScreen.set(screenX, screenY, 0f);
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
        if (lastWorld != null && lastWorld.isLocked()) {
            releasePending = true;
            return;
        }
        if (lastWorld != null) {
            lastWorld.destroyJoint(mouseJoint);
        }
        mouseJoint = null;
        releasePending = false;
    }

    private void resetJointState() {
        destroyJoint();
        groundBody = null;
        pickedBody = null;
    }

    private void finishGesture() {
        clearGesture();
        destroyJoint();
    }

    private void clearGesture() {
        gesturePointer = -1;
        gestureButton = -1;
        beginRequested = false;
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
        releasePending = false;
        groundBody = null;
        pickedBody = null;
        lastWorld = currentWorld;
    }

    @Override
    protected void dispose() {
        cancelInput();
        resetJointState();
        lastWorld = null;
        box2d = null;
        box2dSync = null;
    }

    private static float distanceSquared(Vector2 a, Vector2 b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
