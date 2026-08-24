package games.pixscape.runtime.prefab;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

public final class PrefabLoader {
    public static final String PREFAB_TYPE = "pixscape-prefab";
    public static final int PREFAB_VERSION = PrefabAsset.PREFAB_VERSION;

    private final Json json;

    public PrefabLoader() {
        this.json = createJson();
    }

    public PrefabAsset load(FileHandle file) {
        if (file == null) {
            throw new IllegalArgumentException("Prefab file is required.");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("Prefab file does not exist: " + file.path());
        }

        String serialized = file.readString("UTF-8");
        JsonValue root = new JsonReader().parse(serialized);
        int sourceVersion = requireSupportedVersion(root, file);
        PrefabAsset asset = json.fromJson(PrefabAsset.class, serialized);
        if (sourceVersion == 2) {
            asset.version = PREFAB_VERSION;
        }
        validate(asset, file);
        return asset;
    }

    public void save(FileHandle file, PrefabAsset asset) {
        if (file == null) {
            throw new IllegalArgumentException("Prefab file is required.");
        }
        validate(asset, file);

        if (file.parent() != null) {
            file.parent().mkdirs();
        }

        file.writeString(json.prettyPrint(asset), false, "UTF-8");
    }

    public void validate(PrefabAsset asset, FileHandle file) {
        if (asset == null) {
            throw new IllegalArgumentException("Invalid prefab JSON: " + pathOf(file));
        }

        if (!PREFAB_TYPE.equals(asset.type)) {
            throw new IllegalArgumentException("Invalid prefab type: " + asset.type);
        }

        if (asset.version != PREFAB_VERSION) {
            throw new IllegalArgumentException("Unsupported prefab version: " + asset.version);
        }

        if (asset.entities == null) {
            throw new IllegalArgumentException("Prefab entities list is null: " + pathOf(file));
        }
    }

    private static Json createJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        return json;
    }

    private static int requireSupportedVersion(JsonValue root, FileHandle file) {
        JsonValue version = root != null && root.isObject()
                ? root.get("version")
                : null;
        if (version == null || !version.isLong()) {
            throw new IllegalArgumentException(
                    "Prefab requires numeric version " + PREFAB_VERSION
                            + ": " + pathOf(file));
        }
        int value = version.asInt();
        if (value != 2 && value != PREFAB_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported prefab version: " + value);
        }
        return value;
    }

    private static String pathOf(FileHandle file) {
        return file != null ? file.path() : "<null>";
    }
}
