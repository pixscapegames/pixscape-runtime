package games.pixscape.runtime.api;

/**
 * High-level access to authored local-space sprite corner deformation.
 *
 * <p>Offsets follow BL, BR, TR and TL corner order and remain local to the
 * sprite. Mutations automatically invalidate derived Runtime geometry.</p>
 */
public interface QuadDeformFacade {
    /**
     * Returns whether this entity currently has the complete sprite capability
     * required for authored quad deformation.
     *
     * <p>This is a capability query, not a test for whether a deformation
     * component currently exists.</p>
     */
    boolean exists();

    /**
     * Returns true when at least one authored corner offset is non-zero.
     */
    boolean isDeformed();

    float bottomLeftX();

    float bottomLeftY();

    float bottomRightX();

    float bottomRightY();

    float topRightX();

    float topRightY();

    float topLeftX();

    float topLeftY();

    QuadDeformFacade setBottomLeft(float x, float y);

    QuadDeformFacade setBottomRight(float x, float y);

    QuadDeformFacade setTopRight(float x, float y);

    QuadDeformFacade setTopLeft(float x, float y);

    QuadDeformFacade set(
            float blX, float blY,
            float brX, float brY,
            float trX, float trY,
            float tlX, float tlY);

    /**
     * Removes authored quad deformation and restores normal rectangular sprite
     * geometry.
     */
    QuadDeformFacade reset();
}
