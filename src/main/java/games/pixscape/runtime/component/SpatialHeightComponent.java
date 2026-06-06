package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class SpatialHeightComponent extends PooledComponent {
    public static final float DEFAULT_FOOTPRINT_OFFSET_X = 0f;
    public static final float DEFAULT_FOOTPRINT_OFFSET_Y = 0f;
    public static final float DEFAULT_FOOTPRINT_WIDTH = 32f;
    public static final float DEFAULT_FOOTPRINT_DEPTH = 16f;

    public float altitude = 0f;
    public float height = 0f;
    public float footprintOffsetX = DEFAULT_FOOTPRINT_OFFSET_X;
    public float footprintOffsetY = DEFAULT_FOOTPRINT_OFFSET_Y;
    public float footprintWidth = DEFAULT_FOOTPRINT_WIDTH;
    public float footprintDepth = DEFAULT_FOOTPRINT_DEPTH;

    @Override
    protected void reset() {
        altitude = 0f;
        height = 0f;
        footprintOffsetX = DEFAULT_FOOTPRINT_OFFSET_X;
        footprintOffsetY = DEFAULT_FOOTPRINT_OFFSET_Y;
        footprintWidth = DEFAULT_FOOTPRINT_WIDTH;
        footprintDepth = DEFAULT_FOOTPRINT_DEPTH;
    }
}
