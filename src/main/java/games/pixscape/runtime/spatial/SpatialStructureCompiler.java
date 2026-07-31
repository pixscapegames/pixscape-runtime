package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;

import java.util.Arrays;

/** Strict compiler for deterministic exposed faces of one authored Spatial V3 structure. */
public final class SpatialStructureCompiler {
    private SpatialStructureCompiler() {
    }

    public static CompiledSpatialStructure compile(Array<SpatialBlockData> authoredWalls, int structureId) {
        long started = System.nanoTime();
        if (authoredWalls == null) throw failure(structureId, "authored wall collection is missing");
        if (structureId <= 0) throw failure(structureId, "structure id must be positive");

        int wallCount = countWalls(authoredWalls, structureId);
        if (wallCount == 0) throw failure(structureId, "structure is empty");
        WallInput input = collectAndValidate(authoredWalls, structureId, wallCount);
        Coordinates coordinates = compressCoordinates(input, structureId);
        FaceCompilation complete = compileFaces(input, coordinates, false);
        FaceCompilation actor = compileFaces(input, coordinates, true);
        actor = actor.withFaces(attachActorFaceAnchors(input, actor.faces));

        CompiledSpatialStructure.Diagnostics diagnostics = new CompiledSpatialStructure.Diagnostics(
                wallCount, coordinates.xCount, coordinates.yCount,
                complete.coveredCellCount, complete.rawBoundaryCount, complete.faces.faceCount(),
                actor.coveredCellCount, actor.rawBoundaryCount, actor.faces.faceCount(),
                System.nanoTime() - started);
        return new CompiledSpatialStructure(structureId,
                coordinates.x[0], coordinates.y[0],
                coordinates.x[coordinates.xCount - 1], coordinates.y[coordinates.yCount - 1],
                input.altitude, input.height, complete.faces, actor.faces, diagnostics);
    }

    private static int countWalls(Array<SpatialBlockData> authoredWalls, int structureId) {
        int count = 0;
        for (int i = 0; i < authoredWalls.size; i++) {
            SpatialBlockData wall = authoredWalls.get(i);
            if (wall != null && wall.structureId == structureId) count++;
        }
        return count;
    }

    private static WallInput collectAndValidate(Array<SpatialBlockData> authoredWalls,
                                                int structureId,
                                                int wallCount) {
        WallInput input = new WallInput(wallCount);
        SpatialWallGeometry.Bounds bounds = new SpatialWallGeometry.Bounds();
        int next = 0;
        for (int i = 0; i < authoredWalls.size; i++) {
            SpatialBlockData wall = authoredWalls.get(i);
            if (wall == null || wall.structureId != structureId) continue;
            if (wall.id <= 0) throw failure(structureId, "wall id must be positive");
            for (int previous = 0; previous < next; previous++) {
                if (input.id[previous] == wall.id) {
                    throw failure(structureId, "duplicate wall id " + wall.id);
                }
            }
            if (!SpatialWallGeometry.extractBounds(wall, bounds)) {
                throw failure(structureId, "wall " + wall.id + " has malformed footprint geometry");
            }
            if (!(bounds.maxX > bounds.minX + SpatialWallGeometry.GEOMETRY_EPSILON)
                    || !(bounds.maxY > bounds.minY + SpatialWallGeometry.GEOMETRY_EPSILON)) {
                throw failure(structureId, "wall " + wall.id + " is too thin for an exposed face");
            }
            if (!SpatialWallGeometry.isFinite(wall.altitude)
                    || !SpatialWallGeometry.isFinite(wall.height)
                    || wall.height < SpatialWallGeometry.GEOMETRY_EPSILON) {
                throw failure(structureId, "wall " + wall.id + " has malformed altitude or height");
            }
            input.id[next] = wall.id;
            input.minX[next] = bounds.minX;
            input.maxX[next] = bounds.maxX;
            input.minY[next] = bounds.minY;
            input.maxY[next] = bounds.maxY;
            input.actorOccluder[next] = wall.actorOccluder;
            input.wall[next] = wall;
            if (next == 0) {
                input.altitude = wall.altitude;
                input.height = wall.height;
            } else if (Float.compare(input.altitude, wall.altitude) != 0) {
                throw failure(structureId, "walls have mixed altitude");
            } else if (Float.compare(input.height, wall.height) != 0) {
                throw failure(structureId, "walls have mixed height");
            }
            next++;
        }
        validateTopology(input, structureId);
        return input;
    }

    private static void validateTopology(WallInput input, int structureId) {
        int count = input.id.length;
        boolean[] connected = new boolean[count * count];
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                boolean xOverlap = overlap(input.minX[first], input.maxX[first],
                        input.minX[second], input.maxX[second]);
                boolean yOverlap = overlap(input.minY[first], input.maxY[first],
                        input.minY[second], input.maxY[second]);
                if (!xOverlap || !yOverlap) continue;
                boolean duplicate = sameBounds(input, first, second);
                boolean containment = contains(input, first, second) || contains(input, second, first);
                if (duplicate || containment) {
                    throw failure(structureId, duplicate
                            ? "duplicate wall footprints are forbidden"
                            : "contained wall footprints are forbidden");
                }
                connected[first * count + second] = true;
                connected[second * count + first] = true;
            }
        }
        boolean[] visited = new boolean[count];
        int[] queue = new int[count];
        int read = 0;
        int write = 1;
        visited[0] = true;
        while (read < write) {
            int current = queue[read++];
            for (int candidate = 0; candidate < count; candidate++) {
                if (!visited[candidate] && connected[current * count + candidate]) {
                    visited[candidate] = true;
                    queue[write++] = candidate;
                }
            }
        }
        if (write != count) throw failure(structureId, "walls are disconnected");
    }

    private static Coordinates compressCoordinates(WallInput input, int structureId) {
        int count = input.id.length;
        float[] x = new float[count * 2];
        float[] y = new float[count * 2];
        for (int i = 0; i < count; i++) {
            x[i * 2] = input.minX[i];
            x[i * 2 + 1] = input.maxX[i];
            y[i * 2] = input.minY[i];
            y[i * 2 + 1] = input.maxY[i];
        }
        int xCount = sortAndCanonicalize(x);
        int yCount = sortAndCanonicalize(y);
        for (int i = 0; i < count; i++) {
            input.minXi[i] = canonicalIndex(x, xCount, input.minX[i]);
            input.maxXi[i] = canonicalIndex(x, xCount, input.maxX[i]);
            input.minYi[i] = canonicalIndex(y, yCount, input.minY[i]);
            input.maxYi[i] = canonicalIndex(y, yCount, input.maxY[i]);
            if (input.minXi[i] >= input.maxXi[i] || input.minYi[i] >= input.maxYi[i]) {
                throw failure(structureId, "epsilon canonicalization collapsed wall " + input.id[i]);
            }
        }
        return new Coordinates(x, xCount, y, yCount);
    }

    private static FaceCompilation compileFaces(WallInput input,
                                                Coordinates coordinates,
                                                boolean actorOnly) {
        int columns = coordinates.xCount - 1;
        int rows = coordinates.yCount - 1;
        boolean[] covered = new boolean[columns * rows];
        int[] difference = new int[coordinates.xCount * coordinates.yCount];
        int selectedWallCount = 0;
        for (int wall = 0; wall < input.id.length; wall++) {
            if (actorOnly && !input.actorOccluder[wall]) continue;
            addRectangle(difference, coordinates.xCount,
                    input.minXi[wall], input.minYi[wall], input.maxXi[wall], input.maxYi[wall]);
            selectedWallCount++;
        }
        if (selectedWallCount == 0) {
            return new FaceCompilation(emptyFaces(), 0, 0);
        }
        int coveredCellCount = materialize(difference, coordinates.xCount,
                coordinates.yCount, columns, covered);
        BoundaryBuilder boundary = new BoundaryBuilder(Math.max(4, coveredCellCount * 2));
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int cell = y * columns + x;
                if (!covered[cell]) continue;
                if (x == 0 || !covered[cell - 1]) {
                    boundary.add(CompiledSpatialStructure.MIN_X,
                            coordinates.x[x], coordinates.y[y], coordinates.y[y + 1]);
                }
                if (x == columns - 1 || !covered[cell + 1]) {
                    boundary.add(CompiledSpatialStructure.MAX_X,
                            coordinates.x[x + 1], coordinates.y[y], coordinates.y[y + 1]);
                }
                if (y == 0 || !covered[cell - columns]) {
                    boundary.add(CompiledSpatialStructure.MIN_Y,
                            coordinates.y[y], coordinates.x[x], coordinates.x[x + 1]);
                }
                if (y == rows - 1 || !covered[cell + columns]) {
                    boundary.add(CompiledSpatialStructure.MAX_Y,
                            coordinates.y[y + 1], coordinates.x[x], coordinates.x[x + 1]);
                }
            }
        }
        int rawBoundaryCount = boundary.size();
        boundary.sort();
        boundary.mergeCollinear();
        return new FaceCompilation(boundary.build(), coveredCellCount, rawBoundaryCount);
    }

    private static int sortAndCanonicalize(float[] values) {
        Arrays.sort(values);
        int unique = 0;
        int read = 0;
        while (read < values.length) {
            float canonical = values[read];
            values[unique++] = canonical;
            read++;
            while (read < values.length
                    && values[read] - canonical <= SpatialWallGeometry.GEOMETRY_EPSILON) read++;
        }
        return unique;
    }

    private static int canonicalIndex(float[] canonical, int count, float value) {
        int low = 0;
        int high = count - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            float coordinate = canonical[middle];
            if (value < coordinate) high = middle - 1;
            else if (value - coordinate > SpatialWallGeometry.GEOMETRY_EPSILON) low = middle + 1;
            else return middle;
        }
        throw new IllegalStateException("Canonical structure coordinate is missing.");
    }

    private static void addRectangle(int[] difference,
                                     int width,
                                     int minX,
                                     int minY,
                                     int maxXExclusive,
                                     int maxYExclusive) {
        difference[minY * width + minX]++;
        difference[minY * width + maxXExclusive]--;
        difference[maxYExclusive * width + minX]--;
        difference[maxYExclusive * width + maxXExclusive]++;
    }

    private static int materialize(int[] difference,
                                   int width,
                                   int height,
                                   int cellColumns,
                                   boolean[] output) {
        int covered = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int value = difference[index];
                if (x > 0) value += difference[index - 1];
                if (y > 0) value += difference[index - width];
                if (x > 0 && y > 0) value -= difference[index - width - 1];
                difference[index] = value;
                if (x < width - 1 && y < height - 1 && value > 0) {
                    output[y * cellColumns + x] = true;
                    covered++;
                }
            }
        }
        return covered;
    }

    private static boolean overlap(float minA, float maxA, float minB, float maxB) {
        return Math.min(maxA, maxB) - Math.max(minA, minB) > SpatialWallGeometry.GEOMETRY_EPSILON;
    }

    private static boolean sameBounds(WallInput input, int first, int second) {
        return nearlyEqual(input.minX[first], input.minX[second])
                && nearlyEqual(input.maxX[first], input.maxX[second])
                && nearlyEqual(input.minY[first], input.minY[second])
                && nearlyEqual(input.maxY[first], input.maxY[second]);
    }

    private static boolean contains(WallInput input, int outer, int inner) {
        return input.minX[outer] <= input.minX[inner] + SpatialWallGeometry.GEOMETRY_EPSILON
                && input.maxX[outer] + SpatialWallGeometry.GEOMETRY_EPSILON >= input.maxX[inner]
                && input.minY[outer] <= input.minY[inner] + SpatialWallGeometry.GEOMETRY_EPSILON
                && input.maxY[outer] + SpatialWallGeometry.GEOMETRY_EPSILON >= input.maxY[inner];
    }

    private static boolean nearlyEqual(float first, float second) {
        return Math.abs(first - second) <= SpatialWallGeometry.GEOMETRY_EPSILON;
    }

    private static IllegalArgumentException failure(int structureId, String detail) {
        return new IllegalArgumentException("Cannot compile Spatial V3 structure " + structureId + ": " + detail + ".");
    }

    private static CompiledSpatialStructure.FaceSet emptyFaces() {
        return new CompiledSpatialStructure.FaceSet(new byte[0], new float[0], new float[0], new float[0]);
    }

    private static CompiledSpatialStructure.FaceSet attachActorFaceAnchors(
            WallInput input,
            CompiledSpatialStructure.FaceSet faces) {
        int faceCount = faces.faceCount();
        int[] starts = new int[faceCount];
        int[] counts = new int[faceCount];
        CellBuilder cells = new CellBuilder(8);
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        int[] wallOrder = sortedWallOrder(input);
        for (int face = 0; face < faceCount; face++) {
            starts[face] = cells.size;
            byte orientation = faces.orientation(face);
            float line = faces.constantCoordinate(face);
            float start = faces.startCoordinate(face);
            float end = faces.endCoordinate(face);
            for (int ordered = 0; ordered < wallOrder.length; ordered++) {
                int wallIndex = wallOrder[ordered];
                if (!input.actorOccluder[wallIndex]
                        || !supportsFace(input, wallIndex, orientation, line, start, end)) continue;
                SpatialBlockData wall = input.wall[wallIndex];
                if (!SpatialWallGeometry.extractLinkedCellBounds(wall, linked)) continue;
                for (int refIndex = 0; refIndex < wall.linkedTileRefs.size; refIndex++) {
                    SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(refIndex);
                    if (ref == null || !isBoundaryCell(ref, linked, orientation)) continue;
                    float cellStart = orientation == CompiledSpatialStructure.MIN_X
                            || orientation == CompiledSpatialStructure.MAX_X ? ref.gy : ref.gx;
                    if (Math.min(end, cellStart + 1f) - Math.max(start, cellStart)
                            <= SpatialWallGeometry.GEOMETRY_EPSILON) continue;
                    cells.addUnique(ref.gx, ref.gy, starts[face]);
                }
            }
            cells.sort(starts[face], cells.size);
            counts[face] = cells.size - starts[face];
        }
        return new CompiledSpatialStructure.FaceSet(
                copyOrientations(faces), copyConstants(faces), copyStarts(faces), copyEnds(faces),
                starts, counts, Arrays.copyOf(cells.gx, cells.size), Arrays.copyOf(cells.gy, cells.size));
    }

    private static boolean supportsFace(WallInput input, int wall, byte orientation,
                                        float line, float start, float end) {
        float boundary;
        float wallStart;
        float wallEnd;
        if (orientation == CompiledSpatialStructure.MIN_X) boundary = input.minX[wall];
        else if (orientation == CompiledSpatialStructure.MAX_X) boundary = input.maxX[wall];
        else if (orientation == CompiledSpatialStructure.MIN_Y) boundary = input.minY[wall];
        else boundary = input.maxY[wall];
        if (!nearlyEqual(boundary, line)) return false;
        if (orientation == CompiledSpatialStructure.MIN_X || orientation == CompiledSpatialStructure.MAX_X) {
            wallStart = input.minY[wall];
            wallEnd = input.maxY[wall];
        } else {
            wallStart = input.minX[wall];
            wallEnd = input.maxX[wall];
        }
        return overlap(wallStart, wallEnd, start, end);
    }

    private static boolean isBoundaryCell(SpatialBlockData.LinkedTileRef ref,
                                          SpatialWallGeometry.LinkedCellBounds bounds,
                                          byte orientation) {
        if (orientation == CompiledSpatialStructure.MIN_X) return ref.gx == bounds.minGx;
        if (orientation == CompiledSpatialStructure.MAX_X) return ref.gx == bounds.maxGxExclusive - 1;
        if (orientation == CompiledSpatialStructure.MIN_Y) return ref.gy == bounds.minGy;
        return ref.gy == bounds.maxGyExclusive - 1;
    }

    private static int[] sortedWallOrder(WallInput input) {
        int[] order = new int[input.id.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = 1; i < order.length; i++) {
            int value = order[i];
            int at = i;
            while (at > 0 && input.id[order[at - 1]] > input.id[value]) {
                order[at] = order[at - 1];
                at--;
            }
            order[at] = value;
        }
        return order;
    }

    private static byte[] copyOrientations(CompiledSpatialStructure.FaceSet faces) {
        byte[] out = new byte[faces.faceCount()];
        for (int i = 0; i < out.length; i++) out[i] = faces.orientation(i);
        return out;
    }

    private static float[] copyConstants(CompiledSpatialStructure.FaceSet faces) {
        float[] out = new float[faces.faceCount()];
        for (int i = 0; i < out.length; i++) out[i] = faces.constantCoordinate(i);
        return out;
    }

    private static float[] copyStarts(CompiledSpatialStructure.FaceSet faces) {
        float[] out = new float[faces.faceCount()];
        for (int i = 0; i < out.length; i++) out[i] = faces.startCoordinate(i);
        return out;
    }

    private static float[] copyEnds(CompiledSpatialStructure.FaceSet faces) {
        float[] out = new float[faces.faceCount()];
        for (int i = 0; i < out.length; i++) out[i] = faces.endCoordinate(i);
        return out;
    }

    private static final class WallInput {
        final int[] id;
        final float[] minX;
        final float[] maxX;
        final float[] minY;
        final float[] maxY;
        final boolean[] actorOccluder;
        final SpatialBlockData[] wall;
        final int[] minXi;
        final int[] maxXi;
        final int[] minYi;
        final int[] maxYi;
        float altitude;
        float height;

        WallInput(int count) {
            id = new int[count];
            minX = new float[count];
            maxX = new float[count];
            minY = new float[count];
            maxY = new float[count];
            actorOccluder = new boolean[count];
            wall = new SpatialBlockData[count];
            minXi = new int[count];
            maxXi = new int[count];
            minYi = new int[count];
            maxYi = new int[count];
        }
    }

    private static final class Coordinates {
        final float[] x;
        final int xCount;
        final float[] y;
        final int yCount;

        Coordinates(float[] x, int xCount, float[] y, int yCount) {
            this.x = x;
            this.xCount = xCount;
            this.y = y;
            this.yCount = yCount;
        }
    }

    private static final class FaceCompilation {
        final CompiledSpatialStructure.FaceSet faces;
        final int coveredCellCount;
        final int rawBoundaryCount;

        FaceCompilation(CompiledSpatialStructure.FaceSet faces, int coveredCellCount, int rawBoundaryCount) {
            this.faces = faces;
            this.coveredCellCount = coveredCellCount;
            this.rawBoundaryCount = rawBoundaryCount;
        }

        FaceCompilation withFaces(CompiledSpatialStructure.FaceSet replacement) {
            return new FaceCompilation(replacement, coveredCellCount, rawBoundaryCount);
        }
    }

    private static final class CellBuilder {
        int size;
        int[] gx;
        int[] gy;

        CellBuilder(int capacity) {
            gx = new int[capacity];
            gy = new int[capacity];
        }

        void addUnique(int x, int y, int from) {
            for (int i = from; i < size; i++) if (gx[i] == x && gy[i] == y) return;
            ensure(size + 1);
            gx[size] = x;
            gy[size] = y;
            size++;
        }

        void sort(int from, int to) {
            for (int i = from + 1; i < to; i++) {
                int x = gx[i];
                int y = gy[i];
                int at = i;
                while (at > from && (gx[at - 1] > x || gx[at - 1] == x && gy[at - 1] > y)) {
                    gx[at] = gx[at - 1];
                    gy[at] = gy[at - 1];
                    at--;
                }
                gx[at] = x;
                gy[at] = y;
            }
        }

        private void ensure(int required) {
            if (required <= gx.length) return;
            int next = Math.max(required, gx.length * 2);
            gx = Arrays.copyOf(gx, next);
            gy = Arrays.copyOf(gy, next);
        }
    }

    private static final class BoundaryBuilder {
        private int size;
        private byte[] orientation;
        private float[] line;
        private float[] start;
        private float[] end;

        BoundaryBuilder(int capacity) {
            orientation = new byte[capacity];
            line = new float[capacity];
            start = new float[capacity];
            end = new float[capacity];
        }

        int size() { return size; }

        void add(byte faceOrientation, float constant, float faceStart, float faceEnd) {
            if (!(faceEnd > faceStart + SpatialWallGeometry.GEOMETRY_EPSILON)) return;
            ensureCapacity(size + 1);
            orientation[size] = faceOrientation;
            line[size] = constant;
            start[size] = faceStart;
            end[size] = faceEnd;
            size++;
        }

        void sort() {
            int[] order = new int[size];
            int[] temporary = new int[size];
            for (int i = 0; i < size; i++) order[i] = i;
            for (int width = 1; width < size; width <<= 1) {
                for (int left = 0; left < size; left += width << 1) {
                    int middle = Math.min(left + width, size);
                    int right = Math.min(left + (width << 1), size);
                    int first = left;
                    int second = middle;
                    int output = left;
                    while (first < middle || second < right) {
                        if (second >= right || first < middle && compare(order[first], order[second]) <= 0) {
                            temporary[output++] = order[first++];
                        } else {
                            temporary[output++] = order[second++];
                        }
                    }
                }
                int[] swap = order;
                order = temporary;
                temporary = swap;
            }
            reorder(order);
        }

        void mergeCollinear() {
            int write = 0;
            for (int read = 0; read < size; read++) {
                if (write > 0
                        && orientation[write - 1] == orientation[read]
                        && Float.compare(line[write - 1], line[read]) == 0
                        && start[read] <= end[write - 1] + SpatialWallGeometry.GEOMETRY_EPSILON) {
                    if (end[read] > end[write - 1]) end[write - 1] = end[read];
                } else {
                    if (write != read) copy(read, write);
                    write++;
                }
            }
            size = write;
        }

        CompiledSpatialStructure.FaceSet build() {
            return new CompiledSpatialStructure.FaceSet(
                    Arrays.copyOf(orientation, size), Arrays.copyOf(line, size),
                    Arrays.copyOf(start, size), Arrays.copyOf(end, size));
        }

        private int compare(int first, int second) {
            int result = orientation[first] - orientation[second];
            if (result != 0) return result;
            result = Float.compare(line[first], line[second]);
            if (result != 0) return result;
            result = Float.compare(start[first], start[second]);
            return result != 0 ? result : Float.compare(end[first], end[second]);
        }

        private void reorder(int[] order) {
            byte[] oldOrientation = orientation;
            float[] oldLine = line;
            float[] oldStart = start;
            float[] oldEnd = end;
            orientation = new byte[oldOrientation.length];
            line = new float[oldLine.length];
            start = new float[oldStart.length];
            end = new float[oldEnd.length];
            for (int i = 0; i < size; i++) {
                int source = order[i];
                orientation[i] = oldOrientation[source];
                line[i] = oldLine[source];
                start[i] = oldStart[source];
                end[i] = oldEnd[source];
            }
        }

        private void copy(int source, int target) {
            orientation[target] = orientation[source];
            line[target] = line[source];
            start[target] = start[source];
            end[target] = end[source];
        }

        private void ensureCapacity(int required) {
            if (required <= orientation.length) return;
            int next = Math.max(required, orientation.length * 2);
            orientation = Arrays.copyOf(orientation, next);
            line = Arrays.copyOf(line, next);
            start = Arrays.copyOf(start, next);
            end = Arrays.copyOf(end, next);
        }
    }
}
