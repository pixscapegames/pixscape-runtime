package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DrawList;

public final class SpatialOrderingKernel {
    private final SpatialBucketPlanner planner = new SpatialBucketPlanner();
    private final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();

    public int[] orderedSlots() {
        return composer.composedSlots;
    }

    public byte[] orderedDomains() {
        return composer.composedDomains;
    }

    public int orderedSize() {
        return composer.composedSize;
    }

    public void reset() {
        planner.clear();
    }

    public void begin(SpatialActorCollector actors, SpatialFrameSnapshotBuilder snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Spatial frame snapshot is required.");
        }
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);
    }

    public void addRelations(SpatialActorCollector actors,
                             SpatialProjectedFaceCache faces,
                             SpatialFaceRelationSolver relations) {
        planner.addRelations(actors, faces, relations);
    }

    public int finish(DrawList drawList,
                      SpatialActorCollector actors,
                      SpatialFrameSnapshotBuilder snapshot) {
        planner.finish(actors);
        int composedSize = composer.compose(drawList, actors, planner, snapshot);
        return composedSize;
    }

    public int unresolvedConstraintCount() { return planner.unresolvedConstraintCount(); }

}
