package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.animation.AnimationsRuntimeData;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationsRuntimeData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;

/**
 * Runtime project I/O (pixscape-project/project.json).
 * Style identical to ProjectIO:
 * - load: parse -> hydrate runtimeRootDir -> applyDefaultsAndValidate
 * - save: ensure dir -> hydrate runtimeRootDir -> applyDefaultsAndValidate -> prettyPrint
 */
public final class RuntimeProjectIO {

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static final String PROJECT_JSON = RuntimeFs.FILE_PROJECT_JSON;

    private static final Json json = new Json();

    static {
        // also write values == defaults (useful for stability / debug)
        json.setUsePrototypes(false);

        // json standard
        json.setOutputType(JsonWriter.OutputType.json);

        // tolerant during migrations
        json.setIgnoreUnknownFields(true);
    }

    private RuntimeProjectIO() {
    }

    private static RuntimeConfig parseRuntimeConfig(JsonValue root) {
        if (root == null || !root.isObject()) return null;

        RuntimeConfig cfg = new RuntimeConfig();
        cfg.projectFileName = root.getString("projectFileName", cfg.projectFileName);
        cfg.version = root.getString("version", cfg.version);
        cfg.runtimeRootDir = root.getString("runtimeRootDir", cfg.runtimeRootDir);
        cfg.scenesDir = root.getString("scenesDir", cfg.scenesDir);
        cfg.atlasesDir = root.getString("atlasesDir", cfg.atlasesDir);
        cfg.effectsDir = root.getString("effectsDir", cfg.effectsDir);
        cfg.animationsDir = root.getString("animationsDir", cfg.animationsDir);
        cfg.shadersDir = root.getString("shadersDir", cfg.shadersDir);
        cfg.audioDir = root.getString("audioDir", cfg.audioDir);
        cfg.prefabsDir = root.getString("prefabsDir", cfg.prefabsDir);
        cfg.currentSceneName = root.getString("currentSceneName", cfg.currentSceneName);
        cfg.glSamples = root.getInt("glSamples", cfg.glSamples);

        JsonValue scenesNode = root.get("scenes");
        if (scenesNode != null && scenesNode.isObject()) {
            for (JsonValue sceneEntry = scenesNode.child; sceneEntry != null; sceneEntry = sceneEntry.next) {
                if (sceneEntry.name == null) continue;
                cfg.scenes.put(sceneEntry.name, SceneMetaRuntime.fromJson(sceneEntry, sceneEntry.name));
            }
        }

        return cfg;
    }

    public static RuntimeConfig loadProject(FileHandle projectDir) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");

        FileHandle file = projectDir.child(PROJECT_JSON);
        if (!file.exists()) {
            throw new GdxRuntimeException("Missing " + PROJECT_JSON + " in: " + projectDir.path());
        }

        final RuntimeConfig cfg;
        try {
            JsonValue root = new JsonReader().parse(file);
            cfg = parseRuntimeConfig(root);
        } catch (Exception e) {
            throw new GdxRuntimeException("Failed to parse " + PROJECT_JSON + ": " + file.path(), e);
        }

        if (cfg == null) {
            throw new GdxRuntimeException("Invalid " + PROJECT_JSON + " (null): " + file.path());
        }

        // Source of truth: the runtime folder (pixscape-project)
        // => set it if absent / empty
        if (cfg.runtimeRootDir == null || isBlank(cfg.runtimeRootDir)) {
            cfg.runtimeRootDir = projectDir.path();
        }

        // IMPORTANT: normalize + validate here (not later)
        cfg.applyDefaultsAndValidate(file.path());

        return cfg;
    }

    public static void loadTileAnimations(FileHandle projectDir,
                                          TileAnimationRegistry registry) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");
        if (registry == null) throw new GdxRuntimeException("registry is null");

        registry.clear();

        FileHandle file = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        if (!file.exists()) {
            return;
        }

        final TileAnimationsRuntimeData data;
        try {
            JsonValue root = new JsonReader().parse(file);
            data = parseTileAnimations(root);
        } catch (Exception e) {
            throw new GdxRuntimeException(
                    "Failed to parse " + RuntimeFs.FILE_TILE_ANIMATIONS_JSON + ": " + file.path(),
                    e
            );
        }

        if (data == null || data.animations == null) {
            return;
        }

        for (int i = 0, n = data.animations.size; i < n; i++) {
            TileAnimationDefData defData = data.animations.get(i);
            if (defData == null) {
                throw new GdxRuntimeException("Invalid tile animation entry (null): " + file.path());
            }
            registry.put(defData);
        }
    }

    public static void loadAnimations(FileHandle projectDir,
                                      AnimationRegistry registry) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");
        if (registry == null) throw new GdxRuntimeException("registry is null");

        registry.clear();

        FileHandle file = projectDir.child(RuntimeFs.FILE_ANIMATIONS_JSON);
        if (!file.exists()) {
            return;
        }

        final AnimationsRuntimeData data;
        try {
            JsonValue root = new JsonReader().parse(file);
            data = parseAnimations(root);
        } catch (Exception e) {
            throw new GdxRuntimeException(
                    "Failed to parse " + RuntimeFs.FILE_ANIMATIONS_JSON + ": " + file.path(),
                    e
            );
        }

        if (data == null || data.animations == null) {
            return;
        }

        for (int i = 0, n = data.animations.size; i < n; i++) {
            AnimationDefData defData = data.animations.get(i);
            if (defData == null) {
                throw new GdxRuntimeException("Invalid animation entry (null): " + file.path());
            }
            registry.put(defData);
        }
    }

    public static RuntimeTilesetProfiles loadTilesetProfiles(FileHandle projectDir) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");

        FileHandle file = projectDir.child(RuntimeFs.FILE_TILESET_PROFILES_JSON);
        if (!file.exists()) {
            return RuntimeTilesetProfiles.empty();
        }

        try {
            JsonValue root = new JsonReader().parse(file);
            return parseTilesetProfiles(root, file.path());
        } catch (Exception e) {
            throw new GdxRuntimeException(
                    "Failed to parse " + RuntimeFs.FILE_TILESET_PROFILES_JSON + ": " + file.path(),
                    e
            );
        }
    }

    private static TileAnimationsRuntimeData parseTileAnimations(JsonValue root) {
        TileAnimationsRuntimeData data = new TileAnimationsRuntimeData();

        if (root == null || !root.isObject()) {
            return data;
        }

        JsonValue animations = root.get("animations");
        if (animations == null || !animations.isArray()) {
            return data;
        }

        for (JsonValue node = animations.child; node != null; node = node.next) {
            if (node == null || !node.isObject()) continue;

            TileAnimationDefData def = new TileAnimationDefData();
            def.id = node.getInt("id", 0);
            def.name = node.getString("name", null);
            def.frameAssetIds = readIntArray(node.get("frameAssetIds"));
            def.frameDurationsMs = readIntArray(node.get("frameDurationsMs"));

            data.animations.add(def);
        }

        return data;
    }

    private static AnimationsRuntimeData parseAnimations(JsonValue root) {
        AnimationsRuntimeData data = new AnimationsRuntimeData();

        if (root == null || !root.isObject()) {
            return data;
        }

        JsonValue animations = root.get("animations");
        if (animations == null || !animations.isArray()) {
            return data;
        }

        for (JsonValue node = animations.child; node != null; node = node.next) {
            if (node == null || !node.isObject()) continue;

            AnimationDefData def = new AnimationDefData();
            def.assetId = node.getInt("assetId", 0);
            def.name = node.getString("name", null);
            def.fps = node.getFloat("fps", 12f);
            def.currentClip = node.getString("currentClip", null);
            def.frameCount = node.getInt("frameCount", 0);

            JsonValue clips = node.get("clips");
            if (clips != null && clips.isArray()) {
                for (JsonValue clipNode = clips.child; clipNode != null; clipNode = clipNode.next) {
                    if (clipNode == null || !clipNode.isObject()) continue;
                    AnimationClipDefData clip = new AnimationClipDefData();
                    clip.name = clipNode.getString("name", null);
                    clip.start = clipNode.getInt("start", 0);
                    clip.end = clipNode.getInt("end", clip.start);
                    clip.flipX = clipNode.getBoolean("flipX", false);
                    def.clips.add(clip);
                }
            }

            data.animations.add(def);
        }

        return data;
    }

    private static RuntimeTilesetProfiles parseTilesetProfiles(JsonValue root, String path) {
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();

        if (root == null || !root.isObject()) {
            throw new GdxRuntimeException("Invalid tileset profiles manifest (root must be object): " + path);
        }

        String format = root.getString("format", null);
        if (!RuntimeTilesetProfiles.FORMAT.equals(format)) {
            throw new GdxRuntimeException("Unsupported tileset profiles format: " + format + " in " + path);
        }

        int version = root.getInt("version", 0);
        if (version != RuntimeTilesetProfiles.VERSION) {
            throw new GdxRuntimeException("Unsupported tileset profiles version: " + version + " in " + path);
        }

        JsonValue tilesets = root.get("tilesets");
        if (tilesets == null) {
            return profiles;
        }
        if (!tilesets.isArray()) {
            throw new GdxRuntimeException("Invalid tileset profiles manifest (tilesets must be array): " + path);
        }

        for (JsonValue node = tilesets.child; node != null; node = node.next) {
            if (node == null || !node.isObject()) continue;
            profiles.add(parseTilesetProfile(node, path));
        }

        return profiles;
    }

    private static RuntimeTilesetProfile parseTilesetProfile(JsonValue node, String path) {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = node.getInt("tilesetId", 0);
        profile.logicalPath = node.getString("logicalPath", null);
        profile.tileWidth = node.getInt("tileWidth", 0);
        profile.tileHeight = node.getInt("tileHeight", 0);
        profile.referenceCellWidth = node.getInt("referenceCellWidth", profile.tileWidth);
        profile.referenceCellHeight = node.getInt("referenceCellHeight", profile.tileHeight);
        profile.projection = parseTiledProjection(node.getString("projection", "orthogonal"), path);
        profile.anchor = parseTilesetAnchor(node.getString("anchor", "top-center"), path);
        profile.offsetX = node.getInt("offsetX", 0);
        profile.offsetY = node.getInt("offsetY", 0);
        profile.renderSize = parseTilesetRenderSize(node.getString("renderSize", "native"), path);
        profile.tileAssetIds = readIntArray(node.get("tileAssetIds"));
        return profile;
    }

    private static SceneMetaRuntime.TiledProjection parseTiledProjection(String raw, String path) {
        if ("isometric".equalsIgnoreCase(raw) || "ISO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ISO;
        }
        if ("orthogonal".equalsIgnoreCase(raw) || "ORTHO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ORTHO;
        }
        throw new GdxRuntimeException("Unsupported tileset projection: " + raw + " in " + path);
    }

    private static RuntimeTilesetAnchor parseTilesetAnchor(String raw, String path) {
        RuntimeTilesetAnchor anchor = RuntimeTilesetAnchor.fromWireName(raw);
        if (anchor == null) {
            throw new GdxRuntimeException("Unsupported tileset anchor: " + raw + " in " + path);
        }
        return anchor;
    }

    private static RuntimeTilesetRenderSize parseTilesetRenderSize(String raw, String path) {
        RuntimeTilesetRenderSize renderSize = RuntimeTilesetRenderSize.fromWireName(raw);
        if (renderSize == null) {
            throw new GdxRuntimeException("Unsupported tileset render size: " + raw + " in " + path);
        }
        return renderSize;
    }

    private static int[] readIntArray(JsonValue array) {
        if (array == null || !array.isArray()) {
            return new int[0];
        }

        int[] out = new int[array.size];

        int i = 0;
        for (JsonValue item = array.child; item != null; item = item.next) {
            out[i++] = item.asInt();
        }

        return out;
    }
}
