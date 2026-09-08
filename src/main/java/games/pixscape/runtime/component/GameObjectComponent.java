package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Marks a real ECS entity as a Game Object root.
 *
 * <p>The optional source asset identifier is metadata only. The source asset is not a live
 * authority for mutable scene instances and there is no override-tracking contract. A top-level
 * Game Object's {@link EntityIndexComponent} owns its global Layer and z position. Member Layer
 * resolution and member-local z ordering are later hierarchy/rendering concerns; this marker does
 * not duplicate either value.</p>
 */
public final class GameObjectComponent extends PooledComponent {
    public String sourceAssetId = "";

    @Override
    protected void reset() {
        sourceAssetId = "";
    }
}
