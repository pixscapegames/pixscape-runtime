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
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.MultiTextureMeshBatch;
import games.pixscape.runtime.render.batch.TextureArrayMeshBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.ShaderRegistry;

public final class RenderSubmitSystem extends BaseSystem implements ProfiledSystem {
    private static final int MAX_REPEAT_DRAWS_PER_SLOT = 1024;
    private static final float AXIS_EPSILON = 0.0001f;

    private final LayerStateSOA layerState;
    private final FrameRenderQueue frameQueue;
    private final OrthographicCamera cam;
    private final float ambientMulR;
    private final float ambientMulG;
    private final float ambientMulB;

    private final MetricsBatch metricsBatch;
    private final RenderStats stats;
    private final RenderStatsSink statsSink;
    private final ShaderMode fallbackShaderMode;
    private float time = 0f;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private final int[] repeatRange = new int[4];

    // --- ECS : params de shader par entity ---
    private ComponentMapper<ShaderParamsComponent> mShaderParams;

    private final ObjectMap<ShaderProgram, ObjectIntMap<String>> uniformLocationCache = new ObjectMap<>();

    public RenderSubmitSystem(LayerStateSOA layerState,
                              FrameRenderQueue frameQueue,
                              OrthographicCamera cam,
                              float ambientMulR,
                              float ambientMulG,
                              float ambientMulB,
                              MetricsBatch batch,
                              RenderStats stats,
                              RenderStatsSink statsSink) {
        this.layerState = layerState;
        this.frameQueue = frameQueue;
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

    @Override
    protected void begin() {
        time += world.getDelta();
        cam.update();
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_SUBMIT);
            try {
                render();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_SUBMIT, startNs);
            }
        } else {
            render();
        }
    }

    @Override
    protected void end() {
        statsSink.accumulate(stats, Gdx.graphics.getDeltaTime());
    }

    void render() {

        final boolean hasShaderParamsMapper = mShaderParams != null;

        cam.update();

        TextureArrayMeshBatch activeTextureArrayBatch = null;
        if (metricsBatch instanceof TextureArrayMeshBatch) {
            TextureArrayMeshBatch taBatch = (TextureArrayMeshBatch) metricsBatch;
            if (taBatch.getBundle() != null) {
                activeTextureArrayBatch = taBatch;
            }
        }

        metricsBatch.begin(cam.combined, stats);

        ShaderProgram curShader = null;
        int curShaderIdx = -1;

        int curBlendId = Integer.MIN_VALUE;
        float curPackedColor = Float.NaN;

        float curCutoutThreshold = Float.NaN;

        int lastParamsHash = 0;

        int size = frameQueue.size;

        final boolean hasLayerMeta = layerState.maxLayerIndex() >= 0;

        for (int i = 0; i < size; i++) {
            final int texHandle = frameQueue.textureHandle[i];
            if (texHandle == 0) continue;

            if (activeTextureArrayBatch != null && !activeTextureArrayBatch.hasTextureHandle(texHandle)) {
                continue;
            }

            int layerIdx = frameQueue.layerIndex[i];

            if (hasLayerMeta) {
                if (layerIdx < 0 || layerIdx >= layerState.enabled.length) continue;
                if (layerIdx > layerState.maxLayerIndex() || !layerState.enabled[layerIdx]) continue;
            }

            // Shader switch
            final int shaderIdx = frameQueue.shader[i];

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
            final int blendId = frameQueue.blend[i];

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
            float packedColor = frameQueue.colorPacked[i];

            if (packedColor != curPackedColor) {
                metricsBatch.setPackedColor(packedColor);
                curPackedColor = packedColor;
            }

            // Per-entity uniforms
            if (curShader != null && hasShaderParamsMapper) {
                final int entityId = frameQueue.sourceEntity[i];

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

            byte repeat = frameQueue.repeatFlags[i];
            if ((repeat & RenderRepeatFlags.ANY) == 0) {
                drawNormalEntry(i, texHandle);
            } else {
                drawRepeatedEntry(i, texHandle, repeat);
            }
        }

        metricsBatch.end(stats);
    }

    private void drawNormalEntry(int index, int texHandle) {
        metricsBatch.draw(
                texHandle,
                frameQueue.x1[index], frameQueue.y1[index],
                frameQueue.x2[index], frameQueue.y2[index],
                frameQueue.x3[index], frameQueue.y3[index],
                frameQueue.x4[index], frameQueue.y4[index],
                frameQueue.u1[index], frameQueue.v1[index],
                frameQueue.u2[index], frameQueue.v2[index],
                stats
        );

        stats.drawnQuads++;
    }

    private void drawRepeatedEntry(int index, int texHandle, byte repeat) {
        float x1 = frameQueue.x1[index];
        float y1 = frameQueue.y1[index];
        float x2 = frameQueue.x2[index];
        float y2 = frameQueue.y2[index];
        float x3 = frameQueue.x3[index];
        float y3 = frameQueue.y3[index];
        float x4 = frameQueue.x4[index];
        float y4 = frameQueue.y4[index];

        // V1 repeat is axis-aligned only. Rotated quads can be added here later
        // once repeat ranges are computed from oriented bounds.
        if (!isAxisAligned(x1, y1, x2, y2, x3, y3, x4, y4)) {
            drawNormalEntry(index, texHandle);
            return;
        }

        float baseMinX = min4(x1, x2, x3, x4);
        float baseMaxX = max4(x1, x2, x3, x4);
        float baseMinY = min4(y1, y2, y3, y4);
        float baseMaxY = max4(y1, y2, y3, y4);

        float stepX = baseMaxX - baseMinX;
        float stepY = baseMaxY - baseMinY;

        if (((repeat & RenderRepeatFlags.REPEAT_X) != 0 && stepX <= 0f)
                || ((repeat & RenderRepeatFlags.REPEAT_Y) != 0 && stepY <= 0f)) {
            drawNormalEntry(index, texHandle);
            return;
        }

        float viewportW = cam.viewportWidth * cam.zoom;
        float viewportH = cam.viewportHeight * cam.zoom;
        float viewportMinX = cam.position.x - viewportW * 0.5f;
        float viewportMaxX = cam.position.x + viewportW * 0.5f;
        float viewportMinY = cam.position.y - viewportH * 0.5f;
        float viewportMaxY = cam.position.y + viewportH * 0.5f;

        boolean hasVisibleCopies = RenderRepeatRangeCalculator.calculateVisibleRange(
                viewportMinX,
                viewportMaxX,
                viewportMinY,
                viewportMaxY,
                baseMinX,
                baseMaxX,
                baseMinY,
                baseMaxY,
                repeat,
                MAX_REPEAT_DRAWS_PER_SLOT,
                repeatRange
        );

        if (!hasVisibleCopies) {
            return;
        }

        for (int iy = repeatRange[2]; iy <= repeatRange[3]; iy++) {
            float dy = iy * stepY;

            for (int ix = repeatRange[0]; ix <= repeatRange[1]; ix++) {
                float dx = ix * stepX;

                metricsBatch.draw(
                        texHandle,
                        x1 + dx, y1 + dy,
                        x2 + dx, y2 + dy,
                        x3 + dx, y3 + dy,
                        x4 + dx, y4 + dy,
                        frameQueue.u1[index], frameQueue.v1[index],
                        frameQueue.u2[index], frameQueue.v2[index],
                        stats
                );

                stats.drawnQuads++;
            }
        }
    }

    private static boolean isAxisAligned(
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            float x4, float y4) {
        return nearlyEqual(x1, x2)
                && nearlyEqual(x3, x4)
                && nearlyEqual(y1, y4)
                && nearlyEqual(y2, y3);
    }

    private static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) <= AXIS_EPSILON;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
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

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
