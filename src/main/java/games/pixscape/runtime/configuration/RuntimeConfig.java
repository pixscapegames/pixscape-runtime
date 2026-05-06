package games.pixscape.runtime.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.ShaderMode;

/**
 * Exported runtime configuration (project.json on user project side).
 * Ne contient aucun champ studio.
 */
public final class RuntimeConfig {

    public static final String DEFAULT_VERSION = "1";

    /** Technical identity of exported project. */
    public String projectFileName = "";

    public String version = DEFAULT_VERSION;

    /** Optional: can be inferred from FileHandle on engine side. */
    public String runtimeRootDir;

    public String scenesDir = RuntimeFs.DIR_SCENES;
    public String atlasesDir = RuntimeFs.DIR_ATLASES;
    public String effectsDir = RuntimeFs.DIR_EFFECTS;
    public String animationsDir = RuntimeFs.DIR_ANIMATIONS;
    public String shadersDir = RuntimeFs.DIR_SHADERS;
    public String audioDir = RuntimeFs.DIR_AUDIO;
    public String prefabsDir = RuntimeFs.DIR_PREFABS;

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
        if ("GL30".equals(glProfile)) return ShaderMode.TEXTURE_ARRAY;
        if ("GL20".equals(glProfile)) return ShaderMode.TEXTURE_2D;
        return ShaderMode.MULTI_TEXTURE;
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
        for (ObjectMap.Entries<String, SceneMetaRuntime> it = scenes.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, SceneMetaRuntime> e = it.next();
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
        for (ObjectMap.Entries<String, SceneMetaRuntime> it = scenes.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, SceneMetaRuntime> e = it.next();
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

        scenesDir = nonBlankOrDefault(scenesDir, RuntimeFs.DIR_SCENES);
        atlasesDir = nonBlankOrDefault(atlasesDir, RuntimeFs.DIR_ATLASES);
        effectsDir = nonBlankOrDefault(effectsDir, RuntimeFs.DIR_EFFECTS);
        animationsDir = nonBlankOrDefault(animationsDir, RuntimeFs.DIR_ANIMATIONS);
        shadersDir = nonBlankOrDefault(shadersDir, RuntimeFs.DIR_SHADERS);
        audioDir = nonBlankOrDefault(audioDir, RuntimeFs.DIR_AUDIO);
        prefabsDir = nonBlankOrDefault(prefabsDir, RuntimeFs.DIR_PREFABS);

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
        for (ObjectMap.Entries<String, SceneMetaRuntime> it = scenes.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, SceneMetaRuntime> e = it.next();
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
            String byFile = findSceneNameByFile(RuntimeFs.FILE_DEFAULT_SCENE);
            currentSceneName = (byFile != null) ? byFile : firstSceneNameSorted();
        }

        if (currentSceneName == null || !scenes.containsKey(currentSceneName)) {
            throw new RuntimeException("Cannot resolve currentSceneName in: " + pathForErrors);
        }
    }

    private static String nonBlankOrDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    // ---------------------------------------------------------------------
    // Path helpers
    // ---------------------------------------------------------------------

    public FileHandle scenesRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, scenesDir);
    }

    public FileHandle atlasesRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, atlasesDir);
    }

    public FileHandle effectsRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, effectsDir);
    }

    public FileHandle shadersRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, shadersDir);
    }

    public FileHandle animationsRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, animationsDir);
    }

    public FileHandle audioRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, audioDir);
    }

    public FileHandle prefabsRoot(FileHandle runtimeProjectDir) {
        return childOrNull(runtimeProjectDir, prefabsDir);
    }

    private static FileHandle childOrNull(FileHandle root, String child) {
        return root != null ? root.child(child) : null;
    }
}
