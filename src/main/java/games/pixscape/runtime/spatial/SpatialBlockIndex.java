package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;

public final class SpatialBlockIndex {
    private final IntMap<IntArray> cells = new IntMap<>();
    private final IntSet rangeDedupe = new IntSet();
    private final SpatialBlockGeometry.CellRange tmpRange = new SpatialBlockGeometry.CellRange();

    private int layerEntity = -1;
    private int refCount;
    private int skippedNonActorOccluder;
    private int skippedZeroHeight;
    private int skippedInvalidFootprint;
    private int lastVisitedCellCount;
    private int lastVisitedEntryCount;

    private int[] refOwnerLayer = new int[0];
    private int[] refBlockIndex = new int[0];
    private int[] refBlockId = new int[0];
    private int[] refMinGx = new int[0];
    private int[] refMaxGxExclusive = new int[0];
    private int[] refMinGy = new int[0];
    private int[] refMaxGyExclusive = new int[0];
    private float[] refAltitude = new float[0];
    private float[] refHeight = new float[0];

    public void rebuild(int layerEntity, SpatialBlocksComponent blocks) {
        clear();
        this.layerEntity = layerEntity;
        if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) return;

        for (int i = 0, n = blocks.blocks.size; i < n; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            if (!acceptBlock(block)) continue;
            if (!SpatialBlockGeometry.writeCoveredCellRange(block, tmpRange)) {
                skippedInvalidFootprint++;
                continue;
            }

            int ref = addRef(layerEntity, i, block, tmpRange);
            for (int gy = tmpRange.minGy; gy < tmpRange.maxGyExclusive; gy++) {
                for (int gx = tmpRange.minGx; gx < tmpRange.maxGxExclusive; gx++) {
                    IntArray bucket = cells.get(packCell(gx, gy));
                    if (bucket == null) {
                        bucket = new IntArray(false, 4);
                        cells.put(packCell(gx, gy), bucket);
                    }
                    bucket.add(ref);
                }
            }
        }
    }

    public IntArray queryCell(int gx, int gy, IntArray out) {
        if (out == null) out = new IntArray(false, 8);
        out.clear();
        lastVisitedCellCount = 1;
        lastVisitedEntryCount = 0;

        IntArray bucket = cells.get(packCell(gx, gy));
        if (bucket == null) return out;

        lastVisitedEntryCount = bucket.size;
        out.addAll(bucket);
        return out;
    }

    public IntArray queryRange(int minGx,
                               int maxGxExclusive,
                               int minGy,
                               int maxGyExclusive,
                               IntArray out) {
        if (out == null) out = new IntArray(false, 16);
        out.clear();
        rangeDedupe.clear();
        lastVisitedCellCount = 0;
        lastVisitedEntryCount = 0;

        if (maxGxExclusive <= minGx || maxGyExclusive <= minGy) return out;

        for (int gy = minGy; gy < maxGyExclusive; gy++) {
            for (int gx = minGx; gx < maxGxExclusive; gx++) {
                lastVisitedCellCount++;
                IntArray bucket = cells.get(packCell(gx, gy));
                if (bucket == null) continue;

                lastVisitedEntryCount += bucket.size;
                for (int i = 0, n = bucket.size; i < n; i++) {
                    int ref = bucket.get(i);
                    if (rangeDedupe.add(ref)) {
                        out.add(ref);
                    }
                }
            }
        }
        return out;
    }

    public void clear() {
        cells.clear();
        rangeDedupe.clear();
        layerEntity = -1;
        refCount = 0;
        skippedNonActorOccluder = 0;
        skippedZeroHeight = 0;
        skippedInvalidFootprint = 0;
        lastVisitedCellCount = 0;
        lastVisitedEntryCount = 0;
    }

    public int getLayerEntity() {
        return layerEntity;
    }

    public int getRefCount() {
        return refCount;
    }

    public int getCellBucketCount() {
        return cells.size;
    }

    public int getSkippedNonActorOccluder() {
        return skippedNonActorOccluder;
    }

    public int getSkippedZeroHeight() {
        return skippedZeroHeight;
    }

    public int getSkippedInvalidFootprint() {
        return skippedInvalidFootprint;
    }

    public int getLastVisitedCellCount() {
        return lastVisitedCellCount;
    }

    public int getLastVisitedEntryCount() {
        return lastVisitedEntryCount;
    }

    public int getRefOwnerLayer(int ref) {
        return isValidRef(ref) ? refOwnerLayer[ref] : -1;
    }

    public int getRefBlockIndex(int ref) {
        return isValidRef(ref) ? refBlockIndex[ref] : -1;
    }

    public int getRefBlockId(int ref) {
        return isValidRef(ref) ? refBlockId[ref] : 0;
    }

    public int getRefMinGx(int ref) {
        return isValidRef(ref) ? refMinGx[ref] : 0;
    }

    public int getRefMaxGxExclusive(int ref) {
        return isValidRef(ref) ? refMaxGxExclusive[ref] : 0;
    }

    public int getRefMinGy(int ref) {
        return isValidRef(ref) ? refMinGy[ref] : 0;
    }

    public int getRefMaxGyExclusive(int ref) {
        return isValidRef(ref) ? refMaxGyExclusive[ref] : 0;
    }

    public float getRefAltitude(int ref) {
        return isValidRef(ref) ? refAltitude[ref] : 0f;
    }

    public float getRefHeight(int ref) {
        return isValidRef(ref) ? refHeight[ref] : 0f;
    }

    private boolean acceptBlock(SpatialBlockData block) {
        if (block == null) {
            skippedInvalidFootprint++;
            return false;
        }
        if (!block.actorOccluder) {
            skippedNonActorOccluder++;
            return false;
        }
        if (block.height <= 0f) {
            skippedZeroHeight++;
            return false;
        }
        if (block.width <= 0f || block.depth <= 0f) {
            skippedInvalidFootprint++;
            return false;
        }
        return true;
    }

    private int addRef(int layerEntity,
                       int blockIndex,
                       SpatialBlockData block,
                       SpatialBlockGeometry.CellRange range) {
        ensureRefCapacity(refCount + 1);

        int ref = refCount++;
        refOwnerLayer[ref] = layerEntity;
        refBlockIndex[ref] = blockIndex;
        refBlockId[ref] = block.id;
        refMinGx[ref] = range.minGx;
        refMaxGxExclusive[ref] = range.maxGxExclusive;
        refMinGy[ref] = range.minGy;
        refMaxGyExclusive[ref] = range.maxGyExclusive;
        refAltitude[ref] = block.altitude;
        refHeight[ref] = block.height;
        return ref;
    }

    private void ensureRefCapacity(int required) {
        if (required <= refOwnerLayer.length) return;

        int next = Math.max(8, refOwnerLayer.length);
        while (required > next) next <<= 1;

        refOwnerLayer = grow(refOwnerLayer, next);
        refBlockIndex = grow(refBlockIndex, next);
        refBlockId = grow(refBlockId, next);
        refMinGx = grow(refMinGx, next);
        refMaxGxExclusive = grow(refMaxGxExclusive, next);
        refMinGy = grow(refMinGy, next);
        refMaxGyExclusive = grow(refMaxGyExclusive, next);
        refAltitude = grow(refAltitude, next);
        refHeight = grow(refHeight, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static float[] grow(float[] source, int next) {
        float[] expanded = new float[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private boolean isValidRef(int ref) {
        return ref >= 0 && ref < refCount;
    }

    private static int packCell(int gx, int gy) {
        return (gx << 16) ^ (gy & 0xFFFF);
    }
}
