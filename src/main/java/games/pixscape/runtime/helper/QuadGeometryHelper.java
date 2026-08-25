package games.pixscape.runtime.helper;

import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;

/**
 * Runtime implementation detail used to derive final world-space sprite
 * geometry from authored state in BL, BR, TR and TL order.
 *
 * <p>This is an {@code INTERNAL} API. Java-public visibility supports Runtime
 * and first-party tooling use; this helper is not part of the normal
 * high-level gameplay API.</p>
 */
public final class QuadGeometryHelper {
    private QuadGeometryHelper() {
    }

    /**
     * Writes BL, BR, TR and TL world-space corner pairs into {@code out8}.
     */
    public static void toWorldCorners(OrientedBoundsComponent bounds,
                                      TransformComponent transform,
                                      QuadDeformComponent deform,
                                      float[] out8) {
        OrientedBoundsHelper.toCorners(bounds, out8);

        if (transform == null || deform == null || isZero(deform)) {
            return;
        }

        addLocalOffset(out8, 0, deform.blX, deform.blY, bounds, transform);
        addLocalOffset(out8, 2, deform.brX, deform.brY, bounds, transform);
        addLocalOffset(out8, 4, deform.trX, deform.trY, bounds, transform);
        addLocalOffset(out8, 6, deform.tlX, deform.tlY, bounds, transform);
    }

    private static boolean isZero(QuadDeformComponent deform) {
        return deform.blX == 0f && deform.blY == 0f
                && deform.brX == 0f && deform.brY == 0f
                && deform.trX == 0f && deform.trY == 0f
                && deform.tlX == 0f && deform.tlY == 0f;
    }

    private static void addLocalOffset(float[] out8,
                                       int index,
                                       float localX,
                                       float localY,
                                       OrientedBoundsComponent bounds,
                                       TransformComponent transform) {
        float scaledX = localX * transform.scaleX;
        float scaledY = localY * transform.scaleY;

        out8[index] += bounds.ux * scaledX + bounds.vx * scaledY;
        out8[index + 1] += bounds.uy * scaledX + bounds.vy * scaledY;
    }
}
