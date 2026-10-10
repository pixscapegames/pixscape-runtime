package games.pixscape.runtime.render.lighting;

/** Cold triangle data: XYZ, normalized image UV, normal. Shared by receiver and caster. */
public final class LightingSurface {
    public final float[] vertices;
    public boolean receiveLight = true, shadowCaster, twoSided, approximate;
    public float alphaThreshold = .5f;
    public int ownerEntity=-1;
    public LightingSurface(float[] vertices) { this.vertices = vertices; }
}
