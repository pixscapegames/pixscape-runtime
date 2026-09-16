package games.pixscape.runtime.loading;

import games.pixscape.runtime.hud.HudScreenAssetId;
import java.util.Collections;
import java.util.List;

/** Current export contract has only a Scene default; keep preparation rooted in a collection. */
final class SceneHudRoots {
    private SceneHudRoots() { }

    static List<String> collect(SceneMetaRuntime meta) {
        String id = HudScreenAssetId.normalizeOptional(meta.defaultHudScreenId);
        return id == null ? Collections.<String>emptyList() : Collections.singletonList(id);
    }
}
