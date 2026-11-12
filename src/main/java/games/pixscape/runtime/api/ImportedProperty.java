package games.pixscape.runtime.api;

/**
 * Read-only preserved Tiled property. File paths remain source-relative; object IDs remain
 * in their Tiled source domain and are never Pixscape stable IDs. No files or objects are resolved.
 * The XML retains the declared type, custom type, attributes and nested values.
 */
public interface ImportedProperty {
    String typeName();

    String customTypeName();

    String sourceXml();
}
