package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.tiled.TiledProjection;

/**
 * Common grid configuration of a Tiled document. The entity's TransformComponent
 * owns the map origin; its direct hierarchy children are the ordered Tiled layers.
 * Child identity is a scene stable ID, never a position in that order.
 */
public final class TiledMapComponent extends PooledComponent {
    public TiledProjection projection;
    public int tileWidth;
    public int tileHeight;
    public int width;
    public int height;

    public void validate() {
        if (projection == null || tileWidth <= 0 || tileHeight <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Tiled map requires a projection and positive dimensions.");
        }
    }

    public void copyFrom(TiledMapComponent source) {
        projection = source.projection;
        tileWidth = source.tileWidth;
        tileHeight = source.tileHeight;
        width = source.width;
        height = source.height;
    }

    @Override
    protected void reset() {
        projection = null;
        tileWidth = 0;
        tileHeight = 0;
        width = 0;
        height = 0;
    }
}
