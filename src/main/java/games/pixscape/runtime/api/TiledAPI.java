package games.pixscape.runtime.api;

/**
 * High-level API for tile definitions and individual tile layers.
 */
public interface TiledAPI {
    /**
     * Resolves an exported tile definition by tileset asset ID and original local Tiled ID.
     * Returns null for an unknown tileset/tile or a tile omitted from runtime export.
     * O(1) lookup; never creates data. IDs are not list positions or map GIDs.
     * <pre>{@code
     * TileDefinition tile = engine.api().tiled().tile(tilesetAssetId, 7);
     * int cost = tile == null ? 1 : tile.properties().getInt("cost", 1);
     * TileDefinition placed = engine.api().tiled().ofEntityId(tileLayerEntityId)
     *         .tiles().definition(3, 4);
     * boolean solid = placed != null && placed.properties().getBoolean("solid", false);
     * }</pre>
     */
    TileDefinition tile(int tilesetId, int localId);

    /**
     * Returns a tolerant tile-layer view bound to the current entity incarnation and World.
     */
    TiledLayerRef ofEntityId(int entityId);

    /**
     * Returns a tolerant tile-layer view bound to the entity currently resolved by stable ID.
     */
    TiledLayerRef ofStableId(int stableId);

    /**
     * Strictly resolves a tile-layer entity at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledLayerRef requireEntityId(int entityId);

    /**
     * Strictly resolves a tile-layer stable ID at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledLayerRef requireStableId(int stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
