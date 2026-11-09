package games.pixscape.runtime.api;

/**
 * Immutable snapshot of an exported tileset tile definition, shared by all cells using it.
 * Local IDs are authored IDs, may have gaps, and are unique only within their tileset.
 * No cell overrides or Tiled tile-object inheritance are applied here. A retained snapshot
 * remains readable after project replacement; resolve again to read the new project's data.
 */
public interface TileDefinition {
    /** Project asset ID of the owning tileset (also shown in the Studio selection context). */
    int tilesetId();
    /** Original Tiled ID within that tileset, including zero and gaps. Never a GID. */
    int localId();
    /** Logical Pixscape tile asset ID used by {@link TileEditFacade#set(int, int, int)}. */
    int assetId();
    /** Always non-null; typed getters follow {@link CustomProperties}' absence/type rules. */
    CustomProperties properties();
}
