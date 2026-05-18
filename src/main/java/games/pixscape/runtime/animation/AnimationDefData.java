package games.pixscape.runtime.animation;

import com.badlogic.gdx.utils.Array;

public class AnimationDefData {
    public int assetId;
    public String name;
    public float fps = 12f;
    public String currentClip;
    public int frameCount;
    public Array<AnimationClipDefData> clips = new Array<>(AnimationClipDefData[]::new);
}
