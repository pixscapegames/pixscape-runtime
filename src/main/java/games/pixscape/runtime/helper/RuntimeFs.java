package games.pixscape.runtime.helper;

import games.pixscape.runtime.loading.SceneMetaRuntime;

public final class RuntimeFs {

    private RuntimeFs() {
    }

    public static final String EXT_JSON = ".json";
    public static final String EXT_ATLAS = ".atlas";
    public static final String EXT_PREFAB = ".pixprefab";

    public static final String FILE_PROJECT_JSON = "project.json";
    public static final String FILE_DEFAULT_SCENE = "scene1.json";

    public static final String DIR_RUNTIME_PROJECT = "pixscape-project";
    public static final String DIR_SCENES = "scenes";
    public static final String DIR_ATLASES = "atlases";
    public static final String DIR_EFFECTS = "effects";
    public static final String DIR_ANIMATIONS = "animations";
    public static final String DIR_SHADERS = "shaders";
    public static final String DIR_AUDIO = "audio";
    public static final String DIR_PREFABS = "prefabs";

    public static final String RUNTIME_DIR_SHADERS = "shaders";
    public static final String RUNTIME_DIR_SHADER_CORE = RUNTIME_DIR_SHADERS + "/core";
    public static final String RUNTIME_DIR_SHADER_EXAMPLES = RUNTIME_DIR_SHADERS + "/examples";
    public static final String RUNTIME_DIR_SHADER_INCLUDES = RUNTIME_DIR_SHADERS + "/includes";

    public static final String SHADER_VARIANT_DESKTOP_GL30 = "desktop-gl30";
    public static final String SHADER_VARIANT_ES3_WEBGL2 = "es3-webgl2";

    public static final String TEXTURE_ARRAY_POINTLIGHT = "texture-array-pointlight";
    public static final String TEXTURE_ARRAY_CONELIGHT = "texture-array-conelight";

    public static final String FILE_TILE_ANIMATIONS_JSON = "tiled-animations.json";
    public static final String FILE_ANIMATIONS_JSON = "animations.json";
    public static final String FILE_TILESET_PROFILES_JSON = "tileset-profiles.json";

    public static String withExt(String name, String ext) {
        if (name == null) return "";
        if (ext == null || ext.isEmpty()) return name;
        return name.endsWith(ext) ? name : name + ext;
    }

    public static String baseName(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String fileName = (slash >= 0) ? name.substring(slash + 1) : name;
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    public static String filenameOnly(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (slash >= 0) ? path.substring(slash + 1) : path;
    }

    public static String sceneDirName(SceneMetaRuntime meta) {
        if (meta == null) return null;
        String file = meta.getFile();
        if (file == null || file.isEmpty()) return null;

        String trimmed = file;
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        return baseName(trimmed);
    }
}
