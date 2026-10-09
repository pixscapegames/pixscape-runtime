package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.*;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.Arrays;

public class RenderSpriteDirtyUnionTest {
    private final DynamicEntityRenderStateSystemTest gl = new DynamicEntityRenderStateSystemTest();
    private World world;
    private DirtyTrackerSystem dirty;
    private RenderSpriteSyncSystem sync;
    private UpdateWorldGeometrySystem geometry;
    private DynamicEntityRenderState state;
    private IntArray work;
    private static final int ALL = DirtyBits.GEOMETRY | DirtyBits.MATERIAL | DirtyBits.COLOR
            | DirtyBits.ORDER | DirtyBits.LAYER;

    @BeforeClass public static void natives() { DynamicEntityRenderStateSystemTest.loadGdxNatives(); }
    @Before public void setup() throws Exception {
        gl.installGlProxy();
        state = new DynamicEntityRenderState(4);
        dirty = new DirtyTrackerSystem(16);
        geometry = new UpdateWorldGeometrySystem();
        sync = new RenderSpriteSyncSystem(state);
        world = new World(new WorldConfigurationBuilder().with(dirty, geometry, sync).build());
        Field field = RenderSpriteSyncSystem.class.getDeclaredField("work");
        field.setAccessible(true);
        work = (IntArray) field.get(sync);
    }
    @After public void teardown() { if (world != null) world.dispose(); gl.restoreGlProxy(); }

    @Test public void noDirtySkipsWorkAndSingleCategoriesRemainAvailableToConsumers() {
        int e = sprite(); world.process(); dirty.clearFrame();
        float[] before = record(e);
        for (int mask : new int[]{0, DirtyBits.GEOMETRY, DirtyBits.MATERIAL, DirtyBits.COLOR,
                DirtyBits.ORDER, DirtyBits.LAYER, DirtyBits.GEOMETRY | DirtyBits.ORDER, ALL}) {
            dirty.clearFrame(); dirty.mark(e, mask);
            sync.prepareRuntimeAvailability();
            assertWork(mask == 0 ? new int[0] : new int[]{e});
            Assert.assertEquals(mask, dirty.coarseBits(e));
            Assert.assertArrayEquals(before, record(e), 0f);
            assertTicketsMatch(e);
        }
    }

    @Test public void firstAppearanceOrderAndFullStateArePreservedForOverlappingCategories() {
        int a = sprite(), b = sprite(), c = sprite(), d = sprite();
        world.process(); dirty.clearFrame();
        // Category priority wins over the chronological order in which marks are issued.
        dirty.material(c); dirty.order(d); dirty.geometry(b, GeometryDirty.ALL);
        dirty.geometry(a, GeometryDirty.ALL); dirty.color(c); dirty.material(a);
        dirty.order(b); dirty.layer(d); dirty.layer(a);
        sync.prepareRuntimeAvailability();
        assertWork(b, a, c, d);
        dirty.clearFrame();

        TransformComponent transform = world.getMapper(TransformComponent.class).get(a);
        transform.x = 11f; transform.y = 13f;
        world.getMapper(DimensionsComponent.class).get(a).height = 20f;
        TextureRegionComponent uv = world.getMapper(TextureRegionComponent.class).get(a);
        uv.u1 = .1f; uv.v1 = .2f; uv.u2 = .7f; uv.v2 = .8f;
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).get(a);
        material.textureHandle = 42; material.shaderIdx = 3; material.blendModeId = BlendMode.ALPHA.id;
        world.getMapper(TintComponent.class).create(a).rgba = 0x336699CC;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(a);
        index.layerIndex = 3; index.zIndex = 17;
        dirty.geometry(a, GeometryDirty.ALL); dirty.mark(a, ALL);
        geometry.prepareRuntimeAvailability();
        Assert.assertEquals(0, dirty.geomSub(a));
        Assert.assertEquals(ALL, dirty.coarseBits(a));
        sync.prepareRuntimeAvailability();
        assertWork(a);
        int s = state.renderSlotForEntity(a);
        Assert.assertArrayEquals(new float[]{11,13,11,33,21,33,21,13,.1f,.2f,.7f,.8f}, geometryAndUv(s), .0001f);
        Assert.assertEquals(42, state.textureHandle[s]); Assert.assertEquals(3, state.shader[s]);
        Assert.assertEquals(BlendMode.ALPHA.id, state.blend[s]);
        Assert.assertEquals(Color.toFloatBits(.2f,.4f,.6f,.8f), state.colorPacked[s], 0f);
        Assert.assertEquals(.8f, state.a[s], .0001f);
        Assert.assertEquals(3, state.layerIndex[s]); Assert.assertEquals(17, state.z[s]);
        Assert.assertEquals(a, state.runtimeOrder[s]);
        Assert.assertEquals(SortKey64.packForBlend(3, BlendMode.ALPHA.id,42,3,17,a), state.sortKey[s]);
        Assert.assertEquals(ALL, dirty.coarseBits(a)); assertTicketsMatch(a);
    }

    @Test public void consumedEarlierCategoriesDoNotSuppressRemainingWork() {
        int a = sprite(), b = sprite(); world.process(); dirty.clearFrame();
        dirty.mark(a, ALL); dirty.mark(b, ALL);
        dirty.consumeMask(DirtyBits.GEOMETRY | DirtyBits.MATERIAL, e -> {});
        assertTicketsMatch(a); assertTicketsMatch(b);
        sync.prepareRuntimeAvailability(); assertWork(a, b);
        Assert.assertEquals(DirtyBits.COLOR | DirtyBits.ORDER | DirtyBits.LAYER, dirty.coarseBits(a));
        dirty.consume(DirtyBits.COLOR, e -> {});
        dirty.geometry(b, GeometryDirty.POSITION);
        sync.prepareRuntimeAvailability(); assertWork(b, a);
        assertTicketsMatch(a); assertTicketsMatch(b);
        dirty.consumeMask(ALL, e -> {});
        sync.prepareRuntimeAvailability(); assertWork();
    }

    @Test public void deletionDenseMoveAndReusedEntityReceiveOneCompleteRecord() {
        int a = sprite(), b = sprite(), c = sprite(); world.process(); dirty.clearFrame();
        int removedSlot = state.renderSlotForEntity(b);
        float[] survivor = record(c); long survivorKey = state.sortKey[state.renderSlotForEntity(c)];
        dirty.mark(b, ALL); world.delete(b); world.process();
        Assert.assertEquals(DynamicEntityRenderState.NO_SLOT, state.renderSlotForEntity(b));
        Assert.assertEquals(removedSlot, state.renderSlotForEntity(c));
        Assert.assertArrayEquals(survivor, record(c), 0f);
        Assert.assertEquals(survivorKey, state.sortKey[state.renderSlotForEntity(c)]);
        assertTicketsMatch(b);
        int replacement = sprite();
        Assert.assertEquals("Artemis must exercise entity-id reuse", b, replacement);
        world.getMapper(RenderMaterialComponent.class).get(replacement).textureHandle = 99;
        world.getMapper(EntityIndexComponent.class).get(replacement).layerIndex = 2;
        world.process(); assertWork(replacement);
        int s = state.renderSlotForEntity(replacement);
        Assert.assertEquals(99, state.textureHandle[s]); Assert.assertEquals(2, state.layerIndex[s]);
        Assert.assertEquals(3, state.activeCount); Assert.assertEquals(a, state.entityIdForSlot(state.renderSlotForEntity(a)));
        Assert.assertArrayEquals(new float[]{-5,-5,-5,5,5,5,5,-5,0,0,1,1}, geometryAndUv(s), 0f);
        dirty.clearEntity(replacement); sync.prepareRuntimeAvailability(); assertWork();
    }

    @Test public void animatedFlippedSpriteAndLightsRetainTheirRenderData() {
        int animated = sprite(), point = sprite(), cone = sprite();
        world.getMapper(AnimationComponent.class).create(animated);
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(animated);
        repeat.repeatX = true; repeat.repeatY = true;
        world.getMapper(TransformComponent.class).get(animated).scaleX = -1f;
        PointLightComponent light = world.getMapper(PointLightComponent.class).create(point);
        light.r = .2f; light.g = .4f; light.b = .6f; light.intensity = .5f;
        world.getMapper(ConeLightComponent.class).create(cone).intensity = .25f;
        world.process(); assertWork(animated, point, cone);
        int s = state.renderSlotForEntity(animated), p = state.renderSlotForEntity(point), c = state.renderSlotForEntity(cone);
        Assert.assertEquals(1f, state.u1[s], 0f); Assert.assertEquals(0f, state.u2[s], 0f);
        Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[s]);
        Assert.assertEquals(InternalTextures.whiteHandle(), state.textureHandle[p]);
        Assert.assertEquals(InternalTextures.whiteHandle(), state.textureHandle[c]);
        Assert.assertEquals(Color.toFloatBits(.2f,.4f,.6f,1f), state.colorPacked[p], 0f);
        Assert.assertEquals(1f, state.a[c], 0f);
        Assert.assertEquals(0f, state.u1[p], 0f); Assert.assertEquals(1f, state.u2[p], 0f);
        float[] pointBefore = record(point), coneBefore = record(cone);
        dirty.clearFrame(); dirty.mark(point, ALL); dirty.mark(cone, ALL);
        sync.prepareRuntimeAvailability(); assertWork(point, cone);
        Assert.assertArrayEquals(pointBefore, record(point), 0f); Assert.assertArrayEquals(coneBefore, record(cone), 0f);
        for (float intensity : new float[]{0f, .25f, 1f, 3f, 10f}) {
            light.intensity = intensity;
            world.getMapper(ConeLightComponent.class).get(cone).intensity = intensity;
            dirty.clearFrame(); dirty.color(point); dirty.color(cone);
            sync.prepareRuntimeAvailability();
            Assert.assertEquals(Color.toFloatBits(.2f,.4f,.6f,1f), state.colorPacked[p], 0f);
            Assert.assertEquals(Color.WHITE.toFloatBits(), state.colorPacked[c], 0f);
        }
    }

    @Test public void stabilizedWorkBufferIsReused() {
        int e = sprite(); world.process(); dirty.clearFrame(); dirty.mark(e, ALL);
        sync.prepareRuntimeAvailability(); int[] backing = work.items;
        for (int i = 0; i < 100; i++) sync.prepareRuntimeAvailability();
        Assert.assertSame(backing, work.items); assertWork(e);
    }

    private int sprite() {
        int e = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(e); t.x = t.y = -5f;
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(e); d.width = d.height = 10f;
        world.getMapper(OrientedBoundsComponent.class).create(e); world.getMapper(AABBComponent.class).create(e);
        world.getMapper(EntityIndexComponent.class).create(e); world.getMapper(VisibilityComponent.class).create(e);
        TextureRegionComponent uv = world.getMapper(TextureRegionComponent.class).create(e); uv.valid = true; uv.u2 = uv.v2 = 1f;
        world.getMapper(RenderMaterialComponent.class).create(e).textureHandle = 7;
        return e;
    }
    private void assertWork(int... expected) { Assert.assertArrayEquals(expected, Arrays.copyOf(work.items, work.size)); }
    private void assertTicketsMatch(int e) {
        IntArray[] lists = {dirty.geometryEntities(),dirty.materialEntities(),dirty.colorEntities(),dirty.orderEntities(),dirty.layerEntities()};
        int[] bits = {DirtyBits.GEOMETRY,DirtyBits.MATERIAL,DirtyBits.COLOR,DirtyBits.ORDER,DirtyBits.LAYER};
        for (int i=0;i<bits.length;i++) Assert.assertEquals((dirty.coarseBits(e)&bits[i])!=0, lists[i].contains(e));
    }
    private float[] geometryAndUv(int s) { return new float[]{state.x1[s],state.y1[s],state.x2[s],state.y2[s],state.x3[s],state.y3[s],state.x4[s],state.y4[s],state.u1[s],state.v1[s],state.u2[s],state.v2[s]}; }
    private float[] record(int e) {
        int s = state.renderSlotForEntity(e); float[] result = Arrays.copyOf(geometryAndUv(s), 20);
        result[12]=state.textureHandle[s];result[13]=state.shader[s];result[14]=state.blend[s];result[15]=state.colorPacked[s];
        result[16]=state.a[s];result[17]=state.layerIndex[s];result[18]=state.z[s];result[19]=state.repeatFlags[s];return result;
    }
}
