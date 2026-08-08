package games.pixscape.runtime.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class OneShotPixmapTextureArrayDataTest {
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

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
    public void ownedPixmapsAreReleasedAndDisposedAfterSuccessfulConsume() {
        installGl(false);
        CountingPixmap first = new CountingPixmap(3, 2);
        CountingPixmap second = new CountingPixmap(3, 2);
        OneShotPixmapTextureArrayData data = data(true, first, second);

        Assert.assertEquals(2, data.retainedPixmapCount());
        data.prepare();
        data.consumeTextureArrayData();

        Assert.assertEquals(0, data.retainedPixmapCount());
        Assert.assertEquals(1, first.disposeCalls);
        Assert.assertEquals(1, second.disposeCalls);
        assertMetadata(data, 3, 2, 2);
    }

    @Test
    public void ownedPixmapsAreReleasedAndDisposedAfterFailedConsume() {
        installGl(true);
        CountingPixmap first = new CountingPixmap(2, 2);
        CountingPixmap second = new CountingPixmap(2, 2);
        OneShotPixmapTextureArrayData data = data(true, first, second);
        data.prepare();

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class,
                data::consumeTextureArrayData
        );

        Assert.assertEquals("upload failed", failure.getMessage());
        Assert.assertEquals(0, data.retainedPixmapCount());
        Assert.assertEquals(1, first.disposeCalls);
        Assert.assertEquals(1, second.disposeCalls);
        assertMetadata(data, 2, 2, 2);
    }

    @Test
    public void borrowedPixmapsAreReleasedButRemainUsableAfterSuccessfulConsume() {
        installGl(false);
        CountingPixmap first = new CountingPixmap(2, 2);
        CountingPixmap second = new CountingPixmap(2, 2);
        first.setColor(0xff0000ff);
        first.fill();
        OneShotPixmapTextureArrayData data = data(false, first, second);
        data.prepare();

        data.consumeTextureArrayData();

        Assert.assertEquals(0, data.retainedPixmapCount());
        Assert.assertEquals(0, first.disposeCalls);
        Assert.assertEquals(0, second.disposeCalls);
        Assert.assertEquals(0xff0000ff, first.getPixel(0, 0));
        first.dispose();
        second.dispose();
        Assert.assertEquals(1, first.disposeCalls);
        Assert.assertEquals(1, second.disposeCalls);
    }

    @Test
    public void borrowedPixmapsAreReleasedAndRemainCallerOwnedAfterFailedConsume() {
        installGl(true);
        CountingPixmap first = new CountingPixmap(2, 2);
        CountingPixmap second = new CountingPixmap(2, 2);
        OneShotPixmapTextureArrayData data = data(false, first, second);
        data.prepare();

        Assert.assertThrows(IllegalStateException.class, data::consumeTextureArrayData);

        Assert.assertEquals(0, data.retainedPixmapCount());
        Assert.assertEquals(0, first.disposeCalls);
        Assert.assertEquals(0, second.disposeCalls);
        Assert.assertFalse(first.isDisposed());
        Assert.assertFalse(second.isDisposed());
        first.dispose();
        second.dispose();
    }

    private static OneShotPixmapTextureArrayData data(boolean owned, Pixmap... pixmaps) {
        return new OneShotPixmapTextureArrayData(
                new Array<>(pixmaps),
                Pixmap.Format.RGBA8888,
                false,
                owned
        );
    }

    private static void assertMetadata(OneShotPixmapTextureArrayData data,
                                       int width,
                                       int height,
                                       int depth) {
        Assert.assertEquals(width, data.getWidth());
        Assert.assertEquals(height, data.getHeight());
        Assert.assertEquals(depth, data.getDepth());
        Assert.assertEquals(Pixmap.Format.toGlFormat(Pixmap.Format.RGBA8888), data.getInternalFormat());
        Assert.assertEquals(Pixmap.Format.toGlType(Pixmap.Format.RGBA8888), data.getGLType());
        Assert.assertFalse(data.isManaged());
    }

    private static void installGl(boolean failUpload) {
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> {
                    if (failUpload && "glTexSubImage3D".equals(method.getName())) {
                        throw new IllegalStateException("upload failed");
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
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

    private static final class CountingPixmap extends Pixmap {
        int disposeCalls;

        CountingPixmap(int width, int height) {
            super(width, height, Format.RGBA8888);
        }

        @Override
        public void dispose() {
            disposeCalls++;
            super.dispose();
        }
    }
}
