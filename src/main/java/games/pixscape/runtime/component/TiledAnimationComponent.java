package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Makes a regular renderable ECS entity play a shared Tiled animation definition.
 *
 * <p>The persistent {@link #animationId} is authored scene state and identifies the logical
 * {@code TileAnimationDef} played by this entity. The remaining fields intentionally live in the
 * same component because they are the transient playback state and visual cache of that semantic
 * capability: {@link #frameIndex} is the current runtime frame, {@link #frameElapsedMs} is the
 * elapsed runtime time within that frame, and {@link #appliedFrameAssetId} caches the concrete
 * visual asset most recently applied to the entity.</p>
 *
 * <p>Transient playback state is deliberately not serialized. Scene reload therefore restarts
 * playback at frame zero and forces that frame to be applied again. Frame asset ids and exact
 * frame durations remain owned by the shared {@code TileAnimationDef}; they are never duplicated
 * into an entity component.</p>
 */
public final class TiledAnimationComponent extends PooledComponent {

    public int animationId = -1;

    public transient int frameIndex = 0;
    public transient int frameElapsedMs = 0;
    public transient int appliedFrameAssetId = -1;

    @Override
    protected void reset() {
        animationId = -1;
        frameIndex = 0;
        frameElapsedMs = 0;
        appliedFrameAssetId = -1;
    }
}
