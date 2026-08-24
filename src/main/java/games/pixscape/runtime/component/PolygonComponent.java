package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

import java.util.Arrays;

/**
 * Generic authored closed geometry stored as local-space XY vertex pairs.
 */
public final class PolygonComponent extends PooledComponent {
    public float[] vertices = new float[0];

    public void setVertices(float[] source) {
        vertices = source == null || source.length == 0
                ? new float[0]
                : Arrays.copyOf(source, source.length);
    }

    public float[] verticesCopy() {
        return Arrays.copyOf(vertices, vertices.length);
    }

    @Override
    protected void reset() {
        vertices = new float[0];
    }
}
