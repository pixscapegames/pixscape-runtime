package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;

import java.util.Arrays;

/** Compiles authored rectangles into the deterministic exposed boundary of one structure. */
public final class SpatialStructureCompiler {
    private static final byte HORIZONTAL = 0;
    private static final byte VERTICAL = 1;

    private SpatialStructureCompiler() {
    }

    public static CompiledSpatialStructure compile(Array<SpatialBlockData> authoredWalls, int structureId) {
        if (authoredWalls == null || structureId <= 0) return empty(structureId);

        int wallCount = 0;
        for (int i = 0; i < authoredWalls.size; i++) {
            SpatialBlockData wall = authoredWalls.get(i);
            if (wall != null && wall.structureId == structureId) wallCount++;
        }
        if (wallCount == 0) return empty(structureId);

        float[] minX = new float[wallCount];
        float[] maxX = new float[wallCount];
        float[] minY = new float[wallCount];
        float[] maxY = new float[wallCount];
        SpatialBlockData[] walls = new SpatialBlockData[wallCount];
        float[] xs = new float[wallCount * 2];
        float[] ys = new float[wallCount * 2];
        SpatialWallGeometry.Bounds bounds = new SpatialWallGeometry.Bounds();
        int next = 0;
        for (int i = 0; i < authoredWalls.size; i++) {
            SpatialBlockData wall = authoredWalls.get(i);
            if (wall == null || wall.structureId != structureId) continue;
            if (!SpatialWallGeometry.extractBounds(wall, bounds)) {
                throw new IllegalArgumentException("Cannot compile malformed authored wall " + wall.id + ".");
            }
            walls[next] = wall;
            minX[next] = bounds.minX;
            maxX[next] = bounds.maxX;
            minY[next] = bounds.minY;
            maxY[next] = bounds.maxY;
            xs[next * 2] = bounds.minX;
            xs[next * 2 + 1] = bounds.maxX;
            ys[next * 2] = bounds.minY;
            ys[next * 2 + 1] = bounds.maxY;
            next++;
        }
        float structureAltitude = walls[0].altitude;
        float structureHeight = walls[0].height;
        for (int i = 1; i < wallCount; i++) {
            if (Float.compare(walls[i].altitude, structureAltitude) != 0
                    || Float.compare(walls[i].height, structureHeight) != 0) {
                throw new IllegalArgumentException(
                        "Compiled structure walls must share altitude and height: structureId=" + structureId + ".");
            }
        }

        int xCount = sortUnique(xs);
        int yCount = sortUnique(ys);
        if (xCount < 2 || yCount < 2) return empty(structureId);
        int columns = xCount - 1;
        int rows = yCount - 1;
        int cellCount = columns * rows;
        boolean[] occupied = new boolean[cellCount];
        boolean[] actor = new boolean[cellCount];
        boolean[] physics = new boolean[cellCount];
        boolean[] light = new boolean[cellCount];
        boolean[] shadow = new boolean[cellCount];
        boolean[] particle = new boolean[cellCount];
        int differenceSize = xCount * yCount;
        int[] occupiedDifference = new int[differenceSize];
        int[] actorDifference = new int[differenceSize];
        int[] physicsDifference = new int[differenceSize];
        int[] lightDifference = new int[differenceSize];
        int[] shadowDifference = new int[differenceSize];
        int[] particleDifference = new int[differenceSize];

        for (int wallIndex = 0; wallIndex < wallCount; wallIndex++) {
            int x0 = indexOf(xs, xCount, minX[wallIndex]);
            int x1 = indexOf(xs, xCount, maxX[wallIndex]);
            int y0 = indexOf(ys, yCount, minY[wallIndex]);
            int y1 = indexOf(ys, yCount, maxY[wallIndex]);
            SpatialBlockData wall = walls[wallIndex];
            addRectangle(occupiedDifference, xCount, x0, y0, x1, y1);
            if (wall.actorOccluder) addRectangle(actorDifference, xCount, x0, y0, x1, y1);
            if (wall.physicsCollision) addRectangle(physicsDifference, xCount, x0, y0, x1, y1);
            if (wall.lightOccluder) addRectangle(lightDifference, xCount, x0, y0, x1, y1);
            if (wall.shadowCaster) addRectangle(shadowDifference, xCount, x0, y0, x1, y1);
            if (wall.particleOccluder) addRectangle(particleDifference, xCount, x0, y0, x1, y1);
        }
        materialize(occupiedDifference, xCount, yCount, columns, occupied);
        materialize(actorDifference, xCount, yCount, columns, actor);
        materialize(physicsDifference, xCount, yCount, columns, physics);
        materialize(lightDifference, xCount, yCount, columns, light);
        materialize(shadowDifference, xCount, yCount, columns, shadow);
        materialize(particleDifference, xCount, yCount, columns, particle);

        BoundaryBuilder boundary = new BoundaryBuilder(Math.max(4, cellCount * 2));
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int cell = y * columns + x;
                if (!occupied[cell]) continue;
                if (x == 0 || !occupied[cell - 1]) {
                    boundary.add(VERTICAL, xs[x], ys[y], ys[y + 1], -1, 0,
                            actor[cell], physics[cell], light[cell], shadow[cell], particle[cell]);
                }
                if (x == columns - 1 || !occupied[cell + 1]) {
                    boundary.add(VERTICAL, xs[x + 1], ys[y], ys[y + 1], 1, 0,
                            actor[cell], physics[cell], light[cell], shadow[cell], particle[cell]);
                }
                if (y == 0 || !occupied[cell - columns]) {
                    boundary.add(HORIZONTAL, ys[y], xs[x], xs[x + 1], 0, -1,
                            actor[cell], physics[cell], light[cell], shadow[cell], particle[cell]);
                }
                if (y == rows - 1 || !occupied[cell + columns]) {
                    boundary.add(HORIZONTAL, ys[y + 1], xs[x], xs[x + 1], 0, 1,
                            actor[cell], physics[cell], light[cell], shadow[cell], particle[cell]);
                }
            }
        }

        boundary.sort();
        boundary.mergeCollinear();
        float lower = structureAltitude;
        float upper = lower + Math.max(0f, structureHeight);
        return boundary.build(structureId, lower, upper);
    }

    private static int sortUnique(float[] values) {
        Arrays.sort(values);
        int unique = 0;
        for (int i = 0; i < values.length; i++) {
            if (unique == 0 || Float.compare(values[i], values[unique - 1]) != 0) {
                values[unique++] = values[i];
            }
        }
        return unique;
    }

    private static int indexOf(float[] values, int count, float value) {
        int low = 0;
        int high = count - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int compare = Float.compare(values[mid], value);
            if (compare < 0) low = mid + 1;
            else if (compare > 0) high = mid - 1;
            else return mid;
        }
        throw new IllegalStateException("Compressed structure coordinate is missing.");
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

    private static void materialize(int[] difference,
                                    int width,
                                    int height,
                                    int cellColumns,
                                    boolean[] output) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int value = difference[index];
                if (x > 0) value += difference[index - 1];
                if (y > 0) value += difference[index - width];
                if (x > 0 && y > 0) value -= difference[index - width - 1];
                difference[index] = value;
                if (x < width - 1 && y < height - 1) output[y * cellColumns + x] = value > 0;
            }
        }
    }

    private static CompiledSpatialStructure empty(int structureId) {
        return new CompiledSpatialStructure(structureId, 0f, 0f,
                new float[0], new float[0], new float[0], new float[0],
                new byte[0], new byte[0], new boolean[0], new boolean[0],
                new boolean[0], new boolean[0], new boolean[0]);
    }

    private static final class BoundaryBuilder {
        private int size;
        private byte[] orientation;
        private float[] line;
        private float[] start;
        private float[] end;
        private byte[] normalX;
        private byte[] normalY;
        private boolean[] actor;
        private boolean[] physics;
        private boolean[] light;
        private boolean[] shadow;
        private boolean[] particle;

        BoundaryBuilder(int capacity) {
            orientation = new byte[capacity];
            line = new float[capacity];
            start = new float[capacity];
            end = new float[capacity];
            normalX = new byte[capacity];
            normalY = new byte[capacity];
            actor = new boolean[capacity];
            physics = new boolean[capacity];
            light = new boolean[capacity];
            shadow = new boolean[capacity];
            particle = new boolean[capacity];
        }

        void add(byte segmentOrientation, float segmentLine, float segmentStart, float segmentEnd,
                 int nx, int ny, boolean isActor, boolean isPhysics,
                 boolean isLight, boolean isShadow, boolean isParticle) {
            ensureCapacity(size + 1);
            orientation[size] = segmentOrientation;
            line[size] = segmentLine;
            start[size] = segmentStart;
            end[size] = segmentEnd;
            normalX[size] = (byte) nx;
            normalY[size] = (byte) ny;
            actor[size] = isActor;
            physics[size] = isPhysics;
            light[size] = isLight;
            shadow[size] = isShadow;
            particle[size] = isParticle;
            size++;
        }

        void sort() {
            int[] order = new int[size];
            int[] temp = new int[size];
            for (int i = 0; i < size; i++) order[i] = i;
            for (int width = 1; width < size; width <<= 1) {
                for (int left = 0; left < size; left += width << 1) {
                    int middle = Math.min(left + width, size);
                    int right = Math.min(left + (width << 1), size);
                    int a = left;
                    int b = middle;
                    int out = left;
                    while (a < middle || b < right) {
                        if (b >= right || a < middle && compare(order[a], order[b]) <= 0) temp[out++] = order[a++];
                        else temp[out++] = order[b++];
                    }
                }
                int[] swap = order;
                order = temp;
                temp = swap;
            }
            reorder(order);
        }

        void mergeCollinear() {
            int write = 0;
            for (int read = 0; read < size; read++) {
                if (write > 0 && compatible(write - 1, read)
                        && start[read] <= end[write - 1] + SpatialWallGeometry.GEOMETRY_EPSILON) {
                    if (end[read] > end[write - 1]) end[write - 1] = end[read];
                } else {
                    if (write != read) copy(read, write);
                    write++;
                }
            }
            size = write;
        }

        CompiledSpatialStructure build(int structureId, float lower, float upper) {
            float[] sx = new float[size];
            float[] sy = new float[size];
            float[] ex = new float[size];
            float[] ey = new float[size];
            for (int i = 0; i < size; i++) {
                if (orientation[i] == HORIZONTAL) {
                    sx[i] = start[i]; sy[i] = line[i]; ex[i] = end[i]; ey[i] = line[i];
                } else {
                    sx[i] = line[i]; sy[i] = start[i]; ex[i] = line[i]; ey[i] = end[i];
                }
            }
            return new CompiledSpatialStructure(structureId, lower, upper,
                    sx, sy, ex, ey,
                    Arrays.copyOf(normalX, size), Arrays.copyOf(normalY, size),
                    Arrays.copyOf(actor, size),
                    Arrays.copyOf(physics, size), Arrays.copyOf(light, size),
                    Arrays.copyOf(shadow, size), Arrays.copyOf(particle, size));
        }

        private int compare(int first, int second) {
            int result = orientation[first] - orientation[second];
            if (result != 0) return result;
            result = Float.compare(line[first], line[second]);
            if (result != 0) return result;
            result = normalX[first] - normalX[second];
            if (result != 0) return result;
            result = normalY[first] - normalY[second];
            if (result != 0) return result;
            result = compareProperties(first, second);
            if (result != 0) return result;
            result = Float.compare(start[first], start[second]);
            return result != 0 ? result : Float.compare(end[first], end[second]);
        }

        private int compareProperties(int first, int second) {
            int result = compareBoolean(actor[first], actor[second]);
            if (result == 0) result = compareBoolean(physics[first], physics[second]);
            if (result == 0) result = compareBoolean(light[first], light[second]);
            if (result == 0) result = compareBoolean(shadow[first], shadow[second]);
            if (result == 0) result = compareBoolean(particle[first], particle[second]);
            return result;
        }

        private boolean compatible(int first, int second) {
            return orientation[first] == orientation[second]
                    && Float.compare(line[first], line[second]) == 0
                    && normalX[first] == normalX[second]
                    && normalY[first] == normalY[second]
                    && compareProperties(first, second) == 0;
        }

        private void reorder(int[] order) {
            byte[] oldOrientation = orientation;
            float[] oldLine = line;
            float[] oldStart = start;
            float[] oldEnd = end;
            byte[] oldNormalX = normalX;
            byte[] oldNormalY = normalY;
            boolean[] oldActor = actor;
            boolean[] oldPhysics = physics;
            boolean[] oldLight = light;
            boolean[] oldShadow = shadow;
            boolean[] oldParticle = particle;
            orientation = new byte[oldOrientation.length];
            line = new float[oldLine.length];
            start = new float[oldStart.length];
            end = new float[oldEnd.length];
            normalX = new byte[oldNormalX.length];
            normalY = new byte[oldNormalY.length];
            actor = new boolean[oldActor.length];
            physics = new boolean[oldPhysics.length];
            light = new boolean[oldLight.length];
            shadow = new boolean[oldShadow.length];
            particle = new boolean[oldParticle.length];
            for (int i = 0; i < size; i++) {
                int source = order[i];
                orientation[i] = oldOrientation[source]; line[i] = oldLine[source];
                start[i] = oldStart[source]; end[i] = oldEnd[source];
                normalX[i] = oldNormalX[source]; normalY[i] = oldNormalY[source];
                actor[i] = oldActor[source];
                physics[i] = oldPhysics[source]; light[i] = oldLight[source];
                shadow[i] = oldShadow[source]; particle[i] = oldParticle[source];
            }
        }

        private void copy(int source, int target) {
            orientation[target] = orientation[source]; line[target] = line[source];
            start[target] = start[source]; end[target] = end[source];
            normalX[target] = normalX[source]; normalY[target] = normalY[source];
            actor[target] = actor[source];
            physics[target] = physics[source]; light[target] = light[source];
            shadow[target] = shadow[source]; particle[target] = particle[source];
        }

        private void ensureCapacity(int required) {
            if (required <= orientation.length) return;
            int next = Math.max(required, orientation.length * 2);
            orientation = Arrays.copyOf(orientation, next); line = Arrays.copyOf(line, next);
            start = Arrays.copyOf(start, next); end = Arrays.copyOf(end, next);
            normalX = Arrays.copyOf(normalX, next); normalY = Arrays.copyOf(normalY, next);
            actor = Arrays.copyOf(actor, next);
            physics = Arrays.copyOf(physics, next); light = Arrays.copyOf(light, next);
            shadow = Arrays.copyOf(shadow, next); particle = Arrays.copyOf(particle, next);
        }

        private static int compareBoolean(boolean first, boolean second) {
            return first == second ? 0 : first ? 1 : -1;
        }
    }
}
