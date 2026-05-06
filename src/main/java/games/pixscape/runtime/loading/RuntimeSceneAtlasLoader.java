package games.pixscape.runtime.loading;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.AtlasRuntimeService;

public final class RuntimeSceneAtlasLoader {

    private RuntimeSceneAtlasLoader() {}

    public static void loadSceneAtlas(RuntimeConfig cfg,
                                      String sceneName,
                                      FileHandle projectDir,
                                      AtlasRuntimeService atlasRuntimeService) {
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

        FileHandle atlasesRoot = projectDir.child(cfg.atlasesDir);
        FileHandle atlasFile   = atlasesRoot.child(RuntimeFs.withExt(sceneDirName, RuntimeFs.EXT_ATLAS));

        if (!atlasFile.exists()) {
            Gdx.app.log("RuntimeSceneAtlasLoader",
                    "No atlas file for scene '" + sceneName + "' (dir '" + sceneDirName + "') at " + atlasFile.path() +
                            " — sprites SCENE_ATLAS will stay invalid.");
            return;
        }

        atlasRuntimeService.unload(sceneDirName);
        atlasRuntimeService.load(sceneDirName, atlasFile);
        Gdx.app.log("RuntimeSceneAtlasLoader",
                "Atlas loaded for scene '" + sceneName + "' (key '" + sceneDirName + "'): " + atlasFile.path());
    }
}
