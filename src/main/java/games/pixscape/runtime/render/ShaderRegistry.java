package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.render.batch.GLCaps;

/**
 * Simple registry: {@code name -> index}, {@code index -> ShaderProgram}.
 * Handles:
 *  - core shaders based on profilee ({@code 100 / 300es / 330})
 *  - project custom shaders under {@code orig/shaders/<modeDir>}
 */
public final class ShaderRegistry {

    private static final ShaderRegistry INSTANCE = new ShaderRegistry();

    private static final ObjectIntMap<String> nameToIdx = new ObjectIntMap<>();
    private static final Array<ShaderProgram> byIdx = new Array<>();
    private static final Array<ShaderMode> modesByIdx = new Array<>();
    private static final Array<Boolean> isFxByIdx = new Array<>();
    private static final ObjectMap<String, ObjectFloatMap<String>> defaultUniforms = new ObjectMap<>();

    private static final String DEMOS_ROOT = "assets/shaders/demos";

    private static boolean initialized = false;

    // ---- Lazy GL caps (MUST NOT be resolved in static init) ----
    private static GLCaps caps;

    // ---- Project context + cached profilee selection ----
    private static String cachedProfileDir = null;
    private static String cachedGlProfile = null;

    private static String requestedGlProfile = null;
    private static FileHandle projectShadersRoot = null;

    private ShaderRegistry() {}

    public static ShaderRegistry getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------
    // Public API (registry)
    // ------------------------------------------------------------------------

    /** Registers a shader and returns its index (without replacing if the name already exists). */
    public static int register(String name, ShaderProgram sp, ShaderMode mode, boolean fx) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Shader name is empty");
        if (sp == null) throw new IllegalArgumentException("ShaderProgram is null for '" + name + "'");
        if (mode == null) throw new IllegalArgumentException("ShaderMode is null for '" + name + "'");

        int existing = nameToIdx.get(name, -1);
        if (existing >= 0) {
            // Do not silently replace with register(); use registerOrReplace().
            return existing;
        }

        int idx = byIdx.size;
        byIdx.add(sp);
        modesByIdx.add(mode);
        isFxByIdx.add(fx);
        nameToIdx.put(name, idx);
        return idx;
    }

    // compat: those that do not specify fx => false
    public static int register(String name, ShaderProgram sp, ShaderMode mode) {
        return register(name, sp, mode, false);
    }

    /** Registers or replaces an existing shader with the same name. */
    private static int registerOrReplace(String name, ShaderProgram sp, ShaderMode mode, boolean fx) {
        int existing = nameToIdx.get(name, -1);
        if (existing >= 0) {
            ShaderProgram old = byIdx.get(existing);
            if (old != null) old.dispose();

            byIdx.set(existing, sp);

            // keep arrays aligned defensively
            ensureMetaSize(existing + 1);
            modesByIdx.set(existing, mode);
            isFxByIdx.set(existing, fx);

            return existing;
        }
        return register(name, sp, mode, fx);
    }

    private static void ensureMetaSize(int size) {
        while (modesByIdx.size < size) modesByIdx.add(ShaderMode.SPRITE);
        while (isFxByIdx.size < size) isFxByIdx.add(false);
    }

    /** All names (unsorted). */
    public static Array<String> getRegisteredNames() {
        return nameToIdx.keys().toArray();
    }

    /** Names filtered by mode (alpha-sorted). */
    public static Array<String> getNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();
        for (ObjectIntMap.Entry<String> e : nameToIdx) {
            int idx = e.value;
            if (idx >= 0 && idx < modesByIdx.size && modesByIdx.get(idx) == mode) {
                result.add(e.key);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    /** Names of NON-FX shaders for this mode (for EntityProperties). */
    public static Array<String> getMainNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();
        for (ObjectIntMap.Entry<String> e : nameToIdx) {
            int idx = e.value;
            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;

            boolean fx = (idx < isFxByIdx.size) && Boolean.TRUE.equals(isFxByIdx.get(idx));
            if (!fx) result.add(e.key);
        }
        result.sort(String::compareTo);
        return result;
    }

    /** Names of FX shaders for this mode (for CameraProperties post FX). */
    public static Array<String> getFxNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();
        for (ObjectIntMap.Entry<String> e : nameToIdx) {
            int idx = e.value;
            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;

            boolean fx = (idx < isFxByIdx.size) && Boolean.TRUE.equals(isFxByIdx.get(idx));
            if (fx) result.add(e.key);
        }
        result.sort(String::compareTo);
        return result;
    }

    /** Gets by index (null if invalid). */
    public static ShaderProgram getByIdx(int idx) {
        if (idx < 0 || idx >= byIdx.size) return null;
        return byIdx.get(idx);
    }

    /** Gets by name (null if missing). */
    public static ShaderProgram get(String name) {
        int idx = nameToIdx.get(name, -1);
        return idx >= 0 ? byIdx.get(idx) : null;
    }

    /** Index by name (-1 if missing). */
    public static int indexOf(String name) {
        return nameToIdx.get(name, -1);
    }

    /** Nom par index (or null). */
    public static String getName(Integer idx) {
        return nameToIdx.findKey(idx);
    }

    /** Name by ShaderProgram instance (or null if not found). */
    public static String getName(ShaderProgram sp) {
        if (sp == null) return null;
        for (int i = 0, n = byIdx.size; i < n; i++) {
            if (byIdx.get(i) == sp) return getName(i);
        }
        return null;
    }

    /** Mode of a shader by index (or null). */
    public static ShaderMode getMode(int idx) {
        if (idx < 0 || idx >= modesByIdx.size) return null;
        return modesByIdx.get(idx);
    }

    public static ObjectFloatMap<String> getDefaultUniforms(String shaderName) {
        ObjectFloatMap<String> src = defaultUniforms.get(shaderName);
        if (src == null) return null;

        ObjectFloatMap<String> copy = new ObjectFloatMap<>();
        for (ObjectFloatMap.Entry<String> e : src) {
            copy.put(e.key, e.value);
        }
        return copy;
    }

    /** Used by UI: returns current GLSL directory (assets/shaders/100, 300es, 330...). */
    public static String getCurrentProfileDir() {
        return getProfileDir();
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** Releases all shaders and resets state. */
    public static void disposeAll() {
        for (ShaderProgram sp : byIdx) {
            if (sp != null) sp.dispose();
        }
        byIdx.clear();
        nameToIdx.clear();
        modesByIdx.clear();
        isFxByIdx.clear();
        defaultUniforms.clear();

        initialized = false;

        // reset profilee selection
        cachedProfileDir = null;
        cachedGlProfile = null;

        // reset project context
        requestedGlProfile = null;
        projectShadersRoot = null;

        // reset caps (will be re-detected lazily on next init)
        caps = null;
    }

    /**
     * Fully resets registry based on project:
     *  - releases all shaders
     *  - reloads default shaders based on cfg.glProfile
     *  - scans orig/shaders/... to reload custom shaders.
     */
    public static void reloadForProject(String glProfile, FileHandle projectDir, String shadersDir) {
        disposeAll();
        setProjectContext(glProfile, projectDir, shadersDir);
        initDefaults();
    }

    public static void initDefaults(String glProfile, FileHandle projectDir, String shadersDir) {
        setProjectContext(glProfile, projectDir, shadersDir);
        initDefaults();
    }

    /**
     * Loads default shaders + project custom shaders + demos.
     */
    public static void initDefaults() {
        if (initialized) return;

        ShaderProgram.pedantic = false;

        // Force caps early so getProfileDir() and support checks are always safe.
        GLCaps c = caps();

        final String profileDir = getProfileDir();
        final String glProfile = getGlProfile();

        log("ShaderRegistry", "Init defaults with profileDir=" + profileDir
                + " caps=" + c + " glProfile=" + glProfile);

        // 1) Sprite simple -> "default" (obligatoire)
        ShaderProgram sprite = compileShader(
                profileDir + "/sprite.vert",
                profileDir + "/sprite.frag",
                "sprite/default",
                /*mandatory*/ true
        );
        registerOrReplace("default", sprite, ShaderMode.SPRITE, false);

        // 2) Multi-texture -> "mt_default" (optional)
        {
            FileHandle mtV = Gdx.files.internal(profileDir + "/" + ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE) + ".vert");
            FileHandle mtF = Gdx.files.internal(profileDir + "/" + ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE) + ".frag");

            if (mtV.exists() && mtF.exists()) {
                ShaderProgram mt = compileShader(
                        mtV.path(),
                        mtF.path(),
                        "mt_sprite/mt_default",
                        /*mandatory*/ false
                );
                if (mt != null) {
                    registerOrReplace("mt_default", mt, ShaderMode.MULTI_TEXTURE, false);
                }
            }
        }

        // 3) TextureArray -> "ta_default" (optional)
        if (isModeSupportedForCurrentProfile(ShaderMode.TEXTURE_ARRAY)) {
            FileHandle taV = Gdx.files.internal(profileDir + "/" + ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY) + ".vert");
            FileHandle taF = Gdx.files.internal(profileDir + "/" + ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY) + ".frag");

            if (taV.exists() && taF.exists()) {
                ShaderProgram ta = compileShader(
                        taV.path(),
                        taF.path(),
                        "ta_sprite/ta_default",
                        /*mandatory*/ false
                );
                if (ta != null) {
                    registerOrReplace("ta_default", ta, ShaderMode.TEXTURE_ARRAY, false);
                }
            }
            ShaderProgram ta = compileShader(
                    taV.path(), taF.path(),
                    "ta_sprite/ta_default",
                    false
            );
            if (ta != null) {
                registerOrReplace("ta_default", ta, ShaderMode.TEXTURE_ARRAY, false);
            }
        }

        loadCustomShadersForProject();
        loadBuiltinDemoShaders();

        initialized = true;


    }

    // ------------------------------------------------------------------------
    // Custom shader API (UI)
    // ------------------------------------------------------------------------

    /**
     * Tests compilation of a full custom shader (vertex + fragment) for a given mode.
     * Does not register it in the registry.
     */
    public static void testCompile(String name,
                                   String vertexSource,
                                   String fragmentSource,
                                   ShaderMode mode) {
        requireModeSupported(mode);

        if (fragmentSource == null || fragmentSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Fragment shader source is empty for '" + name + "'");
        }
        if (vertexSource == null || vertexSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Vertex shader source is empty for '" + name + "'");
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

    /**
     * Tests compilation of a custom fragment shader for a given mode,
     * using the vertex shader matching mode and project profile.
     */
    public static void testCompile(String name, String fragmentSource, ShaderMode mode) {
        requireModeSupported(mode);

        if (fragmentSource == null || fragmentSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Fragment shader source is empty for '" + name + "'");
        }

        ShaderProgram.pedantic = false;

        FileHandle vertFile = getVertexShaderForMode(mode);
        if (!vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for mode " + mode + ": " + vertFile.path());
        }

        String vertSrc = vertFile.readString("UTF-8");
        ShaderProgram sp = new ShaderProgram(vertSrc, fragmentSource);

        if (!sp.isCompiled()) {
            String msg = "Failed to compile custom shader '" + name + "' (" + mode + "):\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }
        sp.dispose();
    }

    public static int registerCustomShader(String name, FileHandle fragFile, ShaderMode mode, boolean fx) {
        requireModeSupported(mode);

        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Fragment shader file does not exist for '" + name + "': "
                    + (fragFile != null ? fragFile.path() : "null"));
        }

        ShaderProgram.pedantic = false;

        String fragSrc = fragFile.readString("UTF-8");

        FileHandle vertFile = getVertexShaderForMode(mode);
        if (!vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for mode " + mode + ": " + vertFile.path());
        }

        String vertSrc = vertFile.readString("UTF-8");
        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);

        if (!sp.isCompiled()) {
            String msg = "Failed to compile custom shader '" + name + "' (" + mode + ") from "
                    + fragFile.path() + ":\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }

        int idx = registerOrReplace(name, sp, mode, fx);
        log("ShaderRegistry", "Registered custom shader '" + name + "' (" + mode + ", fx=" + fx
                + ") at index " + idx + " from file " + fragFile.path());
        return idx;
    }

    public static int registerCustomShader(String name,
                                           FileHandle vertFile,
                                           FileHandle fragFile,
                                           ShaderMode mode,
                                           boolean fx) {
        requireModeSupported(mode);

        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Fragment shader file does not exist for '" + name + "': "
                    + (fragFile != null ? fragFile.path() : "null"));
        }
        if (vertFile == null || !vertFile.exists()) {
            throw new IllegalArgumentException("Vertex shader file does not exist for '" + name + "': "
                    + (vertFile != null ? vertFile.path() : "null"));
        }

        ShaderProgram.pedantic = false;

        String fragSrc = fragFile.readString("UTF-8");
        String vertSrc = vertFile.readString("UTF-8");

        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile custom shader '" + name + "' (" + mode + ") from "
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
    // Internal: project context, caps, profilee selection
    // ------------------------------------------------------------------------

    private static void setProjectContext(String glProfile, FileHandle projectDir, String shadersDir) {
        requestedGlProfile = (glProfile != null && !glProfile.isBlank()) ? glProfile : null;

        if (projectDir != null && shadersDir != null && !shadersDir.isBlank()) {
            projectShadersRoot = projectDir.child(shadersDir);
        } else {
            projectShadersRoot = null;
        }

        // reset cached profilee selection (depends on project context + caps)
        cachedProfileDir = null;
        cachedGlProfile = null;
    }

    private static GLCaps caps() {
        if (caps == null) {
            caps = GLCaps.detect();
        }
        return caps;
    }

    /** Fills or returns the pair (profileDir, glProfile) for the current project. */
    private static String getProfileDir() {
        if (cachedProfileDir != null) return cachedProfileDir;

        // Ensure caps are ready
        GLCaps c = caps();

        // 1) Profile forced by project
        if (requestedGlProfile != null) {
            cachedGlProfile = requestedGlProfile;
            switch (requestedGlProfile) {
                case "GL20":
                    cachedProfileDir = "assets/shaders/100";
                    return cachedProfileDir;
                case "GL30":
                    // desktop 330, ES => 300es (if you ever pass "GL30" on mobile)
                    cachedProfileDir = "assets/shaders/330";
                    return cachedProfileDir;
                default:
                    // fallback auto
                    break;
            }
        }

        // 2) Fallback: auto based on platform/caps
        GLVersion glv = Gdx.graphics.getGLVersion();
        boolean isGLES = glv.getType() == GLVersion.Type.GLES;

        if (c.supportsES3()) {
            cachedProfileDir = isGLES ? "assets/shaders/300es" : "assets/shaders/330";
            cachedGlProfile = "GL30";
        } else {
            cachedProfileDir = "assets/shaders/100";
            cachedGlProfile = "GL20";
        }
        return cachedProfileDir;
    }

    private static String getGlProfile() {
        if (cachedGlProfile == null) getProfileDir();
        return cachedGlProfile != null ? cachedGlProfile : "GL30";
    }

    private static FileHandle getVertexShaderForMode(ShaderMode mode) {
        String profileDir = getProfileDir();
        String dir = ShaderMode.dirNameForMode(mode);
        return Gdx.files.internal(profileDir + "/" + dir + ".vert");
    }

    private static void requireModeSupported(ShaderMode mode) {
        if (!isModeSupportedForCurrentProfile(mode)) {
            throw new IllegalStateException("Shader mode " + mode + " is not supported for current GL profile.");
        }
    }

    /** Does this mode make sense with current GL profile? */
    private static boolean isModeSupportedForCurrentProfile(ShaderMode mode) {
        String glProfile = getGlProfile();
        GLCaps c = caps();

        switch (mode) {
            case SPRITE:
            case MULTI_TEXTURE:
                return true;
            case TEXTURE_ARRAY:
                return c.supportsTextureArray() && !"GL20".equals(glProfile);
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------------
    // Internal: load custom shaders from project
    // ------------------------------------------------------------------------

    private static void loadCustomShadersForProject() {
        FileHandle root = projectShadersRoot;
        if (root == null || !root.exists() || !root.isDirectory()) return;

        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.SPRITE)), ShaderMode.SPRITE, false);
        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.SPRITE)).child("fx"), ShaderMode.SPRITE, true);

        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE)), ShaderMode.MULTI_TEXTURE, false);
        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.MULTI_TEXTURE)).child("fx"), ShaderMode.MULTI_TEXTURE, true);

        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY)), ShaderMode.TEXTURE_ARRAY, false);
        loadModeDir(root.child(ShaderMode.dirNameForMode(ShaderMode.TEXTURE_ARRAY)).child("fx"), ShaderMode.TEXTURE_ARRAY, true);
    }

    /** Loads all .frag files from a given subfolder for a given ShaderMode. */
    private static void loadModeDir(FileHandle dir, ShaderMode mode, boolean fx) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (!isModeSupportedForCurrentProfile(mode)) return;

        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) continue;

            String name = f.name();
            if (!name.endsWith(".frag")) continue;

            String baseName = name.substring(0, name.length() - ".frag".length());
            FileHandle fragFile = f;
            FileHandle vertFile = dir.child(baseName + ".vert");

            try {
                if (vertFile.exists()) {
                    registerCustomShader(baseName, vertFile, fragFile, mode, fx);
                } else {
                    registerCustomShader(baseName, fragFile, mode, fx);
                }
            } catch (Exception ex) {
                logError("ShaderRegistry",
                        "Failed to load custom shader '" + baseName + "' (" + mode + ", fx=" + fx + ") from "
                                + fragFile.path(),
                        ex);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Internal: compile helpers
    // ------------------------------------------------------------------------

    private static ShaderProgram compileShader(String vertPath, String fragPath, String friendlyName, boolean mandatory) {
        FileHandle v = Gdx.files.internal(vertPath);
        FileHandle f = Gdx.files.internal(fragPath);

        if (!v.exists() || !f.exists()) {
            String msg = "Shader files not found for " + friendlyName
                    + " (vert=" + vertPath + ", frag=" + fragPath + ")";
            if (mandatory) throw new IllegalStateException(msg);
            logError("ShaderRegistry", msg, null);
            return null;
        }

        ShaderProgram sp = new ShaderProgram(v, f);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile shader " + friendlyName + ":\n" + sp.getLog();
            if (mandatory) throw new IllegalStateException(msg);
            logError("ShaderRegistry", msg, null);
            sp.dispose();
            return null;
        }

        log("ShaderRegistry", "Compiled shader " + friendlyName + " from " + vertPath + " / " + fragPath);
        return sp;
    }

    // ------------------------------------------------------------------------
    // Builtin demos
    // ------------------------------------------------------------------------

    private static void loadBuiltinDemoShaders() {
        FileHandle presets = Gdx.files.internal(DEMOS_ROOT + "/params.json");
        if (!presets.exists()) {
            log("ShaderRegistry", "No demo presets found");
            return;
        }

        JsonValue root = new JsonReader().parse(presets);
        defaultUniforms.clear();

        for (JsonValue e = root.child; e != null; e = e.next) {
            String name = e.name;
            if (name == null || name.isBlank()) continue;

            ObjectFloatMap<String> defaults = new ObjectFloatMap<>();
            for (JsonValue uniform = e.child; uniform != null; uniform = uniform.next) {
                if (uniform.name == null || uniform.name.isBlank()) continue;
                defaults.put(uniform.name, uniform.asFloat());
            }
            defaultUniforms.put(name, defaults);

            boolean loaded =
                    tryDemo(name, "sprite", ShaderMode.SPRITE, false) ||
                            tryDemo(name, "ta_sprite", ShaderMode.TEXTURE_ARRAY, false) ||
                            tryDemo(name, "ta_sprite/fx", ShaderMode.TEXTURE_ARRAY, true);

            if (!loaded) {
                log("ShaderRegistry",
                        "Demo shader '" + name + "' has no matching .frag (skipped)");
            }
        }
    }

    private static boolean tryDemo(String name,
                                   String subdir,
                                   ShaderMode mode,
                                   boolean fx) {
        if (!isModeSupportedForCurrentProfile(mode)) return false;

        FileHandle frag = Gdx.files.internal(
                DEMOS_ROOT + "/" + subdir + "/" + name + ".frag"
        );
        if (!frag.exists()) return false;

        FileHandle vert = null;
        if (fx) {
            vert = Gdx.files.internal(
                    DEMOS_ROOT + "/" + subdir + "/" + name + ".vert"
            );
            if (!vert.exists()) {
                logError("ShaderRegistry",
                        "FX demo '" + name + "' missing vertex shader", null);
                return true;
            }
        }

        try {
            registerBuiltinDemoShader(name, vert, frag, mode, fx);
            log("ShaderRegistry", "Loaded demo shader '" + name
                    + "' from " + frag.path());
            return true;
        } catch (Exception ex) {
            logError("ShaderRegistry",
                    "Failed to load demo shader '" + name + "'", ex);
            return true;
        }
    }



    private static void loadDemoDir(FileHandle dir, ShaderMode mode, boolean fx) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        for (FileHandle fragFile : dir.list()) {
            if (fragFile.isDirectory()) continue;
            String name = fragFile.name();
            if (!name.endsWith(".frag")) continue;

            String baseName = name.substring(0, name.length() - ".frag".length());

            try {
                FileHandle vertFile = null;
                if (fx) {
                    // FX: vertex shader in same folder
                    vertFile = dir.child(baseName + ".vert");
                }
                registerBuiltinDemoShader(baseName, vertFile, fragFile, mode, fx);
            } catch (Exception ex) {
                logError("ShaderRegistry",
                        "Failed to load builtin demo shader '" + baseName
                                + "' (" + mode + ", fx=" + fx + ") from " + fragFile.path(),
                        ex);
            }
        }
    }

    private static int registerBuiltinDemoShader(String name,
                                                 FileHandle vertFile,
                                                 FileHandle fragFile,
                                                 ShaderMode mode,
                                                 boolean fx) {
        ShaderProgram.pedantic = false;

        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Builtin demo fragment shader file does not exist for '"
                    + name + "': " + (fragFile != null ? fragFile.path() : "null"));
        }

        if (vertFile == null) {
            vertFile = getVertexShaderForMode(mode);
        }
        if (vertFile == null || !vertFile.exists()) {
            throw new IllegalStateException("Vertex shader file not found for builtin demo '"
                    + name + "' (" + mode + ", fx=" + fx + "): "
                    + (vertFile != null ? vertFile.path() : "null"));
        }

        String fragSrc = fragFile.readString("UTF-8");
        String vertSrc = vertFile.readString("UTF-8");

        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile builtin demo shader '" + name + "' (" + mode
                    + ", fx=" + fx + ") from " + fragFile.path() + " / " + vertFile.path()
                    + ":\n" + sp.getLog();
            sp.dispose();
            throw new IllegalStateException(msg);
        }

        int idx = registerOrReplace(name, sp, mode, fx);
        log("ShaderRegistry", "Registered builtin demo shader '" + name + "' (" + mode + ", fx=" + fx
                + ") at index " + idx + " from file " + fragFile.path());
        return idx;
    }

    // ------------------------------------------------------------------------
    // Logging helpers (safe even before Gdx.app is ready)
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
            if (t != null) Gdx.app.error(tag, msg, t);
            else Gdx.app.error(tag, msg);
        } else {
            System.err.println("[" + tag + "] " + msg);
            if (t != null) t.printStackTrace();
        }
    }
}
