package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

import java.util.Arrays;

/**
 * {@code SUPPORTED_EXPERT} authored closed geometry stored as local-space XY
 * vertex pairs.
 *
 * <p>Ordinary read-only gameplay access should prefer
 * {@link games.pixscape.runtime.api.EntityRef#geometry()}.
 * {@link #setVertices(float[])} copies its input and {@link #verticesCopy()}
 * returns caller-owned data. Direct access to the public backing array is
 * expert-level access; mutations change authored geometry state.</p>
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
