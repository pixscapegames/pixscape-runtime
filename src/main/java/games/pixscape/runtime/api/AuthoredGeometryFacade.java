package games.pixscape.runtime.api;

/**
 * {@code HIGH_LEVEL} read-only view of an entity's authored local 2D geometry.
 *
 * <p>Coordinates do not include transform, scale, rotation, origin, parallax,
 * display offsets or quad deformation. Quad deformation remains a separate
 * capability available through {@link EntityRef#quadDeform()}.</p>
 */
public interface AuthoredGeometryFacade {
    /**
     * Returns whether supported authored local geometry exists.
     */
    boolean exists();

    AuthoredGeometryKind kind();

    /**
     * Authored local width when dimensions exist, otherwise {@code 0}.
     */
    float width();

    /**
     * Authored local height when dimensions exist, otherwise {@code 0}.
     */
    float height();

    /**
     * Number of local vertices: four for rectangles, the authored vertex count
     * for polygons and polylines, and zero when no supported geometry exists.
     */
    int vertexCount();

    float localX(int vertexIndex);

    float localY(int vertexIndex);

    /**
     * Returns true for rectangles and polygons, and false otherwise.
     */
    boolean closed();
}
