package games.pixscape.runtime.animation;

import com.badlogic.gdx.utils.Array;

public final class AnimationsRuntimeData {
    public String version = "1";
    public Array<AnimationDefData> animations = new Array<>(AnimationDefData[]::new);
}
