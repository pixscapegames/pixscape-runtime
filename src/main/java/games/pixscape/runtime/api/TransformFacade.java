package games.pixscape.runtime.api;

import com.badlogic.gdx.math.Vector2;

/**
 * High-level authored transform access for one entity.
 * For standalone entities and top-level Game Objects this is the normal authored world pose.
 * For a Game Object member it is the authored local transform relative to its parent; this
 * facade does not expose the resolved world transform.
 * Setters establish a standalone authored transform when it is absent.
 * Non-finite position, rotation, scale, origin, and delta values are rejected before mutation.
 * Negative finite scale remains supported.
 * Generic mutation throws {@link IllegalStateException} while Runtime Physics owns this entity's
 * Body or a descendant Body.
 */
public interface TransformFacade {
    float x();

    float y();

    /**
     * Allocates a new independent vector containing {@link #x()} and {@link #y()}.
     * Coordinates are local to the parent for Game Object members and authored world
     * coordinates otherwise; no world-transform conversion is performed.
     * Modifying the result never changes the entity or another call's result.
     * A stale reference or an absent transform returns a new zero vector.
     * For frequent calls, prefer {@link #xy(Vector2)} to reuse a vector.
     */
    Vector2 xy();

    /**
     * Copies {@link #x()} and {@link #y()} into {@code out} and returns that same vector,
     * without allocating temporary objects. Suitable for frequent calls with a reused vector.
     * Coordinates are local to the parent for Game Object members and authored world
     * coordinates otherwise; no world-transform conversion is performed.
     * The copied result is independent of the entity: modifying it never changes the entity.
     * The vector is not retained, and this read does not invalidate the transform.
     * A stale reference or an absent transform sets both coordinates to zero.
     *
     * <pre>{@code
     * private final Vector2 position = new Vector2();
     *
     * // On each update:
     * entity.transform().xy(position);
     * }</pre>
     *
     * @param out the caller-owned vector to fill
     * @return exactly {@code out}
     * @throws IllegalArgumentException if {@code out} is null
     */
    Vector2 xy(Vector2 out);

    float rotationRad();

    float scaleX();

    float scaleY();

    TransformFacade setPosition(float x, float y);

    TransformFacade setX(float x);

    TransformFacade setY(float y);

    TransformFacade moveBy(float dx, float dy);

    TransformFacade setRotationRad(float radians);

    TransformFacade rotateByRad(float radians);

    TransformFacade setScale(float uniform);

    TransformFacade setScale(float sx, float sy);

    TransformFacade setScaleX(float sx);

    TransformFacade setScaleY(float sy);

    TransformFacade setOrigin(float ox, float oy);
}
