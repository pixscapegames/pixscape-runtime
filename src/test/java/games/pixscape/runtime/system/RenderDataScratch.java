package games.pixscape.runtime.system;

import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.SortKey64;

final class RenderDataScratch {
    final int[] kind;
    final int[] textureHandle;
    final int[] shader;
    final int[] blend;
    final int[] layerIndex;
    final int[] z;
    final int[] paramsId;
    final int[] customParamsId;
    final int[] runtimeOrder;
    final long[] sortKey;
    final float[] x1, y1, x2, y2, x3, y3, x4, y4;
    final float[] u1, v1, u2, v2;
    final float[] colorPacked;
    final float[] a;
    final byte[] repeatFlags;

    RenderDataScratch(int capacity) {
        kind = new int[capacity];
        textureHandle = new int[capacity];
        shader = new int[capacity];
        blend = new int[capacity];
        layerIndex = new int[capacity];
        z = new int[capacity];
        paramsId = new int[capacity];
        customParamsId = new int[capacity];
        runtimeOrder = new int[capacity];
        sortKey = new long[capacity];
        x1 = new float[capacity];
        y1 = new float[capacity];
        x2 = new float[capacity];
        y2 = new float[capacity];
        x3 = new float[capacity];
        y3 = new float[capacity];
        x4 = new float[capacity];
        y4 = new float[capacity];
        u1 = new float[capacity];
        v1 = new float[capacity];
        u2 = new float[capacity];
        v2 = new float[capacity];
        colorPacked = new float[capacity];
        a = new float[capacity];
        repeatFlags = new byte[capacity];
    }

    void enableSprite(int slot, int layer, int zIndex, int order) {
        kind[slot] = RenderKind.SPRITE;
        textureHandle[slot] = 1;
        shader[slot] = 1;
        blend[slot] = BlendMode.ALPHA.id;
        layerIndex[slot] = layer;
        z[slot] = zIndex;
        runtimeOrder[slot] = order;
        x2[slot] = 1f;
        x3[slot] = 1f;
        y3[slot] = 1f;
        y4[slot] = 1f;
        u2[slot] = 1f;
        v2[slot] = 1f;
        colorPacked[slot] = 1f;
        a[slot] = 1f;
        repeatFlags[slot] = RenderRepeatFlags.NONE;
        sortKey[slot] = SortKey64.packForBlend(shader[slot], blend[slot], textureHandle[slot], layer, zIndex, order);
    }
}
