package games.pixscape.runtime.service;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.render.ShaderVariant;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ShaderRegistryPlatformTargetTest {
    private Application previousApp;
    private GL20 previousGl;
    private GL30 previousGl30;

    @Before
    public void rememberGdxState() {
        previousApp = Gdx.app;
        previousGl = Gdx.gl;
        previousGl30 = Gdx.gl30;
        ShaderRegistry.disposeAll();
    }

    @After
    public void restoreGdxState() {
        ShaderRegistry.disposeAll();
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl30 = previousGl30;
    }

    @Test
    public void desktopTargetResolvesDesktopVariant() {
        initializeUntilShaderLoading(PlatformTarget.DESKTOP_GL30);

        Assert.assertEquals(PlatformTarget.DESKTOP_GL30, ShaderRegistry.getResolvedPlatformTarget());
        Assert.assertEquals(ShaderVariant.DESKTOP_GL30, ShaderRegistry.getCurrentShaderVariant());
    }

    @Test
    public void androidTargetResolvesEs3Variant() {
        initializeUntilShaderLoading(PlatformTarget.ANDROID_ES3);

        Assert.assertEquals(PlatformTarget.ANDROID_ES3, ShaderRegistry.getResolvedPlatformTarget());
        Assert.assertEquals(ShaderVariant.ES3_WEBGL2, ShaderRegistry.getCurrentShaderVariant());
    }

    @Test
    public void htmlTargetResolvesEs3Variant() {
        initializeUntilShaderLoading(PlatformTarget.HTML_WEBGL2);

        Assert.assertEquals(PlatformTarget.HTML_WEBGL2, ShaderRegistry.getResolvedPlatformTarget());
        Assert.assertEquals(ShaderVariant.ES3_WEBGL2, ShaderRegistry.getCurrentShaderVariant());
    }

    @Test
    public void autoStillDetectsWebGl() {
        Gdx.app = application(Application.ApplicationType.WebGL);
        initializeUntilShaderLoading(PlatformTarget.AUTO);

        Assert.assertEquals(PlatformTarget.HTML_WEBGL2, ShaderRegistry.getResolvedPlatformTarget());
        Assert.assertEquals(ShaderVariant.ES3_WEBGL2, ShaderRegistry.getCurrentShaderVariant());
    }

    private static void initializeUntilShaderLoading(PlatformTarget target) {
        GL30 gl = gl30();
        Gdx.gl = gl;
        Gdx.gl30 = gl;

        try {
            ShaderRegistry.initDefaults(target, null, null);
            Assert.fail("Expected missing headless shader files to stop initialization");
        } catch (RuntimeException expected) {
            // Target and variant resolution happen before shader files are loaded.
        }
    }

    private static Application application(final Application.ApplicationType type) {
        return (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                new DefaultInvocationHandler() {
                    @Override
                    Object value(Method method) {
                        if ("getType".equals(method.getName())) return type;
                        return super.value(method);
                    }
                }
        );
    }

    private static GL30 gl30() {
        return (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                new DefaultInvocationHandler()
        );
    }

    private static class DefaultInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return value(method);
        }

        Object value(Method method) {
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) return null;
            if (returnType == Boolean.TYPE) return false;
            if (returnType == Integer.TYPE) return 0;
            if (returnType == Long.TYPE) return 0L;
            if (returnType == Float.TYPE) return 0f;
            if (returnType == Double.TYPE) return 0d;
            return null;
        }
    }
}
