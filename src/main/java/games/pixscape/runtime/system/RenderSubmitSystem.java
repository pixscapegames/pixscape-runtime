package games.pixscape.runtime.system;

import com.artemis.*;
import com.artemis.annotations.SkipWire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ObjectFloatMap;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;

public final class RenderSubmitSystem extends BaseSystem {

    private final RenderStateSOA     state;
    private final LayerStateSOA      layerState;
    private final CameraStateSOA     cameraState;
    private final DrawList           drawList;
    private final OrthographicCamera cam;

    private final MetricsBatch       metricsBatch;
    private final RenderStats        stats;
    private final RenderStatsSink    statsSink;
    private float time = 0f;

    // --- ECS : params de shader par entité ---
    private ComponentMapper<ShaderParamsComponent> mShaderParams;

    // --- DEBUG (reflection, pas de dépendance runtime->studio) ---
    @SkipWire private EntitySubscription pointLightSub;

    @SkipWire private final float[] debugBatchColor = new float[4];
    @SkipWire private final StringBuilder debugSb = new StringBuilder(256);
    @SkipWire private final StringBuilder debugPreviewSb = new StringBuilder(256);

    public RenderSubmitSystem(RenderStateSOA state,
                              LayerStateSOA layerState,
                              CameraStateSOA cameraState,
                              DrawList drawList,
                              OrthographicCamera cam,
                              MetricsBatch batch,
                              RenderStats stats,
                              RenderStatsSink statsSink) {
        this.state        = state;
        this.layerState   = layerState;
        this.cameraState  = cameraState;
        this.drawList     = drawList;
        this.cam          = cam;
        this.metricsBatch = batch;
        this.stats        = stats;
        this.statsSink    = statsSink;
    }

    public RenderStateSOA getState() {
        return state;
    }

    @Override
    protected void begin() {
        time += world.getDelta();
        cam.update();
        // ⚠️ metricsBatch.begin est appelé par caméra dans renderForCamera(...)
    }

    @Override
    protected void processSystem() {
        if (cameraState.maxIndex < 0) return;

        for (int camIndex = 0; camIndex <= cameraState.maxIndex; camIndex++) {
            if (!cameraState.enabled[camIndex]) continue;
            renderForCamera(camIndex);
        }
    }

    @Override
    protected void end() {
        statsSink.accumulate(stats, Gdx.graphics.getDeltaTime());
    }

    private void renderForCamera(int camIndex) {
        cam.update();

        boolean wantsOffscreen = cameraState.useOffscreen[camIndex]
                || cameraState.postFxChainId[camIndex] != 0;

        int fbo = cameraState.fboHandle[camIndex];

        if (wantsOffscreen && fbo != 0) {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        } else {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        AtlasRuntimeService.TextureArrayBundle activeBundle = null;
        if (metricsBatch instanceof TextureArrayMeshBatch taBatch) {
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

        final int cameraLayerMask = cameraState.layerMask[camIndex];
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
                if (layerIdx < 0 || layerIdx >= 31) continue;
                if (layerIdx > layerState.maxLayerIndex() || !layerState.enabled[layerIdx]) continue;

                int bit = 1 << layerIdx;
                if ((cameraLayerMask & bit) == 0) continue;
            }

            // Shader switch (will flush inside setShader)
            final int shaderIdx = state.shader[slot];
            if (shaderIdx != curShaderIdx) {
                curShaderIdx = shaderIdx;
                ShaderProgram sh = ShaderRegistry.getByIdx(shaderIdx);

                if (sh == null) {
                    sh = ShaderRegistry.get("default");
                }
                if (sh != curShader) {
                    metricsBatch.setShader(sh, stats);
                    curShader = sh;

                    // Reset caches dependent on shader state
                    curCutoutThreshold = Float.NaN;
                    lastParamsHash = 0;

                    if (curShader != null) {
                        setUniform1f(curShader, "u_time", time);
                        setUniformLayerOffset(curShader);
                        setUniformAmbientMul(curShader, camIndex);

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
            if (curShader != null && mShaderParams != null) {
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

    private void setUniformAmbientMul(ShaderProgram shader, int camIndex) {
        if (shader == null || !shader.hasUniform("u_ambientMul")) return;

        float r = cameraState.ambientMulR[camIndex];
        float g = cameraState.ambientMulG[camIndex];
        float b = cameraState.ambientMulB[camIndex];

        shader.setUniformf("u_ambientMul", r, g, b);
    }


    private static int hashShaderParams(com.badlogic.gdx.utils.ObjectFloatMap<String> floats) {
        // Hash commutatif (ordre d'itération non garanti) : XOR/mix
        int h = 0x9E3779B9;
        var it = floats.entries();
        while (it.hasNext()) {
            var e = it.next();
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
        ObjectFloatMap.Entries<String> it = floats.entries();
        while (it.hasNext()) {
            ObjectFloatMap.Entry<String> entry = it.next();
            setUniform1f(shader, entry.key, entry.value);
        }
    }

    private void setUniform1f(ShaderProgram shader, String name, float value) {
        if (shader != null) shader.setUniformf(name, value);
    }

    private void setUniformLayerOffset(ShaderProgram shader) {
        if (shader != null && shader.hasUniform("u_layerOffset")) {
            shader.setUniformf("u_layerOffset", 0f, 0f);
        }
    }
}
