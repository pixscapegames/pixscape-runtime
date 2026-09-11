package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

final class HudBatchState {
    private final Color color = new Color(Color.WHITE);
    private float packedColor = Color.WHITE_FLOAT_BITS;

    private boolean blendingDisabled;
    private int blendSrcFunc = GL20.GL_SRC_ALPHA;
    private int blendDstFunc = GL20.GL_ONE_MINUS_SRC_ALPHA;
    private int blendSrcFuncAlpha = GL20.GL_SRC_ALPHA;
    private int blendDstFuncAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA;

    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 realTransformMatrix = new Matrix4();
    private final Matrix4 virtualTransformMatrix = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private final Affine2 vertexAdjustment = new Affine2();
    private final Affine2 inverseRealScratch = new Affine2();
    private final Affine2 requestedTransformScratch = new Affine2();
    private boolean vertexAdjustmentNeeded;
    private boolean realTransformIdentity = true;

    HudBatchState() {
        updateCombinedMatrix();
    }

    void setColor(Color tint) {
        if (tint == null) throw new IllegalArgumentException("tint is null");
        color.set(tint);
        packedColor = tint.toFloatBits();
    }

    void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
        packedColor = color.toFloatBits();
    }

    Color color() {
        return color;
    }

    void setPackedColor(float packedColor) {
        Color.abgr8888ToColor(color, packedColor);
        this.packedColor = packedColor;
    }

    float packedColor() {
        return packedColor;
    }

    boolean isBlendingEnabled() {
        return !blendingDisabled;
    }

    void setBlendingEnabled(boolean enabled) {
        blendingDisabled = !enabled;
    }

    boolean setBlendFunctionSeparate(int srcColor, int dstColor, int srcAlpha, int dstAlpha) {
        if (blendSrcFunc == srcColor && blendDstFunc == dstColor
                && blendSrcFuncAlpha == srcAlpha && blendDstFuncAlpha == dstAlpha) {
            return false;
        }
        blendSrcFunc = srcColor;
        blendDstFunc = dstColor;
        blendSrcFuncAlpha = srcAlpha;
        blendDstFuncAlpha = dstAlpha;
        return true;
    }

    int blendSrcFunc() {
        return blendSrcFunc;
    }

    int blendDstFunc() {
        return blendDstFunc;
    }

    int blendSrcFuncAlpha() {
        return blendSrcFuncAlpha;
    }

    int blendDstFuncAlpha() {
        return blendDstFuncAlpha;
    }

    Matrix4 projectionMatrix() {
        return projectionMatrix;
    }

    Matrix4 transformMatrix() {
        return virtualTransformMatrix;
    }

    Matrix4 combinedMatrix() {
        return combinedMatrix;
    }

    void setProjectionMatrix(Matrix4 projection) {
        if (projection == null) throw new IllegalArgumentException("projection is null");
        syncRealTransformToVirtual();
        projectionMatrix.set(projection);
        updateCombinedMatrix();
    }

    void setTransformMatrix(Matrix4 transform, boolean drawing) {
        if (transform == null) throw new IllegalArgumentException("transform is null");

        if (!drawing) {
            realTransformMatrix.setAsAffine(transform);
            virtualTransformMatrix.setAsAffine(transform);
            realTransformIdentity = affineIsIdentity(realTransformMatrix);
            vertexAdjustment.idt();
            vertexAdjustmentNeeded = false;
            updateCombinedMatrix();
            return;
        }

        if (affineEquals(virtualTransformMatrix, transform)) return;

        requestedTransformScratch.set(transform);
        if (affineEquals(realTransformMatrix, transform)) {
            virtualTransformMatrix.setAsAffine(transform);
            vertexAdjustment.idt();
            vertexAdjustmentNeeded = false;
            return;
        }

        if (realTransformIdentity) {
            vertexAdjustment.set(requestedTransformScratch);
        } else {
            inverseRealScratch.set(realTransformMatrix);
            float determinant = inverseRealScratch.m00 * inverseRealScratch.m11
                    - inverseRealScratch.m01 * inverseRealScratch.m10;
            if (determinant == 0f) {
                throw new IllegalStateException(
                        "HudBatch cannot apply a virtual transform because the active real transform is singular.");
            }
            inverseRealScratch.inv().mul(requestedTransformScratch);
            vertexAdjustment.set(inverseRealScratch);
        }
        virtualTransformMatrix.setAsAffine(transform);
        vertexAdjustmentNeeded = true;
    }

    void syncRealTransformToVirtual() {
        if (!affineEquals(realTransformMatrix, virtualTransformMatrix)) {
            realTransformMatrix.setAsAffine(virtualTransformMatrix);
            realTransformIdentity = affineIsIdentity(realTransformMatrix);
        }
        vertexAdjustment.idt();
        vertexAdjustmentNeeded = false;
        updateCombinedMatrix();
    }

    boolean vertexAdjustmentNeeded() {
        return vertexAdjustmentNeeded;
    }

    Affine2 vertexAdjustment() {
        return vertexAdjustment;
    }

    private void updateCombinedMatrix() {
        combinedMatrix.set(projectionMatrix).mul(realTransformMatrix);
    }

    private static boolean affineEquals(Matrix4 left, Matrix4 right) {
        return left.val[Matrix4.M00] == right.val[Matrix4.M00]
                && left.val[Matrix4.M01] == right.val[Matrix4.M01]
                && left.val[Matrix4.M03] == right.val[Matrix4.M03]
                && left.val[Matrix4.M10] == right.val[Matrix4.M10]
                && left.val[Matrix4.M11] == right.val[Matrix4.M11]
                && left.val[Matrix4.M13] == right.val[Matrix4.M13];
    }

    private static boolean affineIsIdentity(Matrix4 matrix) {
        return matrix.val[Matrix4.M00] == 1f
                && matrix.val[Matrix4.M01] == 0f
                && matrix.val[Matrix4.M03] == 0f
                && matrix.val[Matrix4.M10] == 0f
                && matrix.val[Matrix4.M11] == 1f
                && matrix.val[Matrix4.M13] == 0f;
    }
}

