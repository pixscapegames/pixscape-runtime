package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * Passive authored rectangular walls owned by a tiled layer entity.
 *
 * <p>Walls use integer tiled-cell x/y coordinates and positive width/depth dimensions.
 * Projection to screen space is intentionally deferred to future systems that
 * can read the owning {@link TiledLayerComponent} and tiled map projection.</p>
 */
public final class SpatialBlocksComponent extends PooledComponent {
    public Array<SpatialBlockData> blocks = new Array<>(SpatialBlockData[]::new);
    /** Non-serialized authored snapshot revision, incremented only after an atomic replacement. */
    public transient int revision;

    @Override
    protected void reset() {
        if (blocks == null) {
            blocks = new Array<>(SpatialBlockData[]::new);
        } else {
            blocks.clear();
        }
        revision = 0;
    }

    public boolean hasBlocks() {
        return blocks != null && blocks.size > 0;
    }
}
