package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

public final class PolygonDecomposer {
    public static final int ALGORITHM_VERSION = 1;
    public static final int BOX2D_MAX_POLYGON_VERTICES = 8;

    private PolygonDecomposer() {
    }

    public static PolygonBuildResult build(float[] sourceVertices, int sourceVertexCount) {
        PolygonValidationResult validation =
                PolygonValidator.validate(sourceVertices, sourceVertexCount);
        if (!validation.isValid()) {
            return PolygonBuildResult.failure(validation);
        }

        float[] counterClockwise =
                PolygonValidator.copyCounterClockwise(sourceVertices, sourceVertexCount);
        long hash = PolygonHash.hash(counterClockwise, sourceVertexCount);
        if (sourceVertexCount <= BOX2D_MAX_POLYGON_VERTICES
                && PolygonValidator.isConvex(counterClockwise, sourceVertexCount)) {
            Array<PolygonPartData> parts =
                    new Array<PolygonPartData>(true, 1, PolygonPartData.class);
            PolygonPartData part = new PolygonPartData();
            part.vertexCount = sourceVertexCount;
            part.vertices = copy(counterClockwise, sourceVertexCount);
            parts.add(part);
            return PolygonBuildResult.success(
                    counterClockwise,
                    sourceVertexCount,
                    ALGORITHM_VERSION,
                    hash,
                    parts);
        }
        return PolygonTriangulator.triangulate(counterClockwise, sourceVertexCount);
    }

    private static float[] copy(float[] vertices, int count) {
        float[] copy = new float[count * 2];
        System.arraycopy(vertices, 0, copy, 0, copy.length);
        return copy;
    }
}
