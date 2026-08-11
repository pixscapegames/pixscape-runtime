package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.IntArray;

public class AnimationComponent extends PooledComponent {

    public IntArray animationAssetIds = new IntArray();
    public String currentClip = "";

    /** Pooled playback baseline; authored spawns replace this with {@code AnimationDef.fps()}. */
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
        animationAssetIds.clear();
        currentClip = "";
        fps = 12f;
        stateTime = 0f;
        playing = true;
        loop = true;
        frame = -1;
    }
}
