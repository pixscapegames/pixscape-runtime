package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.property.PropertySet;

/**
 * Optional authored custom properties attached to a Runtime entity.
 */
public final class CustomPropertiesComponent extends PooledComponent {
    public PropertySet properties = new PropertySet();

    @Override
    protected void reset() {
        if (properties == null) properties = new PropertySet();
        else properties.clear();
    }
}
