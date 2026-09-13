package games.pixscape.runtime.hud.document;

/** Typed public failure from the HUD JSON codec. */
public final class HudDocumentLoadException extends RuntimeException {
    private final HudDocumentLoadCode code;
    private final String source;

    HudDocumentLoadException(HudDocumentLoadCode code, String source, String message,
                             Throwable cause) {
        super(message, cause);
        this.code = code;
        this.source = source;
    }

    public HudDocumentLoadCode code() {
        return code;
    }

    public String source() {
        return source;
    }
}
