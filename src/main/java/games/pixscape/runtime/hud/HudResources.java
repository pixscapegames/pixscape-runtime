package games.pixscape.runtime.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.service.AtlasRuntimeService;

/** Owned, immutable HUD resource snapshot prepared before Scene2D drawing. */
public final class HudResources implements Disposable {
    private final String skinId;
    private final String atlasId;
    private final HudTextureProfile textureProfile;
    private Skin skin;
    private TextureAtlas atlas;
    private AtlasRuntimeService.TextureArrayBundle textureArrayBundle;
    private boolean disposed;

    private HudResources(String skinId, String atlasId, HudTextureProfile textureProfile,
                         Skin skin, TextureAtlas atlas,
                         AtlasRuntimeService.TextureArrayBundle textureArrayBundle) {
        this.skinId = skinId;
        this.atlasId = atlasId;
        this.textureProfile = textureProfile;
        this.skin = skin;
        this.atlas = atlas;
        this.textureArrayBundle = textureArrayBundle;
    }

    /**
     * Synchronously prepares a complete HUD resource snapshot on the active LibGDX GL thread.
     * The returned object exclusively owns the loaded atlas, Skin, and TextureArray bundle.
     */
    public static HudResources prepare(HudScreenAsset asset, FileHandle runtimeProjectDir) {
        if (asset == null) throw new IllegalArgumentException("HudScreenAsset is required.");
        if (runtimeProjectDir == null) {
            throw new IllegalArgumentException("Runtime project directory is required.");
        }
        asset.validate();
        String skinId = requireReference(asset.skinId, "skinId");
        String atlasId = requireReference(asset.atlasId, "atlasId");
        HudTextureProfile profile = HudTextureProfile.forId(asset.textureProfileId);

        FileHandle skinFile = resolveRequired(runtimeProjectDir, skinId, "Skin");
        FileHandle atlasFile = resolveRequired(runtimeProjectDir, atlasId, "TextureAtlas");
        TextureAtlasData atlasData = new TextureAtlasData(
                atlasFile, atlasFile.parent(), false);
        validateAtlasMetadata(atlasData.getPages(), profile);

        TextureAtlas atlas = null;
        Skin skin = null;
        AtlasRuntimeService.TextureArrayBundle bundle = null;
        boolean completed = false;
        try {
            atlas = new TextureAtlas(atlasData);
            Array<Texture> orderedPages = orderedPageTextures(atlasData);
            validateLoadedPages(orderedPages, profile);

            skin = new Skin();
            skin.addRegions(atlas);
            skin.load(skinFile);
            validateFonts(skin, orderedPages);

            GLCaps caps = GLCaps.detect();
            caps.validateTextureArray(
                    profile.pageWidth(), profile.pageHeight(), 1 + orderedPages.size);
            bundle = AtlasRuntimeService.buildTextureArrayFromTextures(
                    orderedPages, profile.pageWidth(), profile.pageHeight(), caps);

            HudResources resources = new HudResources(
                    skinId, atlasId, profile, skin, atlas, bundle);
            completed = true;
            return resources;
        } finally {
            if (!completed) disposeFailedPreparation(skin, bundle, atlas);
        }
    }

    private static String requireReference(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "HudResources preparation requires HudScreenAsset." + field + ".");
        }
        return value;
    }

    private static FileHandle resolveRequired(
            FileHandle runtimeProjectDir, String logicalId, String resourceKind) {
        FileHandle file = runtimeProjectDir.child(logicalId);
        if (!file.exists()) {
            throw new IllegalArgumentException("HUD " + resourceKind
                    + " resource does not exist: " + logicalId + ".");
        }
        return file;
    }

    static void validateAtlasMetadata(
            Array<TextureAtlasData.Page> pages, HudTextureProfile profile) {
        if (pages == null || pages.size == 0) {
            throw new IllegalArgumentException("HUD TextureAtlas must contain at least one page.");
        }
        for (int i = 0; i < pages.size; i++) {
            TextureAtlasData.Page page = pages.get(i);
            String context = page.name != null ? "HUD atlas page '" + page.name + "'"
                    : "HUD atlas page " + i;
            if (page.width > 0f && page.height > 0f) {
                AtlasRuntimeService.validateTextureArrayPageDimensions(
                        (int) page.width, (int) page.height,
                        profile.pageWidth(), profile.pageHeight(), context);
            }
            validateSampler(context, page.minFilter, page.magFilter,
                    page.uWrap, page.vWrap, page.useMipMaps, profile);
        }
    }

    static void validateLoadedPages(Array<Texture> pages, HudTextureProfile profile) {
        for (int i = 0; i < pages.size; i++) {
            Texture page = pages.get(i);
            String context = "HUD atlas page " + i;
            AtlasRuntimeService.validateTextureArrayPageDimensions(
                    page.getWidth(), page.getHeight(),
                    profile.pageWidth(), profile.pageHeight(), context);
            validateSampler(context, page.getMinFilter(), page.getMagFilter(),
                    page.getUWrap(), page.getVWrap(),
                    page.getTextureData().useMipMaps(), profile);
        }
    }

    static void validateSampler(String context,
                                Texture.TextureFilter minFilter,
                                Texture.TextureFilter magFilter,
                                Texture.TextureWrap uWrap,
                                Texture.TextureWrap vWrap,
                                boolean useMipMaps,
                                HudTextureProfile profile) {
        if (minFilter != profile.minFilter() || magFilter != profile.magFilter()
                || uWrap != profile.uWrap() || vWrap != profile.vWrap()
                || useMipMaps != profile.useMipMaps()) {
            throw new IllegalStateException(context + " has sampler policy "
                    + minFilter + "/" + magFilter + ", " + uWrap + "/" + vWrap
                    + ", mipmaps=" + useMipMaps + "; expected "
                    + profile.minFilter() + "/" + profile.magFilter() + ", "
                    + profile.uWrap() + "/" + profile.vWrap() + ", mipmaps="
                    + profile.useMipMaps() + " for profile '" + profile.id() + "'.");
        }
    }

    static Array<Texture> orderedPageTextures(TextureAtlasData data) {
        Array<TextureAtlasData.Page> pages = data.getPages();
        Array<Texture> ordered = new Array<Texture>(pages.size);
        for (int i = 0; i < pages.size; i++) {
            Texture texture = pages.get(i).texture;
            if (texture == null) {
                throw new IllegalStateException(
                        "HUD atlas page " + i + " was not loaded.");
            }
            ordered.add(texture);
        }
        return ordered;
    }

    static void validateFonts(Skin skin, Array<Texture> atlasPages) {
        ObjectMap<String, BitmapFont> fonts = skin.getAll(BitmapFont.class);
        if (fonts == null) return;
        for (ObjectMap.Entry<String, BitmapFont> entry : fonts.entries()) {
            Array<TextureRegion> regions = entry.value.getRegions();
            for (int i = 0; i < regions.size; i++) {
                Texture texture = regions.get(i).getTexture();
                if (!atlasPages.contains(texture, true)) {
                    throw new IllegalStateException("HUD BitmapFont '" + entry.key
                            + "' uses a texture that is not part of the declared HUD atlas.");
                }
            }
        }
    }

    private static void disposeFailedPreparation(
            Skin skin, AtlasRuntimeService.TextureArrayBundle bundle, TextureAtlas atlas) {
        try {
            if (skin != null) skin.dispose();
        } catch (RuntimeException ignored) {
            // Preserve the preparation failure while continuing best-effort cleanup.
        }
        try {
            if (bundle != null && bundle.textureArray != null) bundle.textureArray.dispose();
        } catch (RuntimeException ignored) {
            // Preserve the preparation failure while continuing best-effort cleanup.
        }
        try {
            if (atlas != null) atlas.dispose();
        } catch (RuntimeException ignored) {
            // Preserve the preparation failure.
        }
    }

    public String skinId() {
        requireOpen();
        return skinId;
    }

    public String atlasId() {
        requireOpen();
        return atlasId;
    }

    public HudTextureProfile textureProfile() {
        requireOpen();
        return textureProfile;
    }

    public Skin skin() {
        requireOpen();
        return skin;
    }

    public TextureAtlas atlas() {
        requireOpen();
        return atlas;
    }

    public AtlasRuntimeService.TextureArrayBundle textureArrayBundle() {
        requireOpen();
        return textureArrayBundle;
    }

    public boolean isDisposed() {
        return disposed;
    }

    private void requireOpen() {
        if (disposed) throw new IllegalStateException("HudResources has been disposed.");
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        try {
            skin.dispose();
        } catch (RuntimeException disposalFailure) {
            failure = disposalFailure;
        }
        try {
            textureArrayBundle.textureArray.dispose();
        } catch (RuntimeException disposalFailure) {
            if (failure == null) failure = disposalFailure;
        }
        try {
            atlas.dispose();
        } catch (RuntimeException disposalFailure) {
            if (failure == null) failure = disposalFailure;
        }
        skin = null;
        textureArrayBundle = null;
        atlas = null;
        if (failure != null) throw failure;
    }
}
