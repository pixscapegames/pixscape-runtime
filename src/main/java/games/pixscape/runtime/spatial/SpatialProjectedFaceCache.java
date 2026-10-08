package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.FloatArray;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import java.util.Arrays;

/** Flat projected actor-occluder faces and canonical map-local tile anchors. */
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
    /** Derived reverse index. Membership indices refer to faceAnchorIndices. */
    public int[] anchorMembershipHead = new int[0];
    public int[] membershipNext = new int[0];
    public int[] membershipFace = new int[0];
    /** Direct authored-wall junction witnesses, indexed by the FRONT membership. */
    int[] membershipJunctionStart = new int[0];
    int[] membershipJunctionCount = new int[0];
    int[] junctionFace = new int[0];
    float[] junctionMinX = new float[0], junctionMaxX = new float[0];
    /** Real lateral endpoint pairs, indexed by their shared tile (never T continuations). */
    int[] anchorCornerHead = new int[0];
    int[] cornerNext = new int[0];
    int[] cornerMembershipA = new int[0];
    int[] cornerMembershipB = new int[0];
    private int cornerCount;
    private long[] faceMinVertex = new long[0];
    private long[] faceMaxVertex = new long[0];
    private byte[] faceOrientation = new byte[0];
    public int[] structureFaceStart = new int[0];
    public int[] structureFaceCount = new int[0];
    public float[] structureMinX = new float[0];
    public float[] structureMaxX = new float[0];

    private final LongMap<Integer> anchorByCell = new LongMap<>();

    private int[] rawAnchorGx = new int[0];
    private int[] rawAnchorGy = new int[0];
    private int faceAnchorIndexTotal;
    private int compiledRevision = Integer.MIN_VALUE;
    private TiledProjection projection;
    private int tileWidth;
    private int tileHeight;
    private float originX;
    private float originY;
    private float planeAltitude;
    private int revision;
    private int projectionCount;
    private final float[] endpoints = new float[4];

    public boolean ensure(SpatialCompiledLayerCache compiled, TiledMapLayerData map) {
        if (compiled == null || map == null) return false;
        if (compiledRevision == compiled.revision() && projection == map.projection
                && tileWidth == map.tileWidth && tileHeight == map.tileHeight
                && Float.compare(originX, map.originX) == 0 && Float.compare(originY, map.originY) == 0
                && Float.compare(planeAltitude, map.defaultTileAltitude) == 0) return false;

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
                faceOrientation[face] = set.orientation(compiledFace);
                long startVertex = vertexKey(set.startX(compiledFace), set.startY(compiledFace));
                long endVertex = vertexKey(set.endX(compiledFace), set.endY(compiledFace));
                faceMinVertex[face] = endpoints[0] < endpoints[2] ? startVertex : endVertex;
                faceMaxVertex[face] = endpoints[0] < endpoints[2] ? endVertex : startVertex;
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
        buildReverseMemberships();
        buildJunctions(compiled.source(), map);
        buildLateralCorners();
        compiledRevision = compiled.revision();
        projection = map.projection;
        tileWidth = map.tileWidth;
        tileHeight = map.tileHeight;
        originX = map.originX;
        originY = map.originY;
        planeAltitude = map.defaultTileAltitude;
        revision++;
        projectionCount++;
        return true;
    }

    public int revision() { return revision; }
    public int projectionCount() { return projectionCount; }

    /**
     * Cold-only provenance compilation within this map owner. Direct authored walls must
     * overlap in cell space and supply orthogonal faces of the same structure. Membership
     * clipping retains the authored portion even when straight compiled faces are merged.
     * The published witness bounds are projected X intervals, not a visibility test.
     * Indirect structure connectivity is deliberately excluded.
     */
    private void buildJunctions(SpatialBlocksComponent source, TiledMapLayerData map) {
        membershipJunctionStart = new int[faceAnchorIndexTotal];
        membershipJunctionCount = new int[faceAnchorIndexTotal];
        IntArray neighbors = new IntArray();
        FloatArray minimum = new FloatArray(), maximum = new FloatArray();
        if (source == null) { junctionFace = new int[0]; junctionMinX = junctionMaxX = new float[0]; return; }
        float[] local = new float[4];
        for (int f = 0; f < faceCount; f++) {
            int end = faceAnchorIndexStart[f] + faceAnchorIndexCount[f];
            for (int m = faceAnchorIndexStart[f]; m < end; m++) {
                membershipJunctionStart[m] = neighbors.size;
                int anchor = faceAnchorIndices[m];
                for (int i = 0; i < source.blocks.size; i++) {
                    SpatialBlockData a = source.blocks.get(i);
                    if (!supportsWall(f, a)) continue;
                    // Clip provenance to the authored portion represented by this membership.
                    boolean vertical = faceOrientation[f] < CompiledSpatialStructure.MIN_Y;
                    float lo = vertical ? Math.max(a.y, anchorGy[anchor]) : Math.max(a.x, anchorGx[anchor]);
                    float hi = vertical ? Math.min(a.y + a.depth, anchorGy[anchor] + 1f)
                            : Math.min(a.x + a.width, anchorGx[anchor] + 1f);
                    if (!(hi > lo)) continue;
                    float constant = Float.intBitsToFloat((int) (vertical ? faceMinVertex[f] >>> 32 : faceMinVertex[f]));
                    map.projectSpatialPoint(vertical ? constant : lo, vertical ? lo : constant, faceAltitude[f], local, 0);
                    map.projectSpatialPoint(vertical ? constant : hi, vertical ? hi : constant, faceAltitude[f], local, 2);
                    if (Math.max(local[0], local[2]) < faceAnchorScreenMinX[m] - SpatialLineRelation.EPSILON
                            || Math.min(local[0], local[2]) > faceAnchorScreenMaxX[m] + SpatialLineRelation.EPSILON) continue;
                    for (int j = 0; j < source.blocks.size; j++) {
                        SpatialBlockData b = source.blocks.get(j);
                        if (i == j || b == null || !b.actorOccluder || b.structureId != a.structureId
                                || Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x) <= SpatialWallGeometry.GEOMETRY_EPSILON
                                || Math.min(a.y + a.depth, b.y + b.depth) - Math.max(a.y, b.y) <= SpatialWallGeometry.GEOMETRY_EPSILON) continue;
                        for (int other = 0; other < faceCount; other++) {
                            if ((faceOrientation[f] < CompiledSpatialStructure.MIN_Y)
                                    == (faceOrientation[other] < CompiledSpatialStructure.MIN_Y)) continue;
                            if (!supportsWall(other, b)) continue;
                            boolean bv = faceOrientation[other] < CompiledSpatialStructure.MIN_Y;
                            float x1 = Float.intBitsToFloat((int) (faceMinVertex[other] >>> 32));
                            float y1 = Float.intBitsToFloat((int) faceMinVertex[other]);
                            float x2 = Float.intBitsToFloat((int) (faceMaxVertex[other] >>> 32));
                            float y2 = Float.intBitsToFloat((int) faceMaxVertex[other]);
                            float lower = bv ? Math.max(Math.min(y1,y2), b.y) : Math.max(Math.min(x1,x2), b.x);
                            float upper = bv ? Math.min(Math.max(y1,y2), b.y+b.depth) : Math.min(Math.max(x1,x2), b.x+b.width);
                            map.projectSpatialPoint(bv ? x1 : lower, bv ? lower : y1, faceAltitude[other], local, 0);
                            map.projectSpatialPoint(bv ? x1 : upper, bv ? upper : y1, faceAltitude[other], local, 2);
                            float min = Math.min(local[0],local[2]), max = Math.max(local[0],local[2]);
                            boolean duplicate = false;
                            for(int p=membershipJunctionStart[m];p<neighbors.size;p++)
                                if(neighbors.get(p)==other && minimum.get(p)==min && maximum.get(p)==max) { duplicate=true; break; }
                            if(!duplicate) { neighbors.add(other); minimum.add(min); maximum.add(max); }
                        }
                    }
                }
                membershipJunctionCount[m] = neighbors.size - membershipJunctionStart[m];
            }
        }
        junctionFace = neighbors.toArray();
        junctionMinX = minimum.toArray(); junctionMaxX = maximum.toArray();
    }

    private boolean supportsWall(int f, SpatialBlockData wall) {
        if (wall == null || !wall.actorOccluder || wall.structureId != faceStructureId[f]) return false;
        float x1 = Float.intBitsToFloat((int) (faceMinVertex[f] >>> 32));
        float y1 = Float.intBitsToFloat((int) faceMinVertex[f]);
        float x2 = Float.intBitsToFloat((int) (faceMaxVertex[f] >>> 32));
        float y2 = Float.intBitsToFloat((int) faceMaxVertex[f]);
        float epsilon = SpatialWallGeometry.GEOMETRY_EPSILON;
        if (faceOrientation[f] < CompiledSpatialStructure.MIN_Y) {
            float edge = faceOrientation[f] == CompiledSpatialStructure.MIN_X ? wall.x : wall.x + wall.width;
            return Math.abs(x1 - edge) <= epsilon
                    && Math.min(Math.max(y1, y2), wall.y + wall.depth) > Math.max(Math.min(y1, y2), wall.y) + epsilon;
        }
        float edge = faceOrientation[f] == CompiledSpatialStructure.MIN_Y ? wall.y : wall.y + wall.depth;
        return Math.abs(y1 - edge) <= epsilon
                && Math.min(Math.max(x1, x2), wall.x + wall.width) > Math.max(Math.min(x1, x2), wall.x) + epsilon;
    }

    private void buildCanonicalAnchors(TiledMapLayerData map) {
        for (int i = 0; i < faceAnchorIndexTotal; i++) {
            anchorGx[i] = rawAnchorGx[i];
            anchorGy[i] = rawAnchorGy[i];
        }
        sortPairs(anchorGx, anchorGy, faceAnchorIndexTotal);
        anchorCount = 0;
        anchorByCell.clear();
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
        rebuildAnchorIndex();
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
        int anchor = anchorForCell(gx, gy);
        if (anchor >= 0) return anchor;
        throw new IllegalStateException("Missing canonical Spatial anchor.");
    }

    /** Complete derived index, rebuilt with projection/membership publication. */
    public int anchorForCell(int gx, int gy) {
        Integer anchor = anchorByCell.get(cellKey(gx, gy));
        return anchor != null ? anchor : -1;
    }

    private static long cellKey(int gx, int gy) {
        return ((long) gx << 32) | (gy & 0xffffffffL);
    }

    void rebuildAnchorIndex() {
        anchorByCell.clear();
        for (int anchor = 0; anchor < anchorCount; anchor++) {
            long key = cellKey(anchorGx[anchor], anchorGy[anchor]);
            if (anchorByCell.containsKey(key)) {
                throw new IllegalStateException("Duplicate canonical Spatial cell anchor.");
            }
            anchorByCell.put(key, anchor);
        }
    }

    private void buildReverseMemberships() {
        ensureAnchorCapacity(anchorCount);
        if (membershipNext.length < faceAnchorIndexTotal) {
            int next = capacity(membershipNext.length, faceAnchorIndexTotal);
            membershipNext = grow(membershipNext, next);
            membershipFace = grow(membershipFace, next);
        }
        Arrays.fill(anchorMembershipHead, 0, anchorCount, -1);
        for (int face = 0; face < faceCount; face++) {
            int end = faceAnchorIndexStart[face] + faceAnchorIndexCount[face];
            for (int membership = faceAnchorIndexStart[face]; membership < end; membership++) {
                int anchor = faceAnchorIndices[membership];
                membershipFace[membership] = face;
                membershipNext[membership] = anchorMembershipHead[anchor];
                anchorMembershipHead[anchor] = membership;
            }
        }
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

    private static long vertexKey(float x, float y) {
        return ((long) Float.floatToIntBits(x == 0f ? 0f : x) << 32)
                | (Float.floatToIntBits(y == 0f ? 0f : y) & 0xffffffffL);
    }

    /** Cold rebuild: index exact compiled vertices; intersect sorted memberships linearly. */
    private void buildLateralCorners() {
        if (anchorCornerHead.length < anchorCount) anchorCornerHead = new int[capacity(anchorCornerHead.length, anchorCount)];
        Arrays.fill(anchorCornerHead, 0, anchorCount, -1);
        cornerCount = 0;
        LongMap<Integer> starts = new LongMap<>(), ends = new LongMap<>();
        LongMap<Boolean> continuations = new LongMap<>();
        for (int structure = 0; structure < structureCount; structure++) {
            starts.clear(); ends.clear(); continuations.clear();
            int end = structureFaceStart[structure] + structureFaceCount[structure];
            for (int face = structureFaceStart[structure]; face < end; face++) {
                int me = faceAnchorIndexStart[face] + faceAnchorIndexCount[face];
                for (int m = faceAnchorIndexStart[face]; m < me; m++) {
                    // A wall crossing both sides of this cell makes this a continuation/T,
                    // even if another exterior pair happens to form a notch there.
                    if (screenMinX[face] < faceAnchorScreenMinX[m]
                            && screenMaxX[face] > faceAnchorScreenMaxX[m])
                        continuations.put(faceAnchorIndices[m], Boolean.TRUE);
                }
            }
            for (int face = structureFaceStart[structure]; face < end; face++) {
                Integer a = starts.put(faceMinVertex[face], face);
                Integer b = ends.put(faceMaxVertex[face], face);
                if (a != null) addLateralCorner(a, face, 1, continuations);
                if (b != null) addLateralCorner(b, face, -1, continuations);
            }
        }
    }

    private void addLateralCorner(int a, int b, int opening, LongMap<Boolean> continuations) {
        if (slope[a] * slope[b] >= 0f) return;
        // Concave opening between two walls, not the convex end cap of one rectangle.
        byte vertical = opening < 0 ? CompiledSpatialStructure.MIN_X : CompiledSpatialStructure.MAX_X;
        byte horizontal = opening < 0 ? CompiledSpatialStructure.MAX_Y : CompiledSpatialStructure.MIN_Y;
        if (!(faceOrientation[a] == vertical && faceOrientation[b] == horizontal
                || faceOrientation[b] == vertical && faceOrientation[a] == horizontal)) return;
        int ma = faceAnchorIndexStart[a], mb = faceAnchorIndexStart[b];
        int ea = ma + faceAnchorIndexCount[a], eb = mb + faceAnchorIndexCount[b];
        while (ma < ea && mb < eb) {
            int aa = faceAnchorIndices[ma], ab = faceAnchorIndices[mb];
            if (aa < ab) { ma++; continue; }
            if (ab < aa) { mb++; continue; }
            if (continuations.containsKey(aa)) { ma++; mb++; continue; }
            float vertexX = opening < 0 ? screenMaxX[a] : screenMinX[a];
            if (vertexX < faceAnchorScreenMinX[ma] || vertexX > faceAnchorScreenMaxX[ma]
                    || vertexX < faceAnchorScreenMinX[mb] || vertexX > faceAnchorScreenMaxX[mb]) {
                ma++; mb++; continue;
            }
            if (cornerCount == cornerNext.length) {
                int n = capacity(cornerCount, cornerCount + 1);
                cornerNext = grow(cornerNext, n); cornerMembershipA = grow(cornerMembershipA, n);
                cornerMembershipB = grow(cornerMembershipB, n);
            }
            cornerMembershipA[cornerCount] = ma++;
            cornerMembershipB[cornerCount] = mb++;
            cornerNext[cornerCount] = anchorCornerHead[aa];
            anchorCornerHead[aa] = cornerCount++;
        }
    }

    boolean isLocalLateralSector(int corner, float x, float y, float radius) {
        int ma = cornerMembershipA[corner], mb = cornerMembershipB[corner];
        if (x + radius < Math.max(faceAnchorScreenMinX[ma], faceAnchorScreenMinX[mb]) - SpatialLineRelation.EPSILON
                || x - radius > Math.min(faceAnchorScreenMaxX[ma], faceAnchorScreenMaxX[mb]) + SpatialLineRelation.EPSILON) return false;
        int a = membershipFace[ma], b = membershipFace[mb];
        boolean opensWest = faceMaxVertex[a] == faceMaxVertex[b];
        float vertexX = opensWest ? screenMaxX[a] : screenMinX[a];
        if (opensWest ? x >= vertexX : x <= vertexX) return false;
        float ay = slope[a] * x + intercept[a], by = slope[b] * x + intercept[b];
        return y > Math.min(ay, by) + SpatialLineRelation.EPSILON
                && y < Math.max(ay, by) - SpatialLineRelation.EPSILON;
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
        faceMinVertex = new long[next]; faceMaxVertex = new long[next];
        faceOrientation = new byte[next];
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
        anchorMembershipHead = grow(anchorMembershipHead, next);
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
