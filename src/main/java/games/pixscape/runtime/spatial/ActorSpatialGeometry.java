package games.pixscape.runtime.spatial;

/**
 * Reusable image-zone geometry on the native BL-TL-TR / BL-TR-BR triangles.
 * Positions are projected world pixels before display offsets. UVs are native normalized
 * image coordinates; the material's signed atlas region applies the final texture flip.
 */
public final class ActorSpatialGeometry {
    public static final int MAX_VERTICES = 30;
    public static final int STRIDE = 4;
    public final float[] vertices = new float[MAX_VERTICES * STRIDE];
    private final float[] quad = new float[8];
    private final float[] input = new float[16];
    private final float[] output = new float[16];
    public int vertexCount;
    public int revision;
    public float minX, minY, maxX, maxY;
    private float x, y, width, height;
    private boolean flipX, flipY, initialized;

    public void update(ActorSpatialRectangleData rectangle,
                       float x1, float y1, float x2, float y2,
                       float x3, float y3, float x4, float y4,
                       boolean reflectedX, boolean reflectedY) {
        if (rectangle == null) {
            throw new IllegalArgumentException("Spatial representation is incomplete: define an occlusion rectangle and shadow pivot.");
        }
        float rx = rectangle.x;
        float ry = rectangle.y;
        float rw = rectangle.width;
        float rh = rectangle.height;
        if (initialized && x == rx && y == ry && width == rw && height == rh
                && flipX == reflectedX && flipY == reflectedY
                && quad[0] == x1 && quad[1] == y1 && quad[2] == x2 && quad[3] == y2
                && quad[4] == x3 && quad[5] == y3 && quad[6] == x4 && quad[7] == y4) {
            return;
        }
        x = rx;
        y = ry;
        width = rw;
        height = rh;
        flipX = reflectedX;
        flipY = reflectedY;
        quad[0] = x1;
        quad[1] = y1;
        quad[2] = x2;
        quad[3] = y2;
        quad[4] = x3;
        quad[5] = y3;
        quad[6] = x4;
        quad[7] = y4;
        initialized = true;
        vertexCount = 0;
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        float left = flipX ? 1f - rx - rw : rx;
        float bottom = flipY ? 1f - ry - rh : ry;
        for (int triangle = 0; triangle < 2; triangle++) {
            input[0] = input[1] = 0f;
            input[2] = triangle == 0 ? 0f : 1f;
            input[3] = 1f;
            input[4] = 1f;
            input[5] = triangle == 0 ? 1f : 0f;
            int count = clip(input, 3, output, 0, left, true);
            count = clip(output, count, input, 0, left + rw, false);
            count = clip(input, count, output, 1, bottom, true);
            count = clip(output, count, input, 1, bottom + rh, false);
            for (int i = 1; i + 1 < count; i++) {
                int start = vertexCount;
                write(triangle, input[0], input[1]);
                write(triangle, input[i * 2], input[i * 2 + 1]);
                write(triangle, input[(i + 1) * 2], input[(i + 1) * 2 + 1]);
                int a = start * STRIDE, b = a + STRIDE, c = b + STRIDE;
                float area = (vertices[b] - vertices[a]) * (vertices[c + 1] - vertices[a + 1])
                        - (vertices[b + 1] - vertices[a + 1]) * (vertices[c] - vertices[a]);
                if (area == 0f) {
                    vertexCount = start;
                }
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            int at = i * STRIDE;
            minX = Math.min(minX, vertices[at]);
            minY = Math.min(minY, vertices[at + 1]);
            maxX = Math.max(maxX, vertices[at]);
            maxY = Math.max(maxY, vertices[at + 1]);
        }
        if (vertexCount == 0) {
            minX = maxX = x1;
            minY = maxY = y1;
        }
        revision++;
    }

    /** Maps a reference-image point, preserving the native diagonal and texture flips. */
    public void localToWorld(float u, float v, float[] out, int at) {
        if (flipX) {
            u = 1f - u;
        }
        if (flipY) {
            v = 1f - v;
        }
        position(u <= v ? 0 : 1, u, v, out, at);
    }

    /**
     * Inverts a chosen native triangle. A locked gesture keeps that triangle even outside it.
     * Returns false for a singular triangle without changing the output.
     */
    public boolean worldToLocal(float px, float py, int triangle, float[] out, int at) {
        int b = triangle == 0 ? 2 : 4;
        int c = triangle == 0 ? 4 : 6;
        float bx = quad[b] - quad[0], by = quad[b + 1] - quad[1];
        float cx = quad[c] - quad[0], cy = quad[c + 1] - quad[1];
        float determinant = bx * cy - by * cx;
        if (Math.abs(determinant) <= .000001f) {
            return false;
        }
        float dx = px - quad[0], dy = py - quad[1];
        float wb = (dx * cy - dy * cx) / determinant;
        float wc = (bx * dy - by * dx) / determinant;
        float u = triangle == 0 ? wc : wb + wc;
        float v = triangle == 0 ? wb + wc : wb;
        out[at] = flipX ? 1f - u : u;
        out[at + 1] = flipY ? 1f - v : v;
        return true;
    }

    public int triangleAt(float u, float v) {
        return (flipX ? 1f - u : u) <= (flipY ? 1f - v : v) ? 0 : 1;
    }

    private void write(int triangle, float u, float v) {
        int at = vertexCount++ * STRIDE;
        position(triangle, u, v, vertices, at);
        vertices[at + 2] = u;
        vertices[at + 3] = v;
    }

    private void position(int triangle, float u, float v, float[] out, int at) {
        float a = triangle == 0 ? 1f - v : 1f - u;
        float b = triangle == 0 ? v - u : v;
        float c = triangle == 0 ? u : u - v;
        int bi = triangle == 0 ? 2 : 4;
        int ci = triangle == 0 ? 4 : 6;
        out[at] = a * quad[0] + b * quad[bi] + c * quad[ci];
        out[at + 1] = a * quad[1] + b * quad[bi + 1] + c * quad[ci + 1];
    }

    private static int clip(float[] from, int count, float[] to, int axis,
                            float limit, boolean greater) {
        if (count == 0) {
            return 0;
        }
        int written = 0;
        int previous = (count - 1) * 2;
        for (int i = 0; i < count; i++) {
            int current = i * 2;
            float a = from[previous + axis], b = from[current + axis];
            boolean insideA = greater ? a >= limit : a <= limit;
            boolean insideB = greater ? b >= limit : b <= limit;
            if (insideA != insideB) {
                float t = (limit - a) / (b - a);
                to[written++] = from[previous] + t * (from[current] - from[previous]);
                to[written++] = from[previous + 1] + t * (from[current + 1] - from[previous + 1]);
            }
            if (insideB) {
                to[written++] = from[current];
                to[written++] = from[current + 1];
            }
            previous = current;
        }
        return written / 2;
    }
}
