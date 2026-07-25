package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PixscapeEnginePhysicsLifecycleTest {
    private Application previousApp;

    @Before
    public void installApplication() {
        previousApp = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @After
    public void restoreApplication() {
        Gdx.app = previousApp;
    }

    @Test
    public void disabledSceneDisposesAfterTeardownAndEnabledSceneRecreatesLazily()
            throws Exception {
        Box2dWorldService initial = new Box2dWorldService(
                100f, new Vector2(0f, -9.8f), true);
        Box2dSyncSystem sync = new Box2dSyncSystem(initial);
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), sync,
                        new PhysicsSpatialFootprintSyncSystem(100f))
                .build());
        PixscapeEngine engine = new PixscapeEngine();
        set(engine, "world", world);
        set(engine, "box2dSyncSystem", sync);
        set(engine, "box2dWorldService", initial);

        SceneMetaRuntime disabled = new SceneMetaRuntime();
        disabled.physicsEnabled = false;
        apply(engine, disabled, true);

        Assert.assertTrue(initial.isDisposed());
        Assert.assertNull(engine.getBox2dWorldService());
        Assert.assertNull(sync.getBox2d());
        Assert.assertFalse(sync.isEnabled());
        Assert.assertFalse(sync.isStepEnabled());

        SceneMetaRuntime enabled = new SceneMetaRuntime();
        enabled.physicsEnabled = true;
        enabled.pixelsPerMeter = 64f;
        enabled.gravityX = 1f;
        enabled.gravityY = -3f;
        enabled.doSleep = false;
        apply(engine, enabled, true);

        Box2dWorldService recreated = engine.getBox2dWorldService();
        Assert.assertNotNull(recreated);
        Assert.assertNotSame(initial, recreated);
        Assert.assertEquals(64f, recreated.ppm, 0f);
        Assert.assertFalse(recreated.isDoSleep());
        Assert.assertTrue(sync.isEnabled());
        Assert.assertTrue(sync.isStepEnabled());
        engine.dispose();
    }

    private static void apply(
            PixscapeEngine engine, SceneMetaRuntime meta, boolean activate)
            throws Exception {
        Method method = PixscapeEngine.class.getDeclaredMethod(
                "applyPhysicsFromScene", SceneMetaRuntime.class, boolean.class);
        method.setAccessible(true);
        method.invoke(engine, meta, activate);
    }

    private static void set(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        return null;
    }
}
