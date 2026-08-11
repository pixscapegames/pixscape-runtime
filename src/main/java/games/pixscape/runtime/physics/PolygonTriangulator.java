package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

public final class PolygonTriangulator {
    private PolygonTriangulator() {
    }

    public static PolygonBuildResult triangulate(float[] sourceVertices, int sourceVertexCount) {
        PolygonValidationResult validation =
                PolygonValidator.validate(sourceVertices, sourceVertexCount);
        if (!validation.isValid()) {
            return PolygonBuildResult.failure(validation);
        }

        float[] vertices =
                PolygonValidator.copyCounterClockwise(sourceVertices, sourceVertexCount);
        int[] indices = new int[sourceVertexCount];
        for (int i = 0; i < sourceVertexCount; i++) {
            indices[i] = i;
        }

        int remaining = sourceVertexCount;
        long guard = (long) sourceVertexCount * (long) sourceVertexCount;
        Array<PolygonPartData> parts = new Array<PolygonPartData>(
                true, Math.max(1, sourceVertexCount - 2), PolygonPartData.class);

        while (remaining > 3) {
            boolean clipped = false;
            for (int i = 0; i < remaining; i++) {
                int previousSlot = (i + remaining - 1) % remaining;
                int nextSlot = (i + 1) % remaining;
                int previous = indices[previousSlot];
                int current = indices[i];
                int next = indices[nextSlot];
                if (!isConvexCorner(vertices, previous, current, next)) {
                    continue;
                }
                if (containsAnyPointInsideTriangle(
                        vertices, indices, remaining, previous, current, next)) {
                    continue;
                }

                parts.add(makeTriangle(vertices, previous, current, next));
                for (int k = i; k < remaining - 1; k++) {
                    indices[k] = indices[k + 1];
                }
                remaining--;
                clipped = true;
                break;
            }

            guard--;
            if (!clipped || guard <= 0L) {
                return PolygonBuildResult.failure(
                        PolygonValidationResult.error(
                                PolygonValidationResult.TRIANGULATION_FAILED,
                                "Polygon triangulation failed."));
            }
        }

        parts.add(makeTriangle(vertices, indices[0], indices[1], indices[2]));
        return PolygonBuildResult.success(
                vertices,
                sourceVertexCount,
                PolygonDecomposer.ALGORITHM_VERSION,
                PolygonHash.hash(vertices, sourceVertexCount),
                parts);
    }

    private static boolean isConvexCorner(
            float[] vertices, int previous, int current, int next) {
        float ax = PolygonValidator.x(vertices, previous);
        float ay = PolygonValidator.y(vertices, previous);
        float bx = PolygonValidator.x(vertices, current);
        float by = PolygonValidator.y(vertices, current);
        float cx = PolygonValidator.x(vertices, next);
        float cy = PolygonValidator.y(vertices, next);
        return (bx - ax) * (cy - by) - (by - ay) * (cx - bx) > PolygonValidator.EPS;
    }

    private static boolean containsAnyPointInsideTriangle(
            float[] vertices,
            int[] indices,
            int remaining,
            int a,
            int b,
            int c) {
        float ax = PolygonValidator.x(vertices, a);
        float ay = PolygonValidator.y(vertices, a);
        float bx = PolygonValidator.x(vertices, b);
        float by = PolygonValidator.y(vertices, b);
        float cx = PolygonValidator.x(vertices, c);
        float cy = PolygonValidator.y(vertices, c);
        for (int i = 0; i < remaining; i++) {
            int point = indices[i];
            if (point == a || point == b || point == c) {
                continue;
            }
            if (pointInTriangle(
                    ax, ay, bx, by, cx, cy,
                    PolygonValidator.x(vertices, point),
                    PolygonValidator.y(vertices, point))) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointInTriangle(
            float ax, float ay,
            float bx, float by,
            float cx, float cy,
            float px, float py) {
        float first = cross(ax, ay, bx, by, px, py);
        float second = cross(bx, by, cx, cy, px, py);
        float third = cross(cx, cy, ax, ay, px, py);
        return first >= -PolygonValidator.EPS
                && second >= -PolygonValidator.EPS
                && third >= -PolygonValidator.EPS;
    }

    private static float cross(
            float ax, float ay,
            float bx, float by,
            float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static PolygonPartData makeTriangle(float[] vertices, int a, int b, int c) {
        PolygonPartData part = new PolygonPartData();
        part.vertexCount = 3;
        part.vertices = new float[]{
                PolygonValidator.x(vertices, a), PolygonValidator.y(vertices, a),
                PolygonValidator.x(vertices, b), PolygonValidator.y(vertices, b),
                PolygonValidator.x(vertices, c), PolygonValidator.y(vertices, c)
        };
        return part;
    }
}
