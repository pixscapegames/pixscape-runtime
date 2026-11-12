package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLink;

/**
 * Passive authored rectangular walls owned by a Tiled Map entity.
 *
 * <p>Walls use integer tiled-cell x/y coordinates and positive width/depth dimensions.
 * Projection to screen space is intentionally deferred to future systems that
 * can read the owning {@link TiledLayerComponent} and tiled map projection.</p>
 */
public final class SpatialBlocksComponent extends PooledComponent {
    public Array<SpatialBlockData> blocks = new Array<>(SpatialBlockData[]::new);
    public Array<SpatialBlockLink> links = new Array<>(SpatialBlockLink.class);
    public int nextSpatialBlockId = 1;
    /**
     * Non-serialized authored snapshot revision, incremented only after an atomic replacement.
     */
    public transient int revision;

    private transient int checkedRevision;
    private transient int geometryRevision;
    private transient Array<SpatialBlockData> publishedBlocks;
    private transient Array<SpatialBlockLink> publishedLinks;

    /**
     * O(1) between authored publications. Compare exact geometry once per coarse revision,
     * including in-place edits; labels have no effect on ordering, lights or shadows.
     */
    public int geometryRevision() {
        if (publishedBlocks != null && checkedRevision == revision) return geometryRevision;
        if (!matchesPublishedGeometry()) {
            publishedBlocks = new Array<>(SpatialBlockData[]::new);
            if (blocks != null) for (SpatialBlockData block : blocks)
                publishedBlocks.add(block == null ? null : block.copy());
            publishedLinks = games.pixscape.runtime.spatial.SpatialBlockLinks.copy(links);
            geometryRevision++;
        }
        checkedRevision = revision;
        return geometryRevision;
    }

    private boolean matchesPublishedGeometry() {
        int blockCount = blocks == null ? 0 : blocks.size;
        int linkCount = links == null ? 0 : links.size;
        if (publishedBlocks == null || publishedBlocks.size != blockCount || publishedLinks.size != linkCount)
            return false;
        for (int i = 0; i < blockCount; i++)
            if (!SpatialBlockData.sameGeometry(blocks.get(i), publishedBlocks.get(i))) return false;
        for (int i = 0; i < linkCount; i++) {
            SpatialBlockLink a = links.get(i), b = publishedLinks.get(i);
            if (a == b) continue;
            if (a == null || b == null || a.firstBlockId != b.firstBlockId || a.secondBlockId != b.secondBlockId
                    || a.firstFace != b.firstFace || a.secondFace != b.secondFace) return false;
        }
        return true;
    }

    @Override
    protected void reset() {
        if (blocks == null) {
            blocks = new Array<>(SpatialBlockData[]::new);
        } else {
            blocks.clear();
        }
        nextSpatialBlockId = 1;
        if (links == null) links = new Array<>(SpatialBlockLink.class);
        else links.clear();
        revision = 0;
        publishedBlocks = null;
        publishedLinks = null;
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
