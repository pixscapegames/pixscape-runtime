package games.pixscape.runtime.spatial;

import com.artemis.ComponentMapper;
import com.artemis.EntityManager;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.RenderSourceDomain;

public final class SpatialActorCollector {
    private ComponentMapper<games.pixscape.runtime.component.AnimationComponent> animations;
    private ComponentMapper<games.pixscape.runtime.component.AssetRefComponent> assets;
    float[] influenceBounds = new float[0];

    public void setRepresentationMappers(ComponentMapper<games.pixscape.runtime.component.AnimationComponent> animations,
                                         ComponentMapper<games.pixscape.runtime.component.AssetRefComponent> assets) {
        this.animations = animations;
        this.assets = assets;
    }

    private ActorSpatialRectangleData rectangle(int entity, SpatialRepresentationComponent spatial) {
        if (spatial == null || spatial.rectangles == null) return null;
        games.pixscape.runtime.component.AnimationComponent animation = animations == null ? null : animations.getSafe(entity, null);
        games.pixscape.runtime.component.AssetRefComponent asset = assets == null ? null : assets.getSafe(entity, null);
        return spatial.rectangles.get(asset == null ? 0 : asset.assetId, animation == null ? null : animation.currentClip);
    }

    private final SpatialActorGeometry.Footprint tmpFootprint = new SpatialActorGeometry.Footprint();

    public int actorCount;

    public int[] actorSlot = new int[0];
    private int[] actorIndexBySlot = new int[0];

    /**
     * Frame snapshot identity for on-demand diagnostics; ECS ids are not durable identities.
     */
    public int entityId(int actor) {
        return actorEntityId[actor];
    }

    int[] actorEntityId = new int[0];
    int[] actorDrawIndex = new int[0];
    int[] actorLayerIndex = new int[0];
    int[] actorStableOrder = new int[0];
    String[] actorName = new String[0];

    float[] actorFootX = new float[0];
    float[] actorFootY = new float[0];
    float[] actorAltitude = new float[0];
    float[] actorHeight = new float[0];
    float[] actorCircleX = new float[0];
    float[] actorCircleY = new float[0];
    float[] actorCircleRadius = new float[0];
    float[] actorBaseStartX = new float[0];
    float[] actorBaseStartY = new float[0];
    float[] actorBaseEndX = new float[0];
    float[] actorBaseEndY = new float[0];

    public void clear() {
        for (int actor = 0; actor < actorCount; actor++) actorIndexBySlot[actorSlot[actor]] = -1;
        actorCount = 0;
    }

    /**
     * Complete frame-local lookup; cleared before collecting the next draw list.
     */
    public int actorIndexForSlot(int slot) {
        return slot >= 0 && slot < actorIndexBySlot.length ? actorIndexBySlot[slot] : -1;
    }

    public void collect(DrawList drawList,
                        DynamicEntityRenderState state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                        ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper) {
        collect(drawList,
                state,
                spatialLayers,
                entityManager,
                entityIndexMapper,
                transformMapper,
                spatialRepresentationMapper,
                spatialFootprintMapper,
                null,
                null);
    }

    public void collect(DrawList drawList,
                        DynamicEntityRenderState state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                        ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
                        ComponentMapper<PixscapeIdentityComponent> identityMapper) {
        collect(drawList, state, spatialLayers, entityManager, entityIndexMapper, transformMapper,
                spatialRepresentationMapper, spatialFootprintMapper, identityMapper, null);
    }

    public void collect(DrawList drawList,
                        DynamicEntityRenderState state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                        ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
                        ComponentMapper<PixscapeIdentityComponent> identityMapper,
                        WorldTransformState worldTransforms) {
        clear();
        if (drawList == null || state == null || drawList.size <= 0) return;

        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            if (domains[drawIndex] != RenderSourceDomain.SOURCE_ECS) {
                continue;
            }
            int slot = data[drawIndex];
            collectSlot(slot,
                    drawIndex,
                    state,
                    spatialLayers,
                    entityManager,
                    entityIndexMapper,
                    transformMapper,
                    spatialRepresentationMapper,
                    spatialFootprintMapper,
                    identityMapper,
                    worldTransforms);
        }
    }

    public int actorCount() {
        return actorCount;
    }

    /**
     * Uses the projected active rectangle collected for this frame, with display offsets applied once.
     */
    public void writeInfluenceQuad(int actor, float offsetX, float offsetY, float[] out) {
        int b = actor * 4;
        float left = influenceBounds[b] + offsetX, bottom = influenceBounds[b + 1] + offsetY;
        float right = influenceBounds[b + 2] + offsetX, top = influenceBounds[b + 3] + offsetY;
        out[0] = left;
        out[1] = bottom;
        out[2] = right;
        out[3] = bottom;
        out[4] = right;
        out[5] = top;
        out[6] = left;
        out[7] = top;
    }

    float circleRadius(int actor) {
        if (actor < 0 || actor >= actorCount || actor >= actorCircleRadius.length) return 0f;
        float radius = actorCircleRadius[actor];
        return Float.isFinite(radius) && radius > 0f ? radius : 0f;
    }

    boolean collectSlot(int slot,
                        int drawIndex,
                        DynamicEntityRenderState state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                        ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
                        ComponentMapper<PixscapeIdentityComponent> identityMapper,
                        WorldTransformState worldTransforms) {
        if (!isEligibleActorSlotWithoutFootprint(slot,
                state,
                spatialLayers,
                entityManager,
                entityIndexMapper,
                spatialRepresentationMapper)) {
            return false;
        }

        int entity = state.renderSlotToEntityId[slot];
        TransformComponent transform = transformMapper.getSafe(entity, null);
        SpatialRepresentationComponent height = spatialRepresentationMapper.getSafe(entity, null);
        if (!writeActorCircleFootprint(entity,
                transform,
                spatialFootprintMapper,
                worldTransforms,
                tmpFootprint)) {
            return false;
        }

        ActorSpatialRectangleData rectangle = rectangle(entity, height);
        if (rectangle == null) return false;
        ensureActorCapacity(actorCount + 1);
        int actor = actorCount++;
        actorSlot[actor] = slot;
        if (slot >= actorIndexBySlot.length) {
            int previous = actorIndexBySlot.length;
            actorIndexBySlot = grow(actorIndexBySlot, Math.max(slot + 1, Math.max(8, previous * 2)));
            java.util.Arrays.fill(actorIndexBySlot, previous, actorIndexBySlot.length, -1);
        }
        actorIndexBySlot[slot] = actor;
        actorEntityId[actor] = entity;
        actorDrawIndex[actor] = drawIndex;
        actorLayerIndex[actor] = state.layerIndex[slot];
        actorStableOrder[actor] = stableActorId(slot, state);
        PixscapeIdentityComponent identity = identityMapper != null
                ? identityMapper.getSafe(entity, null)
                : null;
        actorName[actor] = identity != null ? identity.name : null;
        actorFootX[actor] = tmpFootprint.footX;
        actorFootY[actor] = tmpFootprint.footY;
        actorAltitude[actor] = tmpFootprint.bottom;
        actorHeight[actor] = rectangle.height;
        ActorSpatialRectanglePlacement.influenceBounds(rectangle, tmpFootprint.footX, tmpFootprint.footY,
                influenceBounds, actor * 4);
        actorCircleX[actor] = tmpFootprint.footX;
        actorCircleY[actor] = tmpFootprint.footY;
        actorCircleRadius[actor] = actorFootRadius(tmpFootprint);
        actorBaseStartX[actor] = tmpFootprint.minX;
        actorBaseStartY[actor] = tmpFootprint.maxY;
        actorBaseEndX[actor] = tmpFootprint.maxX;
        actorBaseEndY[actor] = tmpFootprint.maxY;
        return true;
    }

    public boolean isEligibleActorSlot(int slot,
                                       DynamicEntityRenderState state,
                                       boolean[] spatialLayers,
                                       EntityManager entityManager,
                                       ComponentMapper<EntityIndexComponent> entityIndexMapper,
                                       ComponentMapper<TransformComponent> transformMapper,
                                       ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                                       ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper) {
        return isEligibleActorSlot(slot, state, spatialLayers, entityManager, entityIndexMapper,
                transformMapper, spatialRepresentationMapper, spatialFootprintMapper, null);
    }

    public boolean isEligibleActorSlot(int slot,
                                       DynamicEntityRenderState state,
                                       boolean[] spatialLayers,
                                       EntityManager entityManager,
                                       ComponentMapper<EntityIndexComponent> entityIndexMapper,
                                       ComponentMapper<TransformComponent> transformMapper,
                                       ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
                                       ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
                                       WorldTransformState worldTransforms) {
        if (!isEligibleActorSlotWithoutFootprint(
                slot, state, spatialLayers, entityManager, entityIndexMapper, spatialRepresentationMapper)) {
            return false;
        }

        int entity = state.renderSlotToEntityId[slot];
        TransformComponent transform = transformMapper != null
                ? transformMapper.getSafe(entity, null)
                : null;
        return writeActorCircleFootprint(
                entity,
                transform,
                spatialFootprintMapper,
                worldTransforms,
                null);
    }

    public boolean isEligibleActorSlotOnSpatialLayer(
            int slot,
            DynamicEntityRenderState state,
            boolean spatialLayerEnabled,
            EntityManager entityManager,
            ComponentMapper<EntityIndexComponent> entityIndexMapper,
            ComponentMapper<TransformComponent> transformMapper,
            ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
            ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper) {
        return isEligibleActorSlotOnSpatialLayer(slot, state, spatialLayerEnabled, entityManager,
                entityIndexMapper, transformMapper, spatialRepresentationMapper, spatialFootprintMapper, null);
    }

    public boolean isEligibleActorSlotOnSpatialLayer(
            int slot,
            DynamicEntityRenderState state,
            boolean spatialLayerEnabled,
            EntityManager entityManager,
            ComponentMapper<EntityIndexComponent> entityIndexMapper,
            ComponentMapper<TransformComponent> transformMapper,
            ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper,
            ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
            WorldTransformState worldTransforms) {
        if (!isEligibleActorSlotWithoutFootprint(
                slot,
                state,
                null,
                spatialLayerEnabled,
                entityManager,
                entityIndexMapper,
                spatialRepresentationMapper)) {
            return false;
        }

        int entity = state.renderSlotToEntityId[slot];
        TransformComponent transform = transformMapper != null
                ? transformMapper.getSafe(entity, null)
                : null;
        return writeActorCircleFootprint(
                entity,
                transform,
                spatialFootprintMapper,
                worldTransforms,
                null);
    }

    private boolean isEligibleActorSlotWithoutFootprint(
            int slot,
            DynamicEntityRenderState state,
            boolean[] spatialLayers,
            EntityManager entityManager,
            ComponentMapper<EntityIndexComponent> entityIndexMapper,
            ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper) {
        return isEligibleActorSlotWithoutFootprint(
                slot,
                state,
                spatialLayers,
                false,
                entityManager,
                entityIndexMapper,
                spatialRepresentationMapper);
    }

    private boolean isEligibleActorSlotWithoutFootprint(
            int slot,
            DynamicEntityRenderState state,
            boolean[] spatialLayers,
            boolean spatialLayerEnabled,
            EntityManager entityManager,
            ComponentMapper<EntityIndexComponent> entityIndexMapper,
            ComponentMapper<SpatialRepresentationComponent> spatialRepresentationMapper) {
        if (!isRenderableSlot(slot, state)) return false;

        int entity = state.renderSlotToEntityId[slot];
        if (entity < 0) return false;
        if (entityManager == null || !entityManager.isActive(entity)) return false;

        int effectiveLayerIndex = state.layerIndex[slot];
        if (spatialLayers != null) {
            if (!isSpatialLayer(effectiveLayerIndex, spatialLayers)) return false;
        } else if (!spatialLayerEnabled) {
            return false;
        }

        SpatialRepresentationComponent height = spatialRepresentationMapper != null
                ? spatialRepresentationMapper.getSafe(entity, null)
                : null;
        ActorSpatialRectangleData rectangle = rectangle(entity, height);
        return rectangle != null && rectangle.width > 0f && rectangle.height > 0f;
    }

    static boolean writeActorCircleFootprint(int entity,
                                                    TransformComponent transform,
                                                    ComponentMapper<SpatialFootprintComponent> spatialFootprintMapper,
                                                    WorldTransformState worldTransforms,
                                                    SpatialActorGeometry.Footprint out) {
        return games.pixscape.runtime.service.SpatialActorPose.resolve(entity, transform,
                spatialFootprintMapper == null ? null : spatialFootprintMapper.getSafe(entity, null),
                worldTransforms, out);
    }

    private static boolean isRenderableSlot(int slot, DynamicEntityRenderState state) {
        return state != null
                && slot >= 0
                && slot < state.activeCount
                && state.kind[slot] == RenderKind.SPRITE
                && state.enabled[slot]
                && state.visible[slot]
                && state.textureHandle[slot] != 0;
    }

    private static boolean isSpatialLayer(int layerIndex, boolean[] spatialLayers) {
        return spatialLayers != null
                && layerIndex >= 0
                && layerIndex < spatialLayers.length
                && spatialLayers[layerIndex];
    }

    private static int stableActorId(int slot, DynamicEntityRenderState state) {
        return slot;
    }

    private static float actorFootRadius(SpatialActorGeometry.Footprint footprint) {
        float width = footprint.maxX - footprint.minX;
        float depth = footprint.maxY - footprint.minY;
        return Math.max(width, depth) * 0.5f;
    }

    private void ensureActorCapacity(int required) {
        if (required <= actorSlot.length) return;
        int next = Math.max(8, actorSlot.length);
        while (required > next) next <<= 1;

        actorSlot = grow(actorSlot, next);
        actorEntityId = grow(actorEntityId, next);
        actorDrawIndex = grow(actorDrawIndex, next);
        actorLayerIndex = grow(actorLayerIndex, next);
        actorStableOrder = grow(actorStableOrder, next);
        String[] expandedNames = new String[next];
        System.arraycopy(actorName, 0, expandedNames, 0, actorName.length);
        actorName = expandedNames;
        actorFootX = grow(actorFootX, next);
        actorFootY = grow(actorFootY, next);
        actorAltitude = grow(actorAltitude, next);
        actorHeight = grow(actorHeight, next);
        influenceBounds = grow(influenceBounds, next * 4);
        actorCircleX = grow(actorCircleX, next);
        actorCircleY = grow(actorCircleY, next);
        actorCircleRadius = grow(actorCircleRadius, next);
        actorBaseStartX = grow(actorBaseStartX, next);
        actorBaseStartY = grow(actorBaseStartY, next);
        actorBaseEndX = grow(actorBaseEndX, next);
        actorBaseEndY = grow(actorBaseEndY, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static float[] grow(float[] source, int next) {
        float[] expanded = new float[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
