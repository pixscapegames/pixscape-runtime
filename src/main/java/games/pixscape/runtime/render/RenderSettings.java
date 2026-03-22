package games.pixscape.runtime.render;

import games.pixscape.runtime.render.batch.GLCaps;

public final class RenderSettings {

    public enum RenderMode {
        SIMPLE,   // batch simple (MeshBatch + shader "default")
        MULTI,    // MultiTextureMeshBatch + "mt_default"
        ARRAY,    // TextureArrayMeshBatch + "ta_default"
        AUTO      // choix en fonction de GLCaps
    }

    private final RenderMode mode;
    private final boolean requireES3;

    // --------- Constructeurs ---------

    public RenderSettings(RenderMode mode, boolean requireES3) {
        if (mode == null) throw new IllegalArgumentException("mode cannot be null");
        this.mode = mode;
        this.requireES3 = requireES3;
    }


    // --------- Accesseurs ---------

    public RenderMode mode() {
        return mode;
    }

    public boolean requireES3() {
        return requireES3;
    }

    public boolean useTextureArray() {
        return mode == RenderMode.ARRAY;
    }

    // --------- Helpers statiques ---------

    /** Mode simple : batch 1 texture, aucun besoin ES3. */
    public static RenderSettings simple() {
        return new RenderSettings(RenderMode.SIMPLE, false);
    }

    /** Multi-texture mode: MultiTextureMeshBatch if possible. */
    public static RenderSettings multi() {
        return new RenderSettings(RenderMode.MULTI, false);
    }

    /** Mode TextureArray : requiert ES3 + support texture array. */
    public static RenderSettings array(boolean requireES3) {
        return new RenderSettings(RenderMode.ARRAY, requireES3);
    }

    /** Automatic mode: BatchFactory will choose based on GLCaps. */
    public static RenderSettings auto(boolean requireES3) {
        return new RenderSettings(RenderMode.AUTO, requireES3);
    }

    /**
     * "Smart" helper based on capabilities:
     *  - TextureArray if available
     *  - otherwise MultiTexture if >= 8 units
     *  - sinon SIMPLE
     */
    public static RenderSettings defaultEditor(GLCaps caps) {
        if (caps.supportsTextureArray() && caps.supportsES3()) {
            return new RenderSettings(RenderMode.ARRAY, false);
        }
        if (caps.maxTextureUnits() >= 8) {
            return new RenderSettings(RenderMode.MULTI, false);
        }
        return new RenderSettings(RenderMode.SIMPLE, false);
    }


    /** Profil mobile bas de gamme : forcer SIMPLE. */
    public static RenderSettings forLowEndMobile(GLCaps caps) {
        return new RenderSettings(RenderMode.SIMPLE, false);
    }

    /** Desktop "comfort" profile: ARRAY if possible, otherwise MULTI. */
    public static RenderSettings forDesktop(GLCaps caps) {
        if (caps.supportsTextureArray() && caps.supportsES3()) {
            return new RenderSettings(RenderMode.ARRAY, false);
        }
        return new RenderSettings(RenderMode.MULTI, false);
    }
}
