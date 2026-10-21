package games.pixscape.runtime.render.lighting;

/** Cold triangle data: XYZ, normalized image UV, normal. Shared by receiver and caster. */
public final class LightingSurface {
    public final float[] vertices;
    public boolean receiveLight = true, shadowCaster, twoSided, approximate;
    /** Derived simple-sheet topology: six vertices in SpriteSurfaceSupport.corner order, never a local mesh/tile. */
    public boolean imageQuad;
    /** Derived provenance: ordinary ECS image supports are actors; Tiled/compiled map supports are decor. */
    public boolean actor;
    /** Prepared only for ordinary image actors; independent of the receiver vertices/normals. */
    public ActorShadowGeometry actorShadow;
    public float[] shadowVertices;
    public int shadowVertexCount;
    public float alphaThreshold = .5f;
    public int ownerEntity=-1;
    /** Lighting normal only; geometric vertex normals remain authoritative for shadows. */
    public games.pixscape.runtime.service.NormalMapBinding normalMap;
    /** Coverage completion only: valid geometric vertices are copied unchanged; casters use vertices. */
    public float[] receiverVertices;
    /** Immutable local completion for authored moving surfaces; prepared when their description changes. */
    public float[] localReceiverVertices;
    /** Cold native/physical overlay; weights reuse the current transformed receiver publication. */
    public ForwardTopology forwardTopology;
    public LightingSurface(float[] vertices) { this.vertices = vertices; }
}
