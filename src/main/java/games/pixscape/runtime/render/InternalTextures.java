package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import games.pixscape.runtime.service.TextureRegistry;

public final class InternalTextures {
    private InternalTextures() {
    }

    private static Texture white1x1;

    public static void initIfNeeded() {
        // 1) Create the texture if needed (only once)
        if (white1x1 == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fill();

            white1x1 = new Texture(pm);
            pm.dispose();

            white1x1.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        }

        // 2) IMPORTANT: after TextureRegistry.clear(), mapping is lost.
        // So we re-reserve WHITE_HANDLE at EACH call, but only when necessary.
        Texture mapped = TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE);
        if (mapped != white1x1) {
            TextureRegistry.reserveHandle(TextureRegistry.WHITE_HANDLE, white1x1);
        }
    }

    public static Texture whiteTexture() {
        initIfNeeded();
        return white1x1;
    }

    public static int whiteHandle() {
        initIfNeeded();
        return TextureRegistry.WHITE_HANDLE;
    }

    public static void dispose() {
        if (white1x1 != null) {
            white1x1.dispose();
            white1x1 = null;
        }
        // No clear here: TextureRegistry.clear() handles lifecycle side.
    }
}
