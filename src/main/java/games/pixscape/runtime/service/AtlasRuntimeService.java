package games.pixscape.runtime.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureArrayData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.render.InternalTextures;


public class AtlasRuntimeService {

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static final int ATLAS_SIZE = 2048;

    public static int fixedLayerSize() {
        return ATLAS_SIZE;
    }

    protected final ObjectMap<String, TextureAtlas> atlases = new ObjectMap<>();
    protected final ObjectMap<String, TextureArrayBundle> bundles = new ObjectMap<>();
    private final ObjectMap<String, AtlasAssetIndex> indexesByTag = new ObjectMap<>();
    private final ObjectSet<String> ownedAtlasTags = new ObjectSet<>();
    private static final boolean DEBUG_BUNDLE_LIFECYCLE = false;

    public AtlasRuntimeService() {
    }

    // ---------------- load/unload ----------------

    public void load(String tag, FileHandle atlasFile) {
        TextureAtlas atlas = new TextureAtlas(atlasFile);
        load(tag, atlas, true);
        Gdx.app.debug("AtlasService", "Loaded atlas '" + tag + "' from " + atlasFile.path());
    }

    void load(String tag, TextureAtlas atlas) {
        load(tag, atlas, true);
    }

    /**
     * Uses an externally owned atlas without taking disposal ownership.
     *
     * <p>This is the object-reuse seam for AssetManager integration. The owner
     * must keep the atlas alive until it is unloaded from this service.</p>
     */
    public void loadBorrowed(String tag, TextureAtlas atlas) {
        load(tag, atlas, false);
    }

    private void load(String tag, TextureAtlas atlas, boolean owned) {
        if (atlas == null) {
            throw new IllegalArgumentException(
                    "Atlas '" + tag + "' must not be null.");
        }
        AtlasAssetIndex index;
        try {
            index = AtlasAssetIndexBuilder.build(tag, atlas);
            Array<Texture> pageTextures = getPageTextures(atlas);
            for (int i = 0, n = pageTextures.size; i < n; i++) {
                Texture texture = pageTextures.get(i);
                texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            }
        } catch (RuntimeException failure) {
            if (owned) atlas.dispose();
            throw failure;
        }

        TextureAtlas previousAtlas = atlases.get(tag);
        boolean ownedPreviousAtlas = ownedAtlasTags.contains(tag);
        TextureArrayBundle previousBundle = bundles.remove(tag);
        indexesByTag.put(tag, index);
        atlases.put(tag, atlas);
        if (owned) {
            ownedAtlasTags.add(tag);
        } else {
            ownedAtlasTags.remove(tag);
        }

        if (previousAtlas != null && previousAtlas != atlas && ownedPreviousAtlas) {
            previousAtlas.dispose();
        }
        if (previousBundle != null) {
            logBundleEvent("dispose", tag, previousBundle.textureArray);
            previousBundle.textureArray.dispose();
        }
    }

    public void unload(String tag) {
        indexesByTag.remove(tag);
        boolean ownedAtlas = ownedAtlasTags.remove(tag);
        TextureAtlas a = atlases.remove(tag);
        if (a != null && ownedAtlas) a.dispose();
        TextureArrayBundle b = bundles.remove(tag);
        if (b != null) {
            logBundleEvent("dispose", tag, b.textureArray);
            b.textureArray.dispose();
        }
    }

    public void unloadAll() {
        for (ObjectMap.Entries<String, TextureAtlas> it = atlases.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, TextureAtlas> entry = it.next();
            if (ownedAtlasTags.contains(entry.key)) entry.value.dispose();
        }
        atlases.clear();
        ownedAtlasTags.clear();
        for (ObjectMap.Values<TextureArrayBundle> it = bundles.values(); it.hasNext(); ) {
            TextureArrayBundle b = it.next();
            logBundleEvent("dispose", "__all__", b.textureArray);
            b.textureArray.dispose();
        }
        bundles.clear();
        indexesByTag.clear();
        flushDeferredDisposals();
    }

    // ---------------- access ----------------
    /**
     * Returns the precomputed first-region metadata in O(1) average time.
     */
    public AtlasRegionMetadata resolveCached(int assetId, String tag) {
        requirePositiveAssetId(assetId);
        if (tag == null || isBlank(tag)) return null;
        AtlasAssetBinding binding = resolveBinding(assetId, tag);
        return binding != null ? binding.metadata() : null;
    }

    public TextureAtlas getAtlas(String tag) {
        return atlases.get(tag);
    }

    /**
     * Resolves the complete binding for an asset in O(1) average time.
     */
    public AtlasAssetBinding resolveBinding(int assetId, String tag) {
        requirePositiveAssetId(assetId);
        if (tag == null || isBlank(tag)) return null;
        AtlasAssetIndex index = indexesByTag.get(tag);
        return index != null ? index.get(assetId) : null;
    }

    private static void requirePositiveAssetId(int assetId) {
        if (assetId <= 0) {
            throw new IllegalArgumentException(
                    "Asset id must be > 0, got " + assetId + ".");
        }
    }

    int indexBuildRegionVisits(String tag) {
        AtlasAssetIndex index = indexesByTag.get(tag);
        return index != null ? index.buildRegionVisits() : -1;
    }

    /**
     * Returns unique page textures from the atlas, preserving region encounter order.
     *
     * @param atlas source atlas
     * @return unique texture pages referenced by the atlas
     */
    public static Array<Texture> getPageTextures(TextureAtlas atlas) {
        Array<Texture> out = new Array<>();
        if (atlas == null) return out;
        Array<AtlasRegion> regions = atlas.getRegions();
        for (int i = 0, n = regions.size; i < n; i++) {
            AtlasRegion r = regions.get(i);
            Texture t = r.getTexture();
            if (!out.contains(t, true)) {
                out.add(t);
            }
        }
        return out;
    }

    // ---------------- TextureArray bundle ----------------

    public static final class TextureArrayBundle {
        public final TextureArray textureArray;
        public final IntIntMap handle2layer; // handle(Texture) -> layer

        public TextureArrayBundle(TextureArray ta, IntIntMap map) {
            this.textureArray = ta;
            this.handle2layer = map;
        }
    }

    public static TextureArrayBundle buildTextureArrayFromAtlas(TextureAtlas atlas) {
        return buildTextureArrayFromTextures(getPageTextures(atlas));
    }

    /**
     * Builds a {@link TextureArrayBundle} from atlas page textures using libGDX texture-array data.
     *
     * @param textures atlas page textures in stable order
     * @return texture-array bundle with a {@code textureHandle -> layer} mapping
     */
    public static TextureArrayBundle buildTextureArrayFromTextures(Array<Texture> textures) {
        InternalTextures.initIfNeeded();

        // 0) Build source list without the internal white texture.
        Array<Texture> sources = new Array<>(textures != null ? textures.size : 0);
        if (textures != null) {
            Texture whiteTex = InternalTextures.whiteTexture();
            for (int i = 0; i < textures.size; i++) {
                Texture t = textures.get(i);
                if (t == null) continue;
                if (t == whiteTex) continue;
                sources.add(t);
            }
        }

        // 1) Copy each source texture to a pixmap (atlas size is fixed).
        Array<Pixmap> srcs = new Array<>(sources.size);
        for (int i = 0; i < sources.size; i++) {
            Texture t = sources.get(i);
            Pixmap pm = obtainPixmapCopy(t);
            validateAtlasPageSize(pm, t);
            srcs.add(pm);
        }

        // 2) White pixmap (layer 0) in fixed atlas size (required by TextureArray)
        Pixmap whitePm = new Pixmap(ATLAS_SIZE, ATLAS_SIZE, Format.RGBA8888);
        whitePm.setBlending(Pixmap.Blending.None);
        whitePm.setColor(1f, 1f, 1f, 1f);
        whitePm.fill();

        // 3) Normalize all pages to the fixed atlas size.
        Array<Pixmap> normalized = new Array<>(srcs.size);
        for (int i = 0; i < srcs.size; i++) {
            normalized.add(normalizeTo(srcs.get(i), ATLAS_SIZE, ATLAS_SIZE));
        }

        // 4) TextureData[]: index 0 = white (WxH), then normalized pages (WxH).
        TextureData[] data = new TextureData[1 + normalized.size];

        data[0] = new PixmapTextureData(
                whitePm,
                Format.RGBA8888,
                false,
                true // dispose whitePm
        );

        for (int i = 0; i < normalized.size; i++) {
            data[i + 1] = new PixmapTextureData(
                    normalized.get(i),
                    Format.RGBA8888,
                    false,
                    true // dispose normalized[i]
            );
        }

        // 5) Build TextureArray
        TextureArrayData tad = new FileTextureArrayData(Format.RGBA8888, false, data);
        TextureArray ta = new TextureArray(tad);
        ta.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        ta.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

        // 6) Build map: handle -> layer.
        IntIntMap handle2layer = new IntIntMap();

        int whiteHandle = InternalTextures.whiteHandle();
        handle2layer.put(whiteHandle, 0);

        // Layers 1..N map to sources[i].
        for (int i = 0; i < sources.size; i++) {
            Texture page = sources.get(i);
            int handle = TextureRegistry.handleOf(page);

            int layer = i + 1;
            handle2layer.put(handle, layer);
        }

        // 7) Dispose temporary source pixmaps (copies).
        for (int i = 0; i < srcs.size; i++) {
            srcs.get(i).dispose();
        }
        // normalized pixmaps and whitePm are disposed by PixmapTextureData.

        return new TextureArrayBundle(ta, handle2layer);
    }


    // --------------- bundle cache + active tag ---------------

    public TextureArrayBundle getOrBuildBundle(String tag) {
        TextureArrayBundle b = bundles.get(tag);
        if (b != null) return b;
        TextureAtlas a = atlases.get(tag);
        if (a == null) throw new IllegalStateException("No atlas for tag: " + tag);
        b = buildTextureArrayFromAtlas(a);
        bundles.put(tag, b);
        logBundleEvent("build", tag, b.textureArray);
        return b;
    }

    /**
     * Rebuilds the cached texture-array bundle for an atlas tag.
     * Call this only at explicit safe points such as atlas reload barriers.
     *
     * @param tag atlas tag to rebuild
     * @return rebuilt bundle, or {@code null} when {@code tag} is empty
     */
    public TextureArrayBundle rebuildBundle(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        TextureAtlas atlas = atlases.get(tag);
        if (atlas == null) {
            throw new IllegalStateException("No atlas for tag: " + tag);
        }

        TextureArrayBundle previous = bundles.get(tag);
        TextureArrayBundle rebuilt = buildTextureArrayFromAtlas(atlas);
        bundles.put(tag, rebuilt);
        logBundleEvent(previous == null ? "build" : "rebuild", tag, rebuilt.textureArray);
        return rebuilt;
    }

    public TextureArrayBundle bundle(String tag) {
        return bundles.get(tag);
    }

    private final Array<TextureArrayBundle> deferredDisposals = new Array<>();

    public void deferDispose(TextureArrayBundle bundle) {
        if (bundle == null) return;
        deferredDisposals.add(bundle);
    }

    public void flushDeferredDisposals() {
        if (deferredDisposals.isEmpty()) return;
        for (int i = 0, n = deferredDisposals.size; i < n; i++) {
            TextureArrayBundle bundle = deferredDisposals.get(i);
            if (bundle != null && bundle.textureArray != null) {
                logBundleEvent("dispose", "__deferred__", bundle.textureArray);
                bundle.textureArray.dispose();
            }
        }
        deferredDisposals.clear();
    }

    // ---------------- pixmap helpers ----------------

    private static Pixmap obtainPixmapCopy(Texture tex) {
        TextureData td = tex.getTextureData();
        if (!td.isPrepared()) td.prepare();

        Pixmap src = td.consumePixmap();
        Pixmap copy = new Pixmap(src.getWidth(), src.getHeight(), src.getFormat());
        copy.setBlending(Pixmap.Blending.None);
        copy.drawPixmap(src, 0, 0);

        if (td.disposePixmap()) src.dispose();
        return copy;
    }

    private static Pixmap normalizeTo(Pixmap pm, int W, int H) {
        // Always create a new pixmap with size (W, H).
        Pixmap out = new Pixmap(W, H, Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        out.drawPixmap(pm, 0, 0);
        // Do not dispose here: disposal is centralized later.
        return out;
    }

    private static void validateAtlasPageSize(Pixmap pm, Texture sourceTexture) {
        if (pm.getWidth() > ATLAS_SIZE || pm.getHeight() > ATLAS_SIZE) {
            throw new IllegalStateException(
                    "Atlas page exceeds fixed size " + ATLAS_SIZE + "x" + ATLAS_SIZE
                            + " for texture " + sourceTexture
                            + " (" + pm.getWidth() + "x" + pm.getHeight() + ")"
            );
        }
    }

    public Array<String> listTags() {
        Array<String> out = new Array<>(atlases.size);
        for (ObjectMap.Keys<String> it = atlases.keys(); it.hasNext; ) {
            out.add(it.next());
        }
        out.sort();
        return out;
    }

    private void logBundleEvent(String action, String tag, TextureArray textureArray) {
        if (!DEBUG_BUNDLE_LIFECYCLE || textureArray == null) return;
        Gdx.app.debug("AtlasService", action
                + " bundle tag=" + tag
                + " textureArray@" + System.identityHashCode(textureArray));
    }

}
