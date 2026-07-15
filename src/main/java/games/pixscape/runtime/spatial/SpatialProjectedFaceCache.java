package games.pixscape.runtime.spatial;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Flat projected actor-occluder faces and canonical layer-local tile anchors. */
public final class SpatialProjectedFaceCache {

    public int faceCount;
    public int anchorCount;
    public int structureCount;
    public int[] faceStructureId = new int[0];
    public int[] faceCompiledIndex = new int[0];
    public float[] faceAltitude = new float[0];
    public float[] faceHeight = new float[0];
    public float[] screenMinX = new float[0];
    public float[] screenMaxX = new float[0];
    public float[] slope = new float[0];
    public float[] intercept = new float[0];
    public float[] inverseNormalLength = new float[0];
    public int[] faceAnchorIndexStart = new int[0];
    public int[] faceAnchorIndexCount = new int[0];
    public int[] faceAnchorIndices = new int[0];
    public float[] faceAnchorScreenMinX = new float[0];
    public float[] faceAnchorScreenMaxX = new float[0];
    public int[] anchorGx = new int[0];
    public int[] anchorGy = new int[0];
    public int[] anchorTiledRef = new int[0];
    public boolean[] anchorResolved = new boolean[0];
    public int[] anchorBeforeBucket = new int[0];
    public int[] anchorAfterBucket = new int[0];
    public int[] structureFaceStart = new int[0];
    public int[] structureFaceCount = new int[0];
    public float[] structureMinX = new float[0];
    public float[] structureMaxX = new float[0];

    private int[] rawAnchorGx = new int[0];
    private int[] rawAnchorGy = new int[0];
    private int faceAnchorIndexTotal;
    private int compiledRevision = Integer.MIN_VALUE;
    private SceneMetaRuntime.TiledProjection projection;
    private int tileWidth;
    private int tileHeight;
    private float originX;
    private float originY;
    private int revision;
    private int projectionCount;
    private final float[] endpoints = new float[4];

    public boolean ensure(SpatialCompiledLayerCache compiled, TiledMapLayerData map) {
        if (compiled == null || map == null) return false;
        if (compiledRevision == compiled.revision() && projection == map.projection
                && tileWidth == map.tileWidth && tileHeight == map.tileHeight
                && Float.compare(originX, map.originX) == 0 && Float.compare(originY, map.originY) == 0) return false;

        int faceCapacity = 0;
        int membershipCapacity = 0;
        for (int structure = 0; structure < compiled.structureCount(); structure++) {
            CompiledSpatialStructure.FaceSet set = compiled.structure(structure).actorOccluder();
            faceCapacity += set.faceCount();
            membershipCapacity += set.anchorCellTotal();
        }
        ensureFaceCapacity(faceCapacity);
        ensureMembershipCapacity(membershipCapacity);
        ensureAnchorCapacity(membershipCapacity);
        ensureStructureCapacity(compiled.structureCount());
        faceCount = 0;
        faceAnchorIndexTotal = 0;
        structureCount = compiled.structureCount();

        for (int structureIndex = 0; structureIndex < structureCount; structureIndex++) {
            CompiledSpatialStructure structure = compiled.structure(structureIndex);
            CompiledSpatialStructure.FaceSet set = structure.actorOccluder();
            structureFaceStart[structureIndex] = faceCount;
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            for (int compiledFace = 0; compiledFace < set.faceCount(); compiledFace++) {
                map.projectSpatialPoint(set.startX(compiledFace), set.startY(compiledFace),
                        structure.altitude(), endpoints, 0);
                map.projectSpatialPoint(set.endX(compiledFace), set.endY(compiledFace),
                        structure.altitude(), endpoints, 2);
                if (Math.abs(endpoints[2] - endpoints[0]) <= SpatialFaceRelationSolver.RELATION_EPSILON) continue;

                int face = faceCount++;
                faceStructureId[face] = structure.structureId();
                faceCompiledIndex[face] = compiledFace;
                faceAltitude[face] = structure.altitude();
                faceHeight[face] = structure.height();
                writeProjection(face, endpoints[0], endpoints[1], endpoints[2], endpoints[3]);
                minX = Math.min(minX, screenMinX[face]);
                maxX = Math.max(maxX, screenMaxX[face]);

                faceAnchorIndexStart[face] = faceAnchorIndexTotal;
                int sourceStart = set.anchorCellStart(compiledFace);
                int sourceCount = set.anchorCellCount(compiledFace);
                for (int local = 0; local < sourceCount; local++) {
                    int gx = set.anchorGx(sourceStart + local);
                    int gy = set.anchorGy(sourceStart + local);
                    if (!SpatialAnchoredSegmentProjection.project(set, compiledFace, gx, gy,
                            structure.altitude(), map, endpoints)) continue;
                    rawAnchorGx[faceAnchorIndexTotal] = gx;
                    rawAnchorGy[faceAnchorIndexTotal] = gy;
                    faceAnchorScreenMinX[faceAnchorIndexTotal] = endpoints[0];
                    faceAnchorScreenMaxX[faceAnchorIndexTotal] = endpoints[2];
                    faceAnchorIndexTotal++;
                }
                faceAnchorIndexCount[face] = faceAnchorIndexTotal - faceAnchorIndexStart[face];
            }
            structureFaceCount[structureIndex] = faceCount - structureFaceStart[structureIndex];
            structureMinX[structureIndex] = minX;
            structureMaxX[structureIndex] = maxX;
        }

        buildCanonicalAnchors(map);
        compiledRevision = compiled.revision();
        projection = map.projection;
        tileWidth = map.tileWidth;
        tileHeight = map.tileHeight;
        originX = map.originX;
        originY = map.originY;
        revision++;
        projectionCount++;
        return true;
    }

    public int revision() { return revision; }
    public int projectionCount() { return projectionCount; }

    private void buildCanonicalAnchors(TiledMapLayerData map) {
        for (int i = 0; i < faceAnchorIndexTotal; i++) {
            anchorGx[i] = rawAnchorGx[i];
            anchorGy[i] = rawAnchorGy[i];
        }
        sortPairs(anchorGx, anchorGy, faceAnchorIndexTotal);
        anchorCount = 0;
        for (int i = 0; i < faceAnchorIndexTotal; i++) {
            if (anchorCount == 0 || anchorGx[i] != anchorGx[anchorCount - 1]
                    || anchorGy[i] != anchorGy[anchorCount - 1]) {
                anchorGx[anchorCount] = anchorGx[i];
                anchorGy[anchorCount] = anchorGy[i];
                anchorTiledRef[anchorCount] = map.tiledRenderRefForTile(anchorGx[i], anchorGy[i]);
                anchorResolved[anchorCount] = false;
                anchorBeforeBucket[anchorCount] = -1;
                anchorAfterBucket[anchorCount] = -1;
                anchorCount++;
            }
        }
        for (int face = 0; face < faceCount; face++) {
            int start = faceAnchorIndexStart[face];
            int sourceCount = faceAnchorIndexCount[face];
            for (int local = 0; local < sourceCount; local++) {
                int source = start + local;
                faceAnchorIndices[source] = findAnchor(rawAnchorGx[source], rawAnchorGy[source]);
            }
            sortMembershipRange(start, sourceCount);
            int write = start;
            for (int local = 0; local < sourceCount; local++) {
                int source = start + local;
                int anchor = faceAnchorIndices[source];
                if (write == start || faceAnchorIndices[write - 1] != anchor) {
                    faceAnchorIndices[write] = anchor;
                    faceAnchorScreenMinX[write] = faceAnchorScreenMinX[source];
                    faceAnchorScreenMaxX[write] = faceAnchorScreenMaxX[source];
                    write++;
                }
            }
            faceAnchorIndexCount[face] = write - start;
        }
    }

    private int findAnchor(int gx, int gy) {
        int low = 0;
        int high = anchorCount - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int compare = compare(anchorGx[middle], anchorGy[middle], gx, gy);
            if (compare < 0) low = middle + 1;
            else if (compare > 0) high = middle - 1;
            else return middle;
        }
        throw new IllegalStateException("Missing canonical Spatial anchor.");
    }

    private void writeProjection(int face, float x1, float y1, float x2, float y2) {
        if (x2 < x1) {
            float swap = x1; x1 = x2; x2 = swap;
            swap = y1; y1 = y2; y2 = swap;
        }
        screenMinX[face] = x1;
        screenMaxX[face] = x2;
        slope[face] = (y2 - y1) / (x2 - x1);
        intercept[face] = y1 - slope[face] * x1;
        inverseNormalLength[face] = 1f / (float) Math.sqrt(slope[face] * slope[face] + 1f);
    }

    private static void sortPairs(int[] gx, int[] gy, int count) {
        for (int i = 1; i < count; i++) {
            int x = gx[i];
            int y = gy[i];
            int previous = i - 1;
            while (previous >= 0 && compare(gx[previous], gy[previous], x, y) > 0) {
                gx[previous + 1] = gx[previous];
                gy[previous + 1] = gy[previous];
                previous--;
            }
            gx[previous + 1] = x;
            gy[previous + 1] = y;
        }
    }

    private void sortMembershipRange(int start, int count) {
        int end = start + count;
        for (int i = start + 1; i < end; i++) {
            int value = faceAnchorIndices[i];
            float minX = faceAnchorScreenMinX[i];
            float maxX = faceAnchorScreenMaxX[i];
            int previous = i - 1;
            while (previous >= start && faceAnchorIndices[previous] > value) {
                faceAnchorIndices[previous + 1] = faceAnchorIndices[previous];
                faceAnchorScreenMinX[previous + 1] = faceAnchorScreenMinX[previous];
                faceAnchorScreenMaxX[previous + 1] = faceAnchorScreenMaxX[previous];
                previous--;
            }
            faceAnchorIndices[previous + 1] = value;
            faceAnchorScreenMinX[previous + 1] = minX;
            faceAnchorScreenMaxX[previous + 1] = maxX;
        }
    }

    private static int compare(int gx1, int gy1, int gx2, int gy2) {
        return gx1 < gx2 ? -1 : gx1 > gx2 ? 1 : gy1 < gy2 ? -1 : gy1 > gy2 ? 1 : 0;
    }

    private void ensureFaceCapacity(int required) {
        if (required <= faceStructureId.length) return;
        int next = capacity(faceStructureId.length, required);
        faceStructureId = grow(faceStructureId, next);
        faceCompiledIndex = grow(faceCompiledIndex, next);
        faceAltitude = grow(faceAltitude, next);
        faceHeight = grow(faceHeight, next);
        screenMinX = grow(screenMinX, next);
        screenMaxX = grow(screenMaxX, next);
        slope = grow(slope, next);
        intercept = grow(intercept, next);
        inverseNormalLength = grow(inverseNormalLength, next);
        faceAnchorIndexStart = grow(faceAnchorIndexStart, next);
        faceAnchorIndexCount = grow(faceAnchorIndexCount, next);
    }

    private void ensureMembershipCapacity(int required) {
        if (required <= faceAnchorIndices.length) return;
        int next = capacity(faceAnchorIndices.length, required);
        faceAnchorIndices = grow(faceAnchorIndices, next);
        faceAnchorScreenMinX = grow(faceAnchorScreenMinX, next);
        faceAnchorScreenMaxX = grow(faceAnchorScreenMaxX, next);
        rawAnchorGx = grow(rawAnchorGx, next);
        rawAnchorGy = grow(rawAnchorGy, next);
    }

    private void ensureAnchorCapacity(int required) {
        if (required <= anchorGx.length) return;
        int next = capacity(anchorGx.length, required);
        anchorGx = grow(anchorGx, next);
        anchorGy = grow(anchorGy, next);
        anchorTiledRef = grow(anchorTiledRef, next);
        anchorResolved = grow(anchorResolved, next);
        anchorBeforeBucket = grow(anchorBeforeBucket, next);
        anchorAfterBucket = grow(anchorAfterBucket, next);
    }

    private void ensureStructureCapacity(int required) {
        if (required <= structureFaceStart.length) return;
        int next = capacity(structureFaceStart.length, required);
        structureFaceStart = grow(structureFaceStart, next);
        structureFaceCount = grow(structureFaceCount, next);
        structureMinX = grow(structureMinX, next);
        structureMaxX = grow(structureMaxX, next);
    }

    private static int capacity(int current, int required) { int next=Math.max(4,current); while(next<required)next<<=1; return next; }
    private static int[] grow(int[] source,int next){int[] out=new int[next];System.arraycopy(source,0,out,0,source.length);return out;}
    private static float[] grow(float[] source,int next){float[] out=new float[next];System.arraycopy(source,0,out,0,source.length);return out;}
    private static boolean[] grow(boolean[] source,int next){boolean[] out=new boolean[next];System.arraycopy(source,0,out,0,source.length);return out;}
}
