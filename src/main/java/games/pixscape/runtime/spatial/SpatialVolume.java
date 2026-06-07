package games.pixscape.runtime.spatial;

public final class SpatialVolume {
    public float worldX;
    public float worldY;
    public float altitude;
    public float height;
    public float footprintMinX;
    public float footprintMinY;
    public float footprintMaxX;
    public float footprintMaxY;

    public float bottom() {
        return altitude;
    }

    public float top() {
        return altitude + height;
    }

    public boolean hasHeight() {
        return height > 0f;
    }

    public boolean footprintIntersects(SpatialVolume other) {
        if (other == null) return false;
        return footprintMinX <= other.footprintMaxX
                && footprintMaxX >= other.footprintMinX
                && footprintMinY <= other.footprintMaxY
                && footprintMaxY >= other.footprintMinY;
    }

    public boolean verticalOverlaps(SpatialVolume other) {
        if (other == null || !hasHeight() || !other.hasHeight()) return false;
        return bottom() < other.top() && top() > other.bottom();
    }

    public boolean intersects(SpatialVolume other) {
        return footprintIntersects(other) && verticalOverlaps(other);
    }

    public SpatialVolume set(float worldX,
                             float worldY,
                             float altitude,
                             float height,
                             float footprintMinX,
                             float footprintMinY,
                             float footprintMaxX,
                             float footprintMaxY) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.altitude = altitude;
        this.height = height;
        this.footprintMinX = Math.min(footprintMinX, footprintMaxX);
        this.footprintMinY = Math.min(footprintMinY, footprintMaxY);
        this.footprintMaxX = Math.max(footprintMinX, footprintMaxX);
        this.footprintMaxY = Math.max(footprintMinY, footprintMaxY);
        return this;
    }
}
