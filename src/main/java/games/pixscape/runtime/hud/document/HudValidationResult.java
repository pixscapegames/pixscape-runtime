package games.pixscape.runtime.hud.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validation outcome containing either a materialization-safe wrapper or ordered issues. */
public final class HudValidationResult {
    private final List<HudValidationIssue> issues;
    private final ValidatedHudDocument validatedDocument;

    HudValidationResult(List<HudValidationIssue> issues,
                        ValidatedHudDocument validatedDocument) {
        this.issues = Collections.unmodifiableList(
                new ArrayList<HudValidationIssue>(issues));
        this.validatedDocument = validatedDocument;
    }

    public boolean isValid() {
        return validatedDocument != null;
    }

    public List<HudValidationIssue> issues() {
        return issues;
    }

    /** Returns the validated document and index, or {@code null} when validation failed. */
    public ValidatedHudDocument validatedDocument() {
        return validatedDocument;
    }
}
