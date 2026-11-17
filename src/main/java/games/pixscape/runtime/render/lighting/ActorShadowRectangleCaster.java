package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.spatial.ActorSpatialGeometry;

/**
 * Prepared native-triangle alpha mapping and reusable per-light triangles.
 * The reference publication and its swept bounds never depend on a light.
 */
public final class ActorShadowRectangleCaster {
    public static final int MAX_VERTICES = ActorSpatialGeometry.MAX_VERTICES;
    private static final float DIAGONAL = .70710678f;
    public final float[] vertices = new float[MAX_VERTICES * 8];
    public final float[] lightVertices = new float[MAX_VERTICES * 8];
    private final float[] horizontal = new float[MAX_VERTICES], last = new float[5];
    private ActorSpatialGeometry lastGeometry;
    private int geometryRevision;
    private boolean initialized;
    public int vertexCount;
    public int updates;
    private float pivotX, pivotY, radius, minZ, maxZ;

    public void update(ActorSpatialGeometry geometry, float footX, float footY, float altitude,
                       float offsetX, float offsetY) {
        if (initialized && lastGeometry == geometry && geometryRevision == geometry.revision
                && last[0] == footX && last[1] == footY && last[2] == altitude
                && last[3] == offsetX && last[4] == offsetY) {
            return;
        }
        float sx = footX + offsetX, sy = footY + offsetY;
        pivotX = LightingCoordinates.worldX(sx, sy, altitude);
        pivotY = LightingCoordinates.worldY(sx, sy, altitude);
        vertexCount = geometry.vertexCount;
        radius = 0f;
        minZ = Float.POSITIVE_INFINITY;
        maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertexCount; i++) {
            int source = i * ActorSpatialGeometry.STRIDE, o = i * 8;
            float px = geometry.vertices[source] - sx;
            float z = altitude + geometry.vertices[source + 1] - sy;
            horizontal[i] = px * DIAGONAL;
            radius = Math.max(radius, Math.abs(horizontal[i]));
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            vertices[o] = pivotX + px * .5f;
            vertices[o + 1] = pivotY - px * .5f;
            vertices[o + 2] = z;
            vertices[o + 3] = geometry.vertices[source + 2];
            vertices[o + 4] = geometry.vertices[source + 3];
            vertices[o + 5] = DIAGONAL;
            vertices[o + 6] = DIAGONAL;
            vertices[o + 7] = 0f;
        }
        if (vertexCount == 0) {
            minZ = maxZ = altitude;
        }
        last[0] = footX;
        last[1] = footY;
        last[2] = altitude;
        last[3] = offsetX;
        last[4] = offsetY;
        lastGeometry = geometry;
        geometryRevision = geometry.revision;
        initialized = true;
        updates++;
    }

    /**
     * Once per caster/light before the first face; all depth replays reuse the same vertices.
     */
    public void faceLight(float lightX, float lightY) {
        float dx = lightX - pivotX, dy = lightY - pivotY, d2 = dx * dx + dy * dy;
        float tx = DIAGONAL, ty = -DIAGONAL;
        if (d2 > .000001f) {
            float inverse = 1f / (float) Math.sqrt(d2);
            tx = -dy * inverse;
            ty = dx * inverse;
        }
        System.arraycopy(vertices, 0, lightVertices, 0, vertexCount * 8);
        for (int i = 0; i < vertexCount; i++) {
            int o = i * 8;
            lightVertices[o] = pivotX + horizontal[i] * tx;
            lightVertices[o + 1] = pivotY + horizontal[i] * ty;
            lightVertices[o + 5] = ty;
            lightVertices[o + 6] = -tx;
        }
    }

    public void bounds(LightingFrame.Caster caster) {
        caster.minX = pivotX - radius;
        caster.maxX = pivotX + radius;
        caster.minY = pivotY - radius;
        caster.maxY = pivotY + radius;
        caster.minZ = minZ;
        caster.maxZ = maxZ;
    }
}
