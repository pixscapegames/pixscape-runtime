package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;

/**
 * Persistent ordered bindings owned locally by a spatial-block entity.
 *
 * <p>A published component must always contain at least one binding. Owners with
 * no bindings must not carry this component.</p>
 */
public final class BlockPhysicsBindingsComponent extends PooledComponent {
    public Array<BlockPhysicsBindingData> bindings =
            new Array<>(BlockPhysicsBindingData[]::new);

    @Override
    protected void reset() {
        if (bindings == null) {
            bindings = new Array<>(BlockPhysicsBindingData[]::new);
        } else {
            bindings.clear();
        }
    }
}
