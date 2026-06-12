package games.pixscape.runtime.spatial;

import com.artemis.ComponentMapper;
import com.artemis.EntityManager;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;

public final class SpatialActorCollector {
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private final SpatialActorGeometry.Footprint tmpFootprint = new SpatialActorGeometry.Footprint();

    public int actorCount;

    public int[] actorSlot = new int[0];
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
        actorCount = 0;
    }

    public void collect(DrawList drawList,
                        RenderStateSOA state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialHeightComponent> spatialHeightMapper,
                        ComponentMapper<PhysicsBodyComponent> physicsBodyMapper,
                        ComponentMapper<PhysicsFixturesComponent> physicsFixturesMapper,
                        float pixelsPerMeter) {
        collect(drawList,
                state,
                spatialLayers,
                entityManager,
                entityIndexMapper,
                transformMapper,
                spatialHeightMapper,
                physicsBodyMapper,
                physicsFixturesMapper,
                null,
                pixelsPerMeter);
    }

    public void collect(DrawList drawList,
                        RenderStateSOA state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialHeightComponent> spatialHeightMapper,
                        ComponentMapper<PhysicsBodyComponent> physicsBodyMapper,
                        ComponentMapper<PhysicsFixturesComponent> physicsFixturesMapper,
                        ComponentMapper<PixscapeIdentityComponent> identityMapper,
                        float pixelsPerMeter) {
        clear();
        if (drawList == null || state == null || drawList.size <= 0) return;

        int[] data = drawList.data();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            collectSlot(slot,
                    drawIndex,
                    state,
                    spatialLayers,
                    entityManager,
                    entityIndexMapper,
                    transformMapper,
                    spatialHeightMapper,
                    physicsBodyMapper,
                    physicsFixturesMapper,
                    identityMapper,
                    pixelsPerMeter);
        }
    }

    public int actorCount() {
        return actorCount;
    }

    boolean collectSlot(int slot,
                        int drawIndex,
                        RenderStateSOA state,
                        boolean[] spatialLayers,
                        EntityManager entityManager,
                        ComponentMapper<EntityIndexComponent> entityIndexMapper,
                        ComponentMapper<TransformComponent> transformMapper,
                        ComponentMapper<SpatialHeightComponent> spatialHeightMapper,
                        ComponentMapper<PhysicsBodyComponent> physicsBodyMapper,
                        ComponentMapper<PhysicsFixturesComponent> physicsFixturesMapper,
                        ComponentMapper<PixscapeIdentityComponent> identityMapper,
                        float pixelsPerMeter) {
        if (!isEligibleActorSlot(slot,
                state,
                spatialLayers,
                entityManager,
                entityIndexMapper,
                transformMapper,
                spatialHeightMapper,
                physicsBodyMapper,
                physicsFixturesMapper,
                pixelsPerMeter)) {
            return false;
        }

        int entity = state.entityId[slot];
        TransformComponent transform = transformMapper.getSafe(entity, null);
        SpatialHeightComponent height = spatialHeightMapper.getSafe(entity, null);
        if (!writeActorPhysicsCircleFootprint(entity,
                transform,
                height,
                physicsBodyMapper,
                physicsFixturesMapper,
                pixelsPerMeter,
                tmpFootprint)) {
            return false;
        }

        ensureActorCapacity(actorCount + 1);
        int actor = actorCount++;
        actorSlot[actor] = slot;
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
        actorHeight[actor] = tmpFootprint.top - tmpFootprint.bottom;
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
                                       RenderStateSOA state,
                                       boolean[] spatialLayers,
                                       EntityManager entityManager,
                                       ComponentMapper<EntityIndexComponent> entityIndexMapper,
                                       ComponentMapper<TransformComponent> transformMapper,
                                       ComponentMapper<SpatialHeightComponent> spatialHeightMapper,
                                       ComponentMapper<PhysicsBodyComponent> physicsBodyMapper,
                                       ComponentMapper<PhysicsFixturesComponent> physicsFixturesMapper,
                                       float pixelsPerMeter) {
        if (!isRenderableSlot(slot, state)) return false;

        int entity = state.entityId[slot];
        if (entity < 0 || entity >= state.getCapacity()) return false;
        if (entityManager == null || !entityManager.isActive(entity)) return false;

        EntityIndexComponent index = entityIndexMapper != null
                ? entityIndexMapper.getSafe(entity, null)
                : null;
        if (index == null) return false;
        if (index.layerIndex != state.layerIndex[slot]) return false;
        if (!isSpatialLayer(index.layerIndex, spatialLayers)) return false;

        SpatialHeightComponent height = spatialHeightMapper != null
                ? spatialHeightMapper.getSafe(entity, null)
                : null;
        if (height == null || height.height <= 0f) return false;

        TransformComponent transform = transformMapper != null
                ? transformMapper.getSafe(entity, null)
                : null;
        return writeActorPhysicsCircleFootprint(entity,
                transform,
                height,
                physicsBodyMapper,
                physicsFixturesMapper,
                pixelsPerMeter,
                null);
    }

    static boolean writeActorPhysicsCircleFootprint(int entity,
                                                    TransformComponent transform,
                                                    SpatialHeightComponent height,
                                                    ComponentMapper<PhysicsBodyComponent> physicsBodyMapper,
                                                    ComponentMapper<PhysicsFixturesComponent> physicsFixturesMapper,
                                                    float pixelsPerMeter,
                                                    SpatialActorGeometry.Footprint out) {
        if (transform == null || height == null) return false;
        PhysicsBodyComponent body = physicsBodyMapper != null
                ? physicsBodyMapper.getSafe(entity, null)
                : null;
        if (body == null || !body.enabled) return false;
        PhysicsFixturesComponent fixtures = physicsFixturesMapper != null
                ? physicsFixturesMapper.getSafe(entity, null)
                : null;
        if (fixtures == null || fixtures.fixtures == null || fixtures.fixtures.size == 0) return false;

        float ppm = pixelsPerMeter > 0f ? pixelsPerMeter : DEFAULT_PIXELS_PER_METER;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE) continue;
            if (fixture.radius <= 0f) continue;

            float localX = fixture.offsetX * ppm;
            float localY = fixture.offsetY * ppm;
            float cos = (float) Math.cos(transform.rotationRad);
            float sin = (float) Math.sin(transform.rotationRad);
            float cx = transform.x + localX * cos - localY * sin;
            float cy = transform.y + localX * sin + localY * cos;
            float radius = fixture.radius * ppm;
            if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(radius) || radius <= 0f) {
                continue;
            }

            if (out != null) {
                out.footX = cx;
                out.footY = cy;
                out.minX = cx - radius;
                out.maxX = cx + radius;
                out.minY = cy - radius;
                out.maxY = cy + radius;
                out.bottom = height.altitude;
                out.top = height.altitude + height.height;
                out.pointOnly = false;
            }
            return true;
        }
        return false;
    }

    private static boolean isRenderableSlot(int slot, RenderStateSOA state) {
        return state != null
                && slot >= 0
                && slot < state.getCapacity()
                && state.kind[slot] == RenderStateSOA.KIND_SPRITE
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

    private static int stableActorId(int slot, RenderStateSOA state) {
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
