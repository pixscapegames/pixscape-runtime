package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

public final class PolygonBuildResult {
    private final boolean valid;
    private final PolygonValidationResult validation;
    private final float[] sourceVertices;
    private final int sourceVertexCount;
    private final int algorithmVersion;
    private final long sourceHash;
    private final Array<PolygonPartData> parts;

    private PolygonBuildResult(
            boolean valid,
            PolygonValidationResult validation,
            float[] sourceVertices,
            int sourceVertexCount,
            int algorithmVersion,
            long sourceHash,
            Array<PolygonPartData> parts) {
        this.valid = valid;
        this.validation = validation;
        this.sourceVertices = sourceVertices != null ? sourceVertices : new float[0];
        this.sourceVertexCount = Math.max(0, sourceVertexCount);
        this.algorithmVersion = algorithmVersion;
        this.sourceHash = sourceHash;
        this.parts = parts != null
                ? parts
                : new Array<PolygonPartData>(true, 0, PolygonPartData.class);
    }

    public static PolygonBuildResult success(
            float[] sourceVertices,
            int sourceVertexCount,
            int algorithmVersion,
            long sourceHash,
            Array<PolygonPartData> parts) {
        return new PolygonBuildResult(
                true,
                PolygonValidationResult.ok(),
                sourceVertices,
                sourceVertexCount,
                algorithmVersion,
                sourceHash,
                parts);
    }

    public static PolygonBuildResult failure(PolygonValidationResult validation) {
        return new PolygonBuildResult(
                false,
                validation,
                new float[0],
                0,
                PolygonDecomposer.ALGORITHM_VERSION,
                0L,
                new Array<PolygonPartData>(true, 0, PolygonPartData.class));
    }

    public boolean isValid() {
        return valid;
    }

    public PolygonValidationResult validation() {
        return validation;
    }

    public String message() {
        return validation != null ? validation.message() : "Invalid polygon.";
    }

    public float[] sourceVertices() {
        return sourceVertices;
    }

    public int sourceVertexCount() {
        return sourceVertexCount;
    }

    public int algorithmVersion() {
        return algorithmVersion;
    }

    public long sourceHash() {
        return sourceHash;
    }

    public Array<PolygonPartData> parts() {
        return parts;
    }
}
