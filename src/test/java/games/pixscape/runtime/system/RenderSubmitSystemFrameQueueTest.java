package games.pixscape.runtime.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class RenderSubmitSystemFrameQueueTest {
    private GL20 previousGl;

    @Before
    public void installGlProxy() {
        previousGl = Gdx.gl;
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    @After
    public void restoreGlProxy() {
        Gdx.gl = previousGl;
    }

    @Test
    public void submitDrawsFromFrameQueueOnly() {
        LayerStateSOA layerState = new LayerStateSOA(1);
        FrameRenderQueue queue = new FrameRenderQueue(1);
        RenderStats stats = new RenderStats();
        CapturingBatch batch = new CapturingBatch();

        queue.addQuad(
                42,
                -1,
                BlendMode.OPAQUE.id,
                0,
                0,
                0,
                123L,
                1f,
                2f,
                3f,
                4f,
                5f,
                6f,
                7f,
                8f,
                0.1f,
                0.2f,
                0.3f,
                0.4f,
                0.75f,
                RenderRepeatFlags.NONE,
                FrameRenderQueue.SOURCE_ECS,
                2,
                2
        );

        RenderSubmitSystem submit = new RenderSubmitSystem(
                layerState,
                queue,
                new TestCamera(),
                1f,
                1f,
                1f,
                batch,
                stats,
                new RenderStatsSink(1f)
        );

        submit.render();

        Assert.assertEquals(1, batch.drawCalls);
        Assert.assertEquals(42, batch.textureHandle);
        Assert.assertEquals(1f, batch.x1, 0f);
        Assert.assertEquals(2f, batch.y1, 0f);
        Assert.assertEquals(7f, batch.x4, 0f);
        Assert.assertEquals(8f, batch.y4, 0f);
        Assert.assertEquals(0.1f, batch.u1, 0f);
        Assert.assertEquals(0.4f, batch.v2, 0f);
        Assert.assertEquals(0.75f, batch.packedColor, 0f);
        Assert.assertEquals(1, stats.drawnQuads);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Double.TYPE) return 0d;
        if (returnType == Character.TYPE) return (char) 0;
        return null;
    }

    private static final class TestCamera extends OrthographicCamera {
        TestCamera() {
            viewportWidth = 100f;
            viewportHeight = 100f;
            zoom = 1f;
        }

        public void update() {
        }

        public void update(boolean updateFrustum) {
        }
    }

    private static final class CapturingBatch implements MetricsBatch {
        int drawCalls;
        int textureHandle;
        float x1;
        float y1;
        float x4;
        float y4;
        float u1;
        float v2;
        float packedColor;

        public void begin(Matrix4 combined, RenderStats stats) {
        }

        public void setShader(ShaderProgram shader, RenderStats stats) {
        }

        public void setBlendMode(boolean enabled, int sfactor, int dfactor, RenderStats stats) {
        }

        public void setColor(float r, float g, float b, float a) {
        }

        public void setPackedColor(float packed) {
            packedColor = packed;
        }

        public void draw(int textureHandle,
                         float x1,
                         float y1,
                         float x2,
                         float y2,
                         float x3,
                         float y3,
                         float x4,
                         float y4,
                         float u,
                         float v,
                         float u2,
                         float v2,
                         RenderStats stats) {
            drawCalls++;
            this.textureHandle = textureHandle;
            this.x1 = x1;
            this.y1 = y1;
            this.x4 = x4;
            this.y4 = y4;
            this.u1 = u;
            this.v2 = v2;
        }

        public void flush(RenderStats stats) {
        }

        public void end(RenderStats stats) {
        }

        public void close() {
        }
    }
}
