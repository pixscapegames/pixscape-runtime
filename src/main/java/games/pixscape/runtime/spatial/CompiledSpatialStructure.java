package games.pixscape.runtime.spatial;

/**
 * {@code SUPPORTED_EXPERT} immutable-by-contract primitive-array geometry compiled from one
 * authored Spatial V3 structure.
 *
 * <p>Instances are caller-owned compiler results. Indexed accessors do not allocate; returned
 * nested face/diagnostic objects remain part of this result and must not be treated as live
 * Runtime cache state.</p>
 */
public final class CompiledSpatialStructure {
    public static final byte MIN_X = 0;
    public static final byte MAX_X = 1;
    public static final byte MIN_Y = 2;
    public static final byte MAX_Y = 3;

    private final int structureId;
    private final float minX;
    private final float minY;
    private final float maxX;
    private final float maxY;
    private final float altitude;
    private final float height;
    private final FaceSet complete;
    private final FaceSet actorOccluder;
    private final Diagnostics diagnostics;

    CompiledSpatialStructure(int structureId,
                             float minX,
                             float minY,
                             float maxX,
                             float maxY,
                             float altitude,
                             float height,
                             FaceSet complete,
                             FaceSet actorOccluder,
                             Diagnostics diagnostics) {
        this.structureId = structureId;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.altitude = altitude;
        this.height = height;
        this.complete = complete;
        this.actorOccluder = actorOccluder;
        this.diagnostics = diagnostics;
    }

    public int structureId() { return structureId; }
    public float minX() { return minX; }
    public float minY() { return minY; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float altitude() { return altitude; }
    public float height() { return height; }
    public float lowerZ() { return altitude; }
    public float upperZ() { return altitude + height; }
    public FaceSet complete() { return complete; }
    public FaceSet actorOccluder() { return actorOccluder; }
    public Diagnostics diagnostics() { return diagnostics; }

    /** Complete-structure indexed accessors used by the Studio overlay. */
    public int segmentCount() { return complete.faceCount(); }
    public float startX(int face) { return complete.startX(face); }
    public float startY(int face) { return complete.startY(face); }
    public float endX(int face) { return complete.endX(face); }
    public float endY(int face) { return complete.endY(face); }
    public int normalX(int face) { return complete.normalX(face); }
    public int normalY(int face) { return complete.normalY(face); }

    /** Flat, immutable-by-contract face arrays exposed through allocation-free indexed reads. */
    public static final class FaceSet {
        private final byte[] orientation;
        private final float[] constantCoordinate;
        private final float[] startCoordinate;
        private final float[] endCoordinate;
        private final int[] anchorCellStart;
        private final int[] anchorCellCount;
        private final int[] anchorGx;
        private final int[] anchorGy;

        FaceSet(byte[] orientation,
                float[] constantCoordinate,
                float[] startCoordinate,
                float[] endCoordinate) {
            this(orientation, constantCoordinate, startCoordinate, endCoordinate,
                    new int[orientation.length], new int[orientation.length], new int[0], new int[0]);
        }

        FaceSet(byte[] orientation,
                float[] constantCoordinate,
                float[] startCoordinate,
                float[] endCoordinate,
                int[] anchorCellStart,
                int[] anchorCellCount,
                int[] anchorGx,
                int[] anchorGy) {
            this.orientation = orientation;
            this.constantCoordinate = constantCoordinate;
            this.startCoordinate = startCoordinate;
            this.endCoordinate = endCoordinate;
            this.anchorCellStart = anchorCellStart;
            this.anchorCellCount = anchorCellCount;
            this.anchorGx = anchorGx;
            this.anchorGy = anchorGy;
        }

        public int faceCount() { return orientation.length; }
        public byte orientation(int face) { return orientation[face]; }
        public float constantCoordinate(int face) { return constantCoordinate[face]; }
        public float startCoordinate(int face) { return startCoordinate[face]; }
        public float endCoordinate(int face) { return endCoordinate[face]; }
        public int anchorCellStart(int face) { return anchorCellStart[face]; }
        public int anchorCellCount(int face) { return anchorCellCount[face]; }
        public int anchorCellTotal() { return anchorGx.length; }
        public int anchorGx(int anchor) { return anchorGx[anchor]; }
        public int anchorGy(int anchor) { return anchorGy[anchor]; }

        public float startX(int face) {
            return isVertical(orientation[face]) ? constantCoordinate[face] : startCoordinate[face];
        }

        public float startY(int face) {
            return isVertical(orientation[face]) ? startCoordinate[face] : constantCoordinate[face];
        }

        public float endX(int face) {
            return isVertical(orientation[face]) ? constantCoordinate[face] : endCoordinate[face];
        }

        public float endY(int face) {
            return isVertical(orientation[face]) ? endCoordinate[face] : constantCoordinate[face];
        }

        public int normalX(int face) {
            return orientation[face] == MIN_X ? -1 : orientation[face] == MAX_X ? 1 : 0;
        }

        public int normalY(int face) {
            return orientation[face] == MIN_Y ? -1 : orientation[face] == MAX_Y ? 1 : 0;
        }

        private static boolean isVertical(byte value) {
            return value == MIN_X || value == MAX_X;
        }
    }

    /** Compile-time diagnostics kept outside the hot face arrays. */
    public static final class Diagnostics {
        private final int inputWallCount;
        private final int canonicalXCoordinateCount;
        private final int canonicalYCoordinateCount;
        private final int coveredCellCount;
        private final int rawBoundaryIntervalCount;
        private final int mergedFaceCount;
        private final int actorCoveredCellCount;
        private final int actorRawBoundaryIntervalCount;
        private final int actorMergedFaceCount;
        private final long compileDurationNanos;

        Diagnostics(int inputWallCount,
                    int canonicalXCoordinateCount,
                    int canonicalYCoordinateCount,
                    int coveredCellCount,
                    int rawBoundaryIntervalCount,
                    int mergedFaceCount,
                    int actorCoveredCellCount,
                    int actorRawBoundaryIntervalCount,
                    int actorMergedFaceCount,
                    long compileDurationNanos) {
            this.inputWallCount = inputWallCount;
            this.canonicalXCoordinateCount = canonicalXCoordinateCount;
            this.canonicalYCoordinateCount = canonicalYCoordinateCount;
            this.coveredCellCount = coveredCellCount;
            this.rawBoundaryIntervalCount = rawBoundaryIntervalCount;
            this.mergedFaceCount = mergedFaceCount;
            this.actorCoveredCellCount = actorCoveredCellCount;
            this.actorRawBoundaryIntervalCount = actorRawBoundaryIntervalCount;
            this.actorMergedFaceCount = actorMergedFaceCount;
            this.compileDurationNanos = compileDurationNanos;
        }

        public int inputWallCount() { return inputWallCount; }
        public int structureCount() { return 1; }
        public int canonicalXCoordinateCount() { return canonicalXCoordinateCount; }
        public int canonicalYCoordinateCount() { return canonicalYCoordinateCount; }
        public int coveredCellCount() { return coveredCellCount; }
        public int rawBoundaryIntervalCount() { return rawBoundaryIntervalCount; }
        public int mergedFaceCount() { return mergedFaceCount; }
        public int actorCoveredCellCount() { return actorCoveredCellCount; }
        public int actorRawBoundaryIntervalCount() { return actorRawBoundaryIntervalCount; }
        public int actorMergedFaceCount() { return actorMergedFaceCount; }
        public long compileDurationNanos() { return compileDurationNanos; }
    }
}
