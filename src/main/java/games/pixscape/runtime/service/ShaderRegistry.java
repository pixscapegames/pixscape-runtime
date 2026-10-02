package games.pixscape.runtime.service;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;

/**
 * {@code SUPPORTED_EXPERT} process-wide shader lookup and project shader lifecycle boundary.
 *
 * <p>The registry follows Pixscape's one-active-engine/graphics-context contract and is not
 * thread-safe. Registration transfers disposal ownership of the {@link ShaderProgram} to the
 * registry. Returned programs are borrowed; do not dispose them. A successful project reload
 * or {@link #disposeAll()} invalidates previously returned programs. Failed reloads preserve
 * the active registry and stable shader indexes.</p>
 */
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

    private static ObjectIntMap<String> nameToIdx = new ObjectIntMap<>();
    private static Array<ShaderProgram> byIdx = new Array<>();
    private static Array<ShaderMode> modesByIdx = new Array<>();
    private static Array<ShaderOrigin> originsByIdx = new Array<>();
    private static Array<ShaderRole> rolesByIdx = new Array<>();
    private static ObjectMap<String, Array<ShaderFloatParam>> defaultUniforms = new ObjectMap<>();
    private static Array<ShaderParameterLayout> parameterLayoutsByIdx = new Array<>();
    private static ObjectIntMap<String> reservedIndices = new ObjectIntMap<>();
    private static final String INDEX_FILE = "shader-indices.json";
    private static final int MAX_SHADERS = 1 << SortKey64.SHADER_BITS;
    private static int nextShaderIndex;

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

        int idx = reservedIndices.get(name, -1);
        boolean newlyAllocated = idx < 0;
        if (newlyAllocated) idx = nextShaderIndex;
        if (idx >= MAX_SHADERS) {
            throw new IllegalStateException("Shader index capacity exceeded: " + idx);
        }
        while (byIdx.size <= idx) byIdx.add(null);
        if (byIdx.get(idx) != null) {
            throw new IllegalStateException("Shader index " + idx + " is already occupied by another shader");
        }
        byIdx.set(idx, sp);
        ensureMetaSize(idx + 1);
        modesByIdx.set(idx, mode);
        originsByIdx.set(idx, origin);
        rolesByIdx.set(idx, role);
        parameterLayoutsByIdx.set(idx, new ShaderParameterLayout(name, null));
        nameToIdx.put(name, idx);
        if (newlyAllocated) nextShaderIndex++;
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

    public static Array<ShaderFloatParam> getDefaultUniforms(String shaderName) {
        return defaultUniforms.get(shaderName);
    }

    public static ShaderParameterLayout getParameterLayout(int shaderIndex) {
        if (shaderIndex < 0 || shaderIndex >= parameterLayoutsByIdx.size) return ShaderParameterLayout.EMPTY;
        return parameterLayoutsByIdx.get(shaderIndex);
    }

    /** Registers an ordered, float-only table layout for a material shader. */
    public static void registerParameterLayout(String shaderName, Array<ShaderFloatParam> defaults) {
        int index = indexOf(shaderName);
        if (index < 0) throw new IllegalArgumentException("Unknown shader '" + shaderName + "'");
        ShaderParameterLayout layout = new ShaderParameterLayout(shaderName, defaults);
        parameterLayoutsByIdx.set(index, layout);
        defaultUniforms.put(shaderName, defaults);
    }

    private static void addDefaultUniform(Array<ShaderFloatParam> uniforms, String name, float value) {
        if (uniforms == null || name == null || isBlank(name)) return;
        uniforms.add(new ShaderFloatParam(name, value));
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
        disposePrograms(byIdx);
        clearActiveRegistry();
    }

    private static void disposePrograms(Array<ShaderProgram> programs) {
        for (int i = 0, n = programs.size; i < n; i++) {
            ShaderProgram sp = programs.get(i);
            if (sp != null) sp.dispose();
        }
    }

    private static void clearActiveRegistry() {
        byIdx.clear();
        nameToIdx.clear();
        modesByIdx.clear();
        originsByIdx.clear();
        rolesByIdx.clear();
        defaultUniforms.clear();
        parameterLayoutsByIdx.clear();
        reservedIndices.clear();
        nextShaderIndex = 0;

        initialized = false;

        requestedPlatformTarget = PlatformTarget.AUTO;
        cachedResolvedPlatformTarget = null;
        projectShadersRoot = null;

        cachedVariant = null;
        caps = null;
    }

    public static void reloadForProject(FileHandle projectDir, String shadersDir) {
        RegistrySnapshot previousState = new RegistrySnapshot();
        String oldRoot = projectShadersRoot == null ? null : projectShadersRoot.path();
        String newRoot = projectDir == null || shadersDir == null ? null : projectDir.child(shadersDir).path();
        int previousNext = nextShaderIndex;
        startCandidateRegistry();
        try {
            setProjectContext(PlatformTarget.AUTO, projectDir, shadersDir);
            if (projectShadersRoot != null && !projectShadersRoot.child(INDEX_FILE).exists()
                    && oldRoot != null && oldRoot.equals(newRoot)) {
                for (ObjectIntMap.Entries<String> it = previousState.names.entries(); it.hasNext(); ) {
                    ObjectIntMap.Entry<String> entry = it.next();
                    reservedIndices.put(entry.key, entry.value);
                }
                nextShaderIndex = previousNext;
            }
            initDefaults();
        } catch (RuntimeException | Error failure) {
            disposePrograms(byIdx);
            previousState.restore();
            throw failure;
        }
        disposePrograms(previousState.programs);
    }

    /** Keeps the previous registry by reference while a replacement is prepared. */
    private static final class RegistrySnapshot {
        final ObjectIntMap<String> names = nameToIdx;
        final Array<ShaderProgram> programs = byIdx;
        final Array<ShaderMode> modes = modesByIdx;
        final Array<ShaderOrigin> origins = originsByIdx;
        final Array<ShaderRole> roles = rolesByIdx;
        final ObjectMap<String, Array<ShaderFloatParam>> defaults = defaultUniforms;
        final Array<ShaderParameterLayout> layouts = parameterLayoutsByIdx;
        final ObjectIntMap<String> reserved = reservedIndices;
        final int next = nextShaderIndex;
        final boolean wasInitialized = initialized;
        final PlatformTarget target = requestedPlatformTarget;
        final PlatformTarget resolvedTarget = cachedResolvedPlatformTarget;
        final FileHandle shadersRoot = projectShadersRoot;
        final ShaderVariant variant = cachedVariant;
        final GLCaps glCaps = caps;

        void restore() {
            nameToIdx = names;
            byIdx = programs;
            modesByIdx = modes;
            originsByIdx = origins;
            rolesByIdx = roles;
            defaultUniforms = defaults;
            parameterLayoutsByIdx = layouts;
            reservedIndices = reserved;
            nextShaderIndex = next;
            initialized = wasInitialized;
            requestedPlatformTarget = target;
            cachedResolvedPlatformTarget = resolvedTarget;
            projectShadersRoot = shadersRoot;
            cachedVariant = variant;
            caps = glCaps;
        }
    }

    private static void startCandidateRegistry() {
        nameToIdx = new ObjectIntMap<>();
        byIdx = new Array<>();
        modesByIdx = new Array<>();
        originsByIdx = new Array<>();
        rolesByIdx = new Array<>();
        defaultUniforms = new ObjectMap<>();
        parameterLayoutsByIdx = new Array<>();
        reservedIndices = new ObjectIntMap<>();
        nextShaderIndex = 0;
        initialized = false;
        requestedPlatformTarget = PlatformTarget.AUTO;
        cachedResolvedPlatformTarget = null;
        projectShadersRoot = null;
        cachedVariant = null;
        caps = null;
    }

    /** Persists the project's shader slot allocator after a successful Studio reload. */
    public static void saveProjectIndices() {
        if (projectShadersRoot == null || !initialized) return;
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("nextIndex", new JsonValue(nextShaderIndex));
        JsonValue names = new JsonValue(JsonValue.ValueType.object);
        Array<String> registeredNames = getRegisteredNames();
        for (int i = 0; i < registeredNames.size; i++) {
            String name = registeredNames.get(i);
            names.addChild(name, new JsonValue(indexOf(name)));
        }
        root.addChild("names", names);
        FileHandle file = projectShadersRoot.child(INDEX_FILE);
        String contents = root.toJson(JsonWriter.OutputType.json);
        if (!file.exists() || !contents.equals(file.readString("UTF-8"))) {
            file.writeString(contents, false, "UTF-8");
        }
    }

    public static void initDefaults(FileHandle projectDir, String shadersDir) {
        initDefaults(PlatformTarget.AUTO, projectDir, shadersDir);
    }

    public static void initDefaults(PlatformTarget target, FileHandle projectDir, String shadersDir) {
        setProjectContext(target, projectDir, shadersDir);
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
        loadCoreHudShader(variant);

        loadOptionalCoreDefaultShader(variant, ShaderMode.TEXTURE_2D);
        loadOptionalCoreDefaultShader(variant, ShaderMode.MULTI_TEXTURE);

        registerCoreLightDefaults();
        loadExampleShaders();
        // The saved index map, rather than registration order, preserves project shader identity.
        loadCustomShadersForProject();

        initialized = true;
    }

    private static void registerCoreLightDefaults() {
        Array<ShaderFloatParam> point = ShaderParamsComponent.newShaderFloatArray();
        addDefaultUniform(point, "u_centerX", 0f);
        addDefaultUniform(point, "u_centerY", 0f);
        addDefaultUniform(point, "u_radius", 1f);
        addDefaultUniform(point, "u_falloff", 1.5f);
        defaultUniforms.put(RuntimeFs.TEXTURE_ARRAY_POINTLIGHT, point);
        registerParameterLayout(RuntimeFs.TEXTURE_ARRAY_POINTLIGHT, point);

        Array<ShaderFloatParam> cone = ShaderParamsComponent.newShaderFloatArray();
        addDefaultUniform(cone, "u_centerX", 0f);
        addDefaultUniform(cone, "u_centerY", 0f);
        addDefaultUniform(cone, "u_radius", 1f);
        addDefaultUniform(cone, "u_dirX", 1.0f);
        addDefaultUniform(cone, "u_dirY", 0.0f);
        addDefaultUniform(cone, "u_coneCos", 0.8660254f);
        addDefaultUniform(cone, "u_softness", 0.1f);
        addDefaultUniform(cone, "u_falloff", 1.5f);
        defaultUniforms.put(RuntimeFs.TEXTURE_ARRAY_CONELIGHT, cone);
        registerParameterLayout(RuntimeFs.TEXTURE_ARRAY_CONELIGHT, cone);
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
        testCompile(name, vertexSource, fragmentSource, mode, ShaderParameterLayout.EMPTY);
    }

    public static void testCompile(String name,
                                   String vertexSource,
                                   String fragmentSource,
                                   ShaderMode mode,
                                   ShaderParameterLayout layout) {
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

        ShaderProgram sp = new ShaderProgram(
                withParameterDefines(processedVertexSource, layout),
                withParameterDefines(processedFragmentSource, layout));
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

        reservedIndices.clear();
        nextShaderIndex = 0;
        if (projectShadersRoot != null) loadProjectIndices(projectShadersRoot.child(INDEX_FILE));

        cachedVariant = null;
        cachedResolvedPlatformTarget = null;
    }

    private static void loadProjectIndices(FileHandle file) {
        if (!file.exists()) return;
        JsonValue root = new JsonReader().parse(file);
        JsonValue names = root.get("names");
        JsonValue nextValue = root.get("nextIndex");
        int next = nextValue == null || !nextValue.isNumber() ? -1 : nextValue.asInt();
        if (next < 0 || next > MAX_SHADERS || nextValue.asFloat() != next
                || names == null || !names.isObject()) {
            throw new IllegalStateException("Invalid shader index file: " + file.path());
        }
        boolean[] used = new boolean[next];
        for (JsonValue entry = names.child; entry != null; entry = entry.next) {
            if (entry.name == null || isBlank(entry.name) || !entry.isNumber()) {
                throw new IllegalStateException("Invalid shader index entry in " + file.path());
            }
            int index = entry.asInt();
            if (index < 0 || index >= next || entry.asFloat() != index
                    || used[index] || reservedIndices.containsKey(entry.name)) {
                throw new IllegalStateException("Conflicting shader index in " + file.path()
                        + ": " + entry.name + "=" + index);
            }
            used[index] = true;
            reservedIndices.put(entry.name, index);
        }
        nextShaderIndex = next;
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

        Application.ApplicationType appType = Gdx.app != null ? Gdx.app.getType() : null;

        if (appType == Application.ApplicationType.WebGL) {
            return PlatformTarget.HTML_WEBGL2;
        }

        GLVersion glVersion = Gdx.graphics != null ? Gdx.graphics.getGLVersion() : null;
        boolean isGles = glVersion != null && glVersion.getType() == GLVersion.Type.GLES;

        if (isGles) {
            return PlatformTarget.ANDROID_ES3;
        }

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

    private static FileHandle resolveShaderFile(String path) {
        if (projectShadersRoot != null
                && path != null
                && path.startsWith(RuntimeFs.RUNTIME_DIR_SHADERS + "/")) {
            String rel = path.substring(RuntimeFs.RUNTIME_DIR_SHADERS.length() + 1);
            FileHandle projectFile = projectShadersRoot.child(rel);
            if (projectFile.exists()) return projectFile;
        }

        return Gdx.files.internal(path);
    }

    private static FileHandle getVertexShaderForMode(ShaderMode mode) {
        ShaderVariant variant = getShaderVariant();
        return resolveShaderFile(coreShaderPath(variant, mode, ".vert"));
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
        String vertPath = coreShaderPath(variant, mode, ".vert");
        String fragPath = coreShaderPath(variant, mode, ".frag");

        ShaderProgram shader = compileShader(
                resolveShaderFile(vertPath),
                resolveShaderFile(fragPath),
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

        FileHandle vert = resolveShaderFile(vertPath);
        FileHandle frag = resolveShaderFile(fragPath);

        if (!vert.exists() || !frag.exists()) return;

        ShaderProgram shader = compileShader(
                vert,
                frag,
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

        FileHandle vert = resolveShaderFile(vertPath);
        FileHandle frag = resolveShaderFile(fragPath);

        if (!vert.exists() || !frag.exists()) {
            throw new IllegalStateException("Missing core light shader: " + vertPath + " / " + fragPath);
        }

        ShaderProgram shader = compileShader(
                vert,
                frag,
                "light/" + fileBaseName,
                true
        );

        registerOrReplace(
                fileBaseName,
                shader,
                ShaderMode.TEXTURE_ARRAY,
                ShaderOrigin.CORE,
                ShaderRole.LIGHT
        );
    }

    private static void loadCoreHudShader(ShaderVariant variant) {
        String vertPath = coreShaderPath(variant, RuntimeFs.HUD_TEXTURE_ARRAY, ".vert");
        String fragPath = coreShaderPath(variant, RuntimeFs.HUD_TEXTURE_ARRAY, ".frag");
        ShaderProgram shader = compileShader(
                resolveShaderFile(vertPath),
                resolveShaderFile(fragPath),
                "hud/" + RuntimeFs.HUD_TEXTURE_ARRAY,
                true);
        registerOrReplace(
                RuntimeFs.HUD_TEXTURE_ARRAY,
                shader,
                ShaderMode.TEXTURE_ARRAY,
                ShaderOrigin.CORE,
                ShaderRole.HUD);
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
                throw new IllegalStateException("Failed to load project shader from " + shaderDir.path(), ex);
            }
        }
    }

    private static void loadStructuredShader(FileHandle shaderDir, ShaderRole roleFromPath) {
        String shaderName = shaderDir.name();
        ShaderMode mode = ShaderMode.TEXTURE_ARRAY;
        ShaderRole role = roleFromPath;

        FileHandle metadataFile = shaderDir.child("shader.json");
        JsonValue metadata = null;
        if (metadataFile.exists()) {
            metadata = new JsonReader().parse(metadataFile);

            shaderName = metadata.getString("name", shaderName);
            mode = parseShaderMode(metadata.getString("mode", mode.name()), mode);

            String kind = metadata.getString("kind", metadata.getString("type", role.name()));
            role = parseShaderRole(kind, role);
        }

        if (shaderName == null || isBlank(shaderName)) {
            throw new IllegalStateException("Project shader name is empty: " + shaderDir.path());
        }

        if (!isModeSupportedForCurrentProfile(mode)) {
            return;
        }

        String prefix = variantDirName(getShaderVariant());

        FileHandle vertFile = shaderDir.child(prefix + ".vert");
        FileHandle fragFile = shaderDir.child(prefix + ".frag");

        if (!vertFile.exists() || !fragFile.exists()) {
            throw new IllegalStateException("Missing " + prefix + " variant for project shader '"
                    + shaderName + "' in " + shaderDir.path());
        }

        JsonValue parameters = metadata == null ? null : metadata.get("parameters");
        ShaderParameterLayout layout = ShaderParameterLayout.EMPTY;
        Array<ShaderFloatParam> defaults = null;
        if (parameters != null) {
            if (role != ShaderRole.MATERIAL || mode != ShaderMode.TEXTURE_ARRAY || !parameters.isObject()) {
                throw new IllegalArgumentException("Shader '" + shaderName
                        + "' requires a material texture-array float parameter object");
            }
            defaults = ShaderParamsComponent.newShaderFloatArray();
            for (JsonValue value = parameters.child; value != null; value = value.next) {
                if (!value.isNumber()) {
                    throw new IllegalArgumentException("Shader '" + shaderName + "' parameter '"
                            + value.name + "' must be a float");
                }
                defaults.add(new ShaderFloatParam(value.name, value.asFloat()));
            }
            layout = new ShaderParameterLayout(shaderName, defaults);
        }
        registerProjectShader(shaderName, vertFile, fragFile, mode, role, layout);
        if (defaults != null) registerParameterLayout(shaderName, defaults);
    }

    private static int registerProjectShader(String name,
                                             FileHandle vertFile,
                                             FileHandle fragFile,
                                             ShaderMode mode,
                                             ShaderRole role) {
        return registerProjectShader(name, vertFile, fragFile, mode, role, ShaderParameterLayout.EMPTY);
    }

    private static int registerProjectShader(String name,
                                             FileHandle vertFile,
                                             FileHandle fragFile,
                                             ShaderMode mode,
                                             ShaderRole role,
                                             ShaderParameterLayout layout) {
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
                true,
                layout
        );
        return registerOrReplace(name, sp, mode, ShaderOrigin.USER, role);
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
        FileHandle presets = resolveShaderFile(RuntimeFs.RUNTIME_DIR_SHADER_EXAMPLES + "/params.json");
        if (!presets.exists()) return;

        JsonValue root = new JsonReader().parse(presets);

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            String name = entry.name;
            if (name == null || isBlank(name)) continue;

            Array<ShaderFloatParam> defaults = ShaderParamsComponent.newShaderFloatArray();

            for (JsonValue uniform = entry.child; uniform != null; uniform = uniform.next) {
                if (uniform.name == null || isBlank(uniform.name)) continue;
                defaults.add(new ShaderFloatParam(uniform.name, uniform.asFloat()));
            }

            defaultUniforms.put(name, defaults);
            boolean materialLoaded = tryExample(name, "material", ShaderMode.TEXTURE_ARRAY, ShaderRole.MATERIAL);
            boolean fxLoaded = tryExample(name, "fx", ShaderMode.TEXTURE_ARRAY, ShaderRole.FX);
            if (!materialLoaded && !fxLoaded) {
                throw new IllegalStateException("Example shader '" + name + "' has parameters but no fragment variant");
            }
        }
    }

    private static boolean tryExample(String name,
                                      String category,
                                      ShaderMode mode,
                                      ShaderRole role) {
        if (!isModeSupportedForCurrentProfile(mode)) return false;

        if (nameToIdx.get(name, -1) >= 0) {
            return true;
        }

        String variantDir = variantDirName(getShaderVariant());
        FileHandle categoryVariantDir = resolveShaderFile(
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
            throw new IllegalStateException("Example shader '" + name + "' is missing vertex shader: "
                    + (vertFile != null ? vertFile.path() : "null"));
        }

        try {
            registerExampleShader(name, vertFile, fragFile, mode, role);
            if (role == ShaderRole.MATERIAL) registerParameterLayout(name, defaultUniforms.get(name));
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load example shader '" + name + "'", ex);
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
        return registerOrReplace(name, sp, mode, ShaderOrigin.EXAMPLE, role);
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
        return compileShader(vertFile, fragFile, friendlyName, mandatory, ShaderParameterLayout.EMPTY);
    }

    private static ShaderProgram compileShader(FileHandle vertFile,
                                               FileHandle fragFile,
                                               String friendlyName,
                                               boolean mandatory,
                                               ShaderParameterLayout layout) {
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

        ShaderProgram sp = new ShaderProgram(
                withParameterDefines(vertSrc, layout), withParameterDefines(fragSrc, layout));
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

    private static String withParameterDefines(String source, ShaderParameterLayout layout) {
        if (layout == null || layout.size() == 0) return source;
        String defines = layout.glslDefines();
        int versionEnd = source.startsWith("#version") ? source.indexOf('\n') + 1 : 0;
        if (versionEnd <= 0) return defines + source;
        return source.substring(0, versionEnd) + defines + source.substring(versionEnd);
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
            parameterLayoutsByIdx.set(existing, new ShaderParameterLayout(name, null));

            return existing;
        }

        try {
            return register(name, sp, mode, origin, role);
        } catch (RuntimeException | Error failure) {
            sp.dispose();
            throw failure;
        }
    }

    private static void ensureMetaSize(int size) {
        while (modesByIdx.size < size) modesByIdx.add(ShaderMode.TEXTURE_2D);
        while (originsByIdx.size < size) originsByIdx.add(ShaderOrigin.USER);
        while (rolesByIdx.size < size) rolesByIdx.add(ShaderRole.MATERIAL);
        while (parameterLayoutsByIdx.size < size) parameterLayoutsByIdx.add(ShaderParameterLayout.EMPTY);
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
