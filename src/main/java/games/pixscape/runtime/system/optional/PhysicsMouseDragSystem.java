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
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;

public final class PhysicsMouseDragSystem extends BaseSystem {

    private final OrthographicCamera camera;
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
        this.camera = camera;
    }

    /**
     * Optional late binding for parallax-aware runtime/Preview picking.
     * <p>
     * This is not constructor-required because external developers may create the
     * drag system before the engine exists. When unset, physics parallax defaults
     * to {@code 1f}, preserving old behavior and avoiding NPEs. Runtime/Preview
     * hosts should inject the real engine {@link LayerStateSOA} after engine
     * creation when parallax-aware mouse picking is desired.
     */
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
        if (camera == null || box2dSync == null) return;

        Box2dWorldService current = box2dSync.getBox2d();
        if (current == null || current.world == null || current.isDisposed() || !box2dSync.isEnabled()) {
            clearStateForMissingWorld(current);
            wasPressed = false;
            return;
        }

        if (current != box2d || current.world != lastWorld) {
            resetJointState();
            box2d = current;
            lastWorld = current.world;
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
        if (box2d == null || box2d.world == null) return;
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
        mouseJoint = (MouseJoint) box2d.world.createJoint(def);
    }

    private boolean updateTargetFromCursor() {
        if (box2d == null || box2d.world == null) return false;
        tmpScreen.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(tmpScreen);
        float parallaxX = physicsParallaxX();
        float parallaxY = physicsParallaxY();
        float offsetX = (1f - parallaxX) * camera.position.x;
        float offsetY = (1f - parallaxY) * camera.position.y;
        float logicalX = tmpScreen.x - offsetX;
        float logicalY = tmpScreen.y - offsetY;
        float xM = box2d.pxToM(logicalX);
        float yM = box2d.pxToM(logicalY);
        tmpTarget.set(xM, yM);
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
        box2d.world.QueryAABB(pickCallback, queryPoint.x - r, queryPoint.y - r, queryPoint.x + r, queryPoint.y + r);
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

    private void ensureGroundBody() {
        if (box2d == null || box2d.world == null) return;
        if (groundBody != null) return;
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        groundBody = box2d.world.createBody(def);
    }

    private void destroyJoint() {
        if (mouseJoint == null) return;
        if (box2d != null && box2d.world != null && !box2d.isDisposed()) {
            box2d.world.destroyJoint(mouseJoint);
        }
        mouseJoint = null;
    }

    private void resetJointState() {
        destroyJoint();
        groundBody = null;
        pickedBody = null;
    }

    private void clearStateForMissingWorld(Box2dWorldService current) {
        if (box2d != null && box2d.world != null && mouseJoint != null && !box2d.isDisposed()) {
            box2d.world.destroyJoint(mouseJoint);
        }
        mouseJoint = null;
        groundBody = null;
        pickedBody = null;
        box2d = current;
        lastWorld = (current != null) ? current.world : null;
    }

    private static float distanceSquared(Vector2 a, Vector2 b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
