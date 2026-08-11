package games.pixscape.runtime.service;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;

/**
 * {@code SUPPORTED_EXPERT} owner/coordinator for one derived native Box2D {@link World}.
 *
 * <p>When obtained from {@code PixscapeEngine}, the service and its public {@link #world} are
 * borrowed and engine-owned: callers may perform supported native queries/operations but must not
 * dispose the world or step it independently of Runtime. Reacquire the service after scene/World
 * rebuilds. The service is not thread-safe and is normally used on the LibGDX render thread.</p>
 *
 * <p>Pixels-per-meter converts Pixscape world units to Box2D meters. Step metrics and native
 * counts describe the most recent {@link #step(float)} call and are mutable diagnostics, not
 * authored scene state.</p>
 */
public final class Box2dWorldService {
    public World world;
    private boolean disposed;

    /** Pixscape world units/pixels per Box2D meter. */
    public float ppm;
    private boolean doSleep = true;
    private final Vector2 gravity = new Vector2();
    private final Array<Body> tmpBodies = new Array<>();

    private static final float FIXED_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f; // clamp anti "spiral of death"
    private static final int MAX_SUBSTEPS = 5;

    // STATS
    public int bodyCount = 0;
    public int jointCount = 0;
    public int contactCount = 0;

    private float accumulator = 0f;

    // metrics (optional but very useful)
    public long lastStepTimeNs = 0L;
    public int lastSubsteps = 0;

    private int velIters = 6;
    private int posIters = 2;

    public Box2dWorldService(float ppm, Vector2 gravity, boolean doSleep) {
        this.ppm = ppm;
        this.gravity.set(gravity);
        Box2D.init();
        this.doSleep = doSleep;

        world = new World(gravity, doSleep);
    }

    public Box2dWorldService(float ppm, Vector2 gravity) {
        this(ppm, gravity, true);
    }

    public float pxToM(float px) {
        return px / ppm;
    }

    public float mToPx(float m) {
        return m * ppm;
    }

    public void setPpm(float ppm) {
        if (ppm <= 0f) return;
        this.ppm = ppm;
    }

    public void setDoSleep(boolean sleep) {
        if (this.doSleep == sleep) return;
        this.doSleep = sleep;

        tmpBodies.clear();
        world.getBodies(tmpBodies);
        for (int i = 0; i < tmpBodies.size; i++) {
            Body b = tmpBodies.get(i);
            if (b == null) continue;
            b.setSleepingAllowed(sleep);
            if (!sleep) b.setAwake(true);
        }
    }

    public boolean isDoSleep() {
        return doSleep;
    }

    public void setGravity(float gx, float gy) {
        gravity.set(gx, gy);
        world.setGravity(gravity);
    }

    public void step(float dt) {
        // clamp dt to avoid huge dt (alt-tab etc.)
        float frameTime = Math.min(dt, MAX_FRAME_TIME);
        accumulator += frameTime;

        int substeps = 0;
        long t0 = System.nanoTime();

        while (accumulator >= FIXED_STEP && substeps < MAX_SUBSTEPS) {
            world.step(FIXED_STEP, velIters, posIters);
            accumulator -= FIXED_STEP;
            substeps++;
        }

        // STATS
        bodyCount = world.getBodyCount();
        jointCount = world.getJointCount();
        contactCount = world.getContactCount();

        // If max substeps is reached, we "drop" the rest to avoid falling behind
        if (substeps == MAX_SUBSTEPS) {
            accumulator = 0f;
        }

        lastStepTimeNs = System.nanoTime() - t0;
        lastSubsteps = substeps;


    }

    public void dispose() {
        if (disposed) return;

        if (world != null) {
            world.dispose();
            world = null;
            disposed = true;
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

}
