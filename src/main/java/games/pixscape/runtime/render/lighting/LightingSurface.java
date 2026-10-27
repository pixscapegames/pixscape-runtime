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
    public ActorShadowRectangleCaster actorShadowRectangle;
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
    /** Optional cold XYZ macro-orientation per receiver vertex; never used for shadow depth/bias. */
    public float[] curveNormals;
    /** Immutable Tiled or rounded-support overlay: XYZ, geometric normal, physical UV, lighting normal.
     * Rebuilt with the static support, never shared with moving actor buffers. */
    public float[] forwardAttributes;
    /** Optional explicit caster view of this tile's support, sharing its existing alpha binding. */
    public LightingSurface casterSurface;
    /** POC provenance for one linked tile/block pair. Zero means that no diagnostic self-caster exists. */
    public int pocSourceBlockId;
    /** Cold compact identity shared with the matching silhouette caster; zero is explicitly invalid. */
    public int selfShadowExclusionId;
    /** Shadow-only policy. -1 preserves legacy/archived supports; zero never excludes. */
    public int shadowGroupId=-1;
    public int[] receiverShadowGroups;
    /** Cold per-physical-triangle lighting policy, independent of exclusion provenance. */
    public boolean[] receiverSpriteShading;
    public float receiverLightingMode(int offset) {
        if (offset >= 0 && receiverSpriteShading != null && receiverSpriteShading[offset / 24]) return -2f;
        return twoSided ? 1f : actor ? -1f : 0f;
    }
    public int casterShadowGroup(){return shadowGroupId<0?selfShadowExclusionId:shadowGroupId;}
    public int receiverShadowGroup(int offset){
        if(offset<0)return 0;
        return receiverShadowGroups==null?(shadowGroupId<0?receiverExclusionId(offset):shadowGroupId):receiverShadowGroups[offset/24];
    }
    /** A curved occurrence is a support in its own right, never either adjoining wall. */
    public boolean roundedIdentity;
    public long roundedCellKey;
    /** Cold provenance and resolved identity per physical triangle; zero never excludes. */
    public int[] receiverBlockIds, receiverExclusionIds;
    /** Independently identified volume contributions sharing this tile's alpha texture. */
    public com.badlogic.gdx.utils.Array<LightingSurface> casterContributions;
    public int receiverExclusionId(int sourceFloatOffset){
        if(sourceFloatOffset<0)return 0;
        return receiverExclusionIds==null?selfShadowExclusionId:receiverExclusionIds[sourceFloatOffset/24];
    }
    public LightingSurface(float[] vertices) { this.vertices = vertices; }
}
