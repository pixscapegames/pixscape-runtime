package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

/**
 * Scene2D/HUD {@link Batch} that renders indexed quads through a real
 * {@code sampler2DArray} on the GL30/WebGL2 path.
 *
 * <p>Every texture submitted through the {@link Batch} API must already be a page in the active
 * HUD {@link AtlasRuntimeService.TextureArrayBundle}. Atlas pages are expected to use Pixscape's
 * fixed texture-array layer dimensions, so their normalized UVs can be copied directly. This
 * batch never adds textures to or rebuilds the array while drawing.</p>
 *
 * <p>The supplied default shader, custom shaders, and texture-array bundle are borrowed. This
 * batch owns and disposes only its mesh. Compatible shaders must expose {@code a_position},
 * {@code a_color}, {@code a_texCoord0}, {@code a_layer}, {@code u_projTrans}, and {@code u_array}.</p>
 */
public final class HudBatch implements Batch {
    public static final int DEFAULT_CAPACITY = 1000;
    public static final int MAX_CAPACITY = 8191;

    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int REGION_RESOLVE_CACHE_CAPACITY = 64;

    private final Mesh mesh;
    private final float[] vertices;
    private final int capacity;
    private final HudBatchState state = new HudBatchState();
    private final RegionResolveCache regionResolveCache =
            new RegionResolveCache(REGION_RESOLVE_CACHE_CAPACITY);

    private final ShaderProgram defaultShader;
    private ShaderProgram customShader;
    private int projectionUniformLocation;
    private int arrayUniformLocation;

    private AtlasRuntimeService.TextureArrayBundle bundle;
    private TextureArray textureArray;
    private IntIntMap handleToLayer;

    private int vertexFloatCount;
    private int renderSubmissions;
    private boolean drawing;
    private boolean textureArrayBound;
    private boolean projectionUniformDirty = true;
    private boolean arrayUniformDirty = true;

    public HudBatch(ShaderProgram defaultShader) {
        this(DEFAULT_CAPACITY, defaultShader, null);
    }

    public HudBatch(int capacity, ShaderProgram defaultShader) {
        this(capacity, defaultShader, null);
    }

    /**
     * Creates a HUD batch. The shader and bundle remain owned by their callers.
     *
     * @param capacity maximum number of quads buffered before a flush
     * @param defaultShader default Pixscape texture-array-compatible shader
     * @param bundle active borrowed HUD texture-array bundle, or {@code null} to set it later
     */
    public HudBatch(int capacity, ShaderProgram defaultShader,
                    AtlasRuntimeService.TextureArrayBundle bundle) {
        validateCapacity(capacity);
        requireGl30();
        if (defaultShader == null) throw new IllegalArgumentException("defaultShader is null");
        validateShader(defaultShader);

        this.capacity = capacity;
        this.defaultShader = defaultShader;
        cacheUniformLocations(defaultShader);

        mesh = new Mesh(
                Mesh.VertexDataType.VertexBufferObjectWithVAO,
                false,
                capacity * VERTICES_PER_QUAD,
                capacity * INDICES_PER_QUAD,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.ColorPacked, 4, "a_color"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 1, "a_layer")
        );
        vertices = new float[capacity * HudBatchVertexWriter.HUD_FLOATS_PER_QUAD];

        short[] indices = new short[capacity * INDICES_PER_QUAD];
        int index = 0;
        int vertex = 0;
        for (int quad = 0; quad < capacity; quad++) {
            indices[index++] = (short) vertex;
            indices[index++] = (short) (vertex + 1);
            indices[index++] = (short) (vertex + 2);
            indices[index++] = (short) (vertex + 2);
            indices[index++] = (short) (vertex + 3);
            indices[index++] = (short) vertex;
            vertex += VERTICES_PER_QUAD;
        }
        mesh.setIndices(indices);
        mesh.getIndexData().bind();
        mesh.getIndexData().unbind();

        if (Gdx.graphics != null) {
            state.setProjectionMatrix(new Matrix4().setToOrtho2D(
                    0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        }
        setTextureArrayBundle(bundle);
    }

    static void validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("HudBatch capacity must be greater than zero: " + capacity);
        }
        if (capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "HudBatch capacity cannot exceed " + MAX_CAPACITY + " quads: " + capacity);
        }
    }

    @Override
    public void begin() {
        if (drawing) throw new IllegalStateException("HudBatch.end must be called before begin.");
        requireGl30();
        requireBundle();

        state.syncRealTransformToVirtual();
        drawing = true;
        renderSubmissions = 0;
        textureArrayBound = false;
        projectionUniformDirty = true;
        arrayUniformDirty = true;
        regionResolveCache.clear();

        Gdx.gl.glDepthMask(false);
        bindShaderAndUniforms();
    }

    @Override
    public void end() {
        if (!drawing) throw new IllegalStateException("HudBatch.begin must be called before end.");
        flush();
        drawing = false;
        textureArrayBound = false;
        projectionUniformDirty = true;
        arrayUniformDirty = true;

        Gdx.gl.glDepthMask(true);
        if (state.isBlendingEnabled()) Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void setColor(Color tint) {
        state.setColor(tint);
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        state.setColor(r, g, b, a);
    }

    @Override
    public Color getColor() {
        return state.color();
    }

    @Override
    public void setPackedColor(float packedColor) {
        state.setPackedColor(packedColor);
    }

    @Override
    public float getPackedColor() {
        return state.packedColor();
    }

    @Override
    public void draw(Texture texture, float x, float y, float originX, float originY,
                     float width, float height, float scaleX, float scaleY, float rotation,
                     int srcX, int srcY, int srcWidth, int srcHeight,
                     boolean flipX, boolean flipY) {
        requireDrawing();
        float layer = requireTextureLayer(texture);
        ensureQuadCapacity();

        float worldOriginX = x + originX;
        float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;
        if (scaleX != 1f || scaleY != 1f) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;
        if (rotation != 0f) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float inverseWidth = 1f / texture.getWidth();
        float inverseHeight = 1f / texture.getHeight();
        float u = srcX * inverseWidth;
        float v = (srcY + srcHeight) * inverseHeight;
        float u2 = (srcX + srcWidth) * inverseWidth;
        float v2 = srcY * inverseHeight;
        if (flipX) {
            float swap = u;
            u = u2;
            u2 = swap;
        }
        if (flipY) {
            float swap = v;
            v = v2;
            v2 = swap;
        }
        putQuad(x1, y1, u, v, x2, y2, u, v2,
                x3, y3, u2, v2, x4, y4, u2, v, layer);
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height,
                     int srcX, int srcY, int srcWidth, int srcHeight,
                     boolean flipX, boolean flipY) {
        draw(texture, x, y, 0f, 0f, width, height, 1f, 1f, 0f,
                srcX, srcY, srcWidth, srcHeight, flipX, flipY);
    }

    @Override
    public void draw(Texture texture, float x, float y,
                     int srcX, int srcY, int srcWidth, int srcHeight) {
        draw(texture, x, y, srcWidth, srcHeight,
                srcX, srcY, srcWidth, srcHeight, false, false);
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height,
                     float u, float v, float u2, float v2) {
        requireDrawing();
        float layer = requireTextureLayer(texture);
        ensureQuadCapacity();
        float x2 = x + width;
        float y2 = y + height;
        putQuad(x, y, u, v, x, y2, u, v2,
                x2, y2, u2, v2, x2, y, u2, v, layer);
    }

    @Override
    public void draw(Texture texture, float x, float y) {
        if (texture == null) throw new IllegalArgumentException("texture is null");
        draw(texture, x, y, texture.getWidth(), texture.getHeight());
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height) {
        draw(texture, x, y, width, height, 0f, 1f, 1f, 0f);
    }

    @Override
    public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
        requireDrawing();
        validateSpriteVertices(spriteVertices, offset, count);
        float layer = requireTextureLayer(texture);

        int remainingSourceFloats = count;
        int sourceOffset = offset;
        while (remainingSourceFloats > 0) {
            int availableQuads = (vertices.length - vertexFloatCount)
                    / HudBatchVertexWriter.HUD_FLOATS_PER_QUAD;
            if (availableQuads == 0) {
                flush();
                availableQuads = capacity;
            }
            int requestedQuads = remainingSourceFloats / HudBatchVertexWriter.LIBGDX_FLOATS_PER_QUAD;
            int copiedQuads = Math.min(availableQuads, requestedQuads);
            int sourceFloatCount = copiedQuads * HudBatchVertexWriter.LIBGDX_FLOATS_PER_QUAD;
            vertexFloatCount += HudBatchVertexWriter.copyWithLayer(
                    spriteVertices, sourceOffset, sourceFloatCount,
                    vertices, vertexFloatCount, layer,
                    state.vertexAdjustmentNeeded() ? state.vertexAdjustment() : null);
            sourceOffset += sourceFloatCount;
            remainingSourceFloats -= sourceFloatCount;
        }
    }

    @Override
    public void draw(TextureRegion region, float x, float y) {
        if (region == null) throw new IllegalArgumentException("region is null");
        draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        requireRegion(region);
        requireDrawing();
        float layer = requireTextureLayer(region.getTexture());
        ensureQuadCapacity();
        float x2 = x + width;
        float y2 = y + height;
        putQuad(x, y, region.getU(), region.getV2(),
                x, y2, region.getU(), region.getV(),
                x2, y2, region.getU2(), region.getV(),
                x2, y, region.getU2(), region.getV2(), layer);
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY,
                     float width, float height, float scaleX, float scaleY, float rotation) {
        requireRegion(region);
        requireDrawing();
        float layer = requireTextureLayer(region.getTexture());
        ensureQuadCapacity();
        putTransformedRegion(x, y, originX, originY, width, height,
                scaleX, scaleY, rotation,
                region.getU(), region.getV2(),
                region.getU(), region.getV(),
                region.getU2(), region.getV(),
                region.getU2(), region.getV2(), layer);
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float originX, float originY,
                     float width, float height, float scaleX, float scaleY, float rotation,
                     boolean clockwise) {
        requireRegion(region);
        requireDrawing();
        float layer = requireTextureLayer(region.getTexture());
        ensureQuadCapacity();

        float u1;
        float v1;
        float u2;
        float v2;
        float u3;
        float v3;
        float u4;
        float v4;
        if (clockwise) {
            u1 = region.getU2();
            v1 = region.getV2();
            u2 = region.getU();
            v2 = region.getV2();
            u3 = region.getU();
            v3 = region.getV();
            u4 = region.getU2();
            v4 = region.getV();
        } else {
            u1 = region.getU();
            v1 = region.getV();
            u2 = region.getU2();
            v2 = region.getV();
            u3 = region.getU2();
            v3 = region.getV2();
            u4 = region.getU();
            v4 = region.getV2();
        }
        putTransformedRegion(x, y, originX, originY, width, height,
                scaleX, scaleY, rotation,
                u1, v1, u2, v2, u3, v3, u4, v4, layer);
    }

    @Override
    public void draw(TextureRegion region, float width, float height, Affine2 transform) {
        requireRegion(region);
        if (transform == null) throw new IllegalArgumentException("transform is null");
        requireDrawing();
        float layer = requireTextureLayer(region.getTexture());
        ensureQuadCapacity();

        float x1 = transform.m02;
        float y1 = transform.m12;
        float x2 = transform.m01 * height + transform.m02;
        float y2 = transform.m11 * height + transform.m12;
        float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
        float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
        float x4 = transform.m00 * width + transform.m02;
        float y4 = transform.m10 * width + transform.m12;

        putQuad(x1, y1, region.getU(), region.getV2(),
                x2, y2, region.getU(), region.getV(),
                x3, y3, region.getU2(), region.getV(),
                x4, y4, region.getU2(), region.getV2(), layer);
    }

    @Override
    public void flush() {
        if (vertexFloatCount == 0) return;
        requireBundle();

        bindShaderAndUniforms();
        if (!textureArrayBound) {
            textureArray.bind(0);
            textureArrayBound = true;
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        }

        if (state.isBlendingEnabled()) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            if (state.blendSrcFunc() != -1) {
                Gdx.gl.glBlendFuncSeparate(
                        state.blendSrcFunc(), state.blendDstFunc(),
                        state.blendSrcFuncAlpha(), state.blendDstFuncAlpha());
            }
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        mesh.setVertices(vertices, 0, vertexFloatCount);
        int quadCount = vertexFloatCount / HudBatchVertexWriter.HUD_FLOATS_PER_QUAD;
        mesh.render(activeShader(), GL20.GL_TRIANGLES, 0, quadCount * INDICES_PER_QUAD);
        renderSubmissions++;
        vertexFloatCount = 0;
    }

    @Override
    public void disableBlending() {
        if (!state.isBlendingEnabled()) return;
        if (drawing) flush();
        state.setBlendingEnabled(false);
    }

    @Override
    public void enableBlending() {
        if (state.isBlendingEnabled()) return;
        if (drawing) flush();
        state.setBlendingEnabled(true);
    }

    @Override
    public void setBlendFunction(int srcFunc, int dstFunc) {
        setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    @Override
    public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor,
                                         int srcFuncAlpha, int dstFuncAlpha) {
        if (state.blendSrcFunc() == srcFuncColor && state.blendDstFunc() == dstFuncColor
                && state.blendSrcFuncAlpha() == srcFuncAlpha
                && state.blendDstFuncAlpha() == dstFuncAlpha) {
            return;
        }
        if (drawing) flush();
        state.setBlendFunctionSeparate(srcFuncColor, dstFuncColor, srcFuncAlpha, dstFuncAlpha);
    }

    @Override
    public int getBlendSrcFunc() {
        return state.blendSrcFunc();
    }

    @Override
    public int getBlendDstFunc() {
        return state.blendDstFunc();
    }

    @Override
    public int getBlendSrcFuncAlpha() {
        return state.blendSrcFuncAlpha();
    }

    @Override
    public int getBlendDstFuncAlpha() {
        return state.blendDstFuncAlpha();
    }

    @Override
    public Matrix4 getProjectionMatrix() {
        return state.projectionMatrix();
    }

    @Override
    public Matrix4 getTransformMatrix() {
        return state.transformMatrix();
    }

    @Override
    public void setProjectionMatrix(Matrix4 projection) {
        if (drawing) flush();
        state.setProjectionMatrix(projection);
        projectionUniformDirty = true;
        if (drawing) bindShaderAndUniforms();
    }

    @Override
    public void setTransformMatrix(Matrix4 transform) {
        state.setTransformMatrix(transform, drawing);
    }

    @Override
    public void setShader(ShaderProgram shader) {
        ShaderProgram nextShader = shader != null ? shader : defaultShader;
        if (nextShader == activeShader()) return;
        validateShader(nextShader);
        if (drawing) flush();

        customShader = shader;
        cacheUniformLocations(nextShader);
        projectionUniformDirty = true;
        arrayUniformDirty = true;
        if (drawing) bindShaderAndUniforms();
    }

    @Override
    public ShaderProgram getShader() {
        return activeShader();
    }

    @Override
    public boolean isBlendingEnabled() {
        return state.isBlendingEnabled();
    }

    @Override
    public boolean isDrawing() {
        return drawing;
    }

    /**
     * Replaces the borrowed HUD texture array and handle-to-layer mapping. Changing it while
     * drawing flushes geometry that belongs to the previous bundle.
     */
    public void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        if (bundle == this.bundle) return;
        if (bundle != null && (bundle.textureArray == null || bundle.handle2layer == null)) {
            throw new IllegalArgumentException(
                    "HUD texture-array bundle must contain a TextureArray and handle-to-layer mapping.");
        }
        if (drawing) flush();

        this.bundle = bundle;
        textureArray = bundle != null ? bundle.textureArray : null;
        handleToLayer = bundle != null ? bundle.handle2layer : null;
        regionResolveCache.clear();
        textureArrayBound = false;
        arrayUniformDirty = true;
        if (drawing && bundle != null) bindShaderAndUniforms();
    }

    public AtlasRuntimeService.TextureArrayBundle getTextureArrayBundle() {
        return bundle;
    }

    int renderSubmissionsForTest() {
        return renderSubmissions;
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    private void ensureQuadCapacity() {
        if (vertexFloatCount == vertices.length) flush();
    }

    private void putQuad(float x1, float y1, float u1, float v1,
                         float x2, float y2, float u2, float v2,
                         float x3, float y3, float u3, float v3,
                         float x4, float y4, float u4, float v4,
                         float layer) {
        float color = state.packedColor();
        int index = vertexFloatCount;
        index = putVertex(index, x1, y1, color, u1, v1, layer);
        index = putVertex(index, x2, y2, color, u2, v2, layer);
        index = putVertex(index, x3, y3, color, u3, v3, layer);
        vertexFloatCount = putVertex(index, x4, y4, color, u4, v4, layer);
    }

    private void putTransformedRegion(float x, float y, float originX, float originY,
                                      float width, float height, float scaleX, float scaleY,
                                      float rotation,
                                      float u1, float v1, float u2, float v2,
                                      float u3, float v3, float u4, float v4,
                                      float layer) {
        float worldOriginX = x + originX;
        float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;
        if (scaleX != 1f || scaleY != 1f) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;
        if (rotation != 0f) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        putQuad(x1, y1, u1, v1, x2, y2, u2, v2,
                x3, y3, u3, v3, x4, y4, u4, v4, layer);
    }

    private int putVertex(int index, float x, float y, float color,
                          float u, float v, float layer) {
        if (state.vertexAdjustmentNeeded()) {
            Affine2 adjustment = state.vertexAdjustment();
            vertices[index++] = adjustment.m00 * x + adjustment.m01 * y + adjustment.m02;
            vertices[index++] = adjustment.m10 * x + adjustment.m11 * y + adjustment.m12;
        } else {
            vertices[index++] = x;
            vertices[index++] = y;
        }
        vertices[index++] = color;
        vertices[index++] = u;
        vertices[index++] = v;
        vertices[index++] = layer;
        return index;
    }

    private float requireTextureLayer(Texture texture) {
        if (texture == null) throw new IllegalArgumentException("texture is null");
        requireBundle();
        int handle = TextureRegistry.handleOf(texture);
        int layer = regionResolveCache.resolveLayer(handle, handleToLayer);
        if (layer < 0) {
            throw new IllegalStateException(
                    "Texture handle " + handle
                            + " is not registered in the active HUD texture array.");
        }
        return layer;
    }

    private void bindShaderAndUniforms() {
        ShaderProgram shader = activeShader();
        shader.bind();
        if (projectionUniformDirty) {
            shader.setUniformMatrix(projectionUniformLocation, state.combinedMatrix());
            projectionUniformDirty = false;
        }
        if (arrayUniformDirty) {
            shader.setUniformi(arrayUniformLocation, 0);
            arrayUniformDirty = false;
        }
    }

    private ShaderProgram activeShader() {
        return customShader != null ? customShader : defaultShader;
    }

    private void cacheUniformLocations(ShaderProgram shader) {
        projectionUniformLocation = shader.getUniformLocation("u_projTrans");
        arrayUniformLocation = shader.getUniformLocation("u_array");
    }

    private static void validateShader(ShaderProgram shader) {
        if (!shader.isCompiled()) {
            throw new IllegalArgumentException(
                    "HUD texture-array shader is not compiled: " + shader.getLog());
        }
        requireAttribute(shader, "a_position");
        requireAttribute(shader, "a_color");
        requireAttribute(shader, "a_texCoord0");
        requireAttribute(shader, "a_layer");
        requireUniform(shader, "u_projTrans");
        requireUniform(shader, "u_array");
    }

    private static void requireAttribute(ShaderProgram shader, String name) {
        if (!shader.hasAttribute(name)) {
            throw new IllegalArgumentException(
                    "HUD texture-array shader is missing required attribute '" + name + "'.");
        }
    }

    private static void requireUniform(ShaderProgram shader, String name) {
        if (!shader.hasUniform(name)) {
            throw new IllegalArgumentException(
                    "HUD texture-array shader is missing required uniform '" + name + "'.");
        }
    }

    static void validateSpriteVertices(float[] spriteVertices, int offset, int count) {
        if (spriteVertices == null) throw new IllegalArgumentException("spriteVertices is null");
        if (offset < 0 || count < 0 || offset > spriteVertices.length - count) {
            throw new IndexOutOfBoundsException(
                    "Invalid spriteVertices range: offset=" + offset + ", count=" + count
                            + ", length=" + spriteVertices.length);
        }
        if (count % HudBatchVertexWriter.LIBGDX_FLOATS_PER_QUAD != 0) {
            throw new IllegalArgumentException(
                    "spriteVertices count must contain complete 20-float LibGDX quads: " + count);
        }
    }

    private void requireDrawing() {
        if (!drawing) throw new IllegalStateException("HudBatch.begin must be called before draw.");
    }

    private void requireBundle() {
        if (textureArray == null || handleToLayer == null) {
            throw new IllegalStateException("HudBatch has no active HUD texture-array bundle.");
        }
    }

    private static void requireRegion(TextureRegion region) {
        if (region == null) throw new IllegalArgumentException("region is null");
    }

    private static void requireGl30() {
        if (Gdx.gl30 == null) {
            throw new IllegalStateException(
                    "HudBatch requires GL30, OpenGL ES 3, or WebGL2 texture-array support.");
        }
    }
}

