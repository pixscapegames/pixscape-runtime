package games.pixscape.runtime.spatial;

public final class SpatialOrderingKernel {
    private final SpatialBucketPlanner planner = new SpatialBucketPlanner();
    private final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();

    public int[] orderedSlots() {
        return composer.composedSlots;
    }

    public int orderedSize() {
        return composer.composedSize;
    }

    public void begin(SpatialActorCollector actors, SpatialFrameSnapshotBuilder snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Spatial frame snapshot is required.");
        }
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);
    }

    public void addRelations(SpatialActorCollector actors,
                             SpatialBlocksRuntimeCache blockCache,
                             SpatialRelationSolver relations) {
        planner.addRelations(actors, blockCache, relations);
    }

    public int finish(int[] sourceSlots,
                      int sourceSize,
                      SpatialActorCollector actors,
                      SpatialFrameSnapshotBuilder snapshot) {
        planner.finish(actors);
        return composer.compose(sourceSlots, sourceSize, actors, planner, snapshot::isActorSlot);
    }

}
