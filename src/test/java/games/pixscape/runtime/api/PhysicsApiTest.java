package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class PhysicsApiTest {

    @Test
    public void facadeIsCachedAndSettingsExposeSafeEffectiveValues() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        PhysicsAPI physics = engine.api().physics();

        Assert.assertSame(physics, engine.api().physics());
        Assert.assertFalse(physics.isRunning());
        Assert.assertEquals(100f, physics.pixelsPerMeter(), 0f);
        Assert.assertEquals(1f, physics.parallaxX(), 0f);
        Assert.assertEquals(1f, physics.parallaxY(), 0f);

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.pixelsPerMeter = 64f;
        meta.physicsParallaxX = 0.25f;
        meta.physicsParallaxY = Float.NaN;
        setField(engine, "activeSceneMeta", meta);

        Assert.assertEquals(64f, physics.pixelsPerMeter(), 0f);
        Assert.assertEquals(0.25f, physics.parallaxX(), 0f);
        Assert.assertEquals(1f, physics.parallaxY(), 0f);

        meta.pixelsPerMeter = Float.NaN;
        Assert.assertEquals(100f, physics.pixelsPerMeter(), 0f);
    }

    @Test
    public void removeParallaxUsesExactFormulaAndSupportsInPlaceOutput() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        PhysicsAPI physics = engine.api().physics();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        setField(engine, "activeSceneMeta", meta);
        OrthographicCamera camera = new OrthographicCamera();
        camera.position.set(20f, -10f, 0f);
        Vector2 rendered = new Vector2(50f, 30f);
        Vector2 out = new Vector2();

        meta.physicsParallaxX = 0.5f;
        meta.physicsParallaxY = 1f;
        Assert.assertSame(out, physics.removeParallax(rendered, camera, out));
        Assert.assertEquals(40f, out.x, 0f);
        Assert.assertEquals(30f, out.y, 0f);

        meta.physicsParallaxX = 1f;
        meta.physicsParallaxY = 0.25f;
        physics.removeParallax(rendered, camera, out);
        Assert.assertEquals(50f, out.x, 0f);
        Assert.assertEquals(37.5f, out.y, 0f);

        meta.physicsParallaxX = 0f;
        meta.physicsParallaxY = 0f;
        physics.removeParallax(rendered, camera, rendered);
        Assert.assertEquals(30f, rendered.x, 0f);
        Assert.assertEquals(40f, rendered.y, 0f);

        meta.physicsParallaxX = 0.5f;
        meta.physicsParallaxY = 0.25f;
        rendered.set(50f, 30f);
        physics.removeParallax(rendered, camera, rendered);
        Assert.assertEquals(40f, rendered.x, 0f);
        Assert.assertEquals(37.5f, rendered.y, 0f);
    }

    @Test
    public void nativeBridgeTracksCurrentRuntimeWorldAndAuthoritativeBodyMapping()
            throws Exception {
        GdxNativesLoader.load();
        PixscapeEngine engine = new PixscapeEngine();
        PhysicsAPI physics = engine.api().physics();
        PhysicsFixture first = new PhysicsFixture(80f);
        PhysicsFixture second = null;
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.physicsEnabled = false;
            bind(engine, first, meta);

            Assert.assertFalse(physics.isRunning());
            meta.physicsEnabled = true;
            Assert.assertTrue(physics.isRunning());
            Assert.assertEquals(80f, physics.pixelsPerMeter(), 0f);
            Assert.assertSame(first.service.world, physics.box2dWorld());

            int withoutBody = first.world.create();
            int withBody = first.world.create();
            Body nativeBody = first.service.world.createBody(new BodyDef());
            first.world.getMapper(PhysicsRuntimeBodyComponent.class)
                    .create(withBody).body = nativeBody;
            EntityRef missing = engine.api().entities().ofEntityId(Integer.MAX_VALUE);
            EntityRef bodyRef = engine.api().entities().ofEntityId(withBody);

            Assert.assertNull(physics.body(null));
            Assert.assertNull(physics.body(missing));
            Assert.assertNull(physics.body(engine.api().entities().ofEntityId(withoutBody)));
            Assert.assertSame(nativeBody, physics.body(bodyRef));

            second = new PhysicsFixture(120f);
            bind(engine, second, meta);

            Assert.assertTrue(physics.isRunning());
            Assert.assertSame(second.service.world, physics.box2dWorld());
            Assert.assertEquals(120f, physics.pixelsPerMeter(), 0f);
            Assert.assertNull(physics.body(bodyRef));

            second.sync.setStepEnabled(false);
            Assert.assertFalse(physics.isRunning());
        } finally {
            first.dispose();
            if (second != null) second.dispose();
        }
    }

    @Test
    public void removeParallaxRejectsNullArgumentsConsistently() {
        PhysicsAPI physics = new PixscapeEngine().api().physics();
        OrthographicCamera camera = new OrthographicCamera();
        Vector2 vector = new Vector2();

        assertIllegalArgument(() -> physics.removeParallax(null, camera, vector));
        assertIllegalArgument(() -> physics.removeParallax(vector, null, vector));
        assertIllegalArgument(() -> physics.removeParallax(vector, camera, null));
    }

    private static void assertIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void bind(PixscapeEngine engine, PhysicsFixture fixture,
                             SceneMetaRuntime meta) throws Exception {
        setField(engine, "world", fixture.world);
        setField(engine, "activeSceneMeta", meta);
        setField(engine, "box2dWorldService", fixture.service);
        setField(engine, "box2dSyncSystem", fixture.sync);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class PhysicsFixture {
        final Box2dWorldService service;
        final Box2dSyncSystem sync;
        final World world;

        PhysicsFixture(float pixelsPerMeter) {
            service = new Box2dWorldService(pixelsPerMeter, new Vector2());
            sync = new Box2dSyncSystem(service);
            sync.setStepEnabled(true);
            world = new World(new WorldConfigurationBuilder()
                    .with(new DirtyTrackerSystem(16), sync)
                    .build());
        }

        void dispose() {
            world.dispose();
            service.dispose();
        }
    }
}
