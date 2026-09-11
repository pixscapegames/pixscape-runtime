package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class HudBatchTextureLookupTest {
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGl() {
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
        int[] nextTexture = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> {
                    if ("glGenTexture".equals(method.getName())) return nextTexture[0]++;
                    return defaultValue(method.getReturnType());
                });
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        TextureRegistry.clear();
    }

    @After
    public void restoreGl() {
        TextureRegistry.clear();
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
    }

    @Test
    public void unregisteredTextureFailsWithoutRegisteringIt() {
        Texture texture = texture();
        IntIntMap mapping = new IntIntMap();
        try {
            IllegalStateException failure = Assert.assertThrows(
                    IllegalStateException.class,
                    () -> HudBatch.requireRegisteredTextureLayer(
                            texture, mapping, new RegionResolveCache(4)));

            Assert.assertEquals(
                    "HUD texture is not registered in TextureRegistry.",
                    failure.getMessage());
            Assert.assertEquals(TextureRegistry.INVALID_HANDLE,
                    TextureRegistry.findHandle(texture));
            Assert.assertEquals(0, mapping.size);
            Assert.assertEquals(2, TextureRegistry.handleOf(texture));
        } finally {
            texture.dispose();
        }
    }

    @Test
    public void registeredTextureOutsideBundleFailsWithoutChangingEitherState() {
        Texture texture = texture();
        IntIntMap mapping = new IntIntMap();
        try {
            int handle = TextureRegistry.handleOf(texture);

            IllegalStateException failure = Assert.assertThrows(
                    IllegalStateException.class,
                    () -> HudBatch.requireRegisteredTextureLayer(
                            texture, mapping, new RegionResolveCache(4)));

            Assert.assertEquals(
                    "HUD texture handle " + handle
                            + " is not present in the active HUD TextureArray.",
                    failure.getMessage());
            Assert.assertEquals(handle, TextureRegistry.findHandle(texture));
            Assert.assertEquals(0, mapping.size);
        } finally {
            texture.dispose();
        }
    }

    @Test
    public void registeredBundleTextureResolvesNormally() {
        Texture texture = texture();
        IntIntMap mapping = new IntIntMap();
        try {
            int handle = TextureRegistry.handleOf(texture);
            mapping.put(handle, 3);

            Assert.assertEquals(3, HudBatch.requireRegisteredTextureLayer(
                    texture, mapping, new RegionResolveCache(4)));
        } finally {
            texture.dispose();
        }
    }

    private static Texture texture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            return new Texture(pixmap);
        } finally {
            pixmap.dispose();
        }
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
