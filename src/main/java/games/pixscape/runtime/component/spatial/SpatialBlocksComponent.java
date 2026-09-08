package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * Passive authored rectangular walls owned by a Tiled Map entity.
 *
 * <p>Walls use integer tiled-cell x/y coordinates and positive width/depth dimensions.
 * Projection to screen space is intentionally deferred to future systems that
 * can read the owning {@link TiledLayerComponent} and tiled map projection.</p>
 */
public final class SpatialBlocksComponent extends PooledComponent {
    public Array<SpatialBlockData> blocks = new Array<>(SpatialBlockData[]::new);
    public int nextSpatialBlockId = 1;
    /** Non-serialized authored snapshot revision, incremented only after an atomic replacement. */
    public transient int revision;

    @Override
    protected void reset() {
        if (blocks == null) {
            blocks = new Array<>(SpatialBlockData[]::new);
        } else {
            blocks.clear();
        }
        nextSpatialBlockId = 1;
        revision = 0;
    }

    public boolean hasBlocks() {
        return blocks != null && blocks.size > 0;
    }

    public int peekNextSpatialBlockId() {
        validateNextSpatialBlockId();
        return nextSpatialBlockId;
    }

    public int allocateNextSpatialBlockId() {
        validateNextSpatialBlockId();
        return nextSpatialBlockId++;
    }

    private void validateNextSpatialBlockId() {
        if (nextSpatialBlockId <= 0 || nextSpatialBlockId == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "nextSpatialBlockId must be positive and allocatable, got "
                            + nextSpatialBlockId + ".");
        }
    }
}
