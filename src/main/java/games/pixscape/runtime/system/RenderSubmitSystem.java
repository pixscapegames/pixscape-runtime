package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.SkipWire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ObjectFloatMap;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;

public final class RenderSubmitSystem extends BaseSystem {

    private final RenderStateSOA state;
    private final LayerStateSOA layerState;
    private final DrawList drawList;
    private final OrthographicCamera cam;
    private final float ambientMulR;
    private final float ambientMulG;
    private final float ambientMulB;

    private final MetricsBatch metricsBatch;
    private final RenderStats stats;
    private final RenderStatsSink statsSink;
    private final ShaderMode fallbackShaderMode;
    private float time = 0f;

    // --- ECS : params de shader par entity ---
    private ComponentMapper<ShaderParamsComponent> mShaderParams;

    public RenderSubmitSystem(RenderStateSOA state,
                              LayerStateSOA layerState,
                              DrawList drawList,
                              OrthographicCamera cam,
                              float ambientMulR,
                              float ambientMulG,
                              float ambientMulB,
                              MetricsBatch batch,
                              RenderStats stats,
                              RenderStatsSink statsSink) {
        this.state = state;
        this.layerState = layerState;
        this.drawList = drawList;
        this.cam = cam;
        this.ambientMulR = ambientMulR;
        this.ambientMulG = ambientMulG;
        this.ambientMulB = ambientMulB;
        this.metricsBatch = batch;
        this.stats = stats;
        this.statsSink = statsSink;
        this.fallbackShaderMode = resolveFallbackShaderMode(batch);
    }

    private static ShaderMode resolveFallbackShaderMode(MetricsBatch batch) {
        if (batch instanceof TextureArrayMeshBatch) {
            return ShaderMode.TEXTURE_ARRAY;
        } else if (batch instanceof MultiTextureMeshBatch) {
            return ShaderMode.MULTI_TEXTURE;
        } else {
            return ShaderMode.TEXTURE_2D;
        }
    }

    public RenderStateSOA getState() {
        return state;
    }

    @Override
    protected void begin() {
        time += world.getDelta();
        cam.update();
    }

    @Override
    protected void processSystem() {
        render();
    }

    @Override
    protected void end() {
        statsSink.accumulate(stats, Gdx.graphics.getDeltaTime());
    }

    private void render() {
        cam.update();

        AtlasRuntimeService.TextureArrayBundle activeBundle = null;
        if (metricsBatch instanceof TextureArrayMeshBatch) {
            TextureArrayMeshBatch taBatch = (TextureArrayMeshBatch) metricsBatch;
            activeBundle = taBatch.getBundle();

        }

        metricsBatch.begin(cam.combined, stats);

        ShaderProgram curShader = null;
        int curShaderIdx = -1;

        int curBlendId = Integer.MIN_VALUE;
        float curPackedColor = Float.NaN;

        float curCutoutThreshold = Float.NaN;

        int lastParamsHash = 0;

        int[] slots = drawList.data();
        int size = drawList.size;

        final boolean hasLayerMeta = layerState.maxLayerIndex() >= 0;

        for (int i = 0; i < size; i++) {
            final int slot = slots[i];

            final int texHandle = state.textureHandle[slot];
            if (texHandle == 0) continue;
            if (activeBundle != null && !activeBundle.handle2layer.containsKey(texHandle)) {
                continue;
            }

            int layerIdx = state.layerIndex[slot];

            if (hasLayerMeta) {
                if (layerIdx < 0 || layerIdx >= layerState.enabled.length) continue;
                if (layerIdx > layerState.maxLayerIndex() || !layerState.enabled[layerIdx]) continue;
            }

            // Shader switch (will flush inside setShader)
            final int shaderIdx = state.shader[slot];
            if (shaderIdx != curShaderIdx) {
                curShaderIdx = shaderIdx;
                ShaderProgram sh = ShaderRegistry.getByIdx(shaderIdx);

                if (sh == null) {
                    sh = ShaderRegistry.get(fallbackShaderMode.defaultShaderName());
                }

                if (sh != curShader) {
                    metricsBatch.setShader(sh, stats);
                    curShader = sh;

                    // Reset caches dependent on shader state
                    curCutoutThreshold = Float.NaN;
                    lastParamsHash = 0;

                    if (curShader != null) {
                        safeSetUniform1f(curShader, "u_time", time);
                        safeSetUniform2f(curShader, "u_layerOffset", 0f, 0f);
                        safeSetUniform3f(curShader, "u_ambientMul", ambientMulR, ambientMulG, ambientMulB);
                    }
                }
            }

            // Blend switch
            final int blendId = state.blend[slot];
            if (blendId != curBlendId) {
                metricsBatch.flush(stats);

                BlendMode blendMode = BlendMode.fromId(blendId);
                Blend.apply(blendMode);

                metricsBatch.setBlendMode(blendMode.blending, blendMode.srcFactor, blendMode.dstFactor, stats);
                if (stats != null) stats.blendSwitches++;
                curBlendId = blendId;

                if (!blendMode.blending) stats.batchesOpaque++;
                else stats.batchesAlpha++;

                // CUTOUT uniform follows blend
                if (curShader != null && curShader.hasUniform("u_cutoutThreshold")) {
                    float th = (blendId == BlendMode.CUTOUT.id) ? 0.5f : -1f;
                    if (Float.isNaN(curCutoutThreshold) || th != curCutoutThreshold) {
                        curShader.setUniformf("u_cutoutThreshold", th);
                        curCutoutThreshold = th;
                    }
                }
            }

            // Color change (attribute, safe)
            float packedColor = state.colorPacked[slot];
            if (packedColor != curPackedColor) {
                metricsBatch.setPackedColor(packedColor);
                curPackedColor = packedColor;
            }

            // Per-entity uniforms (only flush when actual values differ)
            if (enableEntityShaderParams() && curShader != null && mShaderParams != null) {
                final int entityId = state.entityId[slot];
                if (entityId >= 0 && mShaderParams.has(entityId)) {
                    ShaderParamsComponent params = mShaderParams.get(entityId);
                    if (params != null && params.floats != null && params.floats.size > 0) {
                        int h = hashShaderParams(params.floats);
                        if (h != lastParamsHash) {
                            metricsBatch.flush(stats);
                            applyShaderParams(curShader, params.floats);
                            lastParamsHash = h;
                        }
                    }
                }
            }

            float ox = state.offsetX[slot];
            float oy = state.offsetY[slot];

            metricsBatch.draw(
                    texHandle,
                    state.x1[slot] + ox, state.y1[slot] + oy,
                    state.x2[slot] + ox, state.y2[slot] + oy,
                    state.x3[slot] + ox, state.y3[slot] + oy,
                    state.x4[slot] + ox, state.y4[slot] + oy,
                    state.u1[slot], state.v1[slot],
                    state.u2[slot], state.v2[slot],
                    stats
            );
            stats.drawnQuads++;
        }
        metricsBatch.end(stats);
    }

    private static int hashShaderParams(ObjectFloatMap<String> floats) {
        // Commutative hash (iteration order not guaranteed): XOR/mix
        int h = 0x9E3779B9;
        ObjectFloatMap.Entries<String> it = floats.entries();
        while (it.hasNext()) {
            ObjectFloatMap.Entry<String> e = it.next();
            int kh = (e.key != null ? e.key.hashCode() : 0);
            int vh = Float.floatToIntBits(e.value);
            int x = kh * 0x85EBCA6B ^ vh * 0xC2B2AE35;
            // mix
            x ^= (x >>> 16);
            h ^= x;
            h = Integer.rotateLeft(h, 13) * 5 + 0xE6546B64;
        }
        return h;
    }


    private void applyShaderParams(ShaderProgram shader, ObjectFloatMap<String> floats) {
        if (shader == null || floats == null || floats.size == 0) return;

        ObjectFloatMap.Entries<String> it = floats.entries();
        while (it.hasNext()) {
            ObjectFloatMap.Entry<String> entry = it.next();
            if (entry == null || entry.key == null || entry.key.length() == 0) continue;

            String name = entry.key;

            if (!shader.hasUniform(name)) {
                continue;
            }

            shader.setUniformf(name, entry.value);
        }
    }

    private static void safeSetUniform1f(ShaderProgram shader, String name, float v0) {
        if (shader == null || name == null || name.length() == 0) return;
        if (!shader.hasUniform(name)) return;
        shader.setUniformf(name, v0);
    }

    private static void safeSetUniform2f(ShaderProgram shader, String name, float v0, float v1) {
        if (shader == null || name == null || name.length() == 0) return;
        if (!shader.hasUniform(name)) return;
        shader.setUniformf(name, v0, v1);
    }

    private static void safeSetUniform3f(ShaderProgram shader, String name, float v0, float v1, float v2) {
        if (shader == null || name == null || name.length() == 0) return;
        if (!shader.hasUniform(name)) return;
        shader.setUniformf(name, v0, v1, v2);
    }

    private static boolean enableEntityShaderParams() {
        return Gdx.app == null
                || Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.WebGL;
    }

}
