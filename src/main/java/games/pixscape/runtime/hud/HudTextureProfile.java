package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** Immutable sampling and fixed-page contract for one HUD TextureArray. */
public final class HudTextureProfile {
    public static final String DEFAULT_ID = "hud-fixed-2048-linear-clamp";

    private static final HudTextureProfile DEFAULT = new HudTextureProfile(
            DEFAULT_ID,
            2048,
            2048,
            Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear,
            Texture.TextureWrap.ClampToEdge,
            Texture.TextureWrap.ClampToEdge,
            false,
            Pixmap.Format.RGBA8888);

    private final String id;
    private final int pageWidth;
    private final int pageHeight;
    private final Texture.TextureFilter minFilter;
    private final Texture.TextureFilter magFilter;
    private final Texture.TextureWrap uWrap;
    private final Texture.TextureWrap vWrap;
    private final boolean useMipMaps;
    private final Pixmap.Format outputFormat;

    private HudTextureProfile(String id, int pageWidth, int pageHeight,
                              Texture.TextureFilter minFilter,
                              Texture.TextureFilter magFilter,
                              Texture.TextureWrap uWrap,
                              Texture.TextureWrap vWrap,
                              boolean useMipMaps,
                              Pixmap.Format outputFormat) {
        this.id = id;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.uWrap = uWrap;
        this.vWrap = vWrap;
        this.useMipMaps = useMipMaps;
        this.outputFormat = outputFormat;
    }

    public static HudTextureProfile forId(String id) {
        String normalized = normalizeIdOrDefault(id);
        if (DEFAULT_ID.equals(normalized)) return DEFAULT;
        throw new IllegalArgumentException("Unknown HUD texture profile: " + normalized + ".");
    }

    static String normalizeIdOrDefault(String id) {
        return id == null || id.trim().length() == 0 ? DEFAULT_ID : id.trim();
    }

    public String id() {
        return id;
    }

    public int pageWidth() {
        return pageWidth;
    }

    public int pageHeight() {
        return pageHeight;
    }

    public Texture.TextureFilter minFilter() {
        return minFilter;
    }

    public Texture.TextureFilter magFilter() {
        return magFilter;
    }

    public Texture.TextureWrap uWrap() {
        return uWrap;
    }

    public Texture.TextureWrap vWrap() {
        return vWrap;
    }

    public boolean useMipMaps() {
        return useMipMaps;
    }

    /**
     * Required normalized output format. HUD preparation currently rejects values other than
     * {@link Pixmap.Format#RGBA8888} until the shared builder supports configurable formats.
     */
    public Pixmap.Format outputFormat() {
        return outputFormat;
    }
}
