package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
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
        return s == null || s.trim().isEmpty();
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

    public static RuntimeConfig loadProject(FileHandle projectDir) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");

        FileHandle file = projectDir.child(PROJECT_JSON);
        if (!file.exists()) {
            throw new GdxRuntimeException("Missing " + PROJECT_JSON + " in: " + projectDir.path());
        }

        final RuntimeConfig cfg;
        try {
            cfg = json.fromJson(RuntimeConfig.class, file);
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
            data = json.fromJson(TileAnimationsRuntimeData.class, file);
        } catch (Exception e) {
            throw new GdxRuntimeException(
                    "Failed to parse " + RuntimeFs.FILE_TILE_ANIMATIONS_JSON + ": " + file.path(),
                    e
            );
        }

        if (data == null) {
            throw new GdxRuntimeException(
                    "Invalid " + RuntimeFs.FILE_TILE_ANIMATIONS_JSON + " (null): " + file.path()
            );
        }

        if (data.animations == null) {
            return;
        }

        for (int i = 0, n = data.animations.size; i < n; i++) {
            TileAnimationDefData defData = data.animations.get(i);
            if (defData == null) {
                throw new GdxRuntimeException(
                        "Invalid tile animation entry (null): " + file.path()
                );
            }
            registry.put(defData);
        }
    }
}
