package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * A Tiled document layer. Parent and order use the existing hierarchy relation
 * and EntityIndexComponent.zIndex. Contents stay on their existing ECS entities.
 */
public final class TiledMapLayerComponent extends PooledComponent {
    public enum Kind {
        TILE, OBJECT, IMAGE
    }

    public Kind kind = Kind.TILE;

    @Override
    protected void reset() {
        kind = Kind.TILE;
    }
}
