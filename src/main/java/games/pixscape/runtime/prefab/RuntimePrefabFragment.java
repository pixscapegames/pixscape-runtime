package games.pixscape.runtime.prefab;

import com.artemis.io.SaveFileFormat;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.JsonValue;

public final class RuntimePrefabFragment extends SaveFileFormat {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;

    public RuntimePrefabFragment() {
        super();
    }

    public RuntimePrefabFragment(IntBag entities) {
        super(entities);
    }

    public static void requireCurrentSchema(RuntimePrefabFragment fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException(
                    "Runtime prefab fragment requires schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ".");
        }
        validateSchemaVersion(fragment.schemaVersion);
    }

    public static void requireCurrentSchema(JsonValue root) {
        JsonValue value = root != null && root.isObject()
                ? root.get("schemaVersion")
                : null;
        if (value == null || !value.isLong()) {
            throw new IllegalArgumentException(
                    "Runtime prefab fragment requires numeric schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ".");
        }
        validateSchemaVersion(value.asInt());
    }

    private static void validateSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Runtime prefab fragment requires schemaVersion "
                            + CURRENT_SCHEMA_VERSION + ", found "
                            + schemaVersion + ".");
        }
    }
}
