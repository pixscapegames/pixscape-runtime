package games.pixscape.runtime.loading;

import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.physics.PhysicsShapeIdState;

public class SceneMetaRuntime implements PhysicsShapeIdState {

    public String name;
    public String file;
    public int nextEntityStableId = 1;

    // Physics
    public boolean physicsEnabled = false;
    public float pixelsPerMeter = 100f;
    public float gravityX = 0f;
    public float gravityY = -9.81f;
    public boolean doSleep = true;
    public float physicsParallaxX = Float.NaN;
    public float physicsParallaxY = Float.NaN;
    public int nextPhysicsShapeId = 1;

    // Ambient light
    public float ambientMulR = 1f;
    public float ambientMulG = 1f;
    public float ambientMulB = 1f;

    // Tiled
    public enum TiledProjection {
        ORTHO,
        ISO
    }

    public boolean tiledEnabled = false;
    public TiledProjection tiledProjection = TiledProjection.ORTHO;
    public float tileWidth = 32f;
    public float tileHeight = 32f;
    public int chunkSize = 16;

    // PostFX
    public boolean mainCameraOffscreen = false;

    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public SceneMetaRuntime(SceneMetaRuntime other) {
        copyFrom(other);
    }

    public SceneMetaRuntime() {
    }

    public SceneMetaRuntime(String name, String file) {
        this.name = name;
        this.file = file;
    }

    public static SceneMetaRuntime fromJson(JsonValue json, String fallbackName) {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        if (json == null || !json.isObject()) {
            meta.name = fallbackName;
            return meta;
        }

        meta.name = json.getString("name", fallbackName);
        meta.file = json.getString("file", null);
        meta.physicsEnabled = json.getBoolean("physicsEnabled", meta.physicsEnabled);
        meta.pixelsPerMeter = json.getFloat("pixelsPerMeter", meta.pixelsPerMeter);
        meta.gravityX = json.getFloat("gravityX", meta.gravityX);
        meta.gravityY = json.getFloat("gravityY", meta.gravityY);
        meta.doSleep = json.getBoolean("doSleep", meta.doSleep);
        meta.physicsParallaxX = json.getFloat("physicsParallaxX", meta.physicsParallaxX);
        meta.physicsParallaxY = json.getFloat("physicsParallaxY", meta.physicsParallaxY);
        meta.nextEntityStableId = requiredPositiveInt(json, "nextEntityStableId", fallbackName);
        meta.nextPhysicsShapeId = requiredPositiveInt(
                json, "nextPhysicsShapeId", fallbackName);
        meta.ambientMulR = json.getFloat("ambientMulR", meta.ambientMulR);
        meta.ambientMulG = json.getFloat("ambientMulG", meta.ambientMulG);
        meta.ambientMulB = json.getFloat("ambientMulB", meta.ambientMulB);
        meta.tiledEnabled = json.getBoolean("tiledEnabled", meta.tiledEnabled);

        String projection = json.getString("tiledProjection", meta.tiledProjection.name());
        try {
            meta.tiledProjection = TiledProjection.valueOf(projection.trim().toUpperCase());
        } catch (Exception ignored) {
            meta.tiledProjection = TiledProjection.ORTHO;
        }

        meta.tileWidth = json.getFloat("tileWidth", meta.tileWidth);
        meta.tileHeight = json.getFloat("tileHeight", meta.tileHeight);
        meta.chunkSize = json.getInt("chunkSize", meta.chunkSize);
        meta.mainCameraOffscreen = json.getBoolean("mainCameraOffscreen", meta.mainCameraOffscreen);
        return meta;
    }

    private static int requiredPositiveInt(JsonValue json, String field, String sceneName) {
        JsonValue value = json.get(field);
        if (value == null || !value.isNumber() || value.asInt() <= 0) {
            throw new IllegalArgumentException(
                    "Scene '" + sceneName + "' requires a positive " + field + ".");
        }
        return value.asInt();
    }

    /**
     * Copies only the "runtime settings" fields (not identity).
     */
    public void copyFrom(SceneMetaRuntime other) {
        if (other == null) return;
        this.name = other.name;
        this.file = other.file;
        this.nextEntityStableId = other.nextEntityStableId;
        this.physicsEnabled = other.physicsEnabled;
        this.pixelsPerMeter = other.pixelsPerMeter;
        this.gravityX = other.gravityX;
        this.gravityY = other.gravityY;
        this.doSleep = other.doSleep;
        this.physicsParallaxX = other.physicsParallaxX;
        this.physicsParallaxY = other.physicsParallaxY;
        this.nextPhysicsShapeId = other.nextPhysicsShapeId;
        this.ambientMulR = other.ambientMulR;
        this.ambientMulG = other.ambientMulG;
        this.ambientMulB = other.ambientMulB;
        this.tiledEnabled = other.tiledEnabled;
        this.tiledProjection = other.tiledProjection;
        this.tileWidth = other.tileWidth;
        this.tileHeight = other.tileHeight;
        this.chunkSize = other.chunkSize;
        this.mainCameraOffscreen = other.mainCameraOffscreen;
    }

    @Override
    public int getNextPhysicsShapeId() {
        return nextPhysicsShapeId;
    }

    @Override
    public void setNextPhysicsShapeId(int nextPhysicsShapeId) {
        this.nextPhysicsShapeId = nextPhysicsShapeId;
    }
}
