package games.pixscape.runtime.loading;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.hud.HudResources;
import games.pixscape.runtime.hud.HudTextureProfile;
import games.pixscape.runtime.hud.SceneHudFiles;
import games.pixscape.runtime.particle.ParticleEffectPath;

import java.util.List;

/** Small staged plan for the exact file/resource needs of one selected scene. */
public final class SceneAvailabilityPlan {

    private final FileAvailabilityService availability;
    private final String sceneName;
    private final String sceneTag;
    private final String scenePath;
    private final String atlasPath;
    private final String normalCandidatePath;
    private final games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles normalProfiles;
    private String normalAtlasPath;
    private boolean normalAtlasRequested,normalRequirementsChecked;
    private final FileHandle effectsRoot;
    private final FileHandle gameObjectsRoot;
    private final Array<String> particlePaths = new Array<>();
    private final ObjectSet<String> particlePathSet = new ObjectSet<>();
    private final Array<String> gameObjectPaths = new Array<>();
    private final ObjectSet<String> gameObjectPathSet = new ObjectSet<>();
    private final Array<String> requestedFiles = new Array<>();
    private boolean atlasRequested;
    private boolean dependenciesExpanded;
    private boolean released;
    private final FileHandle runtimeProjectDir;
    private final String hudAtlasId;
    private final SceneHudFiles hudFiles;
    private HudResources hudResources;
    private boolean hudPrepared;

    public SceneAvailabilityPlan(FileAvailabilityService availability,
                                 RuntimeConfig config,
                                 FileHandle runtimeProjectDir,
                                 String sceneName) {
        this(availability, config, runtimeProjectDir, sceneName, null,RuntimeProjectIO.loadTilesetProfiles(runtimeProjectDir));
    }
    public SceneAvailabilityPlan(FileAvailabilityService availability,RuntimeConfig config,FileHandle root,String sceneName,
            games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles profiles) {
        this(availability,config,root,sceneName,null,profiles);
    }

    // Package-private collection seam exercises the selected-root union without new metadata.
    SceneAvailabilityPlan(FileAvailabilityService availability, RuntimeConfig config,
                          FileHandle runtimeProjectDir, String sceneName, List<String> roots) {
        this(availability,config,runtimeProjectDir,sceneName,roots,RuntimeProjectIO.loadTilesetProfiles(runtimeProjectDir));
    }
    private SceneAvailabilityPlan(FileAvailabilityService availability,RuntimeConfig config,FileHandle runtimeProjectDir,
            String sceneName,List<String> roots,games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles profiles) {
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
        this.normalCandidatePath=runtimeProjectDir.child(config.atlasesDir).child(sceneTag+games.pixscape.runtime.service.NormalMapAtlas.SUFFIX+".atlas").path();
        this.normalProfiles=profiles;
        this.normalRequirementsChecked=!meta.useNormalMaps;
        this.effectsRoot = runtimeProjectDir.child(config.effectsDir);
        this.gameObjectsRoot = runtimeProjectDir.child(config.gameObjectsDir);
        this.runtimeProjectDir = runtimeProjectDir;
        this.hudAtlasId = RuntimeFs.DIR_ATLASES + "/hud/" + sceneTag + "/hud.atlas";
        List<String> hudRoots = roots == null ? SceneHudRoots.collect(meta) : roots;
        this.hudFiles = hudRoots.isEmpty() ? null : new SceneHudFiles(availability, runtimeProjectDir, hudAtlasId, hudRoots);

        try {
            requestFile(scenePath);
            addDeclaredParticles(meta);
            addDeclaredGameObjects(meta);
        } catch (RuntimeException failure) {
            try {
                releaseFiles();
            } catch (RuntimeException cleanupFailure) {
                if (Gdx.app != null) Gdx.app.error("PixscapeHud", "Scene file acquisition cleanup failed for " + sceneName + ".", cleanupFailure);
            }
            throw failure;
        }
    }

    public boolean update() {
        requireActive();
        availability.update();
        expandIfSceneAvailable();
        if(!normalRequirementsChecked && dependenciesExpanded && availability.isAvailable(atlasPath,TextureAtlas.class)) {
            if(games.pixscape.runtime.service.NormalMapAtlas.requiresNormals(availability.get(atlasPath,TextureAtlas.class),normalProfiles)) {
                availability.request(normalCandidatePath,TextureAtlas.class);normalAtlasPath=normalCandidatePath;normalAtlasRequested=true;
            }
            normalRequirementsChecked=true;
        }
        if (hudFiles != null) hudFiles.advance();
        return isComplete();
    }

    public void finishOnNative() {
        requireActive();
        do {
            availability.finishLoadingOnNative();
        } while (!update());
    }

    public boolean isComplete() {
        requireActive();
        if (!dependenciesExpanded) return false;
        if(!normalRequirementsChecked)return false;
        if (hudFiles != null && !hudFiles.isComplete()) return false;
        if (!availability.isAvailable(atlasPath, TextureAtlas.class)) return false;
        if(normalAtlasPath!=null && !availability.isAvailable(normalAtlasPath,TextureAtlas.class))return false;
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
        if(normalAtlasPath!=null)total++;
        if (hudFiles != null) total += hudFiles.fileCount();
        int complete = availability.isFileAvailable(scenePath) ? 1 : 0;
        if (hudFiles != null) complete += hudFiles.completeFileCount();
        if (dependenciesExpanded && availability.isAvailable(atlasPath, TextureAtlas.class)) complete++;
        if(dependenciesExpanded && normalAtlasPath!=null && availability.isAvailable(normalAtlasPath,TextureAtlas.class))complete++;
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
    public TextureAtlas normalAtlas() {
        if(!isComplete())throw new IllegalStateException("Scene resources are not available: "+sceneName);
        return normalAtlasPath==null?null:availability.get(normalAtlasPath,TextureAtlas.class);
    }

    public void release() {
        if (released) return;
        released = true;
        try {
            releaseHudResources();
        } finally {
            releaseFiles();
        }
    }

    /** Prepare physical resources before the engine crosses its destructive Scene boundary. */
    public void prepareHudResources() {
        requireActive();
        if (!isComplete()) throw new IllegalStateException("Scene HUD files are not available: " + sceneName);
        if (hudPrepared) return;
        if (hudFiles != null) {
            hudResources = HudResources.prepareEnvironment(runtimeProjectDir, hudAtlasId,
                    HudTextureProfile.DEFAULT_ID, hudFiles.skinIds(),
                    hudFiles.bitmapFontAssetIds());
        }
        hudPrepared = true;
    }

    /** INTERNAL non-owning access for automatic Scene default activation only. */
    public HudResources hudResources() { requireActive(); return hudResources; }

    /** Sessions must already be closed; keep World file leases until World retirement completes. */
    public void releaseHudResources() {
        HudResources previous = hudResources;
        hudResources = null;
        if (previous != null) previous.dispose();
    }

    private void releaseFiles() {
        RuntimeException failure = null;
        try {
            if (hudFiles != null) hudFiles.release();
        } catch (RuntimeException cleanupFailure) {
            failure = cleanupFailure;
        }
        for (int i = 0; i < requestedFiles.size; i++) {
            try {
                availability.releaseFile(requestedFiles.get(i));
            } catch (RuntimeException cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
            }
        }
        requestedFiles.clear();
        if(normalAtlasRequested){
            normalAtlasRequested=false;
            try{availability.release(normalAtlasPath,TextureAtlas.class);}
            catch(RuntimeException cleanupFailure){if(failure==null)failure=cleanupFailure;}
        }
        if (atlasRequested) {
            atlasRequested = false;
            try {
                availability.release(atlasPath, TextureAtlas.class);
            } catch (RuntimeException cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
            }
        }
        if (failure != null) throw failure;
    }

    private void requestFile(String path) {
        availability.requestFile(path);
        requestedFiles.add(path);
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
        atlasRequested = true;
        for (int i = 0; i < particlePaths.size; i++) {
            requestFile(particlePaths.get(i));
        }
        for (int i = 0; i < gameObjectPaths.size; i++) {
            requestFile(gameObjectPaths.get(i));
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
