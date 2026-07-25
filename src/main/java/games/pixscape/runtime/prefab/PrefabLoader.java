package games.pixscape.runtime.prefab;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

public final class PrefabLoader {
    public static final String PREFAB_TYPE = "pixscape-prefab";
    public static final int PREFAB_VERSION = 2;

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

        PrefabAsset asset = json.fromJson(PrefabAsset.class, file.readString("UTF-8"));
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
        json.setIgnoreUnknownFields(true);
        json.setUsePrototypes(false);
        return json;
    }

    private static String pathOf(FileHandle file) {
        return file != null ? file.path() : "<null>";
    }
}
