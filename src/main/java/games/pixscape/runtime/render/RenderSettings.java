package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;

public final class RenderSettings {

    public enum RenderMode {
        SIMPLE,   // MeshBatch + ShaderMode.TEXTURE_2D
        MULTI,    // MultiTextureMeshBatch + ShaderMode.MULTI_TEXTURE
        ARRAY,    // TextureArrayMeshBatch + ShaderMode.TEXTURE_ARRAY
        AUTO
    }

    private final RenderMode mode;
    private final boolean requireES3;

    public RenderSettings(RenderMode mode, boolean requireES3) {
        if (mode == null) throw new IllegalArgumentException("mode cannot be null");
        this.mode = mode;
        this.requireES3 = requireES3;
    }

    public RenderMode mode() {
        return mode;
    }

    public boolean requireES3() {
        return requireES3;
    }

    public boolean useTextureArray() {
        return mode == RenderMode.ARRAY;
    }

    public static RenderSettings simple() {
        return new RenderSettings(RenderMode.SIMPLE, false);
    }

    public static RenderSettings multi() {
        return new RenderSettings(RenderMode.MULTI, false);
    }

    public static RenderSettings array(boolean requireES3) {
        return new RenderSettings(RenderMode.ARRAY, requireES3);
    }

    public static RenderSettings auto(boolean requireES3) {
        return new RenderSettings(RenderMode.AUTO, requireES3);
    }

    public static RenderSettings defaultEditor(GLCaps caps) {
        if (caps.supportsTextureArray() && caps.supportsES3()) {
            return new RenderSettings(RenderMode.ARRAY, false);
        }
        if (caps.maxTextureUnits() >= 8) {
            return new RenderSettings(RenderMode.MULTI, false);
        }
        return new RenderSettings(RenderMode.SIMPLE, false);
    }

    public static RenderSettings forLowEndMobile(GLCaps caps) {
        return new RenderSettings(RenderMode.SIMPLE, false);
    }

    public static RenderSettings forDesktop(GLCaps caps) {
        if (caps.supportsTextureArray() && caps.supportsES3()) {
            return new RenderSettings(RenderMode.ARRAY, false);
        }
        return new RenderSettings(RenderMode.MULTI, false);
    }
}