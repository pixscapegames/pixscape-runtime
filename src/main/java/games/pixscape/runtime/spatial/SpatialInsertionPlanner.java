package games.pixscape.runtime.spatial;

public final class SpatialInsertionPlanner {
    public int planCount;

    int[] planActorIndex = new int[0];
    int[] planActorSlot = new int[0];
    int[] planTargetDrawIndex = new int[0];
    int[] planStableOrder = new int[0];
    int[] planOriginalDrawIndex = new int[0];

    private int[] actorLowerBound = new int[0];
    private int[] actorUpperBound = new int[0];
    private boolean[] actorHasPlan = new boolean[0];

    public void clear() {
        planCount = 0;
    }

    public void plan(SpatialActorCollector actors,
                     SpatialBlocksRuntimeCache blockCache,
                     SpatialRelationSolver relations) {
        clear();
        if (actors == null || actors.actorCount == 0) return;
        if (relations == null || relations.relationCount == 0) return;
        if (blockCache == null) {
            throw new IllegalArgumentException("Spatial block runtime cache is required.");
        }

        ensureActorWorkspaceCapacity(actors.actorCount);
        for (int actor = 0; actor < actors.actorCount; actor++) {
            actorLowerBound[actor] = Integer.MIN_VALUE;
            actorUpperBound[actor] = Integer.MAX_VALUE;
            actorHasPlan[actor] = false;
        }

        for (int relation = 0; relation < relations.relationCount; relation++) {
            int actor = relations.relationActorIndex[relation];
            int block = relations.relationBlockIndex[relation];
            int type = relations.relationType[relation];
            validateRelation(actor, block, type, actors, blockCache);

            actorHasPlan[actor] = true;
            if (type == SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK) {
                int lower = blockCache.blockAnchorEndDrawIndex[block] + 1;
                if (lower > actorLowerBound[actor]) actorLowerBound[actor] = lower;
            } else if (type == SpatialRelationSolver.ACTOR_BEHIND_BLOCK) {
                int upper = blockCache.blockAnchorStartDrawIndex[block];
                if (upper < actorUpperBound[actor]) actorUpperBound[actor] = upper;
            }
        }

        for (int actor = 0; actor < actors.actorCount; actor++) {
            if (!actorHasPlan[actor]) continue;
            int lower = actorLowerBound[actor];
            int upper = actorUpperBound[actor];
            if (lower == Integer.MIN_VALUE) lower = upper;
            if (upper == Integer.MAX_VALUE) upper = lower;
            if (lower > upper) {
                throw new IllegalStateException("Conflicting spatial insertion constraints: actorIndex="
                        + actor + " lowerBound=" + lower + " upperBound=" + upper);
            }
            int target = chooseTarget(actors.actorDrawIndex[actor], lower, upper);
            addPlan(actor, actors, target);
        }

        sortPlansDeterministically();
    }

    public int planCount() {
        return planCount;
    }

    private void validateRelation(int actor,
                                  int block,
                                  int type,
                                  SpatialActorCollector actors,
                                  SpatialBlocksRuntimeCache blockCache) {
        if (actor < 0 || actor >= actors.actorCount) {
            throw new IndexOutOfBoundsException("Invalid spatial relation actor index: " + actor);
        }
        if (block < 0 || block >= blockCache.blockCount) {
            throw new IndexOutOfBoundsException("Invalid spatial relation block index: " + block);
        }
        if (type != SpatialRelationSolver.ACTOR_BEHIND_BLOCK
                && type != SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK) {
            throw new IllegalStateException("Unsupported spatial relation type for insertion planning: " + type);
        }
        if (blockCache.blockAnchorStartDrawIndex[block] < 0 || blockCache.blockAnchorEndDrawIndex[block] < 0) {
            throw new IllegalStateException("Spatial block cache entry is unresolved: block=" + block);
        }
    }

    private static int chooseTarget(int originalDrawIndex, int lower, int upper) {
        if (originalDrawIndex < lower) return lower;
        if (originalDrawIndex > upper) return upper;
        return originalDrawIndex;
    }

    private void addPlan(int actor, SpatialActorCollector actors, int target) {
        ensurePlanCapacity(planCount + 1);
        int plan = planCount++;
        planActorIndex[plan] = actor;
        planActorSlot[plan] = actors.actorSlot[actor];
        planTargetDrawIndex[plan] = target;
        planStableOrder[plan] = actors.actorStableOrder[actor];
        planOriginalDrawIndex[plan] = actors.actorDrawIndex[actor];
    }

    private void sortPlansDeterministically() {
        for (int i = 1; i < planCount; i++) {
            int actorIndex = planActorIndex[i];
            int actorSlot = planActorSlot[i];
            int target = planTargetDrawIndex[i];
            int stable = planStableOrder[i];
            int original = planOriginalDrawIndex[i];
            int j = i - 1;
            while (j >= 0 && comparePlanValues(target, stable, original, actorSlot, actorIndex, j) < 0) {
                planActorIndex[j + 1] = planActorIndex[j];
                planActorSlot[j + 1] = planActorSlot[j];
                planTargetDrawIndex[j + 1] = planTargetDrawIndex[j];
                planStableOrder[j + 1] = planStableOrder[j];
                planOriginalDrawIndex[j + 1] = planOriginalDrawIndex[j];
                j--;
            }
            planActorIndex[j + 1] = actorIndex;
            planActorSlot[j + 1] = actorSlot;
            planTargetDrawIndex[j + 1] = target;
            planStableOrder[j + 1] = stable;
            planOriginalDrawIndex[j + 1] = original;
        }
    }

    private int comparePlanValues(int target,
                                  int stable,
                                  int original,
                                  int slot,
                                  int actorIndex,
                                  int rightPlan) {
        if (target != planTargetDrawIndex[rightPlan]) {
            return target < planTargetDrawIndex[rightPlan] ? -1 : 1;
        }
        if (stable != planStableOrder[rightPlan]) {
            return stable < planStableOrder[rightPlan] ? -1 : 1;
        }
        if (original != planOriginalDrawIndex[rightPlan]) {
            return original < planOriginalDrawIndex[rightPlan] ? -1 : 1;
        }
        if (slot != planActorSlot[rightPlan]) {
            return slot < planActorSlot[rightPlan] ? -1 : 1;
        }
        if (actorIndex != planActorIndex[rightPlan]) {
            return actorIndex < planActorIndex[rightPlan] ? -1 : 1;
        }
        return 0;
    }

    private void ensureActorWorkspaceCapacity(int required) {
        if (required <= actorLowerBound.length) return;
        int next = Math.max(8, actorLowerBound.length);
        while (required > next) next <<= 1;
        actorLowerBound = grow(actorLowerBound, next);
        actorUpperBound = grow(actorUpperBound, next);
        boolean[] expanded = new boolean[next];
        System.arraycopy(actorHasPlan, 0, expanded, 0, actorHasPlan.length);
        actorHasPlan = expanded;
    }

    private void ensurePlanCapacity(int required) {
        if (required <= planActorIndex.length) return;
        int next = Math.max(8, planActorIndex.length);
        while (required > next) next <<= 1;
        planActorIndex = grow(planActorIndex, next);
        planActorSlot = grow(planActorSlot, next);
        planTargetDrawIndex = grow(planTargetDrawIndex, next);
        planStableOrder = grow(planStableOrder, next);
        planOriginalDrawIndex = grow(planOriginalDrawIndex, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
