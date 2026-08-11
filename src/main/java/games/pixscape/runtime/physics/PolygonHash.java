package games.pixscape.runtime.physics;

public final class PolygonHash {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private PolygonHash() {
    }

    public static long hash(float[] vertices, int count) {
        long hash = FNV_OFFSET;
        hash = mix(hash, count);
        if (vertices == null || count <= 0) {
            return hash;
        }
        int valueCount = Math.min(vertices.length, count * 2);
        for (int i = 0; i < valueCount; i++) {
            float value = vertices[i];
            if (value == 0f) {
                value = 0f;
            }
            hash = mix(hash, Float.floatToIntBits(value));
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        hash *= FNV_PRIME;
        return hash;
    }
}
