package games.pixscape.runtime.spatial;

/** Allocation-free, pure actor-versus-projected-face relation solver. */
public final class SpatialFaceRelationSolver {
    public static final byte ACTOR_BEHIND_FACE = 1;
    public static final byte ACTOR_IN_FRONT_OF_FACE = 2;
    static final float RELATION_EPSILON = SpatialLineRelation.EPSILON;

    public int relationCount;
    public int[] actorRelationStart = new int[0];
    public int[] actorRelationCount = new int[0];
    public int[] relationFaceIndex = new int[0];
    public byte[] relationType = new byte[0];

    public void solve(SpatialActorCollector actors, SpatialProjectedFaceCache faces) {
        relationCount = 0;
        if (actors == null || faces == null) return;
        ensureActorCapacity(actors.actorCount);
        for (int actor = 0; actor < actors.actorCount; actor++) {
            int relationStart = relationCount;
            actorRelationStart[actor] = relationStart;
            float x = actors.actorCircleX[actor];
            float y = actors.actorCircleY[actor];
            float bottom = actors.actorAltitude[actor];
            float top = bottom + actors.actorHeight[actor];
            for (int structure = 0; structure < faces.structureCount; structure++) {
                if (x < faces.structureMinX[structure] || x >= faces.structureMaxX[structure]) continue;
                int start = faces.structureFaceStart[structure];
                int end = start + faces.structureFaceCount[structure];
                for (int face = start; face < end; face++) {
                    if (!(top > faces.faceAltitude[face]
                            && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
                    if (x < faces.screenMinX[face] || x >= faces.screenMaxX[face]) continue;
                    float faceY = faces.slope[face] * x + faces.intercept[face];
                    byte type = SpatialLineRelation.relation(faceY, y);
                    add(face, type);
                }
            }
            actorRelationCount[actor] = relationCount - relationStart;
        }
    }

    public int relationCount() { return relationCount; }

    private void add(int face, byte type) {
        ensureRelationCapacity(relationCount + 1);
        relationFaceIndex[relationCount] = face;
        relationType[relationCount] = type;
        relationCount++;
    }

    private void ensureActorCapacity(int required) {
        if (required <= actorRelationStart.length) return;
        int next = capacity(actorRelationStart.length, required);
        actorRelationStart = grow(actorRelationStart, next);
        actorRelationCount = grow(actorRelationCount, next);
    }

    private void ensureRelationCapacity(int required) {
        if (required <= relationFaceIndex.length) return;
        int next = capacity(relationFaceIndex.length, required);
        relationFaceIndex = grow(relationFaceIndex, next);
        relationType = grow(relationType, next);
    }

    private static int capacity(int current,int required){int next=Math.max(8,current);while(next<required)next<<=1;return next;}
    private static int[] grow(int[] source,int next){int[] out=new int[next];System.arraycopy(source,0,out,0,source.length);return out;}
    private static byte[] grow(byte[] source,int next){byte[] out=new byte[next];System.arraycopy(source,0,out,0,source.length);return out;}
}
