package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;

public final class SpatialBlockData {
    public static final float DEFAULT_HEIGHT = 128f;

    public int id = 0;
    /** Positive identity of the connected authored-wall structure in the owning Tiled Map. */
    public int structureId = 0;
    public String name = null;

    /**
     * Exclusive authored wall rectangle in integer tiled-cell coordinates.
     */
    public float x = 0f;
    public float y = 0f;
    public float width = 0f;
    public float depth = 0f;

    public float altitude = 0f;
    public float height = DEFAULT_HEIGHT;
    /** Participates in actor spatial ordering. */
    public boolean actorOccluder = true;
    /** Compiled as light-occlusion metadata; the downstream light consumer is not implemented yet. */
    public boolean lightOccluder = false;
    /** Compiled as shadow-geometry metadata; the downstream shadow consumer is not implemented yet. */
    public boolean shadowCaster = false;
    /** Compiled as particle-occlusion metadata; the downstream particle consumer is not implemented yet. */
    public boolean particleOccluder = false;
    public boolean linkedTileRefsAuthored = false;
    public Array<LinkedTileRef> linkedTileRefs = new Array<>(LinkedTileRef[]::new);

    public SpatialBlockData copy() {
        SpatialBlockData b = new SpatialBlockData();
        b.id = id;
        b.structureId = structureId;
        b.name = name;
        b.x = x;
        b.y = y;
        b.width = width;
        b.depth = depth;
        b.altitude = altitude;
        b.height = height;
        b.actorOccluder = actorOccluder;
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

    public void addLinkedTileRef(int gx, int gy, int tileAssetId) {
        if (linkedTileRefs == null) {
            linkedTileRefs = new Array<>(LinkedTileRef[]::new);
        }
        linkedTileRefsAuthored = true;
        LinkedTileRef ref = new LinkedTileRef();
        ref.gx = gx;
        ref.gy = gy;
        ref.tileAssetId = tileAssetId;
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
            copy.tileAssetId = ref.tileAssetId;
            linkedTileRefs.add(copy);
        }
    }

    public static final class LinkedTileRef {
        public int gx = 0;
        public int gy = 0;
        public int tileAssetId = 0;
    }
}
