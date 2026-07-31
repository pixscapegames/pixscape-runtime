package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.*;

import java.lang.reflect.Proxy;

public class DynamicEntityRenderStateSystemTest {
    private GL20 previousGl;
    private Graphics previousGraphics;

    @BeforeClass
    public static void loadGdxNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGlProxy() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    @After
    public void restoreGlProxy() {
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    @Test
    public void cullingUpdatesDenseRenderSlotWhenEntityIdDiffersFromSlot() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(4);
        OrthographicCamera camera = new OrthographicCamera(100f, 100f);
        camera.position.set(0f, 0f, 0f);

        World world = new World(new WorldConfigurationBuilder()
                .with(new CullingSystem(camera, state))
                .build());

        state.acquireSlotForEntity(1000);
        int entity = world.create();
        AABBComponent aabb = world.getMapper(AABBComponent.class).create(entity);
        aabb.minX = -1f;
        aabb.minY = -1f;
        aabb.maxX = 1f;
        aabb.maxY = 1f;
        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(entity);

        int renderSlot = state.acquireSlotForEntity(entity);
        state.kind[renderSlot] = RenderKind.SPRITE;
        state.enabled[renderSlot] = true;
        state.visible[renderSlot] = false;

        world.process();

        Assert.assertNotEquals(entity, renderSlot);
        Assert.assertTrue(state.visible[renderSlot]);
        Assert.assertTrue(visibility.inView);
        Assert.assertFalse(visibility.culledByFrustum);
    }

    @Test
    public void parallaxWritesOffsetByDenseRenderSlot() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(4);
        LayerStateSOA layers = new LayerStateSOA(4);
        layers.enabled[2] = true;
        layers.parallaxX[2] = 0.5f;
        layers.parallaxY[2] = 0.25f;
        OrthographicCamera camera = new OrthographicCamera(100f, 100f);
        camera.position.set(20f, 40f, 0f);

        World world = new World(new WorldConfigurationBuilder()
                .with(new ParallaxDisplaySystem(state, layers, camera))
                .build());

        state.acquireSlotForEntity(1000);
        int entity = world.create();
        world.getMapper(OrientedBoundsComponent.class).create(entity);
        world.getMapper(RenderMaterialComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(VisibilityComponent.class).create(entity);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).create(entity);
        region.valid = true;

        int renderSlot = state.acquireSlotForEntity(entity);
        state.kind[renderSlot] = RenderKind.SPRITE;
        state.enabled[renderSlot] = true;
        state.layerIndex[renderSlot] = 2;

        world.process();

        Assert.assertNotEquals(entity, renderSlot);
        Assert.assertEquals(10f, state.offsetX[renderSlot], 0.0001f);
        Assert.assertEquals(30f, state.offsetY[renderSlot], 0.0001f);
    }

    @Test
    public void addingAndRemovingPhysicsPreservesSpriteRenderDomainRecord() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(4);
        TiledMapRenderState tiledState = new TiledMapRenderState(4);
        LayerStateSOA layers = new LayerStateSOA(1);
        layers.enabled[0] = true;
        DrawList drawList = new DrawList(4);
        RenderStats stats = new RenderStats();
        OrthographicCamera camera = new OrthographicCamera(100f, 100f);
        camera.position.set(0f, 0f, 0f);
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        new DirtyTrackerSystem(64),
                        new Box2dSyncSystem(box2d),
                        new UpdateWorldGeometrySystem(),
                        new RenderSpriteSyncSystem(state),
                        new CullingSystem(camera, state),
                        new RenderBuildDrawListSystem(state, tiledState, layers, drawList, stats, 64, -1, -1),
                        new DirtyFlushSystem()
                )
                .build());
        PhysicsService physics = new PhysicsService(world, box2d, new games.pixscape.runtime.loading.SceneMetaRuntime());

        int entity = createRenderableSprite(world);
        world.process();
        assertOnlySpriteIsDrawable(entity, state, drawList);

        state.releaseSlotForEntity(entity);
        physics.ensurePhysics(entity);
        world.process();
        assertOnlySpriteIsDrawable(entity, state, drawList);

        physics.removePhysics(entity);
        world.process();
        assertOnlySpriteIsDrawable(entity, state, drawList);

        world.dispose();
        box2d.dispose();
    }

    private static int createRenderableSprite(World world) {
        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = -5f;
        transform.y = -5f;
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
        dimensions.width = 10f;
        dimensions.height = 10f;
        world.getMapper(OrientedBoundsComponent.class).create(entity);
        world.getMapper(AABBComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(VisibilityComponent.class).create(entity);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).create(entity);
        region.valid = true;
        region.u2 = 1f;
        region.v2 = 1f;
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).create(entity);
        material.textureHandle = 7;
        return entity;
    }

    private static void assertOnlySpriteIsDrawable(int entity,
                                                   DynamicEntityRenderState state,
                                                   DrawList drawList) {
        int renderSlot = state.renderSlotForEntity(entity);
        Assert.assertNotEquals(DynamicEntityRenderState.NO_SLOT, renderSlot);
        Assert.assertEquals(entity, state.entityIdForSlot(renderSlot));
        Assert.assertEquals(RenderKind.SPRITE, state.kind[renderSlot]);
        Assert.assertTrue(state.enabled[renderSlot]);
        Assert.assertTrue(state.visible[renderSlot]);
        Assert.assertEquals(1, drawList.size);
        Assert.assertEquals(renderSlot, drawList.get(0));
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
