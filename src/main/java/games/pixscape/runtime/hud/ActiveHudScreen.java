package games.pixscape.runtime.hud;

import com.badlogic.gdx.utils.Disposable;

/** {@code INTERNAL} live HUD screen; always owns its session, explicitly owns or borrows its environment. */
public final class ActiveHudScreen implements Disposable {
    public enum ResourceOwnership { OWNED, BORROWED }
    private final String screenId;
    private final HudScreenAsset asset;
    private final HudResources resources;
    private final ResourceOwnership resourceOwnership;
    private final MaterializedHud materializedHud;
    private final HudSession session;
    private boolean disposed;

    ActiveHudScreen(String screenId, HudScreenAsset asset, HudResources resources,
                    MaterializedHud materializedHud, HudSession session, ResourceOwnership resourceOwnership) {
        if (resourceOwnership == null) throw new IllegalArgumentException("Resource ownership is required.");
        this.screenId = screenId;
        this.asset = asset;
        this.resources = resources;
        this.resourceOwnership = resourceOwnership;
        this.materializedHud = materializedHud;
        this.session = session;
    }

    public String screenId() {
        return screenId;
    }

    public HudScreenAsset asset() {
        return asset;
    }

    public MaterializedHud materializedHud() {
        return materializedHud;
    }

    public boolean isEmpty() {
        return session == null;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public ResourceOwnership resourceOwnership() {
        return resourceOwnership;
    }

    HudResources resources() {
        return resources;
    }

    HudSession session() {
        return session;
    }

    void act(float delta) {
        requireUsable();
        if (session != null) session.act(delta);
    }

    void draw() {
        requireUsable();
        if (session != null) session.draw();
    }

    void resize(int width, int height) {
        requireUsable();
        if (session != null) session.resize(width, height);
    }

    void resize(int screenX, int screenY, int width, int height) {
        requireUsable();
        if (session != null) session.resize(screenX, screenY, width, height);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        if (session != null) {
            try {
                session.dispose();
            } catch (RuntimeException disposalFailure) {
                failure = disposalFailure;
            }
        }
        // The outer owner of a borrowed environment must dispose it only after all sessions.
        if (resourceOwnership == ResourceOwnership.OWNED && resources != null) {
            try {
                resources.dispose();
            } catch (RuntimeException disposalFailure) {
                if (failure == null) failure = disposalFailure;
            }
        }
        if (failure != null) throw failure;
    }

    private void requireUsable() {
        if (disposed) throw new IllegalStateException("ActiveHudScreen has been disposed.");
    }
}
