package games.pixscape.runtime.loading;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class WorldConfigFactoryFixtureAllocatorTest {
    private GL20 previousGl;
    private Graphics previousGraphics;

    @BeforeClass
    public static void loadGdxNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGraphicsProxies() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> {
                    if ("glGenTexture".equals(method.getName())) return 1;
                    return defaultValue(method.getReturnType());
                });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @After
    public void restoreGraphicsProxies() {
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    @Test
    public void worldWithoutSceneContainsUnboundAllocator() {
        World world = buildWorld(null);
        try {
            FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
            Assert.assertNotNull(allocator);
            Assert.assertFalse(allocator.isBound());

            PhysicsService physics = new PhysicsService(world, null);
            try {
                physics.createDefaultFixture();
                Assert.fail("Expected fixture creation without an active scene to fail");
            } catch (IllegalStateException expected) {
                Assert.assertEquals(
                        "Cannot allocate fixture ID: no active scene metadata is bound",
                        expected.getMessage());
            }
        } finally {
            world.dispose();
        }
    }

    @Test
    public void worldWithSceneAllocatesBeforeWorldProcess() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.name = "factory-bound";
        meta.nextFixtureId = 5;
        World world = buildWorld(meta);
        try {
            FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
            Assert.assertTrue(allocator.isBound());
            Assert.assertSame(meta, allocator.sceneMeta());

            Assert.assertEquals(5, new PhysicsService(world, null).createDefaultFixture().fixtureId);
            Assert.assertEquals(6, meta.nextFixtureId);
        } finally {
            world.dispose();
        }
    }

    private static World buildWorld(SceneMetaRuntime meta) {
        return WorldConfigFactory.buildWorld(
                new OrthographicCamera(),
                new DynamicEntityRenderState(),
                new LayerStateSOA(),
                new DrawList(),
                new FrameRenderQueue(),
                new VfxRenderState(),
                new TiledMapRenderState(),
                new RenderStats(),
                0,
                new AtlasRuntimeService(),
                null,
                NoOpSystem::new,
                meta,
                0,
                new TileAnimationRegistry(),
                RuntimeTilesetProfiles.empty(),
                null,
                null,
                null
        ).getWorld();
    }

    private static final class NoOpSystem extends BaseSystem {
        @Override
        protected void processSystem() {
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
        if (type == char.class) return (char) 0;
        return null;
    }
}
