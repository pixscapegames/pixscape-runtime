package games.pixscape.runtime.service;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.ShaderOrigin;
import games.pixscape.runtime.render.ShaderRole;
import games.pixscape.runtime.render.ShaderVariant;
import games.pixscape.runtime.render.batch.GLCaps;

public final class ShaderRegistry {

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static final ShaderRegistry INSTANCE = new ShaderRegistry();

    private static final ObjectIntMap<String> nameToIdx = new ObjectIntMap<>();
    private static final Array<ShaderProgram> byIdx = new Array<>();
    private static final Array<ShaderMode> modesByIdx = new Array<>();
    private static final Array<ShaderOrigin> originsByIdx = new Array<>();
    private static final Array<ShaderRole> rolesByIdx = new Array<>();
    private static final ObjectMap<String, ObjectFloatMap<String>> defaultUniforms = new ObjectMap<>();

    private static boolean initialized = false;
    private static GLCaps caps;

    private static PlatformTarget requestedPlatformTarget = PlatformTarget.AUTO;
    private static PlatformTarget cachedResolvedPlatformTarget = null;

    private static FileHandle projectShadersRoot = null;

    private static ShaderVariant cachedVariant = null;

    private ShaderRegistry() {
    }

    public static ShaderRegistry getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static int register(String name,
                               ShaderProgram sp,
                               ShaderMode mode,
                               ShaderOrigin origin,
                               ShaderRole role) {
        if (name == null || isBlank(name)) {
            throw new IllegalArgumentException("Shader name is empty");
        }
        if (sp == null) {
            throw new IllegalArgumentException("ShaderProgram is null for '" + name + "'");
        }
        if (mode == null) {
            throw new IllegalArgumentException("ShaderMode is null for '" + name + "'");
        }
        if (origin == null) {
            throw new IllegalArgumentException("ShaderOrigin is null for '" + name + "'");
        }
        if (role == null) {
            throw new IllegalArgumentException("ShaderRole is null for '" + name + "'");
        }

        int existing = nameToIdx.get(name, -1);
        if (existing >= 0) return existing;

        int idx = byIdx.size;
        byIdx.add(sp);
        modesByIdx.add(mode);
        originsByIdx.add(origin);
        rolesByIdx.add(role);
        nameToIdx.put(name, idx);
        return idx;
    }

    /**
     * Compatibility overload. Prefer the origin/role overload.
     */
    public static int register(String name, ShaderProgram sp, ShaderMode mode) {
        return register(name, sp, mode, ShaderOrigin.USER, ShaderRole.MATERIAL);
    }

    public static Array<String> getRegisteredNames() {
        Array<String> result = nameToIdx.keys().toArray();
        result.sort(String::compareTo);
        return result;
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

    public static Array<String> getMaterialNamesForMode(ShaderMode mode, boolean includeExamples) {
        return getNamesForModeAndRole(mode, ShaderRole.MATERIAL, includeExamples);
    }

    public static Array<String> getFxNamesForMode(ShaderMode mode, boolean includeExamples) {
        return getNamesForModeAndRole(mode, ShaderRole.FX, includeExamples);
    }

    public static Array<String> getLightNamesForMode(ShaderMode mode) {
        Array<String> result = new Array<>();

        for (ObjectIntMap.Entries<String> it = nameToIdx.entries(); it.hasNext(); ) {
            ObjectIntMap.Entry<String> entry = it.next();
            int idx = entry.value;

            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;
            if (getRole(idx) != ShaderRole.LIGHT) continue;

            result.add(entry.key);
        }

        result.sort(String::compareTo);
        return result;
    }

    /**
     * Compatibility API.
     */
    public static Array<String> getMainNamesForMode(ShaderMode mode) {
        return getMaterialNamesForMode(mode, true);
    }

    /**
     * Compatibility API.
     */
    public static Array<String> getFxNamesForMode(ShaderMode mode) {
        return getFxNamesForMode(mode, true);
    }

    private static Array<String> getNamesForModeAndRole(ShaderMode mode,
                                                        ShaderRole role,
                                                        boolean includeExamples) {
        Array<String> result = new Array<>();

        for (ObjectIntMap.Entries<String> it = nameToIdx.entries(); it.hasNext(); ) {
            ObjectIntMap.Entry<String> entry = it.next();
            int idx = entry.value;

            if (idx < 0 || idx >= modesByIdx.size) continue;
            if (modesByIdx.get(idx) != mode) continue;
            if (getRole(idx) != role) continue;

            ShaderOrigin origin = getOrigin(idx);
            if (!includeExamples && origin == ShaderOrigin.EXAMPLE) continue;

            result.add(entry.key);
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
            if (byIdx.get(i) == sp) return getName(i);
        }

        return null;
    }

    public static ShaderMode getMode(int idx) {
        if (idx < 0 || idx >= modesByIdx.size) return null;
        return modesByIdx.get(idx);
    }

    public static ShaderOrigin getOrigin(String name) {
        int idx = indexOf(name);
        return idx >= 0 ? getOrigin(idx) : null;
    }

    public static ShaderRole getRole(String name) {
        int idx = indexOf(name);
        return idx >= 0 ? getRole(idx) : null;
    }

    public static ShaderOrigin getOrigin(int idx) {
        if (idx < 0 || idx >= originsByIdx.size) return null;
        return originsByIdx.get(idx);
    }

    public static ShaderRole getRole(int idx) {
        if (idx < 0 || idx >= rolesByIdx.size) return null;
        return rolesByIdx.get(idx);
    }

    public static ObjectFloatMap<String> getDefaultUniforms(String shaderName) {
        return defaultUniforms.get(shaderName);
    }

    public static PlatformTarget getCurrentPlatformTarget() {
        return requestedPlatformTarget;
    }

    public static ShaderVariant getCurrentShaderVariant() {
        return getShaderVariant();
    }

    public static String getCurrentShaderVariantDir() {
        return coreShaderDir(getShaderVariant());
    }


    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    public static void disposeAll() {
        for (int i = 0, n = byIdx.size; i < n; i++) {
            ShaderProgram sp = byIdx.get(i);
            if (sp != null) sp.dispose();
        }

        byIdx.clear();
        nameToIdx.clear();
        modesByIdx.clear();
        originsByIdx.clear();
        rolesByIdx.clear();
        defaultUniforms.clear();

        initialized = false;

        requestedPlatformTarget = PlatformTarget.AUTO;
        cachedResolvedPlatformTarget = null;
        projectShadersRoot = null;

        cachedVariant = null;
        caps = null;
    }

    public static void reloadForProject(FileHandle projectDir, String shadersDir) {
        disposeAll();
        setProjectContext(PlatformTarget.AUTO, projectDir, shadersDir);
        initDefaults();
    }

    public static void initDefaults(FileHandle projectDir, String shadersDir) {
        setProjectContext(PlatformTarget.AUTO, projectDir, shadersDir);
        initDefaults();
    }

    public static void initDefaults() {
        if (initialized) return;

        ShaderProgram.pedantic = false;

        GLCaps c = caps();
        ShaderVariant variant = getShaderVariant();

        if (!isModeSupportedForCurrentProfile(ShaderMode.TEXTURE_ARRAY)) {
            throw new IllegalStateException(
                    "Pixscape runtime requires texture array support for target="
                            + requestedPlatformTarget
                            + ", variant=" + variant
                            + ", caps=" + c
            );
        }

        loadMandatoryCoreDefaultShader(variant, ShaderMode.TEXTURE_ARRAY);

        loadCoreLightShader(variant, RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
        loadCoreLightShader(variant, RuntimeFs.TEXTURE_ARRAY_CONELIGHT);

        loadOptionalCoreDefaultShader(variant, ShaderMode.TEXTURE_2D);
        loadOptionalCoreDefaultShader(variant, ShaderMode.MULTI_TEXTURE);

        registerCoreLightDefaults();
        loadCustomShadersForProject();
        loadExampleShaders();

        initialized = true;
    }

    private static void registerCoreLightDefaults() {
        ObjectFloatMap<String> point = new ObjectFloatMap<>();
        point.put("u_centerX", 0f);
        point.put("u_centerY", 0f);
        point.put("u_radius", 1f);
        point.put("u_falloff", 1.5f);
        defaultUniforms.put(RuntimeFs.TEXTURE_ARRAY_POINTLIGHT, point);

        ObjectFloatMap<String> cone = new ObjectFloatMap<>();
        cone.put("u_centerX", 0f);
        cone.put("u_centerY", 0f);
        cone.put("u_radius", 1f);
        cone.put("u_dirX", 1.0f);
        cone.put("u_dirY", 0.0f);
        cone.put("u_coneCos", 0.8660254f);
        cone.put("u_softness", 0.1f);
        cone.put("u_falloff", 1.5f);
        defaultUniforms.put(RuntimeFs.TEXTURE_ARRAY_CONELIGHT, cone);
    }

    public static PlatformTarget getResolvedPlatformTarget() {
        if (cachedResolvedPlatformTarget == null) {
            getShaderVariant();
        }
        return cachedResolvedPlatformTarget != null
                ? cachedResolvedPlatformTarget
                : requestedPlatformTarget;
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

        FileHandle includesDir = Gdx.files.internal(RuntimeFs.RUNTIME_DIR_SHADER_INCLUDES);

        String processedVertexSource = ShaderSourcePreprocessor.preprocess(
                vertexSource,
                null,
                includesDir
        );

        String processedFragmentSource = ShaderSourcePreprocessor.preprocess(
                fragmentSource,
                null,
                includesDir
        );

        ShaderProgram sp = new ShaderProgram(processedVertexSource, processedFragmentSource);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile shader '" + name + "' (" + mode + "):\n" + sp.getLog();
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

        testCompile(name, preprocessShader(vertFile), fragmentSource, mode);
    }

    public static int registerCustomShader(String name,
                                           FileHandle fragFile,
                                           ShaderMode mode,
                                           boolean fx) {
        return registerProjectShader(
                name,
                getVertexShaderForMode(mode),
                fragFile,
                mode,
                fx ? ShaderRole.FX : ShaderRole.MATERIAL
        );
    }

    public static int registerCustomShader(String name,
                                           FileHandle vertFile,
                                           FileHandle fragFile,
                                           ShaderMode mode,
                                           boolean fx) {
        return registerProjectShader(
                name,
                vertFile,
                fragFile,
                mode,
                fx ? ShaderRole.FX : ShaderRole.MATERIAL
        );
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

        if (projectDir != null && shadersDir != null && !isBlank(shadersDir)) {
            projectShadersRoot = projectDir.child(shadersDir);
        } else {
            projectShadersRoot = null;
        }

        cachedVariant = null;
        cachedResolvedPlatformTarget = null;
    }


    private static GLCaps caps() {
        if (caps == null) caps = GLCaps.detect();
        return caps;
    }

    private static ShaderVariant getShaderVariant() {
        if (cachedVariant != null) return cachedVariant;

        switch (requestedPlatformTarget) {
            case DESKTOP_GL30:
                cachedResolvedPlatformTarget = PlatformTarget.DESKTOP_GL30;
                cachedVariant = ShaderVariant.DESKTOP_GL30;
                return cachedVariant;

            case ANDROID_ES3:
                cachedResolvedPlatformTarget = PlatformTarget.ANDROID_ES3;
                cachedVariant = ShaderVariant.ES3_WEBGL2;
                return cachedVariant;

            case HTML_WEBGL2:
                cachedResolvedPlatformTarget = PlatformTarget.HTML_WEBGL2;
                cachedVariant = ShaderVariant.ES3_WEBGL2;
                return cachedVariant;

            case AUTO:
            default:
                cachedResolvedPlatformTarget = detectPlatformTarget();
                cachedVariant = shaderVariantFor(cachedResolvedPlatformTarget);
                return cachedVariant;
        }
    }

    private static ShaderVariant shaderVariantFor(PlatformTarget target) {
        switch (target) {
            case DESKTOP_GL30:
                return ShaderVariant.DESKTOP_GL30;

            case ANDROID_ES3:
            case HTML_WEBGL2:
                return ShaderVariant.ES3_WEBGL2;

            case AUTO:
            default:
                return shaderVariantFor(detectPlatformTarget());
        }
    }

    private static PlatformTarget detectPlatformTarget() {
        GLCaps c = caps();

        if (!c.supportsES3()) {
            throw new IllegalStateException(
                    "Pixscape requires Desktop GL30, Android ES3, or HTML WebGL2. GL20 fallback is no longer supported."
            );
        }

        Gdx.app.log("ShaderRegistry", "appType=" + Gdx.app.getType()
                + " glVersion=" + Gdx.graphics.getGLVersion());

        if (Gdx.app != null
                && Gdx.app.getType() == Application.ApplicationType.WebGL) {
            Gdx.app.log("ShaderRegistry", "Resolved platform target HTML_WEBGL2");
            return PlatformTarget.HTML_WEBGL2;
        }

        GLVersion glVersion = Gdx.graphics.getGLVersion();
        boolean isGles = glVersion != null && glVersion.getType() == GLVersion.Type.GLES;

        if (isGles) {
            Gdx.app.log("ShaderRegistry", "Resolved platform target ANDROID_ES3");
            return PlatformTarget.ANDROID_ES3;
        }

        Gdx.app.log("ShaderRegistry", "Resolved platform target DESKTOP_GL30");
        return PlatformTarget.DESKTOP_GL30;
    }

    private static String variantDirName(ShaderVariant variant) {
        switch (variant) {
            case DESKTOP_GL30:
                return RuntimeFs.SHADER_VARIANT_DESKTOP_GL30;
            case ES3_WEBGL2:
                return RuntimeFs.SHADER_VARIANT_ES3_WEBGL2;
            default:
                throw new IllegalArgumentException("Unknown shader variant: " + variant);
        }
    }

    private static String coreShaderDir(ShaderVariant variant) {
        return RuntimeFs.RUNTIME_DIR_SHADER_CORE + "/" + variantDirName(variant);
    }

    private static String coreShaderPath(ShaderVariant variant, String fileBaseName, String extension) {
        return coreShaderDir(variant) + "/" + fileBaseName + extension;
    }

    private static String coreShaderPath(ShaderVariant variant, ShaderMode mode, String extension) {
        return coreShaderPath(variant, mode.shaderFileBaseName(), extension);
    }

    private static FileHandle getVertexShaderForMode(ShaderMode mode) {
        ShaderVariant variant = getShaderVariant();
        return Gdx.files.internal(coreShaderPath(variant, mode, ".vert"));
    }

    private static void requireModeSupported(ShaderMode mode) {
        if (!isModeSupportedForCurrentProfile(mode)) {
            throw new IllegalStateException("Shader mode " + mode + " is not supported for target " + getResolvedPlatformTarget() + ".");
        }
    }

    private static boolean isModeSupportedForCurrentProfile(ShaderMode mode) {
        GLCaps c = caps();

        switch (mode) {
            case TEXTURE_2D:
            case MULTI_TEXTURE:
                return true;

            case TEXTURE_ARRAY:
                return c.supportsTextureArray();

            default:
                return false;
        }
    }

    // ------------------------------------------------------------------------
    // Core shaders
    // ------------------------------------------------------------------------

    private static void loadMandatoryCoreDefaultShader(ShaderVariant variant, ShaderMode mode) {
        ShaderProgram shader = compileShader(
                coreShaderPath(variant, mode, ".vert"),
                coreShaderPath(variant, mode, ".frag"),
                mode.shaderFileBaseName() + "/" + mode.defaultShaderName(),
                true
        );

        registerOrReplace(
                mode.defaultShaderName(),
                shader,
                mode,
                ShaderOrigin.CORE,
                ShaderRole.MATERIAL
        );
    }

    private static void loadOptionalCoreDefaultShader(ShaderVariant variant, ShaderMode mode) {
        String vertPath = coreShaderPath(variant, mode, ".vert");
        String fragPath = coreShaderPath(variant, mode, ".frag");

        FileHandle vert = Gdx.files.internal(vertPath);
        FileHandle frag = Gdx.files.internal(fragPath);

        if (!vert.exists() || !frag.exists()) return;

        ShaderProgram shader = compileShader(
                vertPath,
                fragPath,
                mode.shaderFileBaseName() + "/" + mode.defaultShaderName(),
                false
        );

        if (shader != null) {
            registerOrReplace(
                    mode.defaultShaderName(),
                    shader,
                    mode,
                    ShaderOrigin.CORE,
                    ShaderRole.MATERIAL
            );
        }
    }

    private static void loadCoreLightShader(ShaderVariant variant, String fileBaseName) {
        String vertPath = coreShaderPath(variant, fileBaseName, ".vert");
        String fragPath = coreShaderPath(variant, fileBaseName, ".frag");

        FileHandle vert = Gdx.files.internal(vertPath);
        FileHandle frag = Gdx.files.internal(fragPath);

        if (!vert.exists() || !frag.exists()) {
            logError("ShaderRegistry", "Missing core light shader: " + vertPath + " / " + fragPath, null);
            return;
        }

        ShaderProgram shader = compileShader(
                vertPath,
                fragPath,
                "light/" + fileBaseName,
                false
        );

        if (shader != null) {
            registerOrReplace(
                    fileBaseName,
                    shader,
                    ShaderMode.TEXTURE_ARRAY,
                    ShaderOrigin.CORE,
                    ShaderRole.LIGHT
            );
        }
    }

    // ------------------------------------------------------------------------
    // Project shaders
    // ------------------------------------------------------------------------

    private static void loadCustomShadersForProject() {
        FileHandle root = projectShadersRoot;
        if (root == null || !root.exists() || !root.isDirectory()) return;

        loadStructuredCustomShaders(root.child("custom"));
    }

    private static void loadStructuredCustomShaders(FileHandle customRoot) {
        if (customRoot == null || !customRoot.exists() || !customRoot.isDirectory()) return;

        loadStructuredShaderCategory(customRoot.child("material"), ShaderRole.MATERIAL);
        loadStructuredShaderCategory(customRoot.child("fx"), ShaderRole.FX);
    }

    private static void loadStructuredShaderCategory(FileHandle categoryDir, ShaderRole roleFromPath) {
        if (categoryDir == null || !categoryDir.exists() || !categoryDir.isDirectory()) return;

        FileHandle[] shaderDirs = categoryDir.list();

        for (FileHandle shaderDir : shaderDirs) {
            if (!shaderDir.isDirectory()) continue;

            try {
                loadStructuredShader(shaderDir, roleFromPath);
            } catch (Exception ex) {
                logError("ShaderRegistry", "Failed to load project shader from " + shaderDir.path(), ex);
            }
        }
    }

    private static void loadStructuredShader(FileHandle shaderDir, ShaderRole roleFromPath) {
        String shaderName = shaderDir.name();
        ShaderMode mode = ShaderMode.TEXTURE_ARRAY;
        ShaderRole role = roleFromPath;

        FileHandle metadataFile = shaderDir.child("shader.json");
        if (metadataFile.exists()) {
            JsonValue metadata = new JsonReader().parse(metadataFile);

            shaderName = metadata.getString("name", shaderName);
            mode = parseShaderMode(metadata.getString("mode", mode.name()), mode);

            String kind = metadata.getString("kind", metadata.getString("type", role.name()));
            role = parseShaderRole(kind, role);
        }

        if (shaderName == null || isBlank(shaderName)) {
            throw new IllegalStateException("Project shader name is empty: " + shaderDir.path());
        }

        if (!isModeSupportedForCurrentProfile(mode)) {
            log("ShaderRegistry", "Skipping project shader '" + shaderName
                    + "' because mode " + mode + " is not supported for current profile.");
            return;
        }

        String prefix = variantDirName(getShaderVariant());

        FileHandle vertFile = shaderDir.child(prefix + ".vert");
        FileHandle fragFile = shaderDir.child(prefix + ".frag");

        if (!vertFile.exists() || !fragFile.exists()) {
            throw new IllegalStateException("Missing " + prefix + " variant for project shader '"
                    + shaderName + "' in " + shaderDir.path());
        }

        registerProjectShader(shaderName, vertFile, fragFile, mode, role);
    }

    private static int registerProjectShader(String name,
                                             FileHandle vertFile,
                                             FileHandle fragFile,
                                             ShaderMode mode,
                                             ShaderRole role) {
        requireModeSupported(mode);

        if (vertFile == null || !vertFile.exists()) {
            throw new IllegalArgumentException("Vertex shader file does not exist for '" + name + "': "
                    + (vertFile != null ? vertFile.path() : "null"));
        }
        if (fragFile == null || !fragFile.exists()) {
            throw new IllegalArgumentException("Fragment shader file does not exist for '" + name + "': "
                    + (fragFile != null ? fragFile.path() : "null"));
        }

        ShaderProgram sp = compileShader(
                vertFile,
                fragFile,
                "project/" + name,
                true
        );

        int idx = registerOrReplace(name, sp, mode, ShaderOrigin.USER, role);

        log("ShaderRegistry", "Registered project shader '" + name + "' (" + mode
                + ", role=" + role + ") at index " + idx
                + " from files " + vertFile.path() + " / " + fragFile.path());

        return idx;
    }

    private static ShaderMode parseShaderMode(String raw, ShaderMode fallback) {
        if (raw == null || isBlank(raw)) return fallback;

        try {
            return ShaderMode.valueOf(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static ShaderRole parseShaderRole(String raw, ShaderRole fallback) {
        if (raw == null || isBlank(raw)) return fallback;

        String normalized = raw.trim().toUpperCase().replace('-', '_');
        if ("POSTFX".equals(normalized) || "POST_FX".equals(normalized)) return ShaderRole.FX;

        try {
            return ShaderRole.valueOf(normalized);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------------
    // Example shaders
    // ------------------------------------------------------------------------

    private static void loadExampleShaders() {
        FileHandle presets = Gdx.files.internal(RuntimeFs.RUNTIME_DIR_SHADER_EXAMPLES + "/params.json");
        if (!presets.exists()) {
            log("ShaderRegistry", "No example shader presets found");
            return;
        }

        JsonValue root = new JsonReader().parse(presets);

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String name = entry.name;
            if (name == null || isBlank(name)) continue;

            ObjectFloatMap<String> defaults = new ObjectFloatMap<>();
            for (JsonValue uniform = entry.child; uniform != null; uniform = uniform.next) {
                if (uniform.name == null || isBlank(uniform.name)) continue;
                defaults.put(uniform.name, uniform.asFloat());
            }
            defaultUniforms.put(name, defaults);

            boolean loaded =
                    tryExample(name, "material", ShaderMode.TEXTURE_ARRAY, ShaderRole.MATERIAL)
                            || tryExample(name, "fx", ShaderMode.TEXTURE_ARRAY, ShaderRole.FX);

            if (!loaded) {
                log("ShaderRegistry", "Example shader '" + name + "' has no matching .frag and was skipped");
            }
        }
    }

    private static boolean tryExample(String name,
                                      String category,
                                      ShaderMode mode,
                                      ShaderRole role) {
        if (!isModeSupportedForCurrentProfile(mode)) return false;

        if (nameToIdx.get(name, -1) >= 0) {
            log("ShaderRegistry", "Skipping example shader '" + name
                    + "' because a shader with the same name is already registered");
            return true;
        }

        String variantDir = variantDirName(getShaderVariant());
        FileHandle categoryVariantDir = Gdx.files.internal(
                RuntimeFs.RUNTIME_DIR_SHADER_EXAMPLES + "/" + category + "/" + variantDir
        );

        FileHandle fragFile = categoryVariantDir.child(name + ".frag");
        if (!fragFile.exists()) return false;

        FileHandle vertFile;
        if (role == ShaderRole.FX) {
            vertFile = categoryVariantDir.child(name + ".vert");
        } else {
            vertFile = getVertexShaderForMode(mode);
        }

        if (vertFile == null || !vertFile.exists()) {
            logError("ShaderRegistry", "Example shader '" + name + "' is missing vertex shader: "
                    + (vertFile != null ? vertFile.path() : "null"), null);
            return true;
        }

        try {
            registerExampleShader(name, vertFile, fragFile, mode, role);
            log("ShaderRegistry", "Loaded example shader '" + name + "' from " + fragFile.path());
            return true;
        } catch (Exception ex) {
            logError("ShaderRegistry", "Failed to load example shader '" + name + "'", ex);
            return true;
        }
    }

    private static int registerExampleShader(String name,
                                             FileHandle vertFile,
                                             FileHandle fragFile,
                                             ShaderMode mode,
                                             ShaderRole role) {
        ShaderProgram sp = compileShader(
                vertFile,
                fragFile,
                "example/" + name,
                true
        );

        int idx = registerOrReplace(name, sp, mode, ShaderOrigin.EXAMPLE, role);

        log("ShaderRegistry", "Registered example shader '" + name + "' (" + mode
                + ", role=" + role + ") at index " + idx
                + " from files " + vertFile.path() + " / " + fragFile.path());

        return idx;
    }

    // ------------------------------------------------------------------------
    // Compile helpers
    // ------------------------------------------------------------------------

    private static ShaderProgram compileShader(String vertPath,
                                               String fragPath,
                                               String friendlyName,
                                               boolean mandatory) {
        return compileShader(
                Gdx.files.internal(vertPath),
                Gdx.files.internal(fragPath),
                friendlyName,
                mandatory
        );
    }

    private static ShaderProgram compileShader(FileHandle vertFile,
                                               FileHandle fragFile,
                                               String friendlyName,
                                               boolean mandatory) {
        if (vertFile == null || fragFile == null || !vertFile.exists() || !fragFile.exists()) {
            String msg = "Shader files not found for " + friendlyName
                    + " (vert=" + (vertFile != null ? vertFile.path() : "null")
                    + ", frag=" + (fragFile != null ? fragFile.path() : "null") + ")";

            if (mandatory) throw new IllegalStateException(msg);

            logError("ShaderRegistry", msg, null);
            return null;
        }

        ShaderProgram.pedantic = false;

        String vertSrc = preprocessShader(vertFile);
        String fragSrc = preprocessShader(fragFile);

        Gdx.app.log("ShaderRegistry", "VERT first chars=[" + vertSrc.substring(0, Math.min(80, vertSrc.length())) + "]");
        Gdx.app.log("ShaderRegistry", "FRAG first chars=[" + fragSrc.substring(0, Math.min(80, fragSrc.length())) + "]");
        log("ShaderRegistry", "Compiled shader " + friendlyName + " from "
                + vertFile.path() + " / " + fragFile.path());

        ShaderProgram sp = new ShaderProgram(vertSrc, fragSrc);
        if (!sp.isCompiled()) {
            String msg = "Failed to compile shader " + friendlyName + " from "
                    + vertFile.path() + " / " + fragFile.path()
                    + ":\n" + sp.getLog();

            sp.dispose();

            if (mandatory) throw new IllegalStateException(msg);

            logError("ShaderRegistry", msg, null);
            return null;
        }

        return sp;
    }

    private static String preprocessShader(FileHandle shaderFile) {
        return ShaderSourcePreprocessor.preprocess(shaderFile, getSharedIncludesDir(shaderFile));
    }

    private static FileHandle getSharedIncludesDir(FileHandle shaderFile) {
        if (shaderFile != null) {
            String path = shaderFile.path().replace('\\', '/');

            if (path.startsWith(RuntimeFs.RUNTIME_DIR_SHADERS + "/")) {
                return Gdx.files.internal(RuntimeFs.RUNTIME_DIR_SHADER_INCLUDES);
            }
        }

        if (projectShadersRoot != null) {
            FileHandle projectIncludes = projectShadersRoot.child("includes");
            if (projectIncludes.exists()) return projectIncludes;
        }

        FileHandle runtimeIncludes = Gdx.files.internal(RuntimeFs.RUNTIME_DIR_SHADER_INCLUDES);
        return runtimeIncludes.exists() ? runtimeIncludes : null;
    }

    private static int registerOrReplace(String name,
                                         ShaderProgram sp,
                                         ShaderMode mode,
                                         ShaderOrigin origin,
                                         ShaderRole role) {
        int existing = nameToIdx.get(name, -1);

        if (existing >= 0) {
            ShaderProgram old = byIdx.get(existing);
            if (old != null) old.dispose();

            byIdx.set(existing, sp);
            ensureMetaSize(existing + 1);
            modesByIdx.set(existing, mode);
            originsByIdx.set(existing, origin);
            rolesByIdx.set(existing, role);

            return existing;
        }

        return register(name, sp, mode, origin, role);
    }

    private static void ensureMetaSize(int size) {
        while (modesByIdx.size < size) modesByIdx.add(ShaderMode.TEXTURE_2D);
        while (originsByIdx.size < size) originsByIdx.add(ShaderOrigin.USER);
        while (rolesByIdx.size < size) rolesByIdx.add(ShaderRole.MATERIAL);
    }

    // ------------------------------------------------------------------------
    // Logging
    // ------------------------------------------------------------------------

    private static void log(String tag, String msg) {
        if (Gdx.app != null) Gdx.app.log(tag, msg);
        else System.out.println("[" + tag + "] " + msg);
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