package games.pixscape.runtime.helper;

import games.pixscape.runtime.render.RenderStateSOA;

public final class RenderSpaceMapper {

    private RenderSpaceMapper() {
    }

    public static float offsetX(RenderStateSOA renderState, int entityId) {
        if (renderState == null
                || entityId < 0
                || entityId >= renderState.offsetX.length
                || !renderState.enabled[entityId]) {
            return 0f;
        }
        return renderState.offsetX[entityId];
    }

    public static float offsetY(RenderStateSOA renderState, int entityId) {
        if (renderState == null
                || entityId < 0
                || entityId >= renderState.offsetY.length
                || !renderState.enabled[entityId]) {
            return 0f;
        }
        return renderState.offsetY[entityId];
    }
}
