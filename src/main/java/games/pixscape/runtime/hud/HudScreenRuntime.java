package games.pixscape.runtime.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** {@code INTERNAL} transactional owner and frame lifecycle for the currently shown HUD. */
public final class HudScreenRuntime implements Disposable {
    private final HudScreenLoader loader;
    private ActiveHudScreen active;

    public HudScreenRuntime(FileHandle runtimeProjectDir, ShaderProgram hudShader) {
        loader = new HudScreenLoader(runtimeProjectDir, hudShader);
    }

    /** Builds the candidate completely before publishing it and retiring the previous screen. */
    public ActiveHudScreen show(String screenId) {
        ActiveHudScreen candidate = loader.load(screenId);
        ActiveHudScreen previous = active;
        active = candidate;
        if (previous != null) previous.dispose();
        return candidate;
    }

    /** Clears and disposes the active screen. This operation is idempotent. */
    public void hide() {
        ActiveHudScreen previous = active;
        active = null;
        if (previous != null) previous.dispose();
    }

    public ActiveHudScreen activeScreen() {
        return active;
    }

    public void act(float delta) {
        ActiveHudScreen current = active;
        if (current != null) current.act(delta);
    }

    public void draw() {
        ActiveHudScreen current = active;
        if (current != null) current.draw();
    }

    public void resize(int width, int height) {
        ActiveHudScreen current = active;
        if (current != null) current.resize(width, height);
    }

    /** Fits the active HUD inside a logical screen sub-region. */
    public void resize(int screenX, int screenY, int width, int height) {
        ActiveHudScreen current = active;
        if (current != null) current.resize(screenX, screenY, width, height);
    }

    @Override
    public void dispose() {
        hide();
    }
}
