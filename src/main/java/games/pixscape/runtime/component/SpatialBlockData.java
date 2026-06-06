package games.pixscape.runtime.component;

import com.badlogic.gdx.utils.Array;

public final class SpatialBlockData {
    public static final float DEFAULT_HEIGHT = 128f;

    public int id = 0;
    public String name = null;
    public boolean enabled = true;

    /**
     * Layer/map-local footprint origin. Width and depth are measured in the
     * same local units; orientation is resolved through the tiled layer later.
     */
    public float x = 0f;
    public float y = 0f;
    public float width = 0f;
    public float depth = 0f;

    public float altitude = 0f;
    public float height = DEFAULT_HEIGHT;
    public SpatialBlockOrientation orientation = SpatialBlockOrientation.TILE_CELL;

    public boolean actorOccluder = true;
    public boolean physicsCollision = false;
    public boolean lightOccluder = false;
    public boolean shadowCaster = false;
    public boolean particleOccluder = false;
    public boolean linkedTileRefsAuthored = false;
    public Array<LinkedTileRef> linkedTileRefs = new Array<>(LinkedTileRef[]::new);

    public SpatialBlockData copy() {
        SpatialBlockData b = new SpatialBlockData();
        b.id = id;
        b.name = name;
        b.enabled = enabled;
        b.x = x;
        b.y = y;
        b.width = width;
        b.depth = depth;
        b.altitude = altitude;
        b.height = height;
        b.orientation = orientation;
        b.actorOccluder = actorOccluder;
        b.physicsCollision = physicsCollision;
        b.lightOccluder = lightOccluder;
        b.shadowCaster = shadowCaster;
        b.particleOccluder = particleOccluder;
        b.linkedTileRefsAuthored = linkedTileRefsAuthored;
        b.copyLinkedTileRefsFrom(this);
        return b;
    }

    public boolean hasAuthoredLinkedTileRefs() {
        return linkedTileRefsAuthored;
    }

    public boolean hasLinkedTileRefs() {
        return linkedTileRefs != null && linkedTileRefs.size > 0;
    }

    public void clearLinkedTileRefs() {
        if (linkedTileRefs == null) {
            linkedTileRefs = new Array<>(LinkedTileRef[]::new);
        } else {
            linkedTileRefs.clear();
        }
        linkedTileRefsAuthored = false;
    }

    public void beginAuthoredLinkedTileRefs() {
        clearLinkedTileRefs();
        linkedTileRefsAuthored = true;
    }

    public void addLinkedTileRef(int gx, int gy, int tileId) {
        if (linkedTileRefs == null) {
            linkedTileRefs = new Array<>(LinkedTileRef[]::new);
        }
        linkedTileRefsAuthored = true;
        LinkedTileRef ref = new LinkedTileRef();
        ref.gx = gx;
        ref.gy = gy;
        ref.tileId = tileId;
        linkedTileRefs.add(ref);
    }

    public void copyLinkedTileRefsFrom(SpatialBlockData source) {
        boolean authored = source != null && source.linkedTileRefsAuthored;
        clearLinkedTileRefs();
        if (source == null || source.linkedTileRefs == null) return;
        linkedTileRefsAuthored = authored;
        for (int i = 0, n = source.linkedTileRefs.size; i < n; i++) {
            LinkedTileRef ref = source.linkedTileRefs.get(i);
            if (ref == null) continue;
            LinkedTileRef copy = new LinkedTileRef();
            copy.gx = ref.gx;
            copy.gy = ref.gy;
            copy.tileId = ref.tileId;
            linkedTileRefs.add(copy);
        }
    }

    public static final class LinkedTileRef {
        public int gx = 0;
        public int gy = 0;
        public int tileId = 0;
    }
}
