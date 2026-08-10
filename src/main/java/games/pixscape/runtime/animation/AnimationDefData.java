package games.pixscape.runtime.animation;

import com.badlogic.gdx.utils.Array;

/**
 * Authored animation input. An omitted fps uses the schema default of 12; identity, frame count,
 * clips, and current clip must otherwise be supplied and valid.
 */
public class AnimationDefData {
    public int assetId;
    public String name;
    public float fps = 12f;
    public String currentClip;
    public int frameCount;
    public Array<AnimationClipDefData> clips = new Array<>(AnimationClipDefData[]::new);
}
