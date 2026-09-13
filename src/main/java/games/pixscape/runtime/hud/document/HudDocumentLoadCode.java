package games.pixscape.runtime.hud.document;

/** Stable categories for failures that occur before a HUD document can be validated. */
public enum HudDocumentLoadCode {
    READ_FAILURE,
    MALFORMED_JSON,
    MISSING_SCHEMA_VERSION,
    UNSUPPORTED_SCHEMA_VERSION,
    DESERIALIZATION_FAILURE,
    WRITE_FAILURE
}
