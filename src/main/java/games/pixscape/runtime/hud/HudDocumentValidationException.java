package games.pixscape.runtime.hud;

import games.pixscape.runtime.hud.document.HudValidationIssue;
import games.pixscape.runtime.hud.document.HudValidationResult;

/** Typed lifecycle failure that preserves all ordered HUD document validation diagnostics. */
public final class HudDocumentValidationException extends RuntimeException {
    public enum Phase {
        STRUCTURAL,
        RESOURCE_AWARE
    }

    private final Phase phase;
    private final String documentId;
    private final HudValidationResult validationResult;

    HudDocumentValidationException(
            Phase phase, String documentId, HudValidationResult validationResult) {
        super(message(phase, documentId, validationResult));
        this.phase = phase;
        this.documentId = documentId;
        this.validationResult = validationResult;
    }

    public Phase phase() {
        return phase;
    }

    public String documentId() {
        return documentId;
    }

    public HudValidationResult validationResult() {
        return validationResult;
    }

    private static String message(
            Phase phase, String documentId, HudValidationResult validationResult) {
        StringBuilder message = new StringBuilder("HUD document validation failed during ")
                .append(phase == Phase.STRUCTURAL ? "structural" : "resource-aware")
                .append(" validation for ").append(documentId).append(':');
        for (int i = 0; i < validationResult.issues().size(); i++) {
            HudValidationIssue issue = validationResult.issues().get(i);
            message.append(" [").append(issue.code()).append(" path=")
                    .append(issue.path());
            if (issue.nodeId() != null) message.append(" nodeId=").append(issue.nodeId());
            message.append("] ").append(issue.message());
        }
        return message.toString();
    }
}
