package games.pixscape.runtime.component;

import java.util.Arrays;

public final class SpatialShapeData {
    public static final int SHAPE_BOX = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_POLYGON = 2;

    public int shapeType = SHAPE_BOX;

    public float[] polyVerts = new float[0];
    public int polyCount = 0;

    public float halfW = 0.5f;
    public float halfH = 0.5f;
    public float radius = 0.5f;

    public float offsetX = 0f;
    public float offsetY = 0f;
    public float angleDeg = 0f;

    public boolean collisionEnabled = false;
    public boolean actorOccluder = false;
    public boolean lightOccluder = false;
    public boolean particleOccluder = false;

    public float elevation = 0f;
    public float height = 0f;

    public SpatialShapeData copy() {
        SpatialShapeData s = new SpatialShapeData();
        s.shapeType = shapeType;
        s.polyCount = polyCount;
        s.polyVerts = polyVerts != null ? Arrays.copyOf(polyVerts, polyVerts.length) : new float[0];
        s.halfW = halfW;
        s.halfH = halfH;
        s.radius = radius;
        s.offsetX = offsetX;
        s.offsetY = offsetY;
        s.angleDeg = angleDeg;
        s.collisionEnabled = collisionEnabled;
        s.actorOccluder = actorOccluder;
        s.lightOccluder = lightOccluder;
        s.particleOccluder = particleOccluder;
        s.elevation = elevation;
        s.height = height;
        return s;
    }
}
