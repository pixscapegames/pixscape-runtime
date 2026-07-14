package games.pixscape.runtime.spatial;

/** Immutable derived exterior boundary of one authored Spatial V3 structure. */
public final class CompiledSpatialStructure {
    private final int structureId;
    private final float lowerZ;
    private final float upperZ;
    private final float[] startX;
    private final float[] startY;
    private final float[] endX;
    private final float[] endY;
    private final byte[] normalX;
    private final byte[] normalY;
    private final boolean[] actorOccluder;
    private final boolean[] physicsCollision;
    private final boolean[] lightOccluder;
    private final boolean[] shadowCaster;
    private final boolean[] particleOccluder;

    CompiledSpatialStructure(int structureId,
                             float lowerZ,
                             float upperZ,
                             float[] startX,
                             float[] startY,
                             float[] endX,
                             float[] endY,
                             byte[] normalX,
                             byte[] normalY,
                             boolean[] actorOccluder,
                             boolean[] physicsCollision,
                             boolean[] lightOccluder,
                             boolean[] shadowCaster,
                             boolean[] particleOccluder) {
        this.structureId = structureId;
        this.lowerZ = lowerZ;
        this.upperZ = upperZ;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.normalX = normalX;
        this.normalY = normalY;
        this.actorOccluder = actorOccluder;
        this.physicsCollision = physicsCollision;
        this.lightOccluder = lightOccluder;
        this.shadowCaster = shadowCaster;
        this.particleOccluder = particleOccluder;
    }

    public int structureId() { return structureId; }
    public float lowerZ() { return lowerZ; }
    public float upperZ() { return upperZ; }
    public int segmentCount() { return startX.length; }
    public float startX(int segment) { return startX[segment]; }
    public float startY(int segment) { return startY[segment]; }
    public float endX(int segment) { return endX[segment]; }
    public float endY(int segment) { return endY[segment]; }
    public int normalX(int segment) { return normalX[segment]; }
    public int normalY(int segment) { return normalY[segment]; }
    public boolean actorOccluder(int segment) { return actorOccluder[segment]; }
    public boolean physicsCollision(int segment) { return physicsCollision[segment]; }
    public boolean lightOccluder(int segment) { return lightOccluder[segment]; }
    public boolean shadowCaster(int segment) { return shadowCaster[segment]; }
    public boolean particleOccluder(int segment) { return particleOccluder[segment]; }
}
