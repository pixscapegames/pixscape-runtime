package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;

public final class SpatialBlockData {
    public static final float DEFAULT_HEIGHT = 128f;

    public int id = 0;
    /**
     * Positive identity of the connected authored-wall structure in the owning Tiled Map.
     */
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
    /**
     * Participates in actor spatial ordering.
     */
    public boolean actorOccluder = true;
    /**
     * Requests a complete volume caster in 2.5D lighting, independent of image visibility.
     */
    public boolean lightOccluder = false;
    /**
     * Requests a complete volume caster; either this flag or lightOccluder enables it.
     */
    public boolean shadowCaster = false;
    /**
     * Applies the physical facade orientation to direct light; independent of shadow visibility.
     */
    public boolean shadeUnlitFaces = true;
    /**
     * Legacy POC field retained for scene compatibility. Authored tile associations now
     * determine alpha contributions; this flag no longer selects a product rendering path.
     */
    public boolean silhouetteShadowCaster = false;
    /**
     * Compiled as particle-occlusion metadata; the downstream particle consumer is not implemented yet.
     */
    public boolean particleOccluder = false;
    public boolean linkedTileRefsAuthored = false;
    public Array<LinkedTileRef> linkedTileRefs = new Array<>(LinkedTileRef[]::new);

    public static boolean sameGeometry(SpatialBlockData a, SpatialBlockData b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.id == b.id
                && a.structureId == b.structureId
                && Float.compare(a.x, b.x) == 0
                && Float.compare(a.y, b.y) == 0
                && Float.compare(a.width, b.width) == 0
                && Float.compare(a.depth, b.depth) == 0
                && Float.compare(a.altitude, b.altitude) == 0
                && Float.compare(a.height, b.height) == 0
                && a.actorOccluder == b.actorOccluder
                && a.lightOccluder == b.lightOccluder
                && a.shadowCaster == b.shadowCaster
                && a.shadeUnlitFaces == b.shadeUnlitFaces
                && a.silhouetteShadowCaster == b.silhouetteShadowCaster
                && a.particleOccluder == b.particleOccluder
                && sameLinkedTileRefs(a, b);
    }

    private static boolean sameLinkedTileRefs(SpatialBlockData a, SpatialBlockData b) {
        if (a.linkedTileRefsAuthored != b.linkedTileRefsAuthored) return false;
        int aSize = a.linkedTileRefs != null ? a.linkedTileRefs.size : 0;
        int bSize = b.linkedTileRefs != null ? b.linkedTileRefs.size : 0;
        if (aSize != bSize) return false;
        for (int i = 0; i < aSize; i++) {
            SpatialBlockData.LinkedTileRef ar = a.linkedTileRefs.get(i);
            SpatialBlockData.LinkedTileRef br = b.linkedTileRefs.get(i);
            if (ar == br) continue;
            if (ar == null || br == null) return false;
            if (ar.gx != br.gx || ar.gy != br.gy || ar.tileAssetId != br.tileAssetId) return false;
        }
        return true;
    }

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
        b.shadeUnlitFaces = shadeUnlitFaces;
        b.silhouetteShadowCaster = silhouetteShadowCaster;
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
