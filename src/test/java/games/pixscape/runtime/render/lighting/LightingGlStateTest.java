package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LightingGlStateTest {
    @Test public void preservesScissorAndBothDepthWriteStates() {
        GL20 previous = Gdx.gl;
        final int[] depth = {1};
        try {
            Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
                    (proxy, method, args) -> {
                        assertEquals("glGetIntegerv", method.getName());
                        int name = (Integer) args[0];
                        IntBuffer result = (IntBuffer) args[1];
                        if (name == GL20.GL_SCISSOR_BOX) {
                            result.put(0, 7); result.put(1, 11); result.put(2, 320); result.put(3, 180);
                        } else {
                            assertEquals(GL20.GL_DEPTH_WRITEMASK, name);
                            result.put(0, depth[0]);
                        }
                        return null;
                    });
            IntBuffer scratch = IntBuffer.allocate(4);
            int[] bounds = new int[4];
            LightingGlState.readScissor(scratch, bounds);
            assertArrayEquals(new int[]{7, 11, 320, 180}, bounds);
            assertEquals(1, LightingGlState.readDepthWriteMask(scratch));
            depth[0] = 0;
            assertEquals(0, LightingGlState.readDepthWriteMask(scratch));
        } finally { Gdx.gl = previous; }
    }
}
