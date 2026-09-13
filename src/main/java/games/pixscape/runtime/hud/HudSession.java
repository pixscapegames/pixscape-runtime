package games.pixscape.runtime.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import games.pixscape.runtime.render.batch.HudBatch;

/**
 * {@code INTERNAL} live Scene2D instance of one chosen HUD screen.
 *
 * <p>The session owns its {@link Stage}, {@link Viewport}, and {@link HudBatch}. It borrows the
 * prepared {@link HudResources} and compiled shader for its entire lifetime; callers must keep
 * both open and must dispose them separately after every borrowing session has been disposed.</p>
 *
 * <p>Version 1 uses a {@link FitViewport}. This preserves the authored logical reference space
 * and aspect ratio, centers its camera, and letterboxes displays with a different aspect ratio.
 * Responsive layout policy remains intentionally deferred.</p>
 */
public final class HudSession implements Disposable {
    private HudResources resources;
    private Viewport viewport;
    private HudBatch hudBatch;
    private Stage stage;
    private MaterializedHud content;
    private boolean disposed;

    private HudSession(HudScreenAsset asset, HudResources resources, ShaderProgram hudShader) {
        if (asset == null) throw new IllegalArgumentException("HudScreenAsset is required.");
        if (resources == null) throw new IllegalArgumentException("HudResources is required.");
        if (resources.isDisposed()) {
            throw new IllegalStateException("HudResources has been disposed.");
        }
        if (hudShader == null) throw new IllegalArgumentException("HUD shader is required.");
        asset.validate();

        this.resources = resources;
        viewport = new FitViewport(asset.referenceWidth, asset.referenceHeight);
        hudBatch = new HudBatch(
                HudBatch.DEFAULT_CAPACITY, hudShader, resources.textureArrayBundle());
        stage = new Stage(viewport, hudBatch);

        if (Gdx.graphics != null
                && Gdx.graphics.getWidth() > 0 && Gdx.graphics.getHeight() > 0) {
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        }
    }

    /** Creates an empty live HUD instance using already prepared borrowed resources. */
    public static HudSession create(
            HudScreenAsset asset, HudResources resources, ShaderProgram hudShader) {
        return new HudSession(asset, resources, hudShader);
    }

    /** Advances Scene2D state. Negative deltas are passed through with normal Stage semantics. */
    public void act(float delta) {
        requireUsable();
        stage.act(delta);
    }

    /** Draws the Stage through the session-owned HudBatch without clearing the framebuffer. */
    public void draw() {
        requireUsable();
        stage.draw();
    }

    /** Updates the fitted screen bounds and centers the logical-space camera. */
    public void resize(int width, int height) {
        requireUsable();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("HUD viewport size must be positive.");
        }
        viewport.update(width, height, true);
        layoutContent();
    }

    /**
     * Installs a successfully materialized detached tree as this session's content.
     * Existing content is replaced only after the candidate root has been sized and laid out.
     */
    public void install(MaterializedHud materializedHud) {
        requireUsable();
        if (materializedHud == null) throw new IllegalArgumentException("MaterializedHud is required.");
        if (content == materializedHud) return;
        Actor candidate = materializedHud.root();
        if (candidate.getParent() != null) {
            throw new IllegalStateException("Materialized HUD root must be detached before installation.");
        }

        layout(candidate);
        if (content != null) content.root().remove();
        stage.addActor(candidate);
        content = materializedHud;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /** Package-private borrowed Stage; valid only while this session is usable; do not dispose. */
    Stage stage() {
        requireUsable();
        return stage;
    }

    /** Package-private borrowed Viewport; valid only while this session is usable. */
    Viewport viewport() {
        requireUsable();
        return viewport;
    }

    /** Package-private borrowed batch for Runtime inspection; do not dispose. */
    HudBatch hudBatch() {
        requireUsable();
        return hudBatch;
    }

    private void requireUsable() {
        if (disposed) throw new IllegalStateException("HudSession has been disposed.");
        if (resources.isDisposed()) {
            throw new IllegalStateException(
                    "Borrowed HudResources was disposed while HudSession is still active.");
        }
    }

    /**
     * Disposes session-owned resources exactly once. The borrowed HudResources and shader are
     * never disposed. LibGDX Stage does not dispose a Batch supplied to Stage(Viewport, Batch),
     * so the session disposes its HudBatch explicitly after disposing the Stage.
     */
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        try {
            if (content != null) content.root().remove();
            stage.dispose();
        } catch (RuntimeException disposalFailure) {
            failure = disposalFailure;
        }
        try {
            hudBatch.dispose();
        } catch (RuntimeException disposalFailure) {
            if (failure == null) failure = disposalFailure;
        }
        stage = null;
        content = null;
        hudBatch = null;
        viewport = null;
        resources = null;
        if (failure != null) throw failure;
    }

    private void layoutContent() {
        if (content != null) layout(content.root());
    }

    private void layout(Actor root) {
        root.setBounds(0f, 0f, viewport.getWorldWidth(), viewport.getWorldHeight());
        if (root instanceof Layout) {
            Layout layout = (Layout) root;
            layout.invalidateHierarchy();
            layout.validate();
        }
    }
}
