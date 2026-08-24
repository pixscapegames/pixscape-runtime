package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.property.PropertySet;

/**
 * Optional authored custom properties attached to a Runtime entity.
 *
 * <p>Direct ECS access is {@code SUPPORTED_EXPERT}. Ordinary gameplay reads
 * should prefer
 * {@link games.pixscape.runtime.api.EntityRef#properties()}. Mutating the
 * {@link PropertySet} through expert ECS access changes authored state.
 * Property names are case-sensitive, CLASS values may nest, and OBJECT values
 * store persistent Pixscape stable IDs rather than Runtime entity IDs.</p>
 */
public final class CustomPropertiesComponent extends PooledComponent {
    public PropertySet properties = new PropertySet();

    @Override
    protected void reset() {
        if (properties == null) properties = new PropertySet();
        else properties.clear();
    }
}
