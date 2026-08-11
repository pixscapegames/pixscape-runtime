package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.particle.ParticleEffectPath;

/** Performs one lightweight scene-JSON scan for authored particle effect paths. */
final class SceneParticleDependencyScanner {

    private SceneParticleDependencyScanner() {
    }

    static ObjectSet<String> scan(FileHandle sceneFile) {
        ObjectSet<String> paths = new ObjectSet<>();
        JsonValue root = new JsonReader().parse(sceneFile);
        collect(root, paths);
        return paths;
    }

    private static void collect(JsonValue value, ObjectSet<String> paths) {
        if (value == null) return;
        if (value.isObject()) {
            JsonValue effectPath = value.get("effectPath");
            JsonValue atlasTag = value.get("atlasTag");
            if (effectPath != null && effectPath.isString()
                    && atlasTag != null && atlasTag.isString()) {
                paths.add(ParticleEffectPath.normalize(effectPath.asString()));
            }
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collect(child, paths);
        }
    }
}
