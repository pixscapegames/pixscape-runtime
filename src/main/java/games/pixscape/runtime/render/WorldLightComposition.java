package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

/** Reusable world targets. Prepare at the frame boundary, never during queue submission. */
public final class WorldLightComposition implements Disposable {
    private final IntBuffer integers = BufferUtils.newIntBuffer(4);
    private final FloatBuffer zeroColor = BufferUtils.newFloatBuffer(4);
    private final int[] viewport = new int[4];
    private FrameBuffer original, field;
    private ShaderProgram shader;
    private Mesh quad;
    private int framebuffer, width, height;
    private boolean scissor, active;

    public void prepare() {
        if (Gdx.gl30 == null) throw new IllegalStateException("World light composition requires GL3 / GLES3 / WebGL2 and blendable RGBA16F");
        integers.clear(); Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, integers);
        if (shader != null && original != null && width == Math.max(1, integers.get(2))
                && height == Math.max(1, integers.get(3))) return;
        saveTarget();
        try {
            if (shader == null) {
                // On WebGL this also enables the color attachment extension before FBO creation.
                Gdx.graphics.supportsExtension("EXT_color_buffer_float");
                Gdx.graphics.supportsExtension("EXT_color_buffer_half_float");
                String version = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop
                        ? "#version 330 core\n" : "#version 300 es\nprecision highp float;\nprecision highp sampler2D;\n";
                shader = new ShaderProgram(version + "in vec2 a_position; in vec2 a_texCoord0; out vec2 uv; void main(){uv=a_texCoord0;gl_Position=vec4(a_position,0.,1.);}",
                        version + "in vec2 uv; uniform sampler2D u_original; uniform sampler2D u_field; uniform vec3 u_ambient; uniform int u_probe; uniform vec4 u_probeColor; out vec4 fragColor; void main(){if(u_probe==1){fragColor=u_probeColor;return;} if(u_probe==2){fragColor=texture(u_field,uv)*.5;return;} vec4 c=texture(u_original,uv);fragColor=vec4(c.rgb*(u_ambient+texture(u_field,uv).rgb),c.a);}");
                if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
                quad = new Mesh(true, 4, 6, VertexAttribute.Position(), VertexAttribute.TexCoords(0));
                // Position() has three components.
                quad.setVertices(new float[]{-1,-1,0,0,0, -1,1,0,0,1, 1,1,0,1,1, 1,-1,0,1,0});
                quad.setIndices(new short[]{0,1,2,2,3,0});
                allocate(1, 1);
                verifyFloatBlending();
            }
            int w = Math.max(1, viewport[2]), h = Math.max(1, viewport[3]);
            if (w != width || h != height) allocate(w, h);
        } catch (RuntimeException failure) {
            dispose();
            throw failure;
        } finally { restoreTarget(); }
    }

    private void allocate(int w, int h) {
        FrameBuffer nextOriginal = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        FrameBuffer nextField = null;
        try {
            GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(w, h);
            builder.addFloatAttachment(GL30.GL_RGBA16F, GL20.GL_RGBA, GL20.GL_FLOAT, true);
            nextField = builder.build();
            nextOriginal.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            nextField.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        } catch (RuntimeException failure) {
            nextOriginal.dispose();
            if (nextField != null) nextField.dispose();
            throw new IllegalStateException("RGBA16F world lighting is unavailable; no lower precision fallback", failure);
        }
        if (original != null) original.dispose();
        if (field != null) field.dispose();
        original = nextOriginal; field = nextField; width = w; height = h;
    }

    /** Cold numerical check: unclamped accumulation, alpha masking, then RGBA8 readback. */
    private void verifyFloatBlending() {
        bindAndClear(field);
        shader.bind(); shader.setUniformi("u_probe", 1);
        shader.setUniformi("u_original", 0); shader.setUniformi("u_field", 1);
        // WebGL rejects attachment feedback even when a dynamic shader branch does not sample it.
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0); Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1); Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        shader.setUniformf("u_probeColor", .75f, .5f, .25f, .5f);
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        draw(); draw();
        Gdx.gl.glBlendFunc(GL20.GL_ZERO, GL20.GL_ONE_MINUS_SRC_ALPHA); draw();
        bindAndClear(original);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        shader.bind(); shader.setUniformi("u_probe", 2); shader.setUniformi("u_field", 1);
        field.getColorBufferTexture().bind(1); draw();
        ByteBuffer pixel = BufferUtils.newByteBuffer(4);
        Gdx.gl.glReadPixels(0, 0, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
        if (Math.abs((pixel.get(0) & 255) - 96) > 2 || Math.abs((pixel.get(1) & 255) - 64) > 2
                || Math.abs((pixel.get(2) & 255) - 32) > 2 || Gdx.gl.glGetError() != GL20.GL_NO_ERROR) {
            throw new IllegalStateException("RGBA16F render / additive blend / alpha mask verification failed; no fallback");
        }
        shader.bind(); shader.setUniformi("u_probe", 0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    public void beginOriginal() {
        if (original == null) throw new IllegalStateException("prepare() must run before world submission");
        saveTarget(); active = true; bindAndClear(original);
    }
    public void beginField() { bindAndClear(field); }

    public void compose(float r, float g, float b, RenderStats stats) {
        restoreTarget();
        active = false;
        original.getColorBufferTexture().bind(0); field.getColorBufferTexture().bind(1);
        shader.bind(); shader.setUniformi("u_original", 0); shader.setUniformi("u_field", 1);
        shader.setUniformi("u_probe", 0); shader.setUniformf("u_ambient", r, g, b);
        // The original buffer contains premultiplied RGB and independent world coverage.
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        draw(); Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        if (stats != null) {
            stats.drawCalls++; stats.textureBinds += 2; stats.shaderBinds++;
            stats.framebufferBinds += 3; stats.framebufferSwitches += 3;
        }
    }

    private void draw() { quad.render(shader, GL20.GL_TRIANGLES, 0, 6); }
    private void bindAndClear(FrameBuffer buffer) {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, buffer.getFramebufferHandle());
        Gdx.gl.glViewport(0, 0, width, height); Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        // GL3/GLES3/WebGL2 attachment clear leaves the caller's clear color untouched.
        // LibGDX's WebGL backend cannot query that color with glGetFloatv.
        zeroColor.clear(); Gdx.gl30.glClearBufferfv(GL30.GL_COLOR, 0, zeroColor);
    }
    private void saveTarget() {
        integers.clear(); Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, integers); framebuffer = integers.get(0);
        integers.clear(); Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, integers);
        for (int i = 0; i < 4; i++) viewport[i] = integers.get(i);
        scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
    }
    private void restoreTarget() {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
        Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (scissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST); else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }
    public void end() { if (active) { restoreTarget(); active = false; } }
    public long textureBytes() { return (long) width * height * 12; }
    public void dispose() {
        end(); if (original != null) original.dispose(); if (field != null) field.dispose();
        if (quad != null) quad.dispose(); if (shader != null) shader.dispose();
        original = field = null; quad = null; shader = null; width = height = 0;
    }
}
