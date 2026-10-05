package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Allocation-free, pure actor-versus-projected-face relation solver. */
public final class SpatialFaceRelationSolver {
    public static final byte ACTOR_BEHIND_FACE = 1;
    public static final byte ACTOR_IN_FRONT_OF_FACE = 2;
    static final float RELATION_EPSILON = SpatialLineRelation.EPSILON;

    public int relationCount;
    public int[] actorRelationStart = new int[0];
    public int[] actorRelationCount = new int[0];
    public int[] relationFaceIndex = new int[0];
    public int[] relationAnchorIndex = new int[0];
    public int[] relationMembershipIndex = new int[0];
    public byte[] relationType = new byte[0];
    public int visualCandidateCount;
    public int[] actorCandidateStart = new int[0];
    public int[] actorCandidateCount = new int[0];
    public int[] candidateAnchorIndex = new int[0];
    private boolean captureCandidates;
    private int[] positiveBehindStamp = new int[0];
    private int[] negativeBehindStamp = new int[0];
    private int localStamp;

    public void setCaptureCandidates(boolean captureCandidates) {
        this.captureCandidates = captureCandidates;
    }

    public void solve(SpatialActorCollector actors, SpatialProjectedFaceCache faces) {
        relationCount = 0;
        visualCandidateCount = 0;
        if (actors == null || faces == null) return;
        ensureActorCapacity(actors.actorCount);
        for (int actor = 0; actor < actors.actorCount; actor++) {
            int relationStart = relationCount;
            actorRelationStart[actor] = relationStart;
            float x = actors.actorCircleX[actor];
            float y = actors.actorCircleY[actor];
            float radius = actors.circleRadius(actor);
            float circleMinX = x - radius;
            float circleMaxX = x + radius;
            float bottom = actors.actorAltitude[actor];
            float top = bottom + actors.actorHeight[actor];
            for (int structure = 0; structure < faces.structureCount; structure++) {
                if (!overlapsSemiOpen(circleMinX, circleMaxX,
                        faces.structureMinX[structure], faces.structureMaxX[structure])) continue;
                int start = faces.structureFaceStart[structure];
                int end = start + faces.structureFaceCount[structure];
                for (int face = start; face < end; face++) {
                    if (!(top > faces.faceAltitude[face]
                            && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
                    if (!overlapsSemiOpen(circleMinX, circleMaxX,
                            faces.screenMinX[face], faces.screenMaxX[face])) continue;
                    float faceY = faces.slope[face] * x + faces.intercept[face];
                    byte type = SpatialLineRelation.circleRelation(faceY, y,
                            inverseNormalLength(faces, face), radius);
                    add(face, -1, -1, type);
                }
            }
            actorRelationCount[actor] = relationCount - relationStart;
        }
    }

    /** Uses visual reach for anchors and finite compiled branches for physical reach. */
    public void solveVisual(SpatialActorCollector actors, SpatialProjectedFaceCache faces,
                            SpatialVisualAnchorSelector selector, DynamicEntityRenderState ecs,
                            TiledMapRenderState tiled, TiledMapLayerData map,
                            float mapOffsetX, float mapOffsetY) {
        relationCount = 0;
        visualCandidateCount = 0;
        if (actors == null || faces == null) return;
        ensureActorCapacity(actors.actorCount);
        for (int actor = 0; actor < actors.actorCount; actor++) {
            int start = relationCount;
            actorRelationStart[actor] = start;
            selector.select(actor, actors, ecs, tiled, map, faces, mapOffsetX, mapOffsetY);
            if (captureCandidates) {
                actorCandidateStart[actor] = visualCandidateCount;
                actorCandidateCount[actor] = selector.candidateCount;
                ensureCandidateCapacity(visualCandidateCount + selector.candidateCount);
                System.arraycopy(selector.candidateAnchors, 0, candidateAnchorIndex,
                        visualCandidateCount, selector.candidateCount);
            }
            visualCandidateCount += selector.candidateCount;
            float x = actors.actorCircleX[actor];
            float y = actors.actorCircleY[actor];
            float radius = actors.circleRadius(actor);
            float bottom = actors.actorAltitude[actor];
            float top = bottom + actors.actorHeight[actor];
            for (int candidate = 0; candidate < selector.candidateCount; candidate++) {
                int anchor = selector.candidateAnchors[candidate];
                for (int membership = faces.anchorMembershipHead[anchor];
                     membership >= 0; membership = faces.membershipNext[membership]) {
                    int face = faces.membershipFace[membership];
                    if (!(top > faces.faceAltitude[face]
                            && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
                    // A support line classifies sides; only its finite branch can supply a constraint.
                    // A merged straight branch still constrains every visually touched supporting tile.
                    // Closed intervals include endpoint tangencies on both adjacent branches deterministically.
                    if (x + radius < faces.screenMinX[face] - RELATION_EPSILON
                            || x - radius > faces.screenMaxX[face] + RELATION_EPSILON) continue;
                    float lineY = faces.slope[face] * x + faces.intercept[face];
                    byte type = SpatialLineRelation.circleRelation(lineY, y,
                            inverseNormalLength(faces, face), radius);
                    add(face, anchor, membership, type);
                }
            }
            retainLocallySupportedJunctionFronts(faces, start, x, y, radius);
            // Outside branch X reach, use the nearest real endpoint's cap wedge. In particular,
            // the closed side of a lateral corner is front, but its north sector remains behind.
            // Never add endpoint caps beside a relevant branch from another candidate.
            if (relationCount == start) {
                for (int candidate = 0; candidate < selector.candidateCount; candidate++) {
                    int anchor = selector.candidateAnchors[candidate];
                    int membership = endpointMembership(faces, anchor, x, y, bottom, top);
                    if (membership >= 0) {
                        int face = faces.membershipFace[membership];
                        float capY = faces.slope[face] * x + faces.intercept[face];
                        add(face, anchor, membership, SpatialLineRelation.relation(capY, y));
                    }
                }
            }
            actorRelationCount[actor] = relationCount - start;
        }
    }

    public int relationCount() { return relationCount; }

    private void retainLocallySupportedJunctionFronts(SpatialProjectedFaceCache faces, int start,
                                                     float x, float y, float radius) {
        if (positiveBehindStamp.length < faces.structureCount) {
            positiveBehindStamp = new int[faces.structureCount];
            negativeBehindStamp = new int[faces.structureCount];
        }
        if (localStamp == Integer.MAX_VALUE) {
            java.util.Arrays.fill(positiveBehindStamp, 0);
            java.util.Arrays.fill(negativeBehindStamp, 0);
            localStamp = 0;
        }
        int stamp = ++localStamp;
        for (int r = start; r < relationCount; r++) {
            if (relationType[r] != ACTOR_BEHIND_FACE) continue;
            int face = relationFaceIndex[r], structure = faces.faceStructureIndex[face];
            if (faces.slope[face] > 0f) positiveBehindStamp[structure] = stamp;
            if (faces.slope[face] < 0f) negativeBehindStamp[structure] = stamp;
        }
        int write = start;
        for (int r = start; r < relationCount; r++) {
            int face = relationFaceIndex[r], m = relationMembershipIndex[r];
            int structure = faces.faceStructureIndex[face];
            boolean crossingBranch = faces.slope[face] > 0f ? negativeBehindStamp[structure] == stamp
                    : faces.slope[face] < 0f && positiveBehindStamp[structure] == stamp;
            // A junction's crossing BEHIND branch already masks the selected visual quad.
            // Only local ground-column support can justify FRONT on the other branch's tile.
            // Without a crossing branch, a wide actor wholly in front of a straight wall
            // still belongs ahead of every visually touched supporting tile.
            boolean nonlocal = x + radius < faces.faceAnchorScreenMinX[m] - RELATION_EPSILON
                    || x - radius > faces.faceAnchorScreenMaxX[m] + RELATION_EPSILON;
            if (relationType[r] == ACTOR_IN_FRONT_OF_FACE && crossingBranch && nonlocal
                    && !faces.supportsLateralApproximation(m, x, y)) continue;
            relationFaceIndex[write] = face; relationAnchorIndex[write] = relationAnchorIndex[r];
            relationMembershipIndex[write] = m; relationType[write++] = relationType[r];
        }
        relationCount = write;
    }

    private static int endpointMembership(SpatialProjectedFaceCache faces, int anchor,
                                         float x, float y, float bottom, float top) {
        float nearestDistance = Float.POSITIVE_INFINITY;
        float endpointX = 0f, endpointY = 0f;
        for (int m = faces.anchorMembershipHead[anchor]; m >= 0; m = faces.membershipNext[m]) {
            int face = faces.membershipFace[m];
            if (!(top > faces.faceAltitude[face] && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
            for (int end = 0; end < 2; end++) {
                float ex = end == 0 ? faces.screenMinX[face] : faces.screenMaxX[face];
                float ey = faces.slope[face] * ex + faces.intercept[face];
                float dx = x - ex, dy = y - ey;
                float distance = dx * dx + dy * dy;
                if (distance < nearestDistance || distance == nearestDistance
                        && (ex < endpointX || ex == endpointX && ey < endpointY)) {
                    nearestDistance = distance; endpointX = ex; endpointY = ey;
                }
            }
        }
        float capY = Float.NEGATIVE_INFINITY;
        int capMembership = -1;
        for (int m = faces.anchorMembershipHead[anchor]; m >= 0; m = faces.membershipNext[m]) {
            int face = faces.membershipFace[m];
            if (!(top > faces.faceAltitude[face] && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
            for (int end = 0; end < 2; end++) {
                float ex = end == 0 ? faces.screenMinX[face] : faces.screenMaxX[face];
                float ey = faces.slope[face] * ex + faces.intercept[face];
                if (Math.abs(ex - endpointX) <= RELATION_EPSILON
                        && Math.abs(ey - endpointY) <= RELATION_EPSILON) {
                    float lineY = faces.slope[face] * x + faces.intercept[face];
                    if (lineY > capY || lineY == capY && (capMembership < 0
                            || face < faces.membershipFace[capMembership])) {
                        capY = lineY;
                        capMembership = m;
                    }
                }
            }
        }
        return capMembership;
    }

    private static boolean overlapsSemiOpen(float circleMinX,
                                            float circleMaxX,
                                            float intervalMinX,
                                            float intervalMaxX) {
        return circleMaxX >= intervalMinX && circleMinX < intervalMaxX;
    }

    private static float inverseNormalLength(SpatialProjectedFaceCache faces, int face) {
        if (face < faces.inverseNormalLength.length && faces.inverseNormalLength[face] > 0f) {
            return faces.inverseNormalLength[face];
        }
        float slope = faces.slope[face];
        return 1f / (float) Math.sqrt(slope * slope + 1f);
    }

    private void add(int face, int anchor, int membership, byte type) {
        ensureRelationCapacity(relationCount + 1);
        relationFaceIndex[relationCount] = face;
        relationAnchorIndex[relationCount] = anchor;
        relationMembershipIndex[relationCount] = membership;
        relationType[relationCount] = type;
        relationCount++;
    }

    private void ensureActorCapacity(int required) {
        if (required <= actorRelationStart.length) return;
        int next = capacity(actorRelationStart.length, required);
        actorRelationStart = grow(actorRelationStart, next);
        actorRelationCount = grow(actorRelationCount, next);
        actorCandidateStart = grow(actorCandidateStart, next);
        actorCandidateCount = grow(actorCandidateCount, next);
    }

    private void ensureCandidateCapacity(int required) {
        if (required <= candidateAnchorIndex.length) return;
        candidateAnchorIndex = grow(candidateAnchorIndex,
                capacity(candidateAnchorIndex.length, required));
    }

    private void ensureRelationCapacity(int required) {
        if (required <= relationFaceIndex.length) return;
        int next = capacity(relationFaceIndex.length, required);
        relationFaceIndex = grow(relationFaceIndex, next);
        relationAnchorIndex = grow(relationAnchorIndex, next);
        relationMembershipIndex = grow(relationMembershipIndex, next);
        relationType = grow(relationType, next);
    }

    private static int capacity(int current,int required){int next=Math.max(8,current);while(next<required)next<<=1;return next;}
    private static int[] grow(int[] source,int next){int[] out=new int[next];System.arraycopy(source,0,out,0,source.length);return out;}
    private static byte[] grow(byte[] source,int next){byte[] out=new byte[next];System.arraycopy(source,0,out,0,source.length);return out;}
}
