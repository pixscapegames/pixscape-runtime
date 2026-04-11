package games.pixscape.runtime.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;

/**
 * Exported runtime configuration (project.json on user project side).
 * Ne contient aucun champ studio.
 */
public final class RuntimeConfig {

    public static final String DEFAULT_VERSION = "1";
    public static final String DEFAULT_SCENES_DIR = RuntimeFs.DIR_SCENES;
    public static final String DEFAULT_ATLASES_DIR = RuntimeFs.DIR_ATLASES;
    public static final String DEFAULT_EFFECTS_DIR = RuntimeFs.DIR_EFFECTS;
    public static final String DEFAULT_ANIMATIONS_DIR = RuntimeFs.DIR_ANIMATIONS;
    public static final String DEFAULT_SHADERS_DIR = RuntimeFs.DIR_SHADERS;
    public static final String DEFAULT_AUDIO_DIR = RuntimeFs.DIR_AUDIO;
    public static final String DEFAULT_SCENE_FILE = RuntimeFs.FILE_DEFAULT_SCENE;

    /** Technical identity of exported project. */
    public String projectFileName = "";

    public String version = DEFAULT_VERSION;

    /** Optional: can be inferred from FileHandle on engine side. */
    public String runtimeRootDir;

    public String scenesDir = DEFAULT_SCENES_DIR;
    public String atlasesDir = DEFAULT_ATLASES_DIR;
    public String effectsDir = DEFAULT_EFFECTS_DIR;
    public String animationsDir = DEFAULT_ANIMATIONS_DIR;
    public String shadersDir = DEFAULT_SHADERS_DIR;
    public String audioDir = DEFAULT_AUDIO_DIR;

    // --- Runtime scenes ---
    public final ObjectMap<String, SceneMetaRuntime> scenes = new ObjectMap<>();
    public String currentSceneName;

    // --- Options projet (runtime) ---
    public String glProfile = "GL30";
    public int glSamples = 0;

    // ---------------------------------------------------------------------
    // Shader mode
    // ---------------------------------------------------------------------

    public ShaderMode getShaderMode() {
        return switch (glProfile) {
            case "GL30" -> ShaderMode.TEXTURE_ARRAY;
            case "GL20" -> ShaderMode.SPRITE;
            default -> ShaderMode.MULTI_TEXTURE;
        };
    }

    // ---------------------------------------------------------------------
    // Scenes
    // ---------------------------------------------------------------------

    public SceneMetaRuntime getCurrentSceneMeta() {
        if (currentSceneName == null) return null;
        return scenes.get(currentSceneName);
    }

    public SceneMetaRuntime getSceneMeta(String name) {
        return scenes.get(name);
    }

    public Array<String> getSceneNamesSorted() {
        Array<String> names = new Array<>();
        for (ObjectMap.Entry<String, SceneMetaRuntime> e : scenes) {
            names.add(e.key);
        }
        names.sort(String::compareTo);
        return names;
    }

    /** scene1.json -> scene1 */
    public static String sceneDirName(SceneMetaRuntime meta) {
        return RuntimeFs.sceneDirName(meta);
    }

    public String getSceneDirName(String sceneName) {
        return sceneDirName(getSceneMeta(sceneName));
    }

    public String findSceneNameByFile(String file) {
        if (file == null || file.isBlank()) return null;

        String expected = RuntimeFs.filenameOnly(file);
        for (ObjectMap.Entry<String, SceneMetaRuntime> e : scenes) {
            SceneMetaRuntime meta = e.value;
            if (meta == null) continue;
            if (expected.equals(RuntimeFs.filenameOnly(meta.file))) {
                return e.key;
            }
        }
        return null;
    }

    public String firstSceneNameSorted() {
        Array<String> names = getSceneNamesSorted();
        return names.size > 0 ? names.first() : null;
    }

    // ---------------------------------------------------------------------
    // Defaults + validation
    // ---------------------------------------------------------------------

    public void applyDefaultsAndValidate(String pathForErrors) {
        if (pathForErrors == null || pathForErrors.isBlank()) {
            pathForErrors = "<runtime-config>";
        }

        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }

        if (projectFileName == null || projectFileName.isBlank()) {
            throw new RuntimeException("Missing projectFileName in: " + pathForErrors);
        }

        if (scenesDir == null || scenesDir.isBlank()) scenesDir = DEFAULT_SCENES_DIR;
        if (atlasesDir == null || atlasesDir.isBlank()) atlasesDir = DEFAULT_ATLASES_DIR;
        if (effectsDir == null || effectsDir.isBlank()) effectsDir = DEFAULT_EFFECTS_DIR;
        if (animationsDir == null || animationsDir.isBlank()) animationsDir = DEFAULT_ANIMATIONS_DIR;
        if (shadersDir == null || shadersDir.isBlank()) shadersDir = DEFAULT_SHADERS_DIR;
        if (audioDir == null || audioDir.isBlank()) audioDir = DEFAULT_AUDIO_DIR;

        if (!DEFAULT_VERSION.equals(version)) {
            throw new RuntimeException("Unsupported project version '" + version + "' in: " + pathForErrors);
        }

        if (!"GL20".equals(glProfile) && !"GL30".equals(glProfile)) {
            glProfile = "GL30";
        }

        if (glSamples != 0 && glSamples != 2 && glSamples != 4 && glSamples != 8) {
            glSamples = 0;
        }

        if (scenes.size == 0) {
            throw new RuntimeException("No scenes in runtime config: " + pathForErrors);
        }

        // Nettoyage scenes + normalisation file + defaults
        for (ObjectMap.Entry<String, SceneMetaRuntime> e : scenes) {
            String key = e.key;
            SceneMetaRuntime meta = e.value;

            if (meta == null) {
                throw new RuntimeException("Scene '" + key + "' is null in: " + pathForErrors);
            }

            if (meta.name == null || meta.name.isBlank()) {
                meta.name = key;
            }

            if (meta.file == null || meta.file.isBlank()) {
                throw new RuntimeException("Scene '" + key + "' has no file in: " + pathForErrors);
            }

            meta.file = RuntimeFs.filenameOnly(meta.file);

            if (meta.file == null || meta.file.isBlank()) {
                throw new RuntimeException("Scene '" + key + "' has invalid file in: " + pathForErrors);
            }

            if (meta.pixelsPerMeter <= 0f) {
                meta.pixelsPerMeter = 100f;
            }
        }

        // Choix scene courante by default :
        // 1) currentSceneName valide
        // 2) scene dont file == scene1.json
        // 3) first sorted
        if (currentSceneName == null || !scenes.containsKey(currentSceneName)) {
            String byFile = findSceneNameByFile(DEFAULT_SCENE_FILE);
            currentSceneName = (byFile != null) ? byFile : firstSceneNameSorted();
        }

        if (currentSceneName == null || !scenes.containsKey(currentSceneName)) {
            throw new RuntimeException("Cannot resolve currentSceneName in: " + pathForErrors);
        }
    }

    // ---------------------------------------------------------------------
    // Path helpers
    // ---------------------------------------------------------------------

    public FileHandle scenesRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(scenesDir) : null;
    }

    public FileHandle atlasesRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(atlasesDir) : null;
    }

    public FileHandle effectsRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(effectsDir) : null;
    }

    public FileHandle shadersRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(shadersDir) : null;
    }

    public FileHandle animationsRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(animationsDir) : null;
    }

    public FileHandle audioRoot(FileHandle runtimeProjectDir) {
        return runtimeProjectDir != null ? runtimeProjectDir.child(audioDir) : null;
    }
}