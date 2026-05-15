package games.pixscape.runtime.engine;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PixscapeEngineLogLevelTest {

    private Application previousApp;

    @Before
    public void rememberGdxApp() {
        previousApp = Gdx.app;
    }

    @After
    public void restoreGdxApp() {
        Gdx.app = previousApp;
    }

    @Test
    public void defaultLogLevelIsInfo() {
        PixscapeEngine engine = new PixscapeEngine();

        Assert.assertEquals(Application.LOG_INFO, engine.getLogLevel());
    }

    @Test
    public void setLogLevelBeforeGdxAppStoresConfiguredLevel() {
        Gdx.app = null;
        PixscapeEngine engine = new PixscapeEngine();

        engine.setLogLevel(Application.LOG_DEBUG);

        Assert.assertEquals(Application.LOG_DEBUG, engine.getLogLevel());
    }

    @Test
    public void setLogLevelWithGdxAppAppliesImmediately() {
        TrackingApplication app = new TrackingApplication(Application.LOG_INFO);
        Gdx.app = app.proxy();
        PixscapeEngine engine = new PixscapeEngine();

        engine.setLogLevel(Application.LOG_ERROR);

        Assert.assertEquals(Application.LOG_ERROR, engine.getLogLevel());
        Assert.assertEquals(Application.LOG_ERROR, app.logLevel);
    }

    @Test
    public void setLogLevelRejectsUnknownLevel() {
        PixscapeEngine engine = new PixscapeEngine();

        try {
            engine.setLogLevel(12345);
            Assert.fail("Expected invalid log level to fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static final class TrackingApplication implements InvocationHandler {
        int logLevel;

        TrackingApplication(int logLevel) {
            this.logLevel = logLevel;
        }

        Application proxy() {
            return (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("setLogLevel".equals(name)) {
                logLevel = (Integer) args[0];
                return null;
            }
            if ("getLogLevel".equals(name)) {
                return logLevel;
            }
            if ("getType".equals(name)) {
                return Application.ApplicationType.HeadlessDesktop;
            }
            if ("toString".equals(name)) {
                return "TrackingApplication";
            }
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
