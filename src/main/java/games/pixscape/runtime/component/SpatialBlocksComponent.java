package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

/**
 * Passive authored spatial volumes owned by a tiled layer entity.
 *
 * <p>Blocks use layer/map-local x/y coordinates and width/depth dimensions.
 * Projection to screen space is intentionally deferred to future systems that
 * can read the owning {@link TiledLayerComponent} and tiled map projection.</p>
 */
public final class SpatialBlocksComponent extends PooledComponent {
    public Array<SpatialBlockData> blocks = new Array<>(SpatialBlockData[]::new);

    @Override
    protected void reset() {
        if (blocks == null) {
            blocks = new Array<>(SpatialBlockData[]::new);
        } else {
            blocks.clear();
        }
    }

    public boolean hasBlocks() {
        return blocks != null && blocks.size > 0;
    }
}
