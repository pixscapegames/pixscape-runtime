package games.pixscape.runtime.tiled.animation;

import com.badlogic.gdx.utils.Array;

public final class TileAnimationsRuntimeData {
    public String version = "1";
    public Array<TileAnimationDefData> animations = new Array<>(TileAnimationDefData[]::new);
}
