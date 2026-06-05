package games.pixscape.runtime.component;

/**
 * Map/grid-local orientation for authored tiled-layer spatial blocks.
 *
 * <p>These values describe how a block footprint should be interpreted against
 * the owning tiled layer later. They intentionally avoid screen-space angles or
 * any fixed isometric ratio; projection belongs to the tiled layer data.</p>
 */
public enum SpatialBlockOrientation {
    TILE_CELL,
    TILE_AXIS_X,
    TILE_AXIS_Y,
    FREE_AXIS,
    CUSTOM
}
