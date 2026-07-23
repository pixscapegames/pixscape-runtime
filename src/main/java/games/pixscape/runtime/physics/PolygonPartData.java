package games.pixscape.runtime.physics;

import java.util.Arrays;

/**
 * Transient convex polygon part produced by the shared polygon compiler.
 */
public final class PolygonPartData {
    public float[] vertices = new float[0];
    public int vertexCount;

    public PolygonPartData copy() {
        PolygonPartData copy = new PolygonPartData();
        copy.vertices = vertices != null
                ? Arrays.copyOf(vertices, vertices.length)
                : new float[0];
        copy.vertexCount = vertexCount;
        return copy;
    }
}
