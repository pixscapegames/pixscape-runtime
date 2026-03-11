package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;

/**
 * I/O du projet runtime (pixscape-project/project.json).
 * Style identique à ProjectIO :
 * - load: parse -> hydrate runtimeRootDir -> applyDefaultsAndValidate
 * - save: ensure dir -> hydrate runtimeRootDir -> applyDefaultsAndValidate -> prettyPrint
 */
public final class RuntimeProjectIO {

    public static final String PROJECT_JSON = RuntimeFs.FILE_PROJECT_JSON;

    private static final Json json = new Json();
    static {
        // écrire aussi les valeurs == defaults (utile pour stabilité / debug)
        json.setUsePrototypes(false);

        // json standard
        json.setOutputType(JsonWriter.OutputType.json);

        // tolérant pendant migrations
        json.setIgnoreUnknownFields(true);
    }

    private RuntimeProjectIO() {}

    public static RuntimeConfig loadProject(FileHandle projectDir) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");

        FileHandle file = projectDir.child(PROJECT_JSON);
        if (!file.exists()) {
            throw new GdxRuntimeException("Missing " + PROJECT_JSON + " in: " + projectDir.path());
        }

        final RuntimeConfig cfg;
        try {
            cfg = json.fromJson(RuntimeConfig.class, file);
        } catch (Exception e) {
            throw new GdxRuntimeException("Failed to parse " + PROJECT_JSON + ": " + file.path(), e);
        }

        if (cfg == null) {
            throw new GdxRuntimeException("Invalid " + PROJECT_JSON + " (null): " + file.path());
        }

        // Source de vérité: le dossier runtime (pixscape-project)
        // => on le fixe si absent / vide
        if (cfg.runtimeRootDir == null || cfg.runtimeRootDir.isBlank()) {
            cfg.runtimeRootDir = projectDir.path();
        }

        // IMPORTANT: normalise + valide ici (pas plus tard)
        cfg.applyDefaultsAndValidate(file.path());

        return cfg;
    }
}
