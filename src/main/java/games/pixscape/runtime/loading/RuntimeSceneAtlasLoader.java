package games.pixscape.runtime.loading;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.AtlasRuntimeService;

public final class RuntimeSceneAtlasLoader {

    private RuntimeSceneAtlasLoader() {
    }

    public static void loadSceneAtlas(RuntimeConfig cfg,
                                      String sceneName,
                                      FileHandle projectDir,
                                      AtlasRuntimeService atlasRuntimeService) {
        loadSceneAtlas(cfg, sceneName, projectDir, atlasRuntimeService, null);
    }

    public static void loadSceneAtlas(RuntimeConfig cfg,
                                      String sceneName,
                                      FileHandle projectDir,
                                      AtlasRuntimeService atlasRuntimeService,
                                      TextureAtlas availableAtlas) {
        if (cfg == null || sceneName == null || sceneName.isEmpty()) {
            Gdx.app.error("RuntimeSceneAtlasLoader", "Invalid cfg/sceneName, skip atlas load.");
            return;
        }
        if (projectDir == null) {
            Gdx.app.error("RuntimeSceneAtlasLoader", "projectDir is null, skip atlas load.");
            return;
        }

        SceneMetaRuntime meta = cfg.getSceneMeta(sceneName);
        String sceneDirName = RuntimeConfig.sceneDirName(meta);
        if (sceneDirName == null || sceneDirName.isEmpty()) {
            Gdx.app.error("RuntimeSceneAtlasLoader",
                    "Cannot resolve scene dir name for '" + sceneName + "', skip atlas load.");
            return;
        }

        if (availableAtlas != null) {
            atlasRuntimeService.unload(sceneDirName);
            atlasRuntimeService.loadBorrowed(sceneDirName, availableAtlas);
            Gdx.app.log("RuntimeSceneAtlasLoader",
                    "Scene atlas reused for '" + sceneName + "'.");
            return;
        }

        FileHandle atlasesRoot = projectDir.child(cfg.atlasesDir);
        FileHandle atlasFile = atlasesRoot.child(RuntimeFs.withExt(sceneDirName, RuntimeFs.EXT_ATLAS));
        if (!atlasFile.exists()) {
            Gdx.app.log("RuntimeSceneAtlasLoader",
                    "No atlas file for scene '" + sceneName + "'. Scene atlas sprites will stay invalid.");
            return;
        }

        atlasRuntimeService.unload(sceneDirName);
        atlasRuntimeService.load(sceneDirName, atlasFile);
        Gdx.app.log("RuntimeSceneAtlasLoader",
                "Scene atlas loaded for '" + sceneName + "'.");
    }
}
