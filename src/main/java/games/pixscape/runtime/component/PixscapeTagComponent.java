package games.pixscape.runtime.component;


import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

public final class PixscapeTagComponent extends PooledComponent {
    public Array<String> tags = new Array<>();

    @Override
    protected void reset() {
        if (tags == null) tags = new Array<>();
        else tags.clear();
    }
}
