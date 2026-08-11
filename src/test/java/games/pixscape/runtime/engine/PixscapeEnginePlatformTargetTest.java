package games.pixscape.runtime.engine;

import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.service.ShaderRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class PixscapeEnginePlatformTargetTest {
    @After
    public void resetShaderRegistry() {
        ShaderRegistry.disposeAll();
    }

    @Test
    public void defaultsToAuto() {
        Assert.assertEquals(PlatformTarget.AUTO, new PixscapeEngine().getPlatformTarget());
    }

    @Test
    public void nullTargetBecomesAuto() {
        PixscapeEngine engine = new PixscapeEngine().setPlatformTarget(PlatformTarget.DESKTOP_GL30);

        engine.setPlatformTarget(null);

        Assert.assertEquals(PlatformTarget.AUTO, engine.getPlatformTarget());
    }

    @Test
    public void targetCannotChangeAfterProjectLoading() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        setLoaded(engine, true);

        try {
            engine.setPlatformTarget(PlatformTarget.ANDROID_ES3);
            Assert.fail("Expected target change after project loading to fail");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(PlatformTarget.AUTO, engine.getPlatformTarget());
        }
    }

    @Test
    public void explicitTargetReachesShaderRegistryDuringEmptyInitialization() {
        PixscapeEngine engine = new PixscapeEngine().setPlatformTarget(PlatformTarget.HTML_WEBGL2);

        try {
            engine.initEmptyRuntime();
            Assert.fail("Expected headless shader initialization to fail");
        } catch (RuntimeException expected) {
            Assert.assertEquals(PlatformTarget.HTML_WEBGL2, ShaderRegistry.getCurrentPlatformTarget());
        }
    }

    @Test
    public void disposalClearsResolvedTargetBeforeAnotherInitialization() {
        PixscapeEngine first = new PixscapeEngine().setPlatformTarget(PlatformTarget.DESKTOP_GL30);
        initializeUntilHeadlessFailure(first);
        Assert.assertEquals(PlatformTarget.DESKTOP_GL30, ShaderRegistry.getCurrentPlatformTarget());

        first.dispose();
        Assert.assertEquals(PlatformTarget.AUTO, ShaderRegistry.getCurrentPlatformTarget());

        PixscapeEngine second = new PixscapeEngine().setPlatformTarget(PlatformTarget.ANDROID_ES3);
        initializeUntilHeadlessFailure(second);
        Assert.assertEquals(PlatformTarget.ANDROID_ES3, ShaderRegistry.getCurrentPlatformTarget());
    }

    private static void initializeUntilHeadlessFailure(PixscapeEngine engine) {
        try {
            engine.initEmptyRuntime();
            Assert.fail("Expected headless shader initialization to fail");
        } catch (RuntimeException expected) {
            // Shader context is established before graphics-dependent initialization.
        }
    }

    private static void setLoaded(PixscapeEngine engine, boolean loaded) throws Exception {
        Field field = PixscapeEngine.class.getDeclaredField("loaded");
        field.setAccessible(true);
        field.setBoolean(engine, loaded);
    }
}
