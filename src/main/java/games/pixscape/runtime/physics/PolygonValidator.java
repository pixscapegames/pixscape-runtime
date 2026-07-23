package games.pixscape.runtime.physics;

public final class PolygonValidator {
    public static final float EPS = 1e-6f;

    private PolygonValidator() {
    }

    public static PolygonValidationResult validate(float[] vertices, int count) {
        if (vertices == null) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.NULL_VERTICES,
                    "Polygon vertices array is null.");
        }
        if (count < 3) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.NOT_ENOUGH_VERTICES,
                    "Polygon must contain at least 3 vertices.");
        }
        if (count > vertices.length / 2) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.ARRAY_TOO_SMALL,
                    "Polygon vertices array is smaller than vertex count.");
        }

        for (int i = 0; i < count; i++) {
            float x = x(vertices, i);
            float y = y(vertices, i);
            if (!isFinite(x) || !isFinite(y)) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.NON_FINITE_VERTEX,
                        "Polygon contains a non-finite vertex.");
            }
        }

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            if (samePoint(vertices, i, next)) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DUPLICATE_VERTEX,
                        "Polygon contains duplicate consecutive vertices.");
            }
            if (distanceSquared(vertices, i, next) <= EPS * EPS) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DEGENERATE_EDGE,
                        "Polygon contains a degenerate edge.");
            }
        }

        for (int i = 0; i < count; i++) {
            int i2 = (i + 1) % count;
            for (int j = i + 1; j < count; j++) {
                int j2 = (j + 1) % count;
                if (i == j || i == j2 || i2 == j || i2 == j2) {
                    continue;
                }
                if (segmentsIntersect(
                        x(vertices, i), y(vertices, i),
                        x(vertices, i2), y(vertices, i2),
                        x(vertices, j), y(vertices, j),
                        x(vertices, j2), y(vertices, j2))) {
                    return PolygonValidationResult.error(
                            PolygonValidationResult.SELF_INTERSECTION,
                            "Polygon has self-intersections.");
                }
            }
        }

        float area = signedArea(vertices, count);
        if (!isFinite(area) || Math.abs(area) <= EPS) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.ZERO_AREA,
                    "Polygon area is too small or non-finite.");
        }

        for (int i = 0; i < count; i++) {
            int previous = (i + count - 1) % count;
            int next = (i + 1) % count;
            if (Math.abs(cross(vertices, previous, i, next)) <= EPS) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DEGENERATE_ANGLE,
                        "Polygon contains a degenerate angle.");
            }
        }
        return PolygonValidationResult.ok();
    }

    public static boolean isConvex(float[] vertices, int count) {
        if (vertices == null || count < 3 || count > vertices.length / 2) {
            return false;
        }
        int sign = 0;
        for (int i = 0; i < count; i++) {
            int previous = (i + count - 1) % count;
            int next = (i + 1) % count;
            float value = cross(vertices, previous, i, next);
            if (Math.abs(value) <= EPS) {
                return false;
            }
            int currentSign = value > 0f ? 1 : -1;
            if (sign == 0) {
                sign = currentSign;
            } else if (sign != currentSign) {
                return false;
            }
        }
        return true;
    }

    public static float signedArea(float[] vertices, int count) {
        if (vertices == null || count < 3 || count > vertices.length / 2) {
            return 0f;
        }
        float sum = 0f;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            sum += x(vertices, i) * y(vertices, next)
                    - x(vertices, next) * y(vertices, i);
        }
        return sum * 0.5f;
    }

    public static float[] copyCounterClockwise(float[] vertices, int count) {
        float[] copy = new float[count * 2];
        if (signedArea(vertices, count) >= 0f) {
            System.arraycopy(vertices, 0, copy, 0, count * 2);
            return copy;
        }
        for (int i = 0; i < count; i++) {
            int source = count - 1 - i;
            copy[i * 2] = x(vertices, source);
            copy[i * 2 + 1] = y(vertices, source);
        }
        return copy;
    }

    static float x(float[] vertices, int index) {
        return vertices[index * 2];
    }

    static float y(float[] vertices, int index) {
        return vertices[index * 2 + 1];
    }

    static float cross(float[] vertices, int a, int b, int c) {
        float abx = x(vertices, b) - x(vertices, a);
        float aby = y(vertices, b) - y(vertices, a);
        float bcx = x(vertices, c) - x(vertices, b);
        float bcy = y(vertices, c) - y(vertices, b);
        return abx * bcy - aby * bcx;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean samePoint(float[] vertices, int a, int b) {
        return distanceSquared(vertices, a, b) <= EPS * EPS;
    }

    private static float distanceSquared(float[] vertices, int a, int b) {
        float dx = x(vertices, a) - x(vertices, b);
        float dy = y(vertices, a) - y(vertices, b);
        return dx * dx + dy * dy;
    }

    private static boolean segmentsIntersect(
            float ax, float ay, float bx, float by,
            float cx, float cy, float dx, float dy) {
        float o1 = orient(ax, ay, bx, by, cx, cy);
        float o2 = orient(ax, ay, bx, by, dx, dy);
        float o3 = orient(cx, cy, dx, dy, ax, ay);
        float o4 = orient(cx, cy, dx, dy, bx, by);
        if (o1 * o2 < -EPS && o3 * o4 < -EPS) {
            return true;
        }
        if (Math.abs(o1) <= EPS && onSegment(ax, ay, bx, by, cx, cy)) return true;
        if (Math.abs(o2) <= EPS && onSegment(ax, ay, bx, by, dx, dy)) return true;
        if (Math.abs(o3) <= EPS && onSegment(cx, cy, dx, dy, ax, ay)) return true;
        return Math.abs(o4) <= EPS && onSegment(cx, cy, dx, dy, bx, by);
    }

    private static float orient(
            float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static boolean onSegment(
            float ax, float ay, float bx, float by, float px, float py) {
        return px >= Math.min(ax, bx) - EPS
                && px <= Math.max(ax, bx) + EPS
                && py >= Math.min(ay, by) - EPS
                && py <= Math.max(ay, by) + EPS;
    }
}
