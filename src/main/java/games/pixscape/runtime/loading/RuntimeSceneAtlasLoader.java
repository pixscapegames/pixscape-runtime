package games.pixscape.runtime.loading;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.service.AtlasRuntimeService;

public final class RuntimeSceneAtlasLoader {

    private RuntimeSceneAtlasLoader() {
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

        if (availableAtlas == null) {
            throw new IllegalArgumentException(
                    "Manager-owned scene atlas is required for '" + sceneName + "'.");
        }
        atlasRuntimeService.unload(sceneDirName);
        atlasRuntimeService.loadBorrowed(sceneDirName, availableAtlas);
        Gdx.app.log("RuntimeSceneAtlasLoader",
                "Manager-owned scene atlas reused for '" + sceneName + "'.");
    }
}
