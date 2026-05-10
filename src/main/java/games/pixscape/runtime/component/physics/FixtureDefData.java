package games.pixscape.runtime.component.physics;

import java.util.Arrays;

public final class FixtureDefData {
    public static final int SHAPE_BOX = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_POLYGON = 2;

    /**
     * Stable editor/history ID. > 0 when initialized.
     */
    public int fixtureId = 0;

    public int shapeType = SHAPE_BOX;

    // Polygon
    public float[] polyVerts = new float[0];
    public int polyCount = 0;

    // Box
    public float halfW = 0.5f;
    public float halfH = 0.5f;

    // Circle
    public float radius = 0.5f;

    // Transform (local body space, meters)
    public float offsetX = 0f;
    public float offsetY = 0f;
    public float angleDeg = 0f;

    // Material
    public float density = 1f;
    public float friction = 0.2f;
    public float restitution = 0f;
    public boolean isSensor = false;

    // Filter
    public short categoryBits = 0x0001;
    public short maskBits = (short) 0xFFFF;
    public short groupIndex = 0;

    public FixtureDefData copy() {
        FixtureDefData f = new FixtureDefData();
        f.fixtureId = fixtureId;
        f.shapeType = shapeType;
        f.polyCount = polyCount;
        f.polyVerts = (polyVerts != null)
                ? Arrays.copyOf(polyVerts, polyVerts.length)
                : new float[0];
        f.halfW = halfW;
        f.halfH = halfH;
        f.angleDeg = angleDeg;
        f.radius = radius;
        f.offsetX = offsetX;
        f.offsetY = offsetY;
        f.density = density;
        f.friction = friction;
        f.restitution = restitution;
        f.isSensor = isSensor;
        f.categoryBits = categoryBits;
        f.maskBits = maskBits;
        f.groupIndex = groupIndex;
        return f;
    }
}
