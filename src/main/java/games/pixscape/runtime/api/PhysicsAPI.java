package games.pixscape.runtime.api;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * {@code HIGH_LEVEL} Runtime physics queries and coordinate conversion, plus borrowed access to native
 * Box2D objects for operations that do not need a Pixscape-specific abstraction.
 *
 * <p>Native objects returned by this facade are owned by Pixscape and are tied to the active
 * Runtime World/scene. This facade is not thread-safe; use it on the Runtime/render thread.
 * Expert ECS, component, system, and service access remains available separately.</p>
 */
public interface PhysicsAPI {

    /**
     * Returns whether Pixscape physics is currently running for the active scene.
     *
     * <p>This requires an active scene with physics configured, a live native Box2D World,
     * an active {@code Box2dSyncSystem}, and normal physics stepping enabled. It does not
     * merely report the scene metadata's configured flag. Returns {@code false} when any of
     * these runtime conditions is absent.</p>
     */
    boolean isRunning();

    /**
     * Returns the effective number of Pixscape world units/pixels per Box2D meter.
     *
     * <p>The live physics service value is preferred. Otherwise the valid active-scene value,
     * or Runtime's safe default, is returned; invalid scales are never exposed.</p>
     */
    float pixelsPerMeter();

    /**
     * Converts pixels (Pixscape world units) to meters using the current effective
     * {@link #pixelsPerMeter()} value. This scalar conversion does not allocate.
     * Native Box2D methods such as {@link Body#setTransform(float, float, float)}
     * expect positions in meters, not Pixscape world units.
     *
     * <pre>{@code
     * var physics = api.physics();
     * body.setTransform(
     *     physics.pxToM(x),
     *     physics.pxToM(y),
     *     body.getAngle()
     * );
     * }</pre>
     *
     * @param px a coordinate or distance in Pixscape world units (pixels)
     * @return {@code px / pixelsPerMeter()}
     */
    default float pxToM(float px) {
        return px / pixelsPerMeter();
    }

    /**
     * Converts meters to pixels (Pixscape world units) using the current effective
     * {@link #pixelsPerMeter()} value. Native Box2D positions are in meters;
     * this scalar conversion does not allocate or change the native body's units.
     *
     * @param m a coordinate or distance in meters
     * @return {@code m * pixelsPerMeter()}
     * @see #pxToM(float)
     */
    default float mToPx(float m) {
        return m * pixelsPerMeter();
    }

    /**
     * Returns the active scene's effective horizontal physics parallax factor.
     * An unset internal {@code NaN} value, or no active scene, is exposed as {@code 1f}.
     */
    float parallaxX();

    /**
     * Returns the active scene's effective vertical physics parallax factor.
     * An unset internal {@code NaN} value, or no active scene, is exposed as {@code 1f}.
     */
    float parallaxY();

    /**
     * Removes Pixscape's physics-parallax camera offset from an already unprojected rendered
     * world position.
     *
     * <p>The result remains in Pixscape world units; this method does not convert to Box2D
     * meters. For each axis it computes
     * {@code logical = rendered - (1 - parallax) * cameraPosition}. The caller supplies
     * {@code out}, which may be the same object as {@code renderedWorldPosition}; no temporary
     * vector is allocated.</p>
     *
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    Vector2 removeParallax(
            Vector2 renderedWorldPosition,
            OrthographicCamera camera,
            Vector2 out);

    /**
     * Returns the active native Box2D world, or {@code null} when physics is unavailable.
     *
     * <p>The returned world is borrowed and Runtime-owned. Callers must not dispose it.
     * Pixscape controls normal stepping. Its identity and lifetime follow the active Runtime
     * World/scene, so a cached reference becomes invalid across scene or World rebuilds and
     * must be reacquired.</p>
     */
    com.badlogic.gdx.physics.box2d.World box2dWorld();

    /**
     * Returns the live native body realized for {@code entity}, or {@code null} when the
     * entity is null, missing/inactive, or has no live runtime body.
     * A successful synchronous Game Object spawn has already realized its materializable bodies;
     * non-physical members and members with no materializable fixtures legitimately return null.
     * This getter never creates physics or searches another member of the instance.
     *
     * <p>The body is borrowed and Runtime-owned; callers must not destroy it directly. Its
     * reference can change when Pixscape recreates physics or the body, and must not be cached
     * across scene/World rebuilds or body recreation.</p>
     */
    Body body(EntityRef entity);
}
