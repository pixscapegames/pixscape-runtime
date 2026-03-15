package games.pixscape.runtime.loading;

public class SceneMetaRuntime {

    public String name;
    public String file;

    // Physics
    public boolean physicsEnabled = false;
    public float pixelsPerMeter = 100f;
    public float gravityX = 0f;
    public float gravityY = -9.81f;
    public boolean doSleep = true;
    public float physicsParallaxX = Float.NaN;
    public float physicsParallaxY = Float.NaN;

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


    public String getName() { return name; }
    public String getFile() { return file; }

    public SceneMetaRuntime(SceneMetaRuntime other) { copyFrom(other); }

    public SceneMetaRuntime() {}

    public SceneMetaRuntime(String name, String file) {
        this.name = name;
        this.file = file;
    }


    /** Copie uniquement les champs “runtime settings” (pas l’identité). */
    public void copyFrom(SceneMetaRuntime other) {
        this.name               = other.name;
        this.file               = other.file;
        this.physicsEnabled     = other.physicsEnabled;
        this.pixelsPerMeter     = other.pixelsPerMeter;
        this.gravityX           = other.gravityX;
        this.gravityY           = other.gravityY;
        this.doSleep            = other.doSleep;
        this.physicsParallaxX   = other.physicsParallaxX;
        this.physicsParallaxY   = other.physicsParallaxY;
        this.ambientMulR        = other.ambientMulR;
        this.ambientMulG        = other.ambientMulG;
        this.ambientMulB        = other.ambientMulB;
        this.tiledEnabled       = other.tiledEnabled;
        this.tiledProjection    = other.tiledProjection;
        this.tileWidth          = other.tileWidth;
        this.tileHeight         = other.tileHeight;
        this.chunkSize          = other.chunkSize;
        this.mainCameraOffscreen= other.mainCameraOffscreen;
    }
}
