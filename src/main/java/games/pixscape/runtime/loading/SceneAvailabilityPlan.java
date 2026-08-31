package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.runtime.particle.ParticleEffectPath;

/** Small staged plan for the exact file/resource needs of one selected scene. */
public final class SceneAvailabilityPlan {

    private final FileAvailabilityService availability;
    private final String sceneName;
    private final String sceneTag;
    private final String scenePath;
    private final String atlasPath;
    private final FileHandle effectsRoot;
    private final FileHandle gameObjectsRoot;
    private final Array<String> particlePaths = new Array<>();
    private final ObjectSet<String> particlePathSet = new ObjectSet<>();
    private final Array<String> gameObjectPaths = new Array<>();
    private final ObjectSet<String> gameObjectPathSet = new ObjectSet<>();
    private boolean dependenciesExpanded;
    private boolean released;

    public SceneAvailabilityPlan(FileAvailabilityService availability,
                                 RuntimeConfig config,
                                 FileHandle runtimeProjectDir,
                                 String sceneName) {
        if (availability == null) throw new IllegalArgumentException("availability is null");
        if (config == null) throw new IllegalArgumentException("config is null");
        if (runtimeProjectDir == null) throw new IllegalArgumentException("runtimeProjectDir is null");
        SceneMetaRuntime meta = config.getSceneMeta(sceneName);
        if (meta == null) throw new IllegalArgumentException("Unknown scene: " + sceneName);

        this.availability = availability;
        this.sceneName = sceneName;
        this.sceneTag = RuntimeConfig.sceneDirName(meta);
        if (sceneTag == null || sceneTag.length() == 0) {
            throw new IllegalStateException("Cannot resolve scene tag for: " + sceneName);
        }
        this.scenePath = runtimeProjectDir.child(config.scenesDir)
                .child(RuntimeFs.withExt(sceneTag, RuntimeFs.EXT_JSON)).path();
        this.atlasPath = runtimeProjectDir.child(config.atlasesDir)
                .child(RuntimeFs.withExt(sceneTag, RuntimeFs.EXT_ATLAS)).path();
        this.effectsRoot = runtimeProjectDir.child(config.effectsDir);
        this.gameObjectsRoot = runtimeProjectDir.child(config.gameObjectsDir);

        availability.requestFile(scenePath);
        addDeclaredParticles(meta);
        addDeclaredGameObjects(meta);
    }

    public boolean update() {
        requireActive();
        availability.update();
        expandIfSceneAvailable();
        return isComplete();
    }

    public void finishOnNative() {
        requireActive();
        availability.finishLoadingOnNative();
        expandIfSceneAvailable();
        availability.finishLoadingOnNative();
    }

    public boolean isComplete() {
        requireActive();
        if (!dependenciesExpanded) return false;
        if (!availability.isAvailable(atlasPath, TextureAtlas.class)) return false;
        for (int i = 0; i < particlePaths.size; i++) {
            if (!availability.isFileAvailable(particlePaths.get(i))) return false;
        }
        for (int i = 0; i < gameObjectPaths.size; i++) {
            if (!availability.isFileAvailable(gameObjectPaths.get(i))) return false;
        }
        return true;
    }

    public float progress() {
        requireActive();
        int total = 2 + particlePaths.size + gameObjectPaths.size;
        int complete = availability.isFileAvailable(scenePath) ? 1 : 0;
        if (dependenciesExpanded && availability.isAvailable(atlasPath, TextureAtlas.class)) complete++;
        for (int i = 0; i < particlePaths.size; i++) {
            if (availability.isFileAvailable(particlePaths.get(i))) complete++;
        }
        for (int i = 0; i < gameObjectPaths.size; i++) {
            if (availability.isFileAvailable(gameObjectPaths.get(i))) complete++;
        }
        return (float) complete / (float) total;
    }

    public TextureAtlas atlas() {
        if (!isComplete()) throw new IllegalStateException("Scene resources are not available: " + sceneName);
        return availability.get(atlasPath, TextureAtlas.class);
    }

    public void release() {
        if (released) return;
        released = true;
        availability.releaseFile(scenePath);
        if (dependenciesExpanded) availability.release(atlasPath, TextureAtlas.class);
        for (int i = 0; i < particlePaths.size; i++) {
            availability.releaseFile(particlePaths.get(i));
        }
        for (int i = 0; i < gameObjectPaths.size; i++) {
            availability.releaseFile(gameObjectPaths.get(i));
        }
    }

    public String sceneName() {
        return sceneName;
    }

    public String sceneTag() {
        return sceneTag;
    }

    String scenePath() {
        return scenePath;
    }

    String atlasPath() {
        return atlasPath;
    }

    Array<String> particlePaths() {
        return new Array<>(particlePaths);
    }

    private void expandIfSceneAvailable() {
        if (dependenciesExpanded || !availability.isFileAvailable(scenePath)) return;

        ObjectSet<String> authored = SceneParticleDependencyScanner.scan(
                availability.file(scenePath));
        for (ObjectSet.ObjectSetIterator<String> it = authored.iterator(); it.hasNext; ) {
            addParticle(it.next());
        }

        availability.request(atlasPath, TextureAtlas.class);
        for (int i = 0; i < particlePaths.size; i++) {
            availability.requestFile(particlePaths.get(i));
        }
        for (int i = 0; i < gameObjectPaths.size; i++) {
            availability.requestFile(gameObjectPaths.get(i));
        }
        dependenciesExpanded = true;
    }

    private void addDeclaredParticles(SceneMetaRuntime meta) {
        for (int i = 0; i < meta.runtimeParticleEffectPaths.size; i++) {
            addParticle(meta.runtimeParticleEffectPaths.get(i));
        }
    }

    private void addDeclaredGameObjects(SceneMetaRuntime meta) {
        for (int i = 0; i < meta.runtimeGameObjectIds.size; i++) {
            addGameObject(meta.runtimeGameObjectIds.get(i));
        }
    }

    private void addParticle(String effectPath) {
        String normalized = ParticleEffectPath.normalize(effectPath);
        String path = ParticleEffectPath.resolve(effectsRoot, normalized).path();
        path = FileAvailabilityService.normalizePath(path);
        if (particlePathSet.add(path)) particlePaths.add(path);
    }

    private void addGameObject(String gameObjectId) {
        String path = gameObjectsRoot
                .child(GameObjectAssetId.assetName(gameObjectId)
                        + games.pixscape.runtime.gameobject.GameObjectAsset.EXTENSION).path();
        path = FileAvailabilityService.normalizePath(path);
        if (gameObjectPathSet.add(path)) gameObjectPaths.add(path);
    }

    private void requireActive() {
        if (released) throw new IllegalStateException("Scene availability plan is released: " + sceneName);
    }
}
