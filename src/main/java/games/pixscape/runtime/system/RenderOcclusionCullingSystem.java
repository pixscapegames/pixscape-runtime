package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;

import java.util.Arrays;

/**
 * 2D occlusion culling on sprites using RenderStateSOA.
 */
public final class RenderOcclusionCullingSystem extends BaseSystem {

    private final RenderStateSOA state;
    private final RenderStats stats;
    private final OrthographicCamera cam;
    private final Rectangle viewRect = new Rectangle();

    private static final int GRID_W = 32;
    private static final int GRID_H = 18;

    private final boolean[] coveredTiles = new boolean[GRID_W * GRID_H];
    private int[] spriteEntities = new int[256];
    private long[] sortKeys = new long[256];

    public RenderOcclusionCullingSystem(OrthographicCamera cam, RenderStateSOA state, RenderStats stats) {
        this.cam = cam;
        this.state = state;
        this.stats = stats;
    }

    @Override
    protected void begin() {
        cam.update(false);

        float w = cam.viewportWidth * cam.zoom;
        float h = cam.viewportHeight * cam.zoom;
        float x = cam.position.x - w * 0.5f;
        float y = cam.position.y - h * 0.5f;
        viewRect.set(x, y, w, h);

        stats.reset();
        Arrays.fill(coveredTiles, false);
    }

    @Override
    protected void processSystem() {
        int maxId = state.maxEntityId();
        if (maxId < 0) {
            return;
        }

        ensureTempCapacity(maxId + 1);
        int count = 0;

        for (int e = 0; e <= maxId; e++) {
            if (!state.enabled[e] || !state.visible[e]) {
                continue;
            }
            if (state.kind[e] != RenderStateSOA.KIND_SPRITE) {
                continue;
            }

            float minX = Math.min(Math.min(state.x1[e], state.x2[e]), Math.min(state.x3[e], state.x4[e]));
            float minY = Math.min(Math.min(state.y1[e], state.y2[e]), Math.min(state.y3[e], state.y4[e]));
            float maxX = Math.max(Math.max(state.x1[e], state.x2[e]), Math.max(state.x3[e], state.x4[e]));
            float maxY = Math.max(Math.max(state.y1[e], state.y2[e]), Math.max(state.y3[e], state.y4[e]));

            if (maxX < viewRect.x || maxY < viewRect.y
                || minX > viewRect.x + viewRect.width
                || minY > viewRect.y + viewRect.height) {
                continue;
            }

            spriteEntities[count] = e;
            sortKeys[count] = state.sortKey[e];
            count++;
        }

        if (count == 0) {
            return;
        }

        sortBySortKey(spriteEntities, sortKeys, count);

        int occluded = 0;

        for (int i = count - 1; i >= 0; i--) {
            int e = spriteEntities[i];
            if (!state.visible[e]) {
                continue;
            }

            float minX = Math.min(Math.min(state.x1[e], state.x2[e]), Math.min(state.x3[e], state.x4[e]));
            float minY = Math.min(Math.min(state.y1[e], state.y2[e]), Math.min(state.y3[e], state.y4[e]));
            float maxX = Math.max(Math.max(state.x1[e], state.x2[e]), Math.max(state.x3[e], state.x4[e]));
            float maxY = Math.max(Math.max(state.y1[e], state.y2[e]), Math.max(state.y3[e], state.y4[e]));

            if (maxX <= minX || maxY <= minY) {
                continue;
            }

            float vx0 = (minX - viewRect.x) / viewRect.width;
            float vy0 = (minY - viewRect.y) / viewRect.height;
            float vx1 = (maxX - viewRect.x) / viewRect.width;
            float vy1 = (maxY - viewRect.y) / viewRect.height;

            vx0 = clamp01(vx0);
            vy0 = clamp01(vy0);
            vx1 = clamp01(vx1);
            vy1 = clamp01(vy1);

            if (vx1 <= vx0 || vy1 <= vy0) {
                continue;
            }

            int tx0 = (int)(vx0 * GRID_W);
            int ty0 = (int)(vy0 * GRID_H);
            int tx1 = (int)Math.ceil(vx1 * GRID_W);
            int ty1 = (int)Math.ceil(vy1 * GRID_H);

            if (tx0 < 0) tx0 = 0;
            if (ty0 < 0) ty0 = 0;
            if (tx1 > GRID_W) tx1 = GRID_W;
            if (ty1 > GRID_H) ty1 = GRID_H;

            if (tx1 <= tx0 || ty1 <= ty0) {
                continue;
            }

            boolean fullyCovered = true;
            for (int ty = ty0; ty < ty1 && fullyCovered; ty++) {
                int rowOffset = ty * GRID_W;
                for (int tx = tx0; tx < tx1; tx++) {
                    if (!coveredTiles[rowOffset + tx]) {
                        fullyCovered = false;
                        break;
                    }
                }
            }

            if (fullyCovered) {
                state.visible[e] = false;
                occluded++;
                continue;
            }

            boolean isOpaque = (state.blend[e] == BlendMode.OPAQUE.id)
                && state.a[e] >= 0.999f;

            if (isOpaque) {
                for (int ty = ty0; ty < ty1; ty++) {
                    int rowOffset = ty * GRID_W;
                    for (int tx = tx0; tx < tx1; tx++) {
                        coveredTiles[rowOffset + tx] = true;
                    }
                }
            }
        }

        stats.occludedQuads += occluded;
    }

    private void ensureTempCapacity(int required) {
        if (required <= spriteEntities.length) {
            return;
        }
        int newCap = Math.max(required, spriteEntities.length << 1);
        spriteEntities = Arrays.copyOf(spriteEntities, newCap);
        sortKeys = Arrays.copyOf(sortKeys, newCap);
    }

    private static void sortBySortKey(int[] entities, long[] keys, int count) {
        for (int i = 1; i < count; i++) {
            long key = keys[i];
            int ent = entities[i];
            int j = i - 1;
            while (j >= 0 && keys[j] > key) {
                keys[j + 1] = keys[j];
                entities[j + 1] = entities[j];
                j--;
            }
            keys[j + 1] = key;
            entities[j + 1] = ent;
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
