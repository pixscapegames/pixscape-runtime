package games.pixscape.runtime.api;

/**
 * Read-only Runtime view of one typed nested custom-property value.
 */
public interface ClassProperty {
    /** The authored class/type name. */
    String typeName();

    /** Explicitly stored members of this class value. */
    CustomProperties properties();
}
