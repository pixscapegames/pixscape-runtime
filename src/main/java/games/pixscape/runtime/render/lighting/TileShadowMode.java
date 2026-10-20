package games.pixscape.runtime.render.lighting;

/** Scene preparation policy; actor geometry and the shadow domains are independent of this choice. */
public enum TileShadowMode {
    V2,
    V3_PLANE,
    V3_FIXED,
    V3_PROGRESSIVE
}
