package games.pixscape.runtime.hud.document;

/** One deterministic validation issue for an otherwise deserialized HUD document. */
public final class HudValidationIssue {
    private final HudValidationIssueCode code;
    private final String message;
    private final String nodeId;
    private final String path;

    HudValidationIssue(HudValidationIssueCode code, String message, String nodeId, String path) {
        this.code = code;
        this.message = message;
        this.nodeId = nodeId;
        this.path = path;
    }

    public HudValidationIssueCode code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String nodeId() {
        return nodeId;
    }

    public String path() {
        return path;
    }
}
