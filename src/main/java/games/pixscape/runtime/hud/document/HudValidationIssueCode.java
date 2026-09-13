package games.pixscape.runtime.hud.document;

/** Stable machine-readable codes produced by version-1 HUD document validation. */
public enum HudValidationIssueCode {
    UNSUPPORTED_SCHEMA_VERSION,
    MISSING_ROOT,
    INVALID_HIERARCHY,
    MISSING_NODE_ID,
    DUPLICATE_NODE_ID,
    UNKNOWN_NODE_KIND,
    INVALID_NODE_PAYLOAD,
    INVALID_CHILD_PLACEMENT,
    INVALID_CHILD_COUNT,
    INVALID_CELL_CONSTRAINTS,
    INVALID_FREE_PLACEMENT,
    INVALID_DIMENSION,
    MISSING_RESOURCE_REFERENCE,
    UNKNOWN_RESOURCE_REFERENCE
}
