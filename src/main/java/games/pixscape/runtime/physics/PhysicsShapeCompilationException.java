package games.pixscape.runtime.physics;

public final class PhysicsShapeCompilationException extends IllegalArgumentException {
    private final int physicsShapeId;
    private final int partIndex;
    private final int reasonCode;

    public PhysicsShapeCompilationException(
            int physicsShapeId,
            int partIndex,
            int reasonCode,
            String detail) {
        super(buildMessage(physicsShapeId, partIndex, detail));
        this.physicsShapeId = physicsShapeId;
        this.partIndex = partIndex;
        this.reasonCode = reasonCode;
    }

    public int physicsShapeId() {
        return physicsShapeId;
    }

    public int partIndex() {
        return partIndex;
    }

    public int reasonCode() {
        return reasonCode;
    }

    private static String buildMessage(int physicsShapeId, int partIndex, String detail) {
        StringBuilder message = new StringBuilder();
        message.append("Cannot compile physicsShapeId ").append(physicsShapeId);
        if (partIndex >= 0) {
            message.append(", partIndex ").append(partIndex);
        }
        message.append(": ").append(detail != null ? detail : "unknown geometry error");
        return message.toString();
    }
}
