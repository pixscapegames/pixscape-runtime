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
    public static final byte REJECTED_DISTANT_JUNCTION_FRONT = 1;
    public int rejectedCount;
    public int[] rejectedActor = new int[0], rejectedFace = new int[0], rejectedAnchor = new int[0];
    public int[] rejectedMembership = new int[0], rejectedWitnessFace = new int[0];
    public byte[] rejectedReason = new byte[0];
    private int[] behindFaceStamp = new int[0];
    private int qualificationStamp;
    boolean visualRelations;
    float[] actorProjectedX = new float[0], actorProjectedY = new float[0];

    /** Captures candidates and rejected relations for diagnostics; qualification always runs. */
    public void setCaptureCandidates(boolean captureCandidates) {
        this.captureCandidates = captureCandidates;
    }

    public void solve(SpatialActorCollector actors, SpatialProjectedFaceCache faces) {
        visualRelations = false;
        relationCount = 0;
        rejectedCount = 0;
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
                    float faceY = faceLineYAtAltitude(faces, face, x, bottom);
                    byte type = SpatialLineRelation.circleRelation(faceY, y,
                            inverseNormalLength(faces, face), radius);
                    add(face, -1, -1, type);
                }
            }
            actorRelationCount[actor] = relationCount - relationStart;
        }
    }

    /** Selects anchors by the actor influence envelope and limits constraints to finite branch X intervals. */
    public void solveVisual(SpatialActorCollector actors, SpatialProjectedFaceCache faces,
                            SpatialVisualAnchorSelector selector, DynamicEntityRenderState ecs,
                            TiledMapRenderState tiled, TiledMapLayerData map,
                            float mapOffsetX, float mapOffsetY) {
        visualRelations = true;
        relationCount = 0;
        rejectedCount = 0;
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
            float x = actors.actorCircleX[actor] + ecs.offsetX[actors.actorSlot[actor]] - mapOffsetX;
            float y = actors.actorCircleY[actor] + ecs.offsetY[actors.actorSlot[actor]] - mapOffsetY;
            actorProjectedX[actor] = x;
            actorProjectedY[actor] = y;
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
                    float lineY = faceLineYAtAltitude(faces, face, x, bottom);
                    byte type = SpatialLineRelation.circleRelation(lineY, y,
                            inverseNormalLength(faces, face), radius);
                    add(face, anchor, membership, type);
                }
            }
            // Outside branch X reach, use the nearest real endpoint's cap wedge. In particular,
            // the closed side of a lateral corner is front, but its north sector remains behind.
            // Never add endpoint caps beside a relevant branch from another candidate.
            if (relationCount == start) {
                for (int candidate = 0; candidate < selector.candidateCount; candidate++) {
                    int anchor = selector.candidateAnchors[candidate];
                    int membership = endpointMembership(faces, anchor, x, y, bottom, top);
                    if (membership >= 0) {
                        int face = faces.membershipFace[membership];
                        float capY = faceLineYAtAltitude(faces, face, x, bottom);
                        add(face, anchor, membership, SpatialLineRelation.relation(capY, y));
                    }
                }
            }
            qualifyJunctionFronts(actor, start, faces, x, radius);
            actorRelationCount[actor] = relationCount - start;
        }
    }

    /**
     * Removes a distant FRONT from a visually touched tile only when a direct authored
     * orthogonal junction supplies a BEHIND witness in the same map and structure.
     * The actor must reach the witness's finite projected X column; straight branches
     * without such a witness keep their FRONT relations. This is local qualification,
     * not a proof of pixel visibility or full circle-versus-segment intersection.
     */
    void qualifyJunctionFronts(int actor, int start, SpatialProjectedFaceCache faces, float x, float radius) {
        if (behindFaceStamp.length < faces.faceCount) behindFaceStamp = new int[capacity(behindFaceStamp.length, faces.faceCount)];
        if (qualificationStamp == Integer.MAX_VALUE) { java.util.Arrays.fill(behindFaceStamp, 0); qualificationStamp = 0; }
        int stamp = ++qualificationStamp;
        // Stamp all raw BEHIND witnesses before compaction to keep qualification order-independent.
        for (int relation = start; relation < relationCount; relation++)
            if (relationType[relation] == ACTOR_BEHIND_FACE) behindFaceStamp[relationFaceIndex[relation]] = stamp;
        int writeIndex = start;
        for (int relation = start; relation < relationCount; relation++) {
            int membership = relationMembershipIndex[relation], witnessFace = -1;
            if (relationType[relation] == ACTOR_IN_FRONT_OF_FACE && membership >= 0
                    && membership < faces.membershipJunctionStart.length
                    && (x + radius < faces.faceAnchorScreenMinX[membership] - RELATION_EPSILON
                    || x - radius > faces.faceAnchorScreenMaxX[membership] + RELATION_EPSILON)) {
                int junctionEnd = faces.membershipJunctionStart[membership] + faces.membershipJunctionCount[membership];
                for (int junction = faces.membershipJunctionStart[membership]; junction < junctionEnd; junction++)
                    if (behindFaceStamp[faces.junctionFace[junction]] == stamp
                            && x + radius >= faces.junctionMinX[junction] - RELATION_EPSILON
                            && x - radius <= faces.junctionMaxX[junction] + RELATION_EPSILON) {
                        int otherFace = faces.junctionFace[junction];
                        if (witnessFace < 0 || otherFace < witnessFace) witnessFace = otherFace;
                    }
            }
            if (witnessFace >= 0) {
                if (captureCandidates) reject(actor, relation, witnessFace);
                continue;
            }
            relationFaceIndex[writeIndex] = relationFaceIndex[relation];
            relationAnchorIndex[writeIndex] = relationAnchorIndex[relation];
            relationMembershipIndex[writeIndex] = membership;
            relationType[writeIndex++] = relationType[relation];
        }
        relationCount = writeIndex;
    }

    /** Transfers already qualified floor witnesses, then reapplies the existing junction protection. */
    public void applyWallContinuations(SpatialActorCollector actors, SpatialProjectedFaceCache faces,
                                       SpatialWallContinuationCache continuations, int mapIndex) {
        int write = 0;
        for (int actor = 0; actor < actors.actorCount; actor++) {
            int oldStart = actorRelationStart[actor], oldEnd = oldStart + actorRelationCount[actor];
            int start = write;
            for (int r = oldStart; r < oldEnd; r++) {
                int face = relationFaceIndex[r];
                byte type = continuations.resolve(mapIndex, face, actor, actorProjectedX[actor],
                        actors.circleRadius(actor), actors.actorAltitude[actor], relationType[r]);
                relationFaceIndex[write] = face; relationAnchorIndex[write] = relationAnchorIndex[r];
                relationMembershipIndex[write] = relationMembershipIndex[r]; relationType[write++] = type;
            }
            relationCount = write;
            qualifyJunctionFronts(actor, start, faces, actorProjectedX[actor], actors.circleRadius(actor));
            write = relationCount;
            actorRelationStart[actor] = start; actorRelationCount[actor] = write - start;
        }
        relationCount = write;
    }

    private void reject(int actor, int relation, int witness) {
        if (rejectedCount == rejectedActor.length) {
            int n = capacity(rejectedCount, rejectedCount + 1);
            rejectedActor = grow(rejectedActor, n); rejectedFace = grow(rejectedFace, n);
            rejectedAnchor = grow(rejectedAnchor, n); rejectedMembership = grow(rejectedMembership, n);
            rejectedWitnessFace = grow(rejectedWitnessFace, n); rejectedReason = grow(rejectedReason, n);
        }
        rejectedActor[rejectedCount] = actor;
        rejectedFace[rejectedCount] = relationFaceIndex[relation];
        rejectedAnchor[rejectedCount] = relationAnchorIndex[relation];
        rejectedMembership[rejectedCount] = relationMembershipIndex[relation];
        rejectedWitnessFace[rejectedCount] = witness;
        rejectedReason[rejectedCount++] = REJECTED_DISTANT_JUNCTION_FRONT;
    }

    public int relationCount() { return relationCount; }

    private static int endpointMembership(SpatialProjectedFaceCache faces, int anchor,
                                         float x, float y, float bottom, float top) {
        // Endpoint distance and cap ordering use the same altitude plane as the branch relation.
        float nearestDistance = Float.POSITIVE_INFINITY;
        float endpointX = 0f, endpointY = 0f;
        for (int m = faces.anchorMembershipHead[anchor]; m >= 0; m = faces.membershipNext[m]) {
            int face = faces.membershipFace[m];
            if (!(top > faces.faceAltitude[face] && faces.faceAltitude[face] + faces.faceHeight[face] > bottom)) continue;
            for (int end = 0; end < 2; end++) {
                float ex = end == 0 ? faces.screenMinX[face] : faces.screenMaxX[face];
                float ey = faceLineYAtAltitude(faces, face, ex, bottom);
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
                float ey = faceLineYAtAltitude(faces, face, ex, bottom);
                if (Math.abs(ex - endpointX) <= RELATION_EPSILON
                        && Math.abs(ey - endpointY) <= RELATION_EPSILON) {
                    float lineY = faceLineYAtAltitude(faces, face, x, bottom);
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

    /** Projection adds absoluteAltitude - mapPlane; rebasing a face therefore adds actorAltitude - faceBase. */
    private static float faceLineYAtAltitude(SpatialProjectedFaceCache faces, int face, float x, float actorAltitude) {
        return faces.slope[face] * x + faces.intercept[face] + actorAltitude - faces.faceAltitude[face];
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
        actorProjectedX = new float[next]; actorProjectedY = new float[next];
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
