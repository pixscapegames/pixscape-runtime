package games.pixscape.runtime.hud.document;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

/** Deterministic, GL-free JSON reader/writer for the version-1 HUD construction document. */
public final class HudDocumentCodec {
    private static final String MEMORY_SOURCE = "<memory>";

    public HudDocumentV1 read(String serialized) {
        return read(serialized, MEMORY_SOURCE);
    }

    public HudDocumentV1 read(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("HUD document file is required.");
        String serialized;
        try {
            serialized = file.readString("UTF-8");
        } catch (RuntimeException failure) {
            throw failure(HudDocumentLoadCode.READ_FAILURE, file.path(),
                    "Unable to read HUD document " + file.path() + ".", failure);
        }
        return read(serialized, file.path());
    }

    public String write(HudDocumentV1 document) {
        if (document == null) throw new IllegalArgumentException("HUD document is required.");
        try {
            return createJson().prettyPrint(document);
        } catch (RuntimeException failure) {
            throw failure(HudDocumentLoadCode.WRITE_FAILURE, MEMORY_SOURCE,
                    "Unable to serialize HUD document.", failure);
        }
    }

    public void write(FileHandle file, HudDocumentV1 document) {
        if (file == null) throw new IllegalArgumentException("HUD document file is required.");
        String serialized = write(document);
        try {
            FileHandle parent = file.parent();
            if (parent != null) parent.mkdirs();
            file.writeString(serialized, false, "UTF-8");
        } catch (RuntimeException failure) {
            throw failure(HudDocumentLoadCode.WRITE_FAILURE, file.path(),
                    "Unable to write HUD document " + file.path() + ".", failure);
        }
    }

    private HudDocumentV1 read(String serialized, String source) {
        if (serialized == null) {
            throw failure(HudDocumentLoadCode.MALFORMED_JSON, source,
                    "HUD document JSON is required.", null);
        }

        JsonValue root;
        try {
            root = new JsonReader().parse(serialized);
        } catch (RuntimeException failure) {
            throw failure(HudDocumentLoadCode.MALFORMED_JSON, source,
                    "Malformed HUD document JSON in " + source + ".", failure);
        }
        if (root == null || !root.isObject()) {
            throw failure(HudDocumentLoadCode.DESERIALIZATION_FAILURE, source,
                    "HUD document root must be a JSON object in " + source + ".", null);
        }

        JsonValue schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null) {
            throw failure(HudDocumentLoadCode.MISSING_SCHEMA_VERSION, source,
                    "HUD document requires numeric schemaVersion "
                            + HudDocumentV1.CURRENT_SCHEMA_VERSION + " in " + source + ".", null);
        }
        if (!schemaVersion.isLong()) {
            throw failure(HudDocumentLoadCode.DESERIALIZATION_FAILURE, source,
                    "HUD document schemaVersion must be an integer in " + source + ".", null);
        }
        long version = schemaVersion.asLong();
        if (version != HudDocumentV1.CURRENT_SCHEMA_VERSION) {
            throw failure(HudDocumentLoadCode.UNSUPPORTED_SCHEMA_VERSION, source,
                    "Unsupported HUD document schemaVersion " + version + " in " + source
                            + "; expected " + HudDocumentV1.CURRENT_SCHEMA_VERSION + ".", null);
        }

        try {
            HudDocumentV1 document = createJson().fromJson(HudDocumentV1.class, serialized);
            if (document == null) {
                throw failure(HudDocumentLoadCode.DESERIALIZATION_FAILURE, source,
                        "HUD document did not deserialize to an object in " + source + ".", null);
            }
            return document;
        } catch (HudDocumentLoadException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(HudDocumentLoadCode.DESERIALIZATION_FAILURE, source,
                    "HUD document contains unsupported or malformed values in " + source + ".",
                    failure);
        }
    }

    private static Json createJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        json.setTypeName(null);
        return json;
    }

    private static HudDocumentLoadException failure(HudDocumentLoadCode code, String source,
                                                     String message, Throwable cause) {
        return new HudDocumentLoadException(code, source, message, cause);
    }
}
