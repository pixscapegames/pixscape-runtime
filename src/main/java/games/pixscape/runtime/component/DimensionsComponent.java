package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * {@code SUPPORTED_EXPERT} authored local width and height for an entity's
 * normal rectangular geometry, before transform scale.
 *
 * <p>Ordinary read-only gameplay access should prefer
 * {@link games.pixscape.runtime.api.EntityRef#geometry()}.</p>
 */
public class DimensionsComponent extends PooledComponent {
    public float width, height; // logical quad size (before scale)

    @Override
    protected void reset() {

    }
}
