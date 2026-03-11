package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

public final class InternalTextures {
    private InternalTextures() {}

    private static Texture white1x1;

    public static void initIfNeeded() {
        // 1) Créer la texture si besoin (1 seule fois)
        if (white1x1 == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fill();

            white1x1 = new Texture(pm);
            pm.dispose();

            white1x1.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        }

        // 2) IMPORTANT: après TextureRegistry.clear(), le mapping est perdu.
        // On re-réserve donc WHITE_HANDLE à CHAQUE appel, mais seulement si nécessaire.
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
        // Pas de clear ici: c'est le job de TextureRegistry.clear() côté cycle de vie.
    }
}
