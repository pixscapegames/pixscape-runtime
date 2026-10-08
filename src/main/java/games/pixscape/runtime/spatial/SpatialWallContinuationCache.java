package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Prepared local wall continuations; authored volumes and tile identities stay separate. */
public final class SpatialWallContinuationCache {
    private SpatialLayerFaceRuntime[] sources = new SpatialLayerFaceRuntime[0];
    private int[] revisions = new int[0], layers = new int[0], faceOffsets = new int[0];
    private int mapCount, faceCount, rebuildCount;
    private Reference[][] references = new Reference[0][];
    private int[] stamps = new int[0];
    private byte[] types = new byte[0];
    private int frameStamp;
    private float[] displayOffsetX, displayOffsetY;

    /** Rebuilds only when owners, projected geometry, or owning display layers change. */
    public boolean ensure(SpatialLayerFaceRuntime[] runtimes, TiledMapLayerData[] maps,
                          int[] displayLayers, int count) {
        boolean changed = count != mapCount;
        for (int i = 0; i < count && !changed; i++)
            changed = sources[i] != runtimes[i] || revisions[i] != runtimes[i].projected.revision()
                    || layers[i] != displayLayers[i];
        if (!changed) return false;
        sources = Arrays.copyOf(runtimes, count);
        revisions = new int[count]; layers = Arrays.copyOf(displayLayers, count);
        faceOffsets = new int[count]; mapCount = count; faceCount = 0;
        Array<Face> faces = new Array<Face>();
        Array<Wall> walls = new Array<Wall>();
        HashMap<Edge, Array<Wall>> edges = new HashMap<Edge, Array<Wall>>();
        for (int m = 0; m < count; m++) {
            SpatialLayerFaceRuntime runtime = runtimes[m]; TiledMapLayerData map = maps[m];
            SpatialProjectedFaceCache projected = runtime.projected;
            revisions[m] = projected.revision(); faceOffsets[m] = faceCount;
            double shiftX = map.projection == TiledProjection.ISO
                    ? (double) map.originX / map.tileWidth + ((double) map.originY - map.defaultTileAltitude) / map.tileHeight
                    : (double) map.originX / map.tileWidth;
            double shiftY = map.projection == TiledProjection.ISO
                    ? -(double) map.originX / map.tileWidth + ((double) map.originY - map.defaultTileAltitude) / map.tileHeight
                    : ((double) map.originY - map.defaultTileAltitude) / map.tileHeight;
            IntMap<Array<Wall>> byStructure = new IntMap<Array<Wall>>();
            for (int b = 0; b < runtime.compiled.source().blocks.size; b++) {
                SpatialBlockData block = runtime.compiled.source().blocks.get(b);
                if (!block.actorOccluder) continue;
                Wall wall = new Wall(m, block, shiftX, shiftY);
                walls.add(wall);
                Array<Wall> group = byStructure.get(block.structureId);
                if (group == null) { group = new Array<Wall>(); byStructure.put(block.structureId, group); }
                group.add(wall);
                // Indexed exact boundary joins, not an all-block pair traversal.
                index(edges, wall, wall.crossMin); index(edges, wall, wall.crossMax);
            }
            for (int s = 0; s < runtime.compiled.structureCount(); s++) {
                CompiledSpatialStructure structure = runtime.compiled.structure(s);
                CompiledSpatialStructure.FaceSet set = structure.actorOccluder();
                Array<Wall> group = byStructure.get(structure.structureId());
                int end = projected.structureFaceStart[s] + projected.structureFaceCount[s];
                for (int f = projected.structureFaceStart[s]; f < end; f++) {
                    int compiledFace = projected.faceCompiledIndex[f];
                    int orientation = set.orientation(compiledFace), axis = orientation / 2;
                    double shift = axis == 0 ? shiftX : shiftY, alongShift = axis == 0 ? shiftY : shiftX;
                    Face face = new Face(m, f, faceCount++, orientation,
                            set.constantCoordinate(compiledFace) + shift,
                            set.startCoordinate(compiledFace) + alongShift,
                            set.endCoordinate(compiledFace) + alongShift);
                    faces.add(face);
                    if (group == null) continue;
                    for (int b = 0; b < group.size; b++) {
                        Wall wall = group.get(b);
                        // Only longitudinal faces; no end-cap or orthogonal-junction propagation.
                        if (wall.axis != axis || face.constant != ((orientation & 1) == 0 ? wall.crossMin : wall.crossMax)) continue;
                        if (Math.min(face.end, wall.end) > Math.max(face.start, wall.start)) wall.faces.add(face);
                    }
                }
            }
        }
        for (int u = 0; u < walls.size; u++) {
            Wall upper = walls.get(u);
            connect(upper, edges.get(new Edge(upper.axis, upper.crossMin)), maps, true);
            connect(upper, edges.get(new Edge(upper.axis, upper.crossMax)), maps, false);
        }
        references = new Reference[faceCount][];
        for (int f = 0; f < faces.size; f++) flatten(faces.get(f), faces, maps);
        rebuildCount++;
        return true;
    }

    private static void index(HashMap<Edge, Array<Wall>> index, Wall wall, double coordinate) {
        Edge key = new Edge(wall.axis, coordinate); Array<Wall> bucket = index.get(key);
        if (bucket == null) { bucket = new Array<Wall>(); index.put(key, bucket); }
        bucket.add(wall);
    }

    private void connect(Wall upper, Array<Wall> candidates, TiledMapLayerData[] maps, boolean firstEdge) {
        if (candidates == null) return;
        for (int i = 0; i < candidates.size; i++) {
            Wall lower = candidates.get(i);
            TiledMapLayerData a = maps[lower.map], b = maps[upper.map];
            if (lower.map == upper.map
                    || a.projection != b.projection || a.tileWidth != b.tileWidth || a.tileHeight != b.tileHeight
                    || !(lower.base < upper.base && upper.base <= lower.top)) continue;
            boolean sameStrip = upper.crossMin == lower.crossMin && upper.crossMax == lower.crossMax;
            boolean touchingStrip = upper.crossMin == lower.crossMax || upper.crossMax == lower.crossMin;
            if ((!sameStrip && !touchingStrip) || sameStrip && !firstEdge) continue;
            double start = Math.max(upper.start, lower.start), end = Math.min(upper.end, lower.end);
            if (!(end > start)) continue;
            for (int uf = 0; uf < upper.faces.size; uf++) for (int lf = 0; lf < lower.faces.size; lf++) {
                Face high = upper.faces.get(uf), low = lower.faces.get(lf);
                if (high.orientation != low.orientation) continue;
                double lo = Math.max(start, Math.max(high.start, low.start));
                double hi = Math.min(end, Math.min(high.end, low.end));
                if (hi > lo) high.parents.add(new Portion(low.global, lo, hi));
            }
        }
    }

    private void flatten(Face face, Array<Face> faces, TiledMapLayerData[] maps) {
        if (references[face.global] != null) return;
        HashSet<Portion> unique = new HashSet<Portion>();
        for (int i = 0; i < face.parents.size; i++) {
            Portion direct = face.parents.get(i); Face parent = faces.get(direct.face);
            flatten(parent, faces, maps);
            if (unique.add(direct)) face.ancestors.add(direct);
            for (int r = 0; r < parent.ancestors.size; r++) {
                Portion ancestor = parent.ancestors.get(r);
                double lo = Math.max(direct.start, ancestor.start), hi = Math.min(direct.end, ancestor.end);
                if (hi > lo) {
                    Portion inherited = new Portion(ancestor.face, lo, hi);
                    if (unique.add(inherited)) face.ancestors.add(inherited);
                }
            }
        }
        Reference[] output = new Reference[face.ancestors.size];
        for (int i = 0; i < output.length; i++) {
            Portion portion = face.ancestors.get(i); Face source = faces.get(portion.face);
            SpatialProjectedFaceCache projected = sources[source.map].projected;
            // X is altitude-independent. Recover the finite common portion on the reference line.
            TiledMapLayerData map = maps[source.map];
            double shiftX = map.projection == TiledProjection.ISO
                    ? (double) map.originX / map.tileWidth + ((double) map.originY - map.defaultTileAltitude) / map.tileHeight
                    : (double) map.originX / map.tileWidth;
            double shiftY = map.projection == TiledProjection.ISO
                    ? -(double) map.originX / map.tileWidth + ((double) map.originY - map.defaultTileAltitude) / map.tileHeight
                    : ((double) map.originY - map.defaultTileAltitude) / map.tileHeight;
            int axis = source.orientation / 2;
            float[] xy = new float[4];
            map.projectSpatialPoint((float) (axis == 0 ? source.constant - shiftX : portion.start - shiftX),
                    (float) (axis == 0 ? portion.start - shiftY : source.constant - shiftY), projected.faceAltitude[source.face], xy, 0);
            map.projectSpatialPoint((float) (axis == 0 ? source.constant - shiftX : portion.end - shiftX),
                    (float) (axis == 0 ? portion.end - shiftY : source.constant - shiftY), projected.faceAltitude[source.face], xy, 2);
            output[i] = new Reference(source.map, source.global, projected.faceAltitude[source.face],
                    projected.faceAltitude[source.face] + projected.faceHeight[source.face], Math.min(xy[0], xy[2]), Math.max(xy[0], xy[2]));
        }
        references[face.global] = output;
    }

    /** Records only retained, locally qualified native witnesses; no invisible lower tile is invented. */
    public void capture(SpatialLayerFaceRuntime[] runtimes, int actorCount) {
        capture(runtimes, actorCount, null, null);
    }

    public void capture(SpatialLayerFaceRuntime[] runtimes, int actorCount,
                        float[] offsetX, float[] offsetY) {
        displayOffsetX = offsetX;
        displayOffsetY = offsetY;
        int required = faceCount * actorCount;
        if (stamps.length < required) { int n = Math.max(required, Math.max(8, stamps.length * 2)); stamps = new int[n]; types = new byte[n]; }
        if (frameStamp == Integer.MAX_VALUE) { Arrays.fill(stamps, 0); frameStamp = 0; }
        int stamp = ++frameStamp;
        for (int m = 0; m < mapCount; m++) {
            SpatialFaceRelationSolver solver = runtimes[m].relations;
            for (int actor = 0; actor < actorCount; actor++) {
                int end = solver.actorRelationStart[actor] + solver.actorRelationCount[actor];
                for (int r = solver.actorRelationStart[actor]; r < end; r++) {
                    int key = actor * faceCount + faceOffsets[m] + solver.relationFaceIndex[r];
                    byte type = solver.relationType[r];
                    if (stamps[key] != stamp) { stamps[key] = stamp; types[key] = type; }
                    else if (types[key] != type) types[key] = 3;
                }
            }
        }
    }

    byte resolve(int map, int face, int actor, float x, float radius, float bottom, byte nativeType) {
        if (sources[map].projected.faceAltitude[face] <= bottom) return nativeType;
        Reference[] candidates = references[faceOffsets[map] + face];
        float nearestBase = Float.NEGATIVE_INFINITY; byte relation = 0;
        for (int i = 0; i < candidates.length; i++) {
            Reference ref = candidates[i]; int key = actor * faceCount + ref.face;
            // Layer numbers may differ, but witnesses must share the actual display reference.
            if (displayOffsetX != null && (displayOffsetX[ref.map] != displayOffsetX[map]
                    || displayOffsetY[ref.map] != displayOffsetY[map])) continue;
            if (!(ref.base <= bottom && bottom < ref.top) || stamps[key] != frameStamp
                    || x + radius < ref.minX - SpatialLineRelation.EPSILON || x - radius > ref.maxX + SpatialLineRelation.EPSILON) continue;
            if (ref.base > nearestBase) { nearestBase = ref.base; relation = types[key]; }
            else if (ref.base == nearestBase && relation != types[key]) relation = 3;
        }
        return relation == 1 || relation == 2 ? relation : nativeType;
    }

    public int rebuildCount() { return rebuildCount; }
    int referenceCount(int map, int face) { return references[faceOffsets[map] + face].length; }

    private static final class Edge {
        final int axis; final double coordinate;
        Edge(int axis, double coordinate) { this.axis = axis; this.coordinate = coordinate == 0 ? 0 : coordinate; }
        @Override public int hashCode() { long bits = Double.doubleToLongBits(coordinate); return 31 * axis + (int) (bits ^ (bits >>> 32)); }
        @Override public boolean equals(Object other) { return other instanceof Edge && axis == ((Edge) other).axis && coordinate == ((Edge) other).coordinate; }
    }
    private static final class Wall {
        final int map, axis; final double crossMin, crossMax, start, end; final float base, top;
        final Array<Face> faces = new Array<Face>();
        Wall(int map, SpatialBlockData block, double shiftX, double shiftY) {
            this.map = map; axis = block.depth > block.width ? 0 : 1;
            crossMin = axis == 0 ? block.x + shiftX : block.y + shiftY;
            crossMax = axis == 0 ? (block.x + block.width) + shiftX : (block.y + block.depth) + shiftY;
            start = axis == 0 ? block.y + shiftY : block.x + shiftX;
            end = axis == 0 ? (block.y + block.depth) + shiftY : (block.x + block.width) + shiftX;
            base = block.altitude; top = block.altitude + block.height;
        }
    }
    private static final class Face {
        final int map, face, global, orientation; final double constant, start, end;
        final Array<Portion> parents = new Array<Portion>(), ancestors = new Array<Portion>();
        Face(int map, int face, int global, int orientation, double constant, double start, double end) {
            this.map = map; this.face = face; this.global = global; this.orientation = orientation;
            this.constant = constant; this.start = start; this.end = end;
        }
    }
    private static final class Portion {
        final int face; final double start, end;
        Portion(int face, double start, double end) { this.face = face; this.start = start == 0 ? 0 : start; this.end = end == 0 ? 0 : end; }
        @Override public int hashCode() { long a = Double.doubleToLongBits(start), b = Double.doubleToLongBits(end); return 31 * face + (int) (a ^ (a >>> 32) ^ b ^ (b >>> 32)); }
        @Override public boolean equals(Object other) { if (!(other instanceof Portion)) return false; Portion p = (Portion) other; return face == p.face && start == p.start && end == p.end; }
    }
    private static final class Reference {
        final int map, face; final float base, top, minX, maxX;
        Reference(int map, int face, float base, float top, float minX, float maxX) {
            this.map = map; this.face = face; this.base = base; this.top = top; this.minX = minX; this.maxX = maxX;
        }
    }
}
