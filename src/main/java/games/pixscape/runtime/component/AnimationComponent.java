package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.ObjectMap;

public class AnimationComponent extends PooledComponent {

    /**
     * Animation group resolved from the atlas asset index.
     * Example: "hero" -> hero_0001, hero_0002, ...
     */
    public String animation = "";

    public ObjectMap<String, Clip> clips = new ObjectMap<>();
    public String currentClip = "";

    public float fps = 12f;
    public float stateTime = 0f;

    public boolean playing = true;
    public boolean loop = true;

    /**
     * Last resolved global frame index (for change detection only).
     */
    public int frame = -1;

    @Override
    protected void reset() {
        animation = "";
        clips.clear();
        currentClip = "";
        fps = 12f;
        stateTime = 0f;
        playing = true;
        loop = true;
        frame = -1;
    }

    public static class Clip {
        public int start;
        public int end;
        public boolean flipX = false;

        public Clip() {
        }

        public Clip(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public Clip getClip() {
        if (currentClip == null || currentClip.isEmpty()) return null;
        return clips.get(currentClip);
    }
}
