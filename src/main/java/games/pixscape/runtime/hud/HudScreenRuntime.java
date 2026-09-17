package games.pixscape.runtime.hud;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** {@code INTERNAL} transactional owner and frame lifecycle for the currently shown HUD. */
public final class HudScreenRuntime implements Disposable {
    private final HudScreenLoader loader;
    private int capturedPointer = -1;
    private final InputProcessor inputProcessor = new InputAdapter() {
        @Override public boolean keyDown(int keycode) {
            return active != null && active.session().stage().keyDown(keycode);
        }

        @Override public boolean keyUp(int keycode) {
            return active != null && active.session().stage().keyUp(keycode);
        }

        @Override public boolean keyTyped(char character) {
            return active != null && active.session().stage().keyTyped(character);
        }

        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            boolean handled = active != null
                    && active.session().stage().touchDown(screenX, screenY, pointer, button);
            if (handled) capturedPointer = pointer;
            return handled;
        }

        @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            boolean handled = active != null
                    && active.session().stage().touchUp(screenX, screenY, pointer, button);
            if (pointer == capturedPointer) capturedPointer = -1;
            return handled;
        }

        @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
            boolean handled = active != null
                    && active.session().stage().touchCancelled(screenX, screenY, pointer, button);
            if (pointer == capturedPointer) capturedPointer = -1;
            return handled;
        }

        @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
            return active != null
                    && active.session().stage().touchDragged(screenX, screenY, pointer);
        }

        @Override public boolean mouseMoved(int screenX, int screenY) {
            return active != null && active.session().stage().mouseMoved(screenX, screenY);
        }

        @Override public boolean scrolled(float amountX, float amountY) {
            return active != null && active.session().stage().scrolled(amountX, amountY);
        }
    };
    private ActiveHudScreen active;

    public HudScreenRuntime(FileHandle runtimeProjectDir, ShaderProgram hudShader) {
        loader = new HudScreenLoader(runtimeProjectDir, hudShader);
    }

    /** Builds the candidate completely before publishing it and retiring the previous screen. */
    public ActiveHudScreen show(String screenId) {
        return install(loader.load(screenId));
    }

    /** INTERNAL engine bridge for Scene default activation; ordinary show remains independently owned. */
    public ActiveHudScreen showBorrowing(String screenId, HudResources environment) {
        if (active != null && active.resourceOwnership() == ActiveHudScreen.ResourceOwnership.OWNED
                && active.resources() == environment) {
            throw new IllegalArgumentException("Cannot borrow the environment owned by the screen being replaced.");
        }
        return install(loader.loadBorrowing(screenId, environment));
    }

    private ActiveHudScreen install(ActiveHudScreen candidate) {
        ActiveHudScreen previous = active;
        capturedPointer = -1;
        active = candidate;
        if (previous != null) previous.dispose();
        return candidate;
    }

    /** Clears and disposes the active screen. This operation is idempotent. */
    public void hide() {
        ActiveHudScreen previous = active;
        active = null;
        capturedPointer = -1;
        if (previous != null) previous.dispose();
    }

    public ActiveHudScreen activeScreen() {
        return active;
    }

    /** Stable input bridge that always targets the currently active HUD Stage. */
    public InputProcessor inputProcessor() {
        return inputProcessor;
    }

    /** Returns whether the active HUD currently owns a pointer gesture. */
    public boolean isPointerCaptured() {
        return capturedPointer >= 0;
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
