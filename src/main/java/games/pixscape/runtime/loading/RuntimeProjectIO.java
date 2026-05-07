package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationsRuntimeData;

/**
 * I/O du projet runtime (pixscape-project/project.json).
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

    private RuntimeProjectIO() {}

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
        cfg.glProfile = root.getString("glProfile", cfg.glProfile);
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
            def.frameAssetIds = readIntArray(node.get("frameAssetIds"));
            def.frameDurationsMs = readIntArray(node.get("frameDurationsMs"));

            data.animations.add(def);
        }

        return data;
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
