package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/**
 * Construit la draw list plate à partir du RenderStateSOA.
 *
 * IMPORTANT:
 * - Ce système est SOA-only: aucune lecture ECS (ComponentMapper) ici.
 * - La visibilité "par entité" (visible + culling) doit être calculée en amont
 *   et stockée dans RenderStateSOA.visible[e].
 * - Ici on applique en plus le filtre de layer via LayerStateSOA (si présent).
 */
public final class RenderBuildDrawListSystem extends BaseSystem {

    private final RenderStateSOA state;
    private final LayerStateSOA  layerState;
    private final DrawList       drawList;
    private final RenderStats    stats;

    public RenderBuildDrawListSystem(RenderStateSOA state, LayerStateSOA layerState, DrawList drawList, RenderStats stats) {
        this.state      = state;
        this.layerState = layerState;
        this.drawList   = drawList;
        this.stats      = stats;
    }

    @Override
    protected void begin() {
        drawList.clear();
        stats.reset();
    }

    @Override
    protected void processSystem() {
        int maxId = state.maxEntityId();
        if (maxId < 0) return;

        final int layerCapacity = (layerState != null) ? layerState.enabled.length : 0;

        for (int e = 0; e <= maxId; e++) {
            if (!state.enabled[e]) continue;

            // Visibilité entité déjà calculée en amont (SOA cache).
            if (!state.visible[e]) continue;

            // Filtre layer (autorité finale côté rendu)
            if (layerState != null) {
                int layerIdx = state.layerIndex[e];
                if (layerIdx >= 0 && layerIdx < layerCapacity && !layerState.enabled[layerIdx]) {
                    continue;
                }
            }

            if (state.kind[e] != RenderStateSOA.KIND_SPRITE) continue;

            drawList.add(e);
        }

        stats.extractedQuads = drawList.size;
    }

    public RenderStateSOA getRenderState() {
        return state;
    }
}
