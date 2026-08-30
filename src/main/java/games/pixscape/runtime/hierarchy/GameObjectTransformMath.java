package games.pixscape.runtime.hierarchy;

import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.TransformComponent;

/**
 * Allocation-free transform math for the authored Game Object hierarchy contract.
 *
 * <p>A Game Object parent frame uses its authored origin as the hierarchy pivot:
 * {@code T(x + origin) * R * S * T(-origin)}. Ordinary drawable child origins remain geometry-only
 * state and are not inherited as hierarchy ownership. Rotation uses the Runtime convention of
 * radians. Positive uniform scale is required only for parent frames; ordinary child transforms
 * retain their existing signed/non-uniform scale representation.</p>
 */
public final class GameObjectTransformMath {
    public static final float DECOMPOSITION_EPSILON = 0.0001f;

    private GameObjectTransformMath() {
    }

    public static boolean isPositiveUniformParentScale(TransformComponent transform) {
        return transform != null
                && finite(transform.scaleX)
                && finite(transform.scaleY)
                && transform.scaleX > 0f
                && Float.compare(transform.scaleX, transform.scaleY) == 0;
    }

    public static void requirePositiveUniformParentScale(TransformComponent transform) {
        if (!isPositiveUniformParentScale(transform)) {
            throw new IllegalArgumentException(
                    "Game Object parent scale must be finite, positive, and uniform.");
        }
    }

    public static boolean isUnitParentScale(TransformComponent transform) {
        return transform != null
                && Float.compare(transform.scaleX, 1f) == 0
                && Float.compare(transform.scaleY, 1f) == 0;
    }

    public static void requireUnitParentScale(TransformComponent transform, String dependentDomain) {
        if (!isUnitParentScale(transform)) {
            String domain = dependentDomain != null && !dependentDomain.isEmpty()
                    ? dependentDomain : "Dependent";
            throw new IllegalArgumentException(
                    domain + " Game Object hierarchy requires parent scale (1, 1).");
        }
    }

    /** Writes the pivot-aware frame of a Game Object root. */
    public static Affine2 toFrame(TransformComponent transform, Affine2 out) {
        if (transform == null || out == null) {
            throw new IllegalArgumentException("Transform and output frame are required.");
        }
        float cos = MathUtils.cos(transform.rotationRad);
        float sin = MathUtils.sin(transform.rotationRad);
        out.m00 = cos * transform.scaleX;
        out.m01 = -sin * transform.scaleY;
        out.m10 = sin * transform.scaleX;
        out.m11 = cos * transform.scaleY;
        out.m02 = transform.x + transform.originX
                - out.m00 * transform.originX - out.m01 * transform.originY;
        out.m12 = transform.y + transform.originY
                - out.m10 * transform.originX - out.m11 * transform.originY;
        return out;
    }

    /** Writes either a Game Object parent frame or an ordinary origin-free child frame. */
    public static Affine2 toMemberFrame(
            TransformComponent transform, boolean gameObject, Affine2 out) {
        if (gameObject) return toFrame(transform, out);
        if (transform == null || out == null) {
            throw new IllegalArgumentException("Transform and output frame are required.");
        }
        float cos = MathUtils.cos(transform.rotationRad);
        float sin = MathUtils.sin(transform.rotationRad);
        out.m00 = cos * transform.scaleX;
        out.m01 = -sin * transform.scaleY;
        out.m02 = transform.x;
        out.m10 = sin * transform.scaleX;
        out.m11 = cos * transform.scaleY;
        out.m12 = transform.y;
        return out;
    }

    /** Multiplies two hierarchy frames and supports aliasing either input with {@code out}. */
    public static Affine2 multiply(Affine2 parent, Affine2 local, Affine2 out) {
        if (parent == null || local == null || out == null) {
            throw new IllegalArgumentException("Parent, local, and output frames are required.");
        }
        float p00 = parent.m00, p01 = parent.m01, p02 = parent.m02;
        float p10 = parent.m10, p11 = parent.m11, p12 = parent.m12;
        float l00 = local.m00, l01 = local.m01, l02 = local.m02;
        float l10 = local.m10, l11 = local.m11, l12 = local.m12;
        out.m00 = p00 * l00 + p01 * l10;
        out.m01 = p00 * l01 + p01 * l11;
        out.m02 = p00 * l02 + p01 * l12 + p02;
        out.m10 = p10 * l00 + p11 * l10;
        out.m11 = p10 * l01 + p11 * l11;
        out.m12 = p10 * l02 + p11 * l12 + p12;
        return out;
    }

    /** Computes {@code inverse(parentWorld) * childWorld} without temporary allocation. */
    public static Affine2 worldToLocal(Affine2 parentWorld, Affine2 childWorld, Affine2 out) {
        if (parentWorld == null || childWorld == null || out == null) {
            throw new IllegalArgumentException("Parent, child, and output frames are required.");
        }
        float p00 = parentWorld.m00, p01 = parentWorld.m01, p02 = parentWorld.m02;
        float p10 = parentWorld.m10, p11 = parentWorld.m11, p12 = parentWorld.m12;
        float determinant = p00 * p11 - p01 * p10;
        if (!finite(determinant) || Math.abs(determinant) <= DECOMPOSITION_EPSILON) {
            throw new IllegalArgumentException("Parent hierarchy transform is not invertible.");
        }
        float inverseDeterminant = 1f / determinant;
        float i00 = p11 * inverseDeterminant;
        float i01 = -p01 * inverseDeterminant;
        float i10 = -p10 * inverseDeterminant;
        float i11 = p00 * inverseDeterminant;
        float i02 = -(i00 * p02 + i01 * p12);
        float i12 = -(i10 * p02 + i11 * p12);

        float w00 = childWorld.m00, w01 = childWorld.m01, w02 = childWorld.m02;
        float w10 = childWorld.m10, w11 = childWorld.m11, w12 = childWorld.m12;
        out.m00 = i00 * w00 + i01 * w10;
        out.m01 = i00 * w01 + i01 * w11;
        out.m02 = i00 * w02 + i01 * w12 + i02;
        out.m10 = i10 * w00 + i11 * w10;
        out.m11 = i10 * w01 + i11 * w11;
        out.m12 = i10 * w02 + i11 * w12 + i12;
        return out;
    }

    /** Composes a supported parent world transform and child local transform. */
    public static TransformComponent localToWorld(
            TransformComponent parentWorld, TransformComponent childLocal, TransformComponent outWorld) {
        return localToWorld(parentWorld, childLocal, false, outWorld);
    }

    /** Composes a child, applying its origin to the hierarchy frame only when it is a Game Object. */
    public static TransformComponent localToWorld(
            TransformComponent parentWorld, TransformComponent childLocal,
            boolean childIsGameObject, TransformComponent outWorld) {
        requireTransforms(parentWorld, childLocal, outWorld);
        requireFiniteTransform(parentWorld);
        requireFiniteTransform(childLocal);
        requirePositiveUniformParentScale(parentWorld);
        float parentScale = parentWorld.scaleX;
        float cos = MathUtils.cos(parentWorld.rotationRad);
        float sin = MathUtils.sin(parentWorld.rotationRad);
        float p00 = cos * parentScale;
        float p01 = -sin * parentScale;
        float p10 = sin * parentScale;
        float p11 = cos * parentScale;
        float parentFrameX = parentWorld.x + parentWorld.originX
                - p00 * parentWorld.originX - p01 * parentWorld.originY;
        float parentFrameY = parentWorld.y + parentWorld.originY
                - p10 * parentWorld.originX - p11 * parentWorld.originY;
        float childPointX = childLocal.x
                + (childIsGameObject ? childLocal.originX : 0f);
        float childPointY = childLocal.y
                + (childIsGameObject ? childLocal.originY : 0f);
        float worldPointX = parentFrameX + p00 * childPointX + p01 * childPointY;
        float worldPointY = parentFrameY + p10 * childPointX + p11 * childPointY;
        outWorld.x = worldPointX - (childIsGameObject ? childLocal.originX : 0f);
        outWorld.y = worldPointY - (childIsGameObject ? childLocal.originY : 0f);
        outWorld.rotationRad = parentWorld.rotationRad + childLocal.rotationRad;
        outWorld.scaleX = parentScale * childLocal.scaleX;
        outWorld.scaleY = parentScale * childLocal.scaleY;
        outWorld.originX = childLocal.originX;
        outWorld.originY = childLocal.originY;
        outWorld.refreshCaches();
        return outWorld;
    }

    /** Converts an existing child world transform into local authored state for a new parent. */
    public static TransformComponent worldToLocal(
            TransformComponent parentWorld, TransformComponent childWorld, TransformComponent outLocal) {
        return worldToLocal(parentWorld, childWorld, false, outLocal);
    }

    /** Inverts a Game Object parent frame while preserving the child's authored origin. */
    public static TransformComponent worldToLocal(
            TransformComponent parentWorld, TransformComponent childWorld,
            boolean childIsGameObject, TransformComponent outLocal) {
        requireTransforms(parentWorld, childWorld, outLocal);
        requireFiniteTransform(parentWorld);
        requireFiniteTransform(childWorld);
        requirePositiveUniformParentScale(parentWorld);
        float cos = MathUtils.cos(parentWorld.rotationRad);
        float sin = MathUtils.sin(parentWorld.rotationRad);
        float parentScale = parentWorld.scaleX;
        float p00 = cos * parentScale;
        float p01 = -sin * parentScale;
        float p10 = sin * parentScale;
        float p11 = cos * parentScale;
        float determinant = p00 * p11 - p01 * p10;
        if (!finite(determinant) || Math.abs(determinant) <= DECOMPOSITION_EPSILON) {
            throw new IllegalArgumentException("Parent hierarchy transform is not invertible.");
        }
        float parentFrameX = parentWorld.x + parentWorld.originX
                - p00 * parentWorld.originX - p01 * parentWorld.originY;
        float parentFrameY = parentWorld.y + parentWorld.originY
                - p10 * parentWorld.originX - p11 * parentWorld.originY;
        float childPointX = childWorld.x
                + (childIsGameObject ? childWorld.originX : 0f);
        float childPointY = childWorld.y
                + (childIsGameObject ? childWorld.originY : 0f);
        float dx = childPointX - parentFrameX;
        float dy = childPointY - parentFrameY;
        float inverseDeterminant = 1f / determinant;
        outLocal.x = (p11 * dx - p01 * dy) * inverseDeterminant
                - (childIsGameObject ? childWorld.originX : 0f);
        outLocal.y = (-p10 * dx + p00 * dy) * inverseDeterminant
                - (childIsGameObject ? childWorld.originY : 0f);
        outLocal.rotationRad = childWorld.rotationRad - parentWorld.rotationRad;
        outLocal.scaleX = childWorld.scaleX / parentScale;
        outLocal.scaleY = childWorld.scaleY / parentScale;
        outLocal.originX = childWorld.originX;
        outLocal.originY = childWorld.originY;
        outLocal.refreshCaches();
        return outLocal;
    }

    /**
     * Extracts a canonical representable transform. Sheared, singular, or non-finite frames are
     * rejected. The canonical decomposition keeps {@code scaleX >= 0} and carries reflection in
     * {@code scaleY}.
     */
    public static TransformComponent extract(
            Affine2 frame, float originX, float originY, TransformComponent out) {
        if (frame == null || out == null) {
            throw new IllegalArgumentException("Hierarchy frame and output transform are required.");
        }
        if (!finite(frame.m00) || !finite(frame.m01) || !finite(frame.m02)
                || !finite(frame.m10) || !finite(frame.m11) || !finite(frame.m12)) {
            throw new IllegalArgumentException("Hierarchy frame must contain finite values.");
        }
        float scaleX = (float) Math.sqrt(frame.m00 * frame.m00 + frame.m10 * frame.m10);
        float columnYLength = (float) Math.sqrt(frame.m01 * frame.m01 + frame.m11 * frame.m11);
        float determinant = frame.m00 * frame.m11 - frame.m01 * frame.m10;
        if (scaleX <= DECOMPOSITION_EPSILON
                || columnYLength <= DECOMPOSITION_EPSILON
                || Math.abs(determinant) <= DECOMPOSITION_EPSILON) {
            throw new IllegalArgumentException("Hierarchy frame is singular and cannot be decomposed.");
        }
        float normalizedDot = (frame.m00 * frame.m01 + frame.m10 * frame.m11)
                / (scaleX * columnYLength);
        if (Math.abs(normalizedDot) > DECOMPOSITION_EPSILON) {
            throw new IllegalArgumentException(
                    "Hierarchy frame contains shear and is not representable by TransformComponent.");
        }
        out.x = frame.m02 - originX + frame.m00 * originX + frame.m01 * originY;
        out.y = frame.m12 - originY + frame.m10 * originX + frame.m11 * originY;
        out.rotationRad = (float) Math.atan2(frame.m10, frame.m00);
        out.scaleX = scaleX;
        out.scaleY = determinant / scaleX;
        out.originX = originX;
        out.originY = originY;
        out.refreshCaches();
        return out;
    }

    private static void requireTransforms(
            TransformComponent parent, TransformComponent child, TransformComponent out) {
        if (parent == null || child == null || out == null) {
            throw new IllegalArgumentException("Parent, child, and output transforms are required.");
        }
        if (out == parent || out == child) {
            throw new IllegalArgumentException("Hierarchy transform output must not alias an input.");
        }
    }

    private static void requireFiniteTransform(TransformComponent transform) {
        if (!finite(transform.x) || !finite(transform.y)
                || !finite(transform.rotationRad)
                || !finite(transform.scaleX) || !finite(transform.scaleY)
                || !finite(transform.originX) || !finite(transform.originY)) {
            throw new IllegalArgumentException("Hierarchy transform must contain finite values.");
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
