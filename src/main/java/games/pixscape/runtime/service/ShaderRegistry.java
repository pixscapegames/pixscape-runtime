package games.pixscape.runtime.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.batch.GLCaps;

/**
 * Runtime shader registry.
 *
 * The registry is initialized for one platform target, then runtime lookups remain simple:
 *
 *   name -> index
 *   index -> ShaderProgram
 *
 * Platform-specific shader selection happens at load time, not at every lookup.
 *
 * Built-in shader profiles:
 *   - assets/shaders/330   : Desktop GL30
 *   - assets/shaders/300es : Android ES3 / HTML WebGL2
 *   - assets/shaders/100   : legacy fallback for AUTO only
 *
 * Custom shader formats:
 *
 * Legacy:
 *   orig/shaders/<modeDir>/<name>.frag
 *   orig/shaders/<modeDir>/<name>.vert optional
 *   orig/shaders/<modeDir>/fx/<name>.frag
 *   orig/shaders/<modeDir>/fx/<name>.vert optional
 *
 * Structured:
 *   orig/shaders/custom/material/<name>/shader.json optional
 *   orig/shaders/custom/material/<name>/desktop.vert
 *   orig/shaders/custom/material/<name>/desktop.frag
 *   orig/shaders/custom/material/<name>/es.vert
 *   orig/shaders/custom/material/<name>/es.frag
 *
 *   orig/shaders/custom/fx/<name>/...
 */
public final class ShaderRegistry {

    private static final ShaderRegistry INSTANCE = new ShaderRegistry();

    private static final ObjectIntMap<String> nameToIdx = new ObjectIntMap<>();
    private static final Array<ShaderProgram> byIdx = new Array<>();
    private static final Array<ShaderMode> modesByIdx = new Array<>();
    private static final Array<Boolean> isFxByIdx = new Array<>();
    private static final ObjectMap<String, ObjectFloatMap<String>> defaultUniforms = new ObjectMap<>();

    private static final String SHADERS_100 = "assets/shaders/100";
    private static final String SHADERS_300_ES = "assets/shaders/300es";
    private static final String SHADERS_330 = "assets/shaders/330";

    private static final String DEMOS_ROOT = "assets/shaders/demos";

    private static boolean initialized = false;

    private static GLCaps caps;

    private static PlatformTarget requestedPlatformTarget = PlatformTarget.AUTO;
    private static FileHandle projectShadersRoot = null;

    private static String cachedProfileDir = null;
    private static String cachedGlProfile = null;

    private ShaderRegistry() {
    }

    public static ShaderRegistry getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static int register(String name, ShaderProgram sp, ShaderMode mode, boolean fx) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shader name is empty");
        }
        if (sp == null) {
            throw new IllegalArgumentException("ShaderProgram is null for '" + name + "'");
        }
        if (mode == null) {
            throw new IllegalArgumentException("ShaderMode is null for '" + name + "'");
        }

        int existing = nameToIdx.get(name, -1);
        if (existing >= 0) {
            return existing;
        }

        int idx = byIdx.size;
        byIdx.add(sp);
        modesByIdx.add(mode);
        isFxByIdx.add(fx);
        nameToIdx.put(name, idx);
        return idx;
    }

    public static int register(String name, ShaderProgram sp, ShaderMode mode) {
        return register(name, sp, mode, false);
    }

    public static Array<String> getRegisteredNames() {
        return nameToIdx.keys().toArray();
    }

    public static Array<String> getNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();

        for (ObjectIntMap.Entries<String> it = nameToIdx.entries(); it.hasNext(); ) {
            ObjectIntMap.Entry<String> entry = it.next();
            int idx = entry.value;

            if (idx >= 0 && idx < modesByIdx.size && modesByIdx.get(idx) == mode) {
                result.add(entry.key);
            }
        }

        result.sort(String::compareTo);
        return result;
    }

    public static Array<String> getMainNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();

        for (ObjectIntMap.Entries<String> it = nameToIdx.entries(); it.hasNext(); ) {
            ObjectIntMap.Entry<String> entry = it.next();
            int idx = entry.value;

            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;

            boolean fx = idx < isFxByIdx.size && Boolean.TRUE.equals(isFxByIdx.get(idx));
            if (!fx) {
                result.add(entry.key);
            }
        }

        result.sort(String::compareTo);
        return result;
    }

    public static Array<String> getFxNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();

        for (ObjectIntMap.Entries<String> it = nameToIdx.entries(); it.hasNext(); ) {
            ObjectIntMap.Entry<String> entry = it.next();
            int idx = entry.value;

            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;

            boolean fx = idx < isFxByIdx.size && Boolean.TRUE.equals(isFxByIdx.get(idx));
            if (fx) {
                result.add(entry.key);
            }
        }

        result.sort(String::compareTo);
        return result;
    }

    public static ShaderProgram getByIdx(int idx) {
        if (idx < 0 || idx >= byIdx.size) return null;
        return byIdx.get(idx);
    }

    public static ShaderProgram get(String name) {
        int idx = nameToIdx.get(name, -1);
        return idx >= 0 ? byIdx.get(idx) : null;
    }

    public static int indexOf(String name) {
        return nameToIdx.get(name, -1);
    }

    public static String getName(Integer idx) {
        if (idx == null) return null;
        return nameToIdx.findKey(idx);
    }

    public static String getName(ShaderProgram sp) {
        if (sp == null) return null;

        for (int i = 0, n = byIdx.size; i < n; i++) {
            if (byIdx.get(i) == sp) {
                return getName(i);
            }
        }

        return null;
    }

    public static ShaderMode getMode(int idx) {
        if (idx < 0 || idx >= modesByIdx.size) return null;
        return modesByIdx.get(idx);
    }

    public static ObjectFloatMap<String> getDefaultUniforms(String shaderName) {
        return defaultUniforms.get(shaderName);
    }

    public static PlatformTarget getCurrentPlatformTarget() {
        return requestedPlatformTarget;
    }

    public static String getCurrentProfileDir() {
        return getProfileDir();
    }

    public static String getCurrentGlProfile() {
        return getGlProfile();
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    public static void disposeAll() {
        for (int i = 0, n = byIdx.size; i < n; i++) {
            ShaderProgram sp = byIdx.get(i);
            if (sp != null) {
                sp.dispose();
            }
        }

        byIdx.clear();
        nameToIdx.clear();
        modesByIdx.clear();
        isFxByIdx.clear();
        defaultUniforms.clear();

        initialized = false;

        requestedPlatformTarget = PlatformTarget.AUTO;
        projectShadersRoot = null;

        cachedProfileDir = null;
        cachedGlProfile = null;

        caps = null;
    }

    public static void reloadForProject(PlatformTarget target, FileHandle projectDir, String shadersDir) {
        disposeAll();
        setProjectContext(target, projectDir, shadersDir);
        initDefaults();
    }

    public static void initDefaults(PlatformTarget target, FileHandle projectDir, String shadersDir) {
        setProjectContext(target, projectDir, shadersDir);
        initDefaults();
    }

    /**
     * Legacy overload.
     * Kept temporarily so existing Studio/runtime calls keep compiling.
     *
     * GL20/GL30 no longer defines the export/runtime platform target.
     * AUTO preserves the old behavior by detecting the actual runtime backend.
     */
    @Deprecated
    public static void reloadForProject(String glProfile, FileHandle projectDir, String shadersDir) {
        reloadForProject(legacyGlProfileToPlatformTarget(glProfile), projectDir, shadersDir);
    }

    @Deprecated
    public static void initDefaults(String glProfile, FileHandle projectDir, String shadersDir) {
        initDefaults(legacyGlProfileToPlatformTarget(glProfile), projectDir, shadersDir);
    }

    public static void initDefaults() {
        if (initialized) return;

        ShaderProgram.pedantic = false;

        GLCaps c = caps();

        String profileDir = getProfileDir();
        String glProfile = getGlProfile();

        log("ShaderRegistry", "Init defaults with target=" + requestedPlatformTarget
                + " profileDir=" + profileDir
                + " glProfile=" + glProfile
                + " caps=" + c);

        ShaderProgram sprite = compileShader(
                profileDir + "/sprite.vert",
                profileDir + "/sprite.frag",
                "sprite/default",
                true
        );
        registerOrReplace("default", sprite, ShaderMode.TEXTURE_2D, false);

        loadOptionalDefaultShader(profileDir, ShaderMode.MULTI_TEXTURE, "mt_default");

        if (isModeSupportedForCurrentProfile(ShaderMode.TEXTURE_ARRAY)) {
            loadOptionalDefaultShader(profileDir, ShaderMode.TEXTURE_ARRAY, "ta_default");
        }

        loadCustomShadersForProject();
        loadBuiltinDemoShaders();

        initialized = true;
    }

    // ------------------------------------------------------------------------
    // Custom shader API
    // ------------------------------------------------------------------------

    public static void testCompile(String name,
                                   String vertexSource,
                                   String fragmentSource,
                                   ShaderMode mode) {
        requireModeSupported(mode);

        if (vertexSource == null || vertexSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Vertex shader source is empty for '" + name + "'");
        }
        if (fragmentSource == null || fragmentSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Fragment shader source is empty for '" + name + "'");
        }

        ShaderProgram.pedantic = false;

        ShaderProgram sp = new ShaderProgram(vertexSource, fragmentSource);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile custom shader '" + name + "' (" + mode + "):\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }

        sp.dispose();
    }

    public static void testCompile(String name, String fragmentSource, ShaderMode mode) {
        requireModeSupported(mode);

        if (fragmentSource == null || fragmentSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Fragment shader source is empty for '" + name + "'");
        }

        FileHandle vertFile = getVertexShaderForMode(mode);
        if (!vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for mode " + mode + ": " + vertFile.path());
        }

        testCompile(name, vertFile.readString("UTF-8"), fragmentSource, mode);
    }

    public static int registerCustomShader(String name,
                                           FileHandle fragFile,
                                           ShaderMode mode,
                                           boolean fx) {
        requireModeSupported(mode);

        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Fragment shader file does not exist for '" + name + "': "
                    + (fragFile != null ? fragFile.path() : "null"));
        }

        FileHandle vertFile = getVertexShaderForMode(mode);
        if (!vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for mode " + mode + ": " + vertFile.path());
        }

        return registerCustomShader(name, vertFile, fragFile, mode, fx);
    }

    public static int registerCustomShader(String name,
                                           FileHandle vertFile,
                                           FileHandle fragFile,
                                           ShaderMode mode,
                                           boolean fx) {
        requireModeSupported(mode);

        if (vertFile == null || !vertFile.exists()) {
            throw new IllegalArgumentException("Vertex shader file does not exist for '" + name + "': "
                    + (vertFile != null ? vertFile.path() : "null"));
        }
        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Fragment shader file does not exist for '" + name + "': "
                    + (fragFile != null ? fragFile.path() : "null"));
        }

        ShaderProgram.pedantic = false;

        String vertSrc = vertFile.readString("UTF-8");
        String fragSrc = fragFile.readString("UTF-8");

        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile custom shader '" + name + "' (" + mode + ", fx=" + fx + ") from "
                    + vertFile.path() + " / " + fragFile.path() + ":\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }

        int idx = registerOrReplace(name, sp, mode, fx);

        log("ShaderRegistry", "Registered custom shader '" + name + "' (" + mode + ", fx=" + fx
                + ") at index " + idx + " from files " + vertFile.path() + " / " + fragFile.path());

        return idx;
    }

    public static int registerCustomShader(String name, FileHandle vertFile, FileHandle fragFile, ShaderMode mode) {
        return registerCustomShader(name, vertFile, fragFile, mode, false);
    }

    public static int registerCustomShader(String name, FileHandle fragFile, ShaderMode mode) {
        return registerCustomShader(name, fragFile, mode, false);
    }

    // ------------------------------------------------------------------------
    // Context / platform resolution
    // ------------------------------------------------------------------------

    private static void setProjectContext(PlatformTarget target, FileHandle projectDir, String shadersDir) {
        requestedPlatformTarget = target != null ? target : PlatformTarget.AUTO;

        if (projectDir != null && shadersDir != null && !shadersDir.isBlank()) {
            projectShadersRoot = projectDir.child(shadersDir);
        } else {
            projectShadersRoot = null;
        }

        cachedProfileDir = null;
        cachedGlProfile = null;
    }

    private static PlatformTarget legacyGlProfileToPlatformTarget(String glProfile) {
        return PlatformTarget.AUTO;
    }

    private static GLCaps caps() {
        if (caps == null) {
            caps = GLCaps.detect();
        }
        return caps;
    }

    private static String getProfileDir() {
        if (cachedProfileDir != null) return cachedProfileDir;

        caps();

        switch (requestedPlatformTarget) {
            case DESKTOP_GL30:
                cachedProfileDir = SHADERS_330;
                cachedGlProfile = "GL30";
                return cachedProfileDir;

            case ANDROID_ES3:
            case HTML_WEBGL2:
                cachedProfileDir = SHADERS_300_ES;
                cachedGlProfile = "GL30";
                return cachedProfileDir;

            case AUTO:
            default:
                return detectProfileDir();
        }
    }

    private static String detectProfileDir() {
        GLCaps c = caps();

        GLVersion glVersion = Gdx.graphics.getGLVersion();
        boolean isGles = glVersion.getType() == GLVersion.Type.GLES;

        if (c.supportsES3()) {
            cachedProfileDir = isGles ? SHADERS_300_ES : SHADERS_330;
            cachedGlProfile = "GL30";
        } else {
            cachedProfileDir = SHADERS_100;
            cachedGlProfile = "GL20";
        }

        return cachedProfileDir;
    }

    private static String getGlProfile() {
        if (cachedGlProfile == null) {
            getProfileDir();
        }

        return cachedGlProfile != null ? cachedGlProfile : "GL30";
    }

    private static String getStructuredVariantPrefixForCurrentProfile() {
        String profileDir = getProfileDir();

        if (SHADERS_330.equals(profileDir)) {
            return "desktop";
        }
        if (SHADERS_300_ES.equals(profileDir)) {
            return "es";
        }

        return null;
    }

    private static FileHandle getVertexShaderForMode(ShaderMode mode) {
        String profileDir = getProfileDir();
        String modeDir = ShaderMode.dirNameForMode(mode);
        return Gdx.files.internal(profileDir + "/" + modeDir + ".vert");
    }

    private static void requireModeSupported(ShaderMode mode) {
        if (!isModeSupportedForCurrentProfile(mode)) {
            throw new IllegalStateException("Shader mode " + mode + " is not supported for current GL profile.");
        }
    }

    private static boolean isModeSupportedForCurrentProfile(ShaderMode mode) {
        String glProfile = getGlProfile();
        GLCaps c = caps();

        switch (mode) {
            case TEXTURE_2D:
            case MULTI_TEXTURE:
                return true;

            case TEXTURE_ARRAY:
                return c.supportsTextureArray() && !"GL20".equals(glProfile);

            default:
                return false;
        }
    }

    // ------------------------------------------------------------------------
    // Default shaders
    // ------------------------------------------------------------------------

    private static void loadOptionalDefaultShader(String profileDir,
                                                  ShaderMode mode,
                                                  String registryName) {
        String modeDir = ShaderMode.dirNameForMode(mode);

        FileHandle vert = Gdx.files.internal(profileDir + "/" + modeDir + ".vert");
        FileHandle frag = Gdx.files.internal(profileDir + "/" + modeDir + ".frag");

        if (!vert.exists() || !frag.exists()) {
            return;
        }

        ShaderProgram shader = compileShader(
                vert.path(),
                frag.path(),
                modeDir + "/" + registryName,
                false
        );

        if (shader != null) {
            registerOrReplace(registryName, shader, mode, false);
        }
    }

    // ------------------------------------------------------------------------
    // Custom shaders
    // ------------------------------------------------------------------------

    private static void loadCustomShadersForProject() {
        FileHandle root = projectShadersRoot;
        if (root == null || !root.exists() || !root.isDirectory()) {
            return;
        }

        loadLegacyCustomShaders(root);
        loadStructuredCustomShaders(root.child("custom"));
    }

    private static void loadLegacyCustomShaders(FileHandle root) {
        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_2D)), ShaderMode.TEXTURE_2D, false);
        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_2D)).child("fx"), ShaderMode.TEXTURE_2D, true);

        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE)), ShaderMode.MULTI_TEXTURE, false);
        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE)).child("fx"), ShaderMode.MULTI_TEXTURE, true);

        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY)), ShaderMode.TEXTURE_ARRAY, false);
        loadLegacyModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY)).child("fx"), ShaderMode.TEXTURE_ARRAY, true);
    }

    private static void loadLegacyModeDir(FileHandle dir, ShaderMode mode, boolean fx) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (!isModeSupportedForCurrentProfile(mode)) return;

        FileHandle[] files = dir.list();

        for (FileHandle file : files) {
            if (file.isDirectory()) continue;

            String fileName = file.name();
            if (!fileName.endsWith(".frag")) continue;

            String shaderName = fileName.substring(0, fileName.length() - ".frag".length());
            FileHandle fragFile = file;
            FileHandle vertFile = dir.child(shaderName + ".vert");

            try {
                if (vertFile.exists()) {
                    registerCustomShader(shaderName, vertFile, fragFile, mode, fx);
                } else {
                    registerCustomShader(shaderName, fragFile, mode, fx);
                }
            } catch (Exception ex) {
                logError("ShaderRegistry",
                        "Failed to load legacy custom shader '" + shaderName + "' (" + mode + ", fx=" + fx + ") from "
                                + fragFile.path(),
                        ex);
            }
        }
    }

    private static void loadStructuredCustomShaders(FileHandle customRoot) {
        if (customRoot == null || !customRoot.exists() || !customRoot.isDirectory()) {
            return;
        }

        loadStructuredShaderCategory(customRoot.child("material"), false);
        loadStructuredShaderCategory(customRoot.child("fx"), true);
    }

    private static void loadStructuredShaderCategory(FileHandle categoryDir, boolean fxFromPath) {
        if (categoryDir == null || !categoryDir.exists() || !categoryDir.isDirectory()) {
            return;
        }

        FileHandle[] shaderDirs = categoryDir.list();

        for (FileHandle shaderDir : shaderDirs) {
            if (!shaderDir.isDirectory()) continue;

            try {
                loadStructuredShader(shaderDir, fxFromPath);
            } catch (Exception ex) {
                logError("ShaderRegistry",
                        "Failed to load structured custom shader from " + shaderDir.path(),
                        ex);
            }
        }
    }

    private static void loadStructuredShader(FileHandle shaderDir, boolean fxFromPath) {
        String shaderName = shaderDir.name();
        ShaderMode mode = ShaderMode.TEXTURE_ARRAY;
        boolean fx = fxFromPath;

        FileHandle metadataFile = shaderDir.child("shader.json");
        if (metadataFile.exists()) {
            JsonValue metadata = new JsonReader().parse(metadataFile);

            shaderName = metadata.getString("name", shaderName);
            mode = parseShaderMode(metadata.getString("mode", mode.name()), mode);

            String type = metadata.getString("type", fx ? "FX" : "MATERIAL");
            fx = isFxType(type) || fxFromPath;
        }

        if (shaderName == null || shaderName.isBlank()) {
            throw new IllegalStateException("Structured shader name is empty: " + shaderDir.path());
        }

        if (!isModeSupportedForCurrentProfile(mode)) {
            log("ShaderRegistry", "Skipping structured custom shader '" + shaderName
                    + "' because mode " + mode + " is not supported for current profile.");
            return;
        }

        String prefix = getStructuredVariantPrefixForCurrentProfile();
        if (prefix == null) {
            log("ShaderRegistry", "Skipping structured custom shader '" + shaderName
                    + "' because current profile has no structured variant prefix: " + getProfileDir());
            return;
        }

        FileHandle vertFile = shaderDir.child(prefix + ".vert");
        FileHandle fragFile = shaderDir.child(prefix + ".frag");

        if (!vertFile.exists() || !fragFile.exists()) {
            throw new IllegalStateException("Missing " + prefix + " variant for structured custom shader '"
                    + shaderName + "' in " + shaderDir.path());
        }

        registerCustomShader(shaderName, vertFile, fragFile, mode, fx);
    }

    private static ShaderMode parseShaderMode(String raw, ShaderMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return ShaderMode.valueOf(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isFxType(String type) {
        if (type == null) return false;

        String normalized = type.trim().toUpperCase();
        return "FX".equals(normalized)
                || "POST_FX".equals(normalized)
                || "POSTFX".equals(normalized);
    }

    // ------------------------------------------------------------------------
    // Built-in demo shaders
    // ------------------------------------------------------------------------

    private static void loadBuiltinDemoShaders() {
        FileHandle presets = Gdx.files.internal(DEMOS_ROOT + "/params.json");
        if (!presets.exists()) {
            log("ShaderRegistry", "No demo shader presets found");
            return;
        }

        JsonValue root = new JsonReader().parse(presets);
        defaultUniforms.clear();

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String name = entry.name;
            if (name == null || name.isBlank()) continue;

            ObjectFloatMap<String> defaults = new ObjectFloatMap<>();
            for (JsonValue uniform = entry.child; uniform != null; uniform = uniform.next) {
                if (uniform.name == null || uniform.name.isBlank()) continue;
                defaults.put(uniform.name, uniform.asFloat());
            }

            defaultUniforms.put(name, defaults);

            boolean loaded =
                    tryDemo(name, "sprite", ShaderMode.TEXTURE_2D, false)
                            || tryDemo(name, "ta_sprite", ShaderMode.TEXTURE_ARRAY, false)
                            || tryDemo(name, "ta_sprite/fx", ShaderMode.TEXTURE_ARRAY, true);

            if (!loaded) {
                log("ShaderRegistry", "Demo shader '" + name + "' has no matching .frag and was skipped");
            }
        }
    }

    private static boolean tryDemo(String name,
                                   String subdir,
                                   ShaderMode mode,
                                   boolean fx) {
        if (!isModeSupportedForCurrentProfile(mode)) return false;

        if (nameToIdx.get(name, -1) >= 0) {
            log("ShaderRegistry", "Skipping demo shader '" + name
                    + "' because a shader with the same name is already registered");
            return true;
        }

        FileHandle fragFile = Gdx.files.internal(DEMOS_ROOT + "/" + subdir + "/" + name + ".frag");
        if (!fragFile.exists()) return false;

        FileHandle vertFile = null;
        if (fx) {
            vertFile = Gdx.files.internal(DEMOS_ROOT + "/" + subdir + "/" + name + ".vert");
            if (!vertFile.exists()) {
                logError("ShaderRegistry", "FX demo shader '" + name + "' is missing vertex shader", null);
                return true;
            }
        }

        try {
            registerBuiltinDemoShader(name, vertFile, fragFile, mode, fx);
            log("ShaderRegistry", "Loaded demo shader '" + name + "' from " + fragFile.path());
            return true;
        } catch (Exception ex) {
            logError("ShaderRegistry", "Failed to load demo shader '" + name + "'", ex);
            return true;
        }
    }

    private static int registerBuiltinDemoShader(String name,
                                                 FileHandle vertFile,
                                                 FileHandle fragFile,
                                                 ShaderMode mode,
                                                 boolean fx) {
        if (nameToIdx.get(name, -1) >= 0) {
            return nameToIdx.get(name, -1);
        }

        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Demo fragment shader file does not exist for '"
                    + name + "': " + (fragFile != null ? fragFile.path() : "null"));
        }

        if (vertFile == null) {
            vertFile = getVertexShaderForMode(mode);
        }

        if (vertFile == null || !vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for demo shader '"
                    + name + "' (" + mode + ", fx=" + fx + "): "
                    + (vertFile != null ? vertFile.path() : "null"));
        }

        ShaderProgram.pedantic = false;

        String vertSrc = vertFile.readString("UTF-8");
        String fragSrc = fragFile.readString("UTF-8");

        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile demo shader '" + name + "' (" + mode
                    + ", fx=" + fx + ") from " + vertFile.path() + " / " + fragFile.path()
                    + ":\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }

        int idx = register(name, sp, mode, fx);

        log("ShaderRegistry", "Registered demo shader '" + name + "' (" + mode + ", fx=" + fx
                + ") at index " + idx + " from files " + vertFile.path() + " / " + fragFile.path());

        return idx;
    }

    // ------------------------------------------------------------------------
    // Compile helpers
    // ------------------------------------------------------------------------

    private static ShaderProgram compileShader(String vertPath,
                                               String fragPath,
                                               String friendlyName,
                                               boolean mandatory) {
        FileHandle vertFile = Gdx.files.internal(vertPath);
        FileHandle fragFile = Gdx.files.internal(fragPath);

        if (!vertFile.exists() || !fragFile.exists()) {
            String msg = "Shader files not found for " + friendlyName
                    + " (vert=" + vertPath + ", frag=" + fragPath + ")";

            if (mandatory) {
                throw new IllegalStateException(msg);
            }

            logError("ShaderRegistry", msg, null);
            return null;
        }

        ShaderProgram sp = new ShaderProgram(vertFile, fragFile);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile shader " + friendlyName + ":\n" + sp.getLog();

            if (mandatory) {
                sp.dispose();
                throw new IllegalStateException(msg);
            }

            logError("ShaderRegistry", msg, null);
            sp.dispose();
            return null;
        }

        log("ShaderRegistry", "Compiled shader " + friendlyName + " from " + vertPath + " / " + fragPath);
        return sp;
    }

    private static int registerOrReplace(String name, ShaderProgram sp, ShaderMode mode, boolean fx) {
        int existing = nameToIdx.get(name, -1);

        if (existing >= 0) {
            ShaderProgram old = byIdx.get(existing);
            if (old != null) {
                old.dispose();
            }

            byIdx.set(existing, sp);
            ensureMetaSize(existing + 1);
            modesByIdx.set(existing, mode);
            isFxByIdx.set(existing, fx);

            return existing;
        }

        return register(name, sp, mode, fx);
    }

    private static void ensureMetaSize(int size) {
        while (modesByIdx.size < size) {
            modesByIdx.add(ShaderMode.TEXTURE_2D);
        }
        while (isFxByIdx.size < size) {
            isFxByIdx.add(false);
        }
    }

    // ------------------------------------------------------------------------
    // Logging
    // ------------------------------------------------------------------------

    private static void log(String tag, String msg) {
        if (Gdx.app != null) {
            Gdx.app.log(tag, msg);
        } else {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logError(String tag, String msg, Throwable t) {
        if (Gdx.app != null) {
            if (t != null) {
                Gdx.app.error(tag, msg, t);
            } else {
                Gdx.app.error(tag, msg);
            }
        } else {
            System.err.println("[" + tag + "] " + msg);
            if (t != null) {
                t.printStackTrace();
            }
        }
    }
}