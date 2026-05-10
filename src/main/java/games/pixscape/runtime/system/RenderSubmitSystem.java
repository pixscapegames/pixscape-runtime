package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.ShaderFloatParam;
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

    private final ObjectMap<ShaderProgram, ObjectIntMap<String>> uniformLocationCache = new ObjectMap<>();

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

        final boolean hasShaderParamsMapper = mShaderParams != null;

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

            // Shader switch
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

                    curCutoutThreshold = Float.NaN;
                    lastParamsHash = 0;

                    if (curShader != null) {
                        setUniform1fCached(curShader, "u_time", time);
                        setUniform2fCached(curShader, "u_layerOffset", 0f, 0f);
                        setUniform3fCached(curShader, "u_ambientMul", ambientMulR, ambientMulG, ambientMulB);
                    }
                }
            }

            // Blend switch
            final int blendId = state.blend[slot];

            if (blendId != curBlendId) {
                metricsBatch.flush(stats);

                BlendMode blendMode = BlendMode.fromId(blendId);

                Blend.apply(blendMode);

                metricsBatch.setBlendMode(
                        blendMode.blending,
                        blendMode.srcFactor,
                        blendMode.dstFactor,
                        stats
                );

                if (stats != null) {
                    stats.blendSwitches++;
                }

                curBlendId = blendId;

                if (!blendMode.blending) {
                    stats.batchesOpaque++;
                } else {
                    stats.batchesAlpha++;
                }

                if (curShader != null) {
                    float th = (blendId == BlendMode.CUTOUT.id) ? 0.5f : -1f;

                    if (Float.isNaN(curCutoutThreshold) || th != curCutoutThreshold) {
                        metricsBatch.flush(stats);
                        curShader.bind();
                        setUniform1fCached(curShader, "u_cutoutThreshold", th);
                        curCutoutThreshold = th;
                    }
                }
            }

            // Color change
            float packedColor = state.colorPacked[slot];

            if (packedColor != curPackedColor) {
                metricsBatch.setPackedColor(packedColor);
                curPackedColor = packedColor;
            }

            // Per-entity uniforms
            if (curShader != null && hasShaderParamsMapper) {
                final int entityId = state.entityId[slot];

                if (entityId >= 0 && mShaderParams.has(entityId)) {
                    ShaderParamsComponent params = mShaderParams.get(entityId);

                    if (params != null
                            && params.floats != null
                            && params.floats.size > 0) {

                        int h = hashShaderParams(params.floats);

                        if (h != lastParamsHash) {
                            metricsBatch.flush(stats);
                            curShader.bind();
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

    private static int hashShaderParams(Array<ShaderFloatParam> floats) {
        if (floats == null || floats.size == 0) {
            return 0;
        }

        int h = 0x9E3779B9;

        for (int i = 0; i < floats.size; i++) {
            ShaderFloatParam param = floats.get(i);

            if (param == null || param.name == null) {
                continue;
            }

            int kh = param.name.hashCode();
            int vh = Float.floatToIntBits(param.value);

            int x = kh * 0x85EBCA6B ^ vh * 0xC2B2AE35;

            x ^= (x >>> 16);

            h ^= x;
            h = Integer.rotateLeft(h, 13) * 5 + 0xE6546B64;
        }

        return h;
    }

    private void applyShaderParams(ShaderProgram shader, Array<ShaderFloatParam> floats) {
        if (shader == null || floats == null || floats.size == 0) {
            return;
        }

        for (int i = 0; i < floats.size; i++) {
            ShaderFloatParam param = floats.get(i);

            if (param == null || param.name == null || param.name.length() == 0) {
                continue;
            }

            setUniform1fCached(shader, param.name, param.value);
        }
    }

    private int getUniformLocationCached(ShaderProgram shader, String uniformName) {
        if (shader == null || uniformName == null || uniformName.length() == 0) {
            return -1;
        }

        ObjectIntMap<String> shaderCache = uniformLocationCache.get(shader);

        if (shaderCache == null) {
            shaderCache = new ObjectIntMap<>();
            uniformLocationCache.put(shader, shaderCache);
        }

        if (shaderCache.containsKey(uniformName)) {
            return shaderCache.get(uniformName, -1);
        }

        int location = shader.getUniformLocation(uniformName);
        shaderCache.put(uniformName, location);
        return location;
    }

    private void setUniform1fCached(ShaderProgram shader, String name, float v0) {
        int location = getUniformLocationCached(shader, name);
        if (location < 0) return;

        shader.setUniformf(location, v0);
    }

    private void setUniform2fCached(ShaderProgram shader, String name, float v0, float v1) {
        int location = getUniformLocationCached(shader, name);
        if (location < 0) return;

        shader.setUniformf(location, v0, v1);
    }

    private void setUniform3fCached(ShaderProgram shader, String name, float v0, float v1, float v2) {
        int location = getUniformLocationCached(shader, name);
        if (location < 0) return;

        shader.setUniformf(location, v0, v1, v2);
    }
}