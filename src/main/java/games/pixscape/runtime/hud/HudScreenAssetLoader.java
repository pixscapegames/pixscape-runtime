package games.pixscape.runtime.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

/** {@code INTERNAL} strict project loader for one logical HUD screen asset. */
public final class HudScreenAssetLoader {
    public HudScreenAsset load(FileHandle runtimeProjectDir, String screenId) {
        if (runtimeProjectDir == null) {
            throw new IllegalArgumentException("Runtime project directory is required.");
        }
        String logicalId = HudScreenAssetId.normalize(screenId);
        FileHandle file = runtimeProjectDir.child(HudScreenAssetId.DIRECTORY)
                .child(HudScreenAssetId.assetName(logicalId) + HudScreenAsset.EXTENSION);
        if (!file.exists()) {
            throw new GdxRuntimeException("HUD screen asset does not exist: " + logicalId
                    + " (" + file.path() + ").");
        }

        String serialized;
        try {
            serialized = file.readString("UTF-8");
        } catch (RuntimeException failure) {
            throw new GdxRuntimeException("Unable to read HUD screen asset " + logicalId
                    + " from " + file.path() + ".", failure);
        }

        try {
            JsonValue root = new JsonReader().parse(serialized);
            JsonValue schema = root != null && root.isObject() ? root.get("schemaVersion") : null;
            if (schema == null || !schema.isLong()) {
                throw new IllegalArgumentException("numeric schemaVersion "
                        + HudScreenAsset.CURRENT_SCHEMA_VERSION + " is required");
            }
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            json.setIgnoreUnknownFields(false);
            json.setUsePrototypes(false);
            HudScreenAsset asset = json.fromJson(HudScreenAsset.class, serialized);
            asset.validate();
            return asset;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid HUD screen asset " + logicalId
                    + " at " + file.path() + ": " + failure.getMessage(), failure);
        }
    }
}
