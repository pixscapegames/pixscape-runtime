package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.*;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.lighting.LightingFrame;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.*;
import org.junit.*;
import java.lang.reflect.Field;

public class LightGameplayApiTest {
    private final EntityRefIdentitySafetyTest environment = new EntityRefIdentitySafetyTest();
    private PixscapeEngine engine;
    private World world;
    private DirtyTrackerSystem dirty;
    private DynamicEntityRenderState dynamic;
    private FrameRenderQueue queue;

    @Before public void setup() throws Exception {
        GdxNativesLoader.load(); environment.installLibGdxProxies(); ShaderRegistry.initDefaults();
        engine = new PixscapeEngine(); dirty = new DirtyTrackerSystem(2048);
        dynamic = new DynamicEntityRenderState(2048); queue = new FrameRenderQueue(2048);
        LayerStateSOA layers = new LayerStateSOA(1); layers.touchLayerIndex(0); layers.enabled[0] = true;
        world = new World(new WorldConfigurationBuilder().with(dirty, new UpdateWorldGeometrySystem(),
                new RenderSpriteSyncSystem(dynamic), new LightingGeometrySystem(dynamic,
                new TiledMapRenderState(1), queue, layers), new DirtyFlushSystem()).build());
        bindWorld(world);
    }

    private void bindWorld(World value) throws Exception {
        Field field = PixscapeEngine.class.getDeclaredField("world"); field.setAccessible(true); field.set(engine, value);
    }

    @After public void cleanup() {
        world.dispose(); ShaderRegistry.disposeAll(); InternalTextures.dispose(); TextureRegistry.clear();
        environment.restoreLibGdx();
    }

    private EntityRef light(boolean cone) {
        int e = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(e); t.x = 30; t.y = 40; t.originX = t.originY = 200;
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(e); d.width = d.height = 400;
        world.getMapper(OrientedBoundsComponent.class).create(e); world.getMapper(AABBComponent.class).create(e);
        world.getMapper(VisibilityComponent.class).create(e); world.getMapper(EntityIndexComponent.class).create(e);
        world.getMapper(RenderMaterialComponent.class).create(e);
        if (cone) world.getMapper(ConeLightComponent.class).create(e).shadowQuality = 0;
        else world.getMapper(PointLightComponent.class).create(e).shadowQuality = 0;
        // Metadata is registered by initDefaults; use the standard source for this fixture.
        world.getMapper(RenderMaterialComponent.class).get(e).shaderIdx = ShaderRegistry.indexOf(cone ? games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_CONELIGHT : games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
        dirty.geometry(e, GeometryDirty.ALL); world.process();
        return engine.api().entities().requireEntityId(e);
    }

    @Test public void frequentParametersReachPublishedLightWithoutGeometryWork() {
        EntityRef ref = light(false); LightFacade light = ref.light();
        Assert.assertSame(light, ref.light());
        LightingFrame.Light published = queue.lighting.lights.first();
        float x = published.x, y = published.y;
        light.setIntensity(.2f).setIntensity(.7f).setFalloff(2f).setColor(.2f,.3f,.4f);
        Assert.assertEquals(0, dirty.geometryEntities().size);
        Assert.assertEquals(1, dirty.colorEntities().size);
        world.process();
        Assert.assertSame(published, queue.lighting.lights.first());
        Assert.assertEquals(.7f, published.intensity, 0); Assert.assertEquals(2f,published.falloff,0);
        Assert.assertEquals(.2f,published.r,0); Assert.assertEquals(.3f,published.g,0); Assert.assertEquals(.4f,published.b,0);
        light.setColor(.2f,.3f,.4f).setIntensity(.7f);
        Assert.assertEquals(0,dirty.coarseBits(ref.entityId()));
        light.setRadius(90).setRadius(120).setHeight(300);
        Assert.assertEquals(1,dirty.geometryEntities().size);
        world.process();
        Assert.assertEquals(120,published.radius,0); Assert.assertEquals(300,published.z,0);
        Assert.assertEquals(x,published.x,.001f); Assert.assertEquals(y,published.y,.001f);
        light.setEnabled(false); world.process(); Assert.assertEquals(0,queue.lighting.lights.size);
        light.setEnabled(true); world.process(); Assert.assertSame(published,queue.lighting.lights.first());
    }

    @Test public void coneOrientationUsesTransformAndAnglesReachConsumer() {
        EntityRef ref = light(true); LightFacade light = ref.light();
        ref.transform().setRotationRad((float)Math.PI / 2);
        light.setConeAngleDeg(60).setInclinationDeg(30).setSoftness(.25f); world.process();
        LightingFrame.Light published = queue.lighting.lights.first();
        Assert.assertEquals(.5f,published.dz,.001f);
        Assert.assertEquals(published.dx,published.dy,.001f);
        Assert.assertEquals(Math.cos(Math.PI/6),published.cosOuter,.001f);
        Assert.assertEquals(Math.cos(Math.PI/8),published.cosInner,.001f);
    }

    @Test public void invalidAndIncompatibleRequestsAreAtomic() {
        EntityRef first = light(false), second = light(false);
        second.light().setEnabled(false); world.process(); engine.setForwardLighting(true);
        Assert.assertThrows(IllegalArgumentException.class, () -> second.light().setEnabled(true));
        Assert.assertFalse(second.light().enabled()); Assert.assertEquals(0,dirty.coarseBits(second.entityId()));
        first.light().setEnabled(false); second.light().setEnabled(true);
        Assert.assertFalse(first.light().enabled()); Assert.assertTrue(second.light().enabled());
        Assert.assertThrows(IllegalArgumentException.class, () -> second.light().setColor(.1f,Float.NaN,.2f));
        Assert.assertEquals(1,second.light().red(),0);
        Assert.assertThrows(IllegalArgumentException.class, () -> second.light().setRadius(Float.POSITIVE_INFINITY));
        Assert.assertEquals(200,second.light().radius(),0);
        Assert.assertThrows(IllegalStateException.class, () -> second.light().setConeAngleDeg(30));
        Assert.assertThrows(IllegalStateException.class, () -> second.light().setShadowQuality(1));
        Assert.assertEquals(0,second.light().shadowQuality());
        second.light().setEnabled(true); // Equal write must not fail admission against itself.
    }

    @Test public void activeSpatialRectangleAndPivotReusePreparedState() {
        int e=world.create();
        world.getMapper(TransformComponent.class).create(e);
        games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent foot=world.getMapper(games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent.class).create(e);
        foot.valid=true;
        games.pixscape.runtime.component.spatial.SpatialHeightComponent height=world.getMapper(games.pixscape.runtime.component.spatial.SpatialHeightComponent.class).create(e);
        games.pixscape.runtime.spatial.ActorSpatialRectangleData rectangle=new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
        height.rectangles=new games.pixscape.runtime.spatial.ActorSpatialRectangles();height.rectangles.staticRectangle=rectangle;
        int slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;
        dynamic.x1[slot]=-50;dynamic.y1[slot]=0;dynamic.x2[slot]=-50;dynamic.y2[slot]=100;
        dynamic.x3[slot]=50;dynamic.y3[slot]=100;dynamic.x4[slot]=50;dynamic.y4[slot]=0;
        world.process();EntityRef ref=engine.api().entities().requireEntityId(e);
        games.pixscape.runtime.render.lighting.ActorShadowRectangleCaster caster=queue.lighting.entities.get(e).actorShadowRectangle;
        int updates=caster.updates;float[] vertices=caster.vertices;
        ref.spatial().setRectangle(-30,4,60,80).setShadowPivot(5,6).setShadowPivot(7,8);
        Assert.assertEquals(updates,caster.updates);world.process();
        Assert.assertSame(caster,queue.lighting.entities.get(e).actorShadowRectangle);Assert.assertSame(vertices,caster.vertices);
        Assert.assertEquals(updates+1,caster.updates);Assert.assertEquals(-4,caster.vertices[2],0);
        ref.spatial().setRectangle(-30,4,60,80).setShadowPivot(7,8);world.process();
        Assert.assertEquals("Equal setters do not rebuild the shadow rectangle",updates+1,caster.updates);
        Assert.assertSame(rectangle,height.rectangles.staticRectangle);
        Assert.assertEquals(60,ref.spatial().rectangleWidth(),0);Assert.assertEquals(-30,ref.spatial().rectangleX(),0);
        Assert.assertEquals(4,ref.spatial().rectangleY(),0);Assert.assertEquals(80,ref.spatial().height(),0);
        Assert.assertEquals(7,ref.spatial().shadowPivotX(),0);Assert.assertEquals(8,ref.spatial().shadowPivotY(),0);
        Assert.assertThrows(IllegalArgumentException.class,()->ref.spatial().setRectangle(0,0,0,10));
        Assert.assertEquals(-30,rectangle.x,0);Assert.assertEquals(60,rectangle.width,0);
        Assert.assertThrows(IllegalArgumentException.class,()->ref.spatial().setShadowPivot(9,Float.NaN));
        Assert.assertEquals(7,rectangle.pivotX,0);Assert.assertEquals(0,dirty.geometryEntities().size);
    }

    @Test public void ambientValidationIsAtomicAndCheap() {
        engine.setAmbientLight(.1f,.2f,.3f);
        Assert.assertThrows(IllegalArgumentException.class,()->engine.setAmbientLight(.8f,Float.NaN,.5f));
        Assert.assertEquals(.1f,engine.ambientRed(),0);Assert.assertEquals(.2f,engine.ambientGreen(),0);Assert.assertEquals(.3f,engine.ambientBlue(),0);
        Assert.assertEquals(0,dirty.geometryEntities().size);
    }

    @Test public void sustainedIntensityMatchesInternalPublicationAndAllocationCost() throws Exception {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocations = (com.sun.management.ThreadMXBean) bean;
        Assume.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int count : new int[]{1,100,1000}) {
            LightFacade[] facades = new LightFacade[count]; PointLightComponent[] components = new PointLightComponent[count];
            for (int i=0;i<count;i++) {
                EntityRef ref=light(false); facades[i]=ref.light(); components[i]=world.getMapper(PointLightComponent.class).get(ref.entityId());
            }
            // Warm both access and the complete publication chain, including Artemis subscriptions.
            for(int frame=0;frame<300;frame++) {
                for(int i=0;i<count;i++) facades[i].setIntensity((frame&1)*.5f);
                world.process();
            }
            long bytes=allocations.getThreadAllocatedBytes(thread), start=System.nanoTime();
            for(int frame=0;frame<500;frame++) {
                for(int i=0;i<count;i++) facades[i].setIntensity((frame&1)*.5f);
                world.process();
            }
            long apiNs=System.nanoTime()-start, apiBytes=allocations.getThreadAllocatedBytes(thread)-bytes;
            bytes=allocations.getThreadAllocatedBytes(thread); start=System.nanoTime();
            for(int frame=0;frame<500;frame++) {
                for(int i=0;i<count;i++) components[i].intensity=(frame&1)*.5f;
                world.process();
            }
            long internalNs=System.nanoTime()-start, internalBytes=allocations.getThreadAllocatedBytes(thread)-bytes;
            Assert.assertEquals(0,dirty.geometryEntities().size);
            for(int i=0;i<count;i++) Assert.assertEquals(.5f,facades[i].intensity(),0);
            Assert.assertTrue("API adds no per-call allocation: " + apiBytes + " vs " + internalBytes,
                    apiBytes <= internalBytes + 4096);
            System.out.println("light-publication n="+count+" frames=500 apiNs="+apiNs+" internalNs="+internalNs
                    +" apiBytes="+apiBytes+" internalBytes="+internalBytes+" geometryDirty=0");
            for(int i=0;i<count;i++) facades[i].setEnabled(false);
            // Disable keeps existing cached storage; remove entities before the next independent size.
            com.artemis.utils.IntBag ids=world.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities();
            for(int i=0;i<ids.size();i++)world.delete(ids.get(i));
            world.process();
        }
    }

    @Test public void staleFacadesNeverRetargetRecycledEntitiesOrWorlds() throws Exception {
        EntityRef ref = light(false); LightFacade old = ref.light(); int e = ref.entityId();
        ref.remove(); world.process(); EntityRef replacement = light(true); Assert.assertEquals(e,replacement.entityId());
        old.setIntensity(99).setColor(Float.NaN,0,0).setEnabled(true);
        Assert.assertFalse(old.hasPoint()); Assert.assertFalse(old.hasCone()); Assert.assertEquals(0,old.intensity(),0);
        Assert.assertEquals(1,replacement.light().intensity(),0);
        World other = new World();
        try { bindWorld(other); replacement.light().setIntensity(4); Assert.assertFalse(replacement.exists()); }
        finally { bindWorld(world); other.dispose(); }
        Assert.assertFalse("Rebinding must not revive handles",replacement.exists());
    }
}
