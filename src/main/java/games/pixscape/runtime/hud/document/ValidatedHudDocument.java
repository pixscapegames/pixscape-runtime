package games.pixscape.runtime.hud.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Successfully validated document and its complete document-wide node index.
 *
 * <p>The index is built once in deterministic preorder and provides O(1) average lookup for
 * present and absent IDs. The wrapped authored DTO must not be mutated after validation; edits
 * require a new validation pass and wrapper.</p>
 */
public final class ValidatedHudDocument {
    private final HudDocumentV1 document;
    private final Map<String, HudNode> nodeIndex;

    ValidatedHudDocument(HudDocumentV1 document, Map<String, HudNode> nodeIndex) {
        this.document = document;
        this.nodeIndex = Collections.unmodifiableMap(
                new LinkedHashMap<String, HudNode>(nodeIndex));
    }

    public HudDocumentV1 document() {
        return document;
    }

    public Map<String, HudNode> nodeIndex() {
        return nodeIndex;
    }

    /** O(1) average lookup in the complete validated node index. */
    public HudNode node(String id) {
        return nodeIndex.get(id);
    }
}
