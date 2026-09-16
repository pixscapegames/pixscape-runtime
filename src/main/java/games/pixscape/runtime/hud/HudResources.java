package games.pixscape.runtime.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import games.pixscape.runtime.service.AtlasRuntimeService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code INTERNAL} owner of a frozen HUD resource environment.
 *
 * <p>Resource membership and profile selection are frozen after preparation. The contained
 * LibGDX objects remain mutable implementation resources; {@code HudResources} is their sole
 * disposal owner. {@link HudVisualResources} exposes only the borrowed values needed to
 * materialize a HUD document. Shared environments require {@link #select(String)}. The legacy
 * catalog/visual methods are supported only by the standalone {@link #prepare} path, whose
 * single-screen selection is fixed at construction, never inferred from catalog size.</p>
 */
public final class HudResources implements Disposable, HudResourceCatalog, HudVisualResources {
    private final Map<String, Skin> skins;
    private final Map<String, TextureRegion> regions;
    private final HudSelectedResources standaloneSelection;
    private final String atlasId;
    private final HudTextureProfile textureProfile;
    private TextureAtlas atlas;
    private AtlasRuntimeService.TextureArrayBundle textureArrayBundle;
    private boolean disposed;

    private HudResources(String atlasId, HudTextureProfile textureProfile,
                         Map<String, Skin> skins, TextureAtlas atlas,
                         AtlasRuntimeService.TextureArrayBundle textureArrayBundle,
                         boolean standalone, String standaloneSkinId) {
        this.skins = Collections.unmodifiableMap(new LinkedHashMap<String, Skin>(skins));
        this.atlasId = atlasId;
        this.textureProfile = textureProfile;
        this.atlas = atlas;
        this.textureArrayBundle = textureArrayBundle;
        Map<String, TextureRegion> indexed = new LinkedHashMap<String, TextureRegion>();
        if (atlas != null) {
            for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
                // Preserve findRegion(name)'s first-region semantics for indexed atlas entries.
                if (!indexed.containsKey(region.name)) indexed.put(region.name, region);
            }
        }
        regions = Collections.unmodifiableMap(indexed);
        standaloneSelection = standalone ? select(standaloneSkinId) : null;
    }

    /**
     * Synchronously prepares exactly the required HUD resource categories.
     * The returned object exclusively owns every loaded atlas, Skin, and TextureArray bundle.
     */
    public static HudResources prepare(HudScreenAsset asset, FileHandle runtimeProjectDir,
                                       HudResourceRequirements requirements) {
        if (asset == null) throw new IllegalArgumentException("HudScreenAsset is required.");
        if (runtimeProjectDir == null) {
            throw new IllegalArgumentException("Runtime project directory is required.");
        }
        if (requirements == null) {
            throw new IllegalArgumentException("HudResourceRequirements is required.");
        }
        asset.validate();
        String skinId = HudResourceId.normalizeOptional(asset.skinId, "Skin");
        String atlasId = HudResourceId.normalizeOptional(asset.atlasId, "TextureAtlas");
        if (requirements.requiresSkin()) skinId = requireReference(skinId, "skinId");
        if (requirements.requiresAtlas()) atlasId = requireReference(atlasId, "atlasId");
        String profileId = HudTextureProfile.normalizeIdOrDefault(asset.textureProfileId);
        return prepareEnvironment(runtimeProjectDir,
                requirements.requiresAtlas() ? atlasId : null, profileId,
                requirements.requiresSkin() ? Collections.singletonList(skinId)
                        : Collections.<String>emptyList(), true,
                requirements.requiresSkin() ? skinId : null);
    }

    /**
     * Prepares one shared atlas/bundle and independent Skins in supplied iteration order.
     * IDs use the same normalization as HudScreenAsset. Duplicate canonical IDs are rejected
     * before graphics allocation. Null/blank atlasId is valid only for a skinless environment;
     * each Skin specification must be nonblank. No implicit screen selection is provided.
     * The returned environment owns all loaded objects; failed preparation cleans them up.
     */
    public static HudResources prepareEnvironment(FileHandle runtimeProjectDir, String atlasId,
                                                  String textureProfileId, Iterable<String> skinIds) {
        return prepareEnvironment(runtimeProjectDir, atlasId, textureProfileId, skinIds, false, null);
    }

    private static HudResources prepareEnvironment(FileHandle runtimeProjectDir, String atlasId,
                                                   String textureProfileId, Iterable<String> skinIds,
                                                   boolean standalone, String standaloneSkinId) {
        if (runtimeProjectDir == null) throw new IllegalArgumentException("Runtime project directory is required.");
        if (skinIds == null) throw new IllegalArgumentException("Skin specifications are required.");
        atlasId = HudResourceId.normalizeOptional(atlasId, "TextureAtlas");
        HudTextureProfile profile = HudTextureProfile.forId(textureProfileId);
        Map<String, FileHandle> skinFiles = new LinkedHashMap<String, FileHandle>();
        for (String specification : skinIds) {
            String id = HudResourceId.normalizeOptional(specification, "Skin");
            if (id == null) throw new IllegalArgumentException("Skin specification ID must be nonblank.");
            if (skinFiles.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate canonical HUD Skin ID: " + id + ".");
            }
            skinFiles.put(id, runtimeProjectDir.child(id));
        }
        if (!skinFiles.isEmpty() && atlasId == null) {
            throw new IllegalArgumentException("HUD Skin preparation requires a shared atlasId.");
        }
        for (String id : skinFiles.keySet()) resolveRequired(runtimeProjectDir, id, "Skin");
        TextureAtlasData atlasData = null;
        if (atlasId != null) {
            validateOutputFormat(profile);
            FileHandle atlasFile = resolveRequired(runtimeProjectDir, atlasId, "TextureAtlas");
            atlasData = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
            validateAtlasMetadata(atlasData.getPages(), profile);
        }

        TextureAtlas atlas = null;
        Map<String, Skin> skins = new LinkedHashMap<String, Skin>();
        AtlasRuntimeService.TextureArrayBundle bundle = null;
        boolean completed = false;
        try {
            Array<Texture> orderedPages = null;
            if (atlasData != null) {
                atlas = new TextureAtlas(atlasData);
                orderedPages = orderedPageTextures(atlasData);
                validateLoadedPages(orderedPages, profile);
            }

            for (Map.Entry<String, FileHandle> entry : skinFiles.entrySet()) {
                Skin skin = new Skin();
                skins.put(entry.getKey(), skin); // Own even a partially loaded Skin on failure.
                skin.addRegions(atlas);
                skin.load(new HudSkinFile(runtimeProjectDir, entry.getKey()));
                validateFonts(skin, orderedPages);
            }

            if (orderedPages != null) {
                GLCaps caps = GLCaps.detect();
                caps.validateTextureArray(
                        profile.pageWidth(), profile.pageHeight(), 1 + orderedPages.size);
                bundle = AtlasRuntimeService.buildTextureArrayFromTextures(
                        orderedPages, profile.pageWidth(), profile.pageHeight(), caps);
            }

            HudResources resources = new HudResources(
                    atlasData != null ? atlasId : null,
                    profile, skins, atlas, bundle, standalone, standaloneSkinId);
            completed = true;
            return resources;
        } finally {
            if (!completed) disposeFailedPreparation(skins, bundle, atlas);
        }
    }

    private static String requireReference(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "HudResources preparation requires HudScreenAsset." + field + ".");
        }
        return value;
    }

    private static void validateOutputFormat(HudTextureProfile profile) {
        if (profile.outputFormat() != Pixmap.Format.RGBA8888) {
            throw new IllegalArgumentException("HUD TextureArray preparation supports only "
                    + "RGBA8888 output; profile '" + profile.id() + "' requests "
                    + profile.outputFormat() + ".");
        }
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
            Map<String, Skin> skins, AtlasRuntimeService.TextureArrayBundle bundle, TextureAtlas atlas) {
        for (Skin skin : skins.values()) {
            try {
                skin.dispose();
            } catch (RuntimeException ignored) {
                // Preserve the preparation failure while continuing best-effort cleanup.
            }
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
        return standaloneSelection().skinId();
    }

    /** Frozen canonical Skin membership; contains no mutable Skin objects. */
    public Set<String> skinIds() {
        requireOpen();
        return skins.keySet();
    }

    /** Explicit cold-path selection; null/blank selects a legitimate skinless view. */
    public HudSelectedResources select(String skinId) {
        requireOpen();
        String id = HudResourceId.normalizeOptional(skinId, "Skin");
        Skin skin = id == null ? null : skins.get(id);
        if (id != null && skin == null) {
            throw new IllegalArgumentException("HUD environment has no prepared Skin: " + id + ".");
        }
        return new HudSelectedResources(this, id, skin);
    }

    /** Fixed standalone compatibility only; shared environments must select explicitly. */
    HudSelectedResources standaloneSelection() {
        requireOpen();
        if (standaloneSelection == null) {
            throw new IllegalStateException("Shared HudResources requires an explicit selected view.");
        }
        return standaloneSelection;
    }

    /** O(1) average present/absent lookup; index is frozen with the owning atlas. */
    TextureRegion sharedRegion(String name) {
        requireOpen();
        return regions.get(name);
    }

    public String atlasId() {
        requireOpen();
        return atlasId;
    }

    public HudTextureProfile textureProfile() {
        requireOpen();
        return textureProfile;
    }

    /** Package-private borrowed mutable Skin; valid only while open; do not mutate or dispose. */
    Skin skin() {
        Skin skin = standaloneSelection().skin();
        if (skin == null) throw new IllegalStateException("HudResources has no prepared Skin.");
        return skin;
    }

    /** Package-private borrowed mutable atlas; valid only while open; do not mutate or dispose. */
    TextureAtlas atlas() {
        requireOpen();
        if (atlas == null) {
            throw new IllegalStateException("HudResources has no prepared TextureAtlas.");
        }
        return atlas;
    }

    /** Package-private borrowed bundle, or null when no atlas was required. */
    AtlasRuntimeService.TextureArrayBundle textureArrayBundle() {
        requireOpen();
        return textureArrayBundle;
    }

    /** Returns whether this prepared snapshot satisfies all requested resource categories. */
    public boolean satisfies(HudResourceRequirements requirements) {
        return standaloneSelection().satisfies(requirements);
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public TextureRegion region(String name) {
        return standaloneSelection().region(name);
    }

    @Override
    public Drawable drawable(String name) {
        return standaloneSelection().drawable(name);
    }

    @Override
    public Label.LabelStyle labelStyle(String name) {
        return standaloneSelection().labelStyle(name);
    }

    @Override
    public TextButton.TextButtonStyle textButtonStyle(String name) {
        return standaloneSelection().textButtonStyle(name);
    }

    @Override
    public boolean hasRegion(String name) {
        return standaloneSelection().hasRegion(name);
    }

    @Override
    public boolean hasDrawable(String name) {
        return standaloneSelection().hasDrawable(name);
    }

    @Override
    public boolean hasLabelStyle(String name) {
        return standaloneSelection().hasLabelStyle(name);
    }

    @Override
    public boolean hasTextButtonStyle(String name) {
        return standaloneSelection().hasTextButtonStyle(name);
    }

    void requireOpen() {
        if (disposed) throw new IllegalStateException("HudResources has been disposed.");
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        for (Skin skin : skins.values()) {
            try {
                skin.dispose();
            } catch (RuntimeException disposalFailure) {
                if (failure == null) failure = disposalFailure;
            }
        }
        try {
            if (textureArrayBundle != null && textureArrayBundle.textureArray != null) {
                textureArrayBundle.textureArray.dispose();
            }
        } catch (RuntimeException disposalFailure) {
            if (failure == null) failure = disposalFailure;
        }
        try {
            if (atlas != null) atlas.dispose();
        } catch (RuntimeException disposalFailure) {
            if (failure == null) failure = disposalFailure;
        }
        textureArrayBundle = null;
        atlas = null;
        if (failure != null) throw failure;
    }
}
