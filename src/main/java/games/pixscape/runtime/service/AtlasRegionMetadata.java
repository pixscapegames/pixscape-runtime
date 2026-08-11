package games.pixscape.runtime.service;

/**
 * Immutable render metadata for the first region of an indexed atlas asset.
 */
public final class AtlasRegionMetadata {

    private final String regionName;
    private final float u1;
    private final float v1;
    private final float u2;
    private final float v2;
    private final int textureHandle;
    private final int pixelWidth;
    private final int pixelHeight;

    AtlasRegionMetadata(
            String regionName,
            float u1,
            float v1,
            float u2,
            float v2,
            int textureHandle,
            int pixelWidth,
            int pixelHeight) {
        this.regionName = regionName;
        this.u1 = u1;
        this.v1 = v1;
        this.u2 = u2;
        this.v2 = v2;
        this.textureHandle = textureHandle;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
    }

    public String regionName() {
        return regionName;
    }

    public float u1() {
        return u1;
    }

    public float v1() {
        return v1;
    }

    public float u2() {
        return u2;
    }

    public float v2() {
        return v2;
    }

    public int textureHandle() {
        return textureHandle;
    }

    public int pixelWidth() {
        return pixelWidth;
    }

    public int pixelHeight() {
        return pixelHeight;
    }
}
