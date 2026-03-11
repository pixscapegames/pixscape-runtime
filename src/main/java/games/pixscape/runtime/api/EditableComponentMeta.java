package games.pixscape.runtime.api;

/**
 * Metadata describing how a runtime component is exposed in the editor.
 */
public final class EditableComponentMeta {
    public final String displayName;
    public final String category;

    /**
     * @param displayName label shown in editor component lists
     * @param category editor grouping name for this component
     */
    public EditableComponentMeta(String displayName, String category) {
        this.displayName = displayName;
        this.category = category;
    }

    /**
     * Factory shortcut for creating metadata descriptors.
     */
    public static EditableComponentMeta of(String displayName, String category) {
        return new EditableComponentMeta(displayName, category);
    }
}
