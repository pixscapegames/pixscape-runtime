package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderKind;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DynamicEntityRenderStateSystemTest {

    @BeforeClass
    public static void loadGdxNatives() {
        GdxNativesLoader.load();
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
}
