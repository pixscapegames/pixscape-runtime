package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

public class GLCapsTest {
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;

    @Before
    public void rememberGl() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
    }

    @After
    public void restoreGl() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
    }

    @Test
    public void detectionRetainsMaximumArrayTextureLayers() {
        GL30 gl = glWithLimits(16, 4096, 256);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;

        GLCaps caps = GLCaps.detect();

        Assert.assertTrue(caps.supportsTextureArray());
        Assert.assertEquals(16, caps.maxTextureUnits);
        Assert.assertEquals(4096, caps.maxTextureSize);
        Assert.assertEquals(256, caps.maxArrayTextureLayers);
    }

    @Test
    public void sevenPagesAndWhiteFitAnEightLayerLimit() {
        GLCaps caps = new GLCaps(true, 8, 2048, 8);

        caps.validateTextureArray(2048, 2048, 8);
    }

    @Test
    public void eightPagesAndWhiteExceedAnEightLayerLimit() {
        GLCaps caps = new GLCaps(true, 8, 2048, 8);

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> caps.validateTextureArray(2048, 2048, 9));

        Assert.assertEquals(
                "TextureArray requires 9 layers but this device supports 8.",
                failure.getMessage());
    }

    @Test
    public void dimensionsAreCheckedAgainstMaximumTextureSize() {
        GLCaps caps = new GLCaps(true, 8, 1024, 16);

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                () -> caps.validateTextureArray(2048, 1024, 2));

        Assert.assertEquals(
                "TextureArray dimensions 2048x1024 exceed GL_MAX_TEXTURE_SIZE 1024.",
                failure.getMessage());
    }

    private static GL30 glWithLimits(int units, int textureSize, int arrayLayers) {
        return (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> {
                    if ("glGetIntegerv".equals(method.getName())) {
                        int parameter = (Integer) args[0];
                        IntBuffer values = (IntBuffer) args[1];
                        if (parameter == GL20.GL_MAX_TEXTURE_IMAGE_UNITS) values.put(0, units);
                        if (parameter == GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) values.put(0, units);
                        if (parameter == GL20.GL_MAX_TEXTURE_SIZE) values.put(0, textureSize);
                        if (parameter == GL30.GL_MAX_ARRAY_TEXTURE_LAYERS) values.put(0, arrayLayers);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
