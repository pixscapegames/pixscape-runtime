package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.SurfaceLightingComponent;
import com.badlogic.gdx.utils.*;
import org.junit.*;

public class ActorShadowGeometryTest {
    private static final float[] Q={17,23,17,223,117,223,117,23};
    @Test public void legacyProfileConversionAndNewEndpointSerializationAreExplicit(){
        Json json=new Json();ActorShadowProfile old=json.fromJson(ActorShadowProfile.class,"{leftV:0.12,rightV:0.22,thickness:0.35}");
        Assert.assertEquals(0,old.leftU,0);Assert.assertEquals(1,old.rightU,0);Assert.assertEquals(.35f,old.thickness,0);
        String encoded=json.toJson(old);Assert.assertTrue(encoded.contains("version:2"));
        ActorShadowProfile p=new ActorShadowProfile(.3f,.1f,.7f,.2f,.35f);
        ActorShadowProfile restored=json.fromJson(ActorShadowProfile.class,json.toJson(p));
        Assert.assertEquals(.3f,restored.leftU,0);Assert.assertEquals(.7f,restored.rightU,0);Assert.assertEquals(.35f,restored.copy().thickness,0);
        try{new ActorShadowProfile(.5f,.5f,.5f,.5f,.35f);Assert.fail("Coincident endpoints");}catch(IllegalArgumentException expected){}
    }
    @Test public void shorteningTheDirectorDoesNotCropAlphaOrChangeReferenceWidthOrThickness(){
        ActorShadowGeometry full=new ActorShadowGeometry(),shorter=new ActorShadowGeometry();
        full.calibrate(Q,new ActorShadowProfile(.2f,.2f,.35f),19,false,false);
        shorter.calibrate(Q,new ActorShadowProfile(.4f,.2f,.6f,.2f,.35f),19,false,false);
        Assert.assertEquals(full.width,shorter.width,0);Assert.assertArrayEquals(full.calibrated,shorter.calibrated,0);
        LightingFrame.Light light=new LightingFrame.Light();light.x=400;light.y=-400;light.z=150;
        Assert.assertArrayEquals(full.forLight(light),shorter.forLight(light),.00002f);
    }
    @Test public void arbitraryAndVerticalDirectorsCoverTheImageOnceAndKeepContactFixed(){
        ActorShadowGeometry g=new ActorShadowGeometry();java.util.Random random=new java.util.Random(329);
        for(int n=0;n<2000;n++){
            ActorShadowProfile p=n<3?new ActorShadowProfile(.5f,0,.5f+(n-1)*.000001f,1,.35f)
                    :new ActorShadowProfile(random.nextFloat(),random.nextFloat(),random.nextFloat(),random.nextFloat(),.35f);
            g.calibrate(Q,p,31,n%2==0,n%3==0);float area=0;float[] b=g.calibrated;
            Assert.assertTrue(g.vertexCount<=ActorShadowGeometry.VERTICES);
            for(int o=0;o<g.vertexCount*8;o+=24)area+=Math.abs((b[o+11]-b[o+3])*(b[o+20]-b[o+4])-(b[o+19]-b[o+3])*(b[o+12]-b[o+4]))*.5f;
            Assert.assertEquals(1,area,.000002f);
            LightingFrame.Light l=new LightingFrame.Light();l.x=g.contactX+g.normalY*500;l.y=g.contactY-g.normalX*500;l.z=31;
            float[] c=g.forLight(l);boolean moved=false,contact=false;
            for(int o=0;o<g.vertexCount*8;o+=8){
                Assert.assertTrue(Float.isFinite(c[o]) && Float.isFinite(c[o+1]) && Float.isFinite(c[o+2]));
                if(b[o+2]==31){contact=true;Assert.assertEquals(b[o],c[o],0);Assert.assertEquals(b[o+1],c[o+1],0);}
                moved|=b[o]!=c[o] || b[o+1]!=c[o+1];
            }
            Assert.assertTrue(contact);Assert.assertTrue("Nonzero thickness must bend even a vertical director",moved);
        }
    }
    @Test public void maximumThicknessMaintainsApproximateWidthThroughAFullRevolution(){
        ActorShadowGeometry g=new ActorShadowGeometry();LightingFrame.Light light=new LightingFrame.Light();
        g.calibrate(Q,new ActorShadowProfile(.2f,.2f,0),0,false,false);
        float[] reference=g.calibrated.clone();
        for(int step=0;step<=3600;step++){
            double a=Math.toRadians(step*.1);light.x=g.contactX+(float)Math.cos(a)*600;light.y=g.contactY+(float)Math.sin(a)*600;light.z=250;
            Assert.assertSame("Zero preserves the calibrated sheet for every source",g.calibrated,g.forLight(light));
        }
        g.calibrate(Q,new ActorShadowProfile(.2f,.2f,.5f),0,false,false);
        Assert.assertArrayEquals("Thickness never changes calibration",reference,g.calibrated,0);
        float[] bent=g.forLight(light).clone();
        for(int step=0;step<=3600;step++){
            double a=Math.toRadians(step*.1);float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
            light.x=g.contactX+dx*600;light.y=g.contactY+dy*600;light.z=250;
            float[] vertices=g.forLight(light);Assert.assertArrayEquals("A single stable sheet for all source directions",bent,vertices,0);
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for(int o=0;o<g.vertexCount*8;o+=8){float cross=-dy*vertices[o]+dx*vertices[o+1];min=Math.min(min,cross);max=Math.max(max,cross);}
            Assert.assertTrue("No blind-angle collapse at maximum thickness",max-min>.893f*g.width);
            Assert.assertTrue("Bounded breadth of the open V sheet",max-min<1.119f*g.width);
        }
        g.calibrate(Q,new ActorShadowProfile(.2f,.2f,.25f),0,false,false);float[] half=g.forLight(light);
        for(int o=0;o<g.vertexCount*8;o+=8)for(int k=0;k<3;k++)Assert.assertEquals("Continuous thickness interpolation",(reference[o+k]+bent[o+k])*.5f,half[o+k],.00002f);
    }
    @Test public void aDegenerateSupportRetainsTheCalibratedFallback(){
        ActorShadowGeometry g=new ActorShadowGeometry();LightingFrame.Light light=new LightingFrame.Light();
        g.calibrate(Q,new ActorShadowProfile(.2f,.2f,.5f),0,false,false);Assert.assertTrue(g.maximumDisplacement()>0);
        g.calibrate(new float[]{0,0,0,0,100,0,100,0},new ActorShadowProfile(.2f,.2f,.5f),0,false,false);
        Assert.assertTrue("Foot width alone is not proof of a valid support",g.width>0);
        Assert.assertEquals(0,g.maximumDisplacement(),0);Assert.assertSame(g.calibrated,g.forLight(light));
    }
    @Test public void imageInverseUsesTheRenderedTrianglesUnderRotationScaleAndDeformedCorners(){
        float[] out=new float[2],q={17,23,7,223,137,193,117,13};
        for(int angle=0;angle<360;angle+=17){
            float[] transformed=q.clone();double r=Math.toRadians(angle);
            for(int i=0;i<8;i+=2){transformed[i]=(float)(q[i]*Math.cos(r)-q[i+1]*Math.sin(r))*1.7f+123;
                transformed[i+1]=(float)(q[i]*Math.sin(r)+q[i+1]*Math.cos(r))*.8f-321;}
            for(int u=0;u<=20;u++)for(int v=0;v<=20;v++){
                float uu=u/20f,vv=v/20f;
                Assert.assertTrue(ActorShadowImageMapping.inverse(transformed,ActorShadowImageMapping.x(transformed,uu,vv),ActorShadowImageMapping.y(transformed,uu,vv),out));
                Assert.assertEquals(uu,out[0],.000002f);Assert.assertEquals(vv,out[1],.000002f);
            }
        }
        Assert.assertFalse(ActorShadowImageMapping.inverse(new float[8],0,0,out));
    }
    @Test public void oneAlphaDomainAndEveryContactVertexStayFixedThroughFullRevolutions(){
        ActorShadowGeometry g=new ActorShadowGeometry();
        for(boolean flipX:new boolean[]{false,true})for(boolean flipY:new boolean[]{false,true})
            for(float left:new float[]{0,.15f,1})for(float right:new float[]{0,.35f,1}){
                g.calibrate(Q,new ActorShadowProfile(left,right,.3f),31,flipX,flipY);
                float area=0;float[] b=g.calibrated;
                for(int o=0;o<g.vertexCount*8;o+=24)area+=Math.abs((b[o+11]-b[o+3])*(b[o+20]-b[o+4])-(b[o+19]-b[o+3])*(b[o+12]-b[o+4]))*.5f;
                Assert.assertEquals("Exactly one image coverage",1,area,.000001f);
                for(int angle=0;angle<=360;angle++){
                    LightingFrame.Light light=new LightingFrame.Light();double a=Math.toRadians(angle);
                    light.x=g.contactX+(float)Math.cos(a)*300;light.y=g.contactY+(float)Math.sin(a)*300;light.z=160;
                    float[] c=g.forLight(light);
                    for(int o=0;o<g.vertexCount*8;o+=8){
                        Assert.assertTrue(Float.isFinite(c[o]) && Float.isFinite(c[o+1]));
                        Assert.assertEquals(b[o+2],c[o+2],0);Assert.assertEquals(b[o+3],c[o+3],0);Assert.assertEquals(b[o+4],c[o+4],0);
                        if(b[o+2]==31)for(int k=0;k<8;k++)Assert.assertEquals("Fixed foot vertex",Float.floatToRawIntBits(b[o+k]),Float.floatToRawIntBits(c[o+k]));
                    }
                }
            }
    }
    @Test public void exactProfileHasMeasuredDepthAndVerticalAndCoincidentLightsStayFinite(){
        ActorShadowGeometry g=new ActorShadowGeometry();g.calibrate(Q,new ActorShadowProfile(.2f,.2f,.2f),0,false,false);
        LightingFrame.Light light=new LightingFrame.Light();light.x=g.contactX+g.normalY*10000;light.y=g.contactY-g.normalX*10000;light.z=0;
        float a=g.amplitude(light),min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int o=0;o<g.vertexCount*8;o+=8){float[] c=g.forLight(light);float transverse=(c[o]-g.contactX)*g.normalX+(c[o+1]-g.contactY)*g.normalY;
            min=Math.min(min,transverse);max=Math.max(max,transverse);}
        Assert.assertEquals(a,max-min,.0001f);Assert.assertEquals(2*.2f*g.width,a,0);
        light.x=g.contactX;light.y=g.contactY;
        for(float z:new float[]{0,1e-9f,100000}){light.z=z;Assert.assertEquals(a,g.amplitude(light),0);for(float f:g.forLight(light))Assert.assertTrue(Float.isFinite(f));}
        g.calibrate(Q,new ActorShadowProfile(.2f,.2f,0),0,false,false);Assert.assertSame(g.calibrated,g.forLight(light));
    }
    @Test public void profileDirectionCrossingsAreContinuousAndExpandedBoundsContainEveryLightResult(){
        ActorShadowGeometry g=new ActorShadowGeometry();g.calibrate(Q,new ActorShadowProfile(.12f,.33f,.5f),0,false,false);
        LightingSurface s=new LightingSurface(new float[48]);s.actorShadow=g;LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=s;caster.bounds();
        LightingFrame.Light light=new LightingFrame.Light();float[] previous=null;
        for(int step=0;step<=3600;step++){
            double a=Math.toRadians(step*.1);light.x=g.contactX+(float)Math.cos(a)*600;light.y=g.contactY+(float)Math.sin(a)*600;light.z=250;
            float[] c=g.forLight(light);
            for(int o=0;o<g.vertexCount*8;o+=8){Assert.assertTrue(c[o]>=caster.minX && c[o]<=caster.maxX && c[o+1]>=caster.minY && c[o+1]<=caster.maxY);
                if(previous!=null){Assert.assertEquals(previous[o],c[o],.3f);Assert.assertEquals(previous[o+1],c[o+1],.3f);}}
            previous=c.clone();
        }
    }
    @Test public void perClipProfilesRoundTripWithoutFrameOrAtlasIdentityAndCopiesAreIndependent(){
        SurfaceLightingComponent s=new SurfaceLightingComponent();s.actorShadow=new ActorShadowProfile(.1f,.2f,.15f);
        s.actorShadowClips=new IntMap<>();ObjectMap<String,ActorShadowProfile> clips=new ObjectMap<>();s.actorShadowClips.put(42,clips);
        clips.put("walk",new ActorShadowProfile(.3f,.4f,.2f));clips.put("idle",new ActorShadowProfile(.1f,.1f,0));
        Json json=new Json();SurfaceLightingComponent loaded=json.fromJson(SurfaceLightingComponent.class,json.toJson(s));
        Assert.assertEquals(.4f,loaded.shadowProfile(42,"walk").rightV,0);Assert.assertEquals(.1f,loaded.shadowProfile(42,"idle").leftV,0);
        Assert.assertNull(loaded.shadowProfile(99,"walk"));Assert.assertNull(loaded.shadowProfile(42,"missing"));
        SurfaceLightingComponent copy=s.copy();clips.get("walk").leftV=.9f;Assert.assertEquals(.3f,copy.shadowProfile(42,"walk").leftV,0);
        Assert.assertEquals(.1f,copy.shadowProfile(0,null).leftV,0);
    }
    @Test public void loadingReservesStorageAndCalibrationRestoresFallbackWhenRemoved(){
        games.pixscape.runtime.render.DynamicEntityRenderState dynamic=new games.pixscape.runtime.render.DynamicEntityRenderState(4);
        games.pixscape.runtime.render.TiledMapRenderState tiled=new games.pixscape.runtime.render.TiledMapRenderState(4);
        games.pixscape.runtime.render.FrameRenderQueue queue=new games.pixscape.runtime.render.FrameRenderQueue(4);
        games.pixscape.runtime.render.LayerStateSOA layers=new games.pixscape.runtime.render.LayerStateSOA(1);layers.enabled[0]=true;
        games.pixscape.runtime.system.LightingGeometrySystem geometry=new games.pixscape.runtime.system.LightingGeometrySystem(dynamic,tiled,queue,layers);
        com.artemis.World world=new com.artemis.World(new com.artemis.WorldConfigurationBuilder().with(geometry).build());
        try{
            int e=world.create(),slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;
            dynamic.x1[slot]=Q[0];dynamic.y1[slot]=Q[1];dynamic.x2[slot]=Q[2];dynamic.y2[slot]=Q[3];
            dynamic.x3[slot]=Q[4];dynamic.y3[slot]=Q[5];dynamic.x4[slot]=Q[6];dynamic.y4[slot]=Q[7];
            geometry.prepareRuntimeAvailability();LightingSurface surface=queue.lighting.entities.get(e);
            ActorShadowGeometry buffers=surface.actorShadow;Assert.assertNotNull("Storage exists before the first World frame",buffers);
            world.process();float[] receiver=surface.vertices.clone();
            SurfaceLightingComponent a=world.getMapper(SurfaceLightingComponent.class).create(e);a.actorShadow=new ActorShadowProfile(.1f,.35f,.2f);a.shadowCaster=true;
            world.process();Assert.assertSame(buffers,surface.actorShadow);Assert.assertFalse(java.util.Arrays.equals(receiver,surface.vertices));
            Assert.assertSame(buffers.calibrated,surface.shadowVertices);Assert.assertTrue(surface.actorShadow.active);
            a.actorShadow=null;world.process();Assert.assertNull(surface.shadowVertices);Assert.assertArrayEquals(receiver,surface.vertices,0);
        }finally{world.dispose();}
    }
    @Test public void originalDiagonalMappingAndSingleCoverageSurviveIndependentCornersAndAllFootSlopes(){
        ActorShadowGeometry g=new ActorShadowGeometry();float[] q={17,23,7,223,137,193,117,13};
        for(int l=0;l<=50;l++)for(int r=0;r<=50;r++){
            g.calibrate(q,new ActorShadowProfile(l/50f,r/50f,.5f),0,false,false);
            Assert.assertTrue(g.vertexCount<=ActorShadowGeometry.VERTICES);float area=0;float[] b=g.calibrated;
            for(int o=0;o<g.vertexCount*8;o+=24){
                area+=Math.abs((b[o+11]-b[o+3])*(b[o+20]-b[o+4])-(b[o+19]-b[o+3])*(b[o+12]-b[o+4]))*.5f;
                boolean above=true,below=true;
                for(int k=0;k<3;k++){
                    float u=b[o+k*8+3],v=b[o+k*8+4];above &= u<=v+.000001f;below &= u>=v-.000001f;
                    float x=u<=v?q[0]+(v-u)*(q[2]-q[0])+u*(q[4]-q[0]):q[0]+(u-v)*(q[6]-q[0])+v*(q[4]-q[0]);
                    float y=u<=v?q[1]+(v-u)*(q[3]-q[1])+u*(q[5]-q[1]):q[1]+(u-v)*(q[7]-q[1])+v*(q[5]-q[1]);
                    Assert.assertEquals(x,LightingCoordinates.screenX(b[o+k*8],b[o+k*8+1]),.00003f);
                    Assert.assertEquals(y,LightingCoordinates.screenY(b[o+k*8],b[o+k*8+1],b[o+k*8+2]),.00003f);
                }
                Assert.assertTrue("No triangle crosses the original image diagonal",above||below);
            }
            Assert.assertEquals(1,area,.000001f);
        }
    }
    @Test public void calibratedReceiverAndCasterShareFootReferencesAtAuthoredAltitude(){
        ActorShadowGeometry g=new ActorShadowGeometry();float[] r=new float[48];
        float[][] quads={Q,{17,23,7,223,137,193,117,13},{-51,130,170,201,192,87,-30,19}};
        ActorShadowProfile[] profiles={new ActorShadowProfile(.2f,.2f,.35f),new ActorShadowProfile(.2f,.12f,.8f,.37f,.35f),new ActorShadowProfile(.5f,.1f,.5f,.9f,0)};
        for(float[] q:quads)for(ActorShadowProfile p:profiles)for(float altitude:new float[]{0,73})
            for(boolean fx:new boolean[]{false,true})for(boolean fy:new boolean[]{false,true}){
                g.calibrate(q,p,altitude,fx,fy);g.writeReceiver(r,q);
                for(float t:new float[]{0,.5f,1}){
                    float u=p.leftU+t*(p.rightU-p.leftU),v=p.leftV+t*(p.rightV-p.leftV);if(fx)u=1-u;if(fy)v=1-v;
                    float[] actual=interpolate(r,u,v);
                    Assert.assertEquals(altitude,actual[2],.00005f);
                    Assert.assertEquals(LightingCoordinates.worldX(ActorShadowImageMapping.x(q,u,v),ActorShadowImageMapping.y(q,u,v),altitude),actual[0],.00008f);
                    Assert.assertEquals(LightingCoordinates.worldY(ActorShadowImageMapping.x(q,u,v),ActorShadowImageMapping.y(q,u,v),altitude),actual[1],.00008f);
                }
                float[] before=r.clone();LightingFrame.Light light=new LightingFrame.Light();light.x=500;light.y=-300;light.z=200;g.forLight(light);
                Assert.assertArrayEquals("Compensation never enters reception",before,r,0);
                for(int o=0;o<48;o+=8){Assert.assertEquals(LightingCoordinates.screenX(r[o],r[o+1]),ActorShadowImageMapping.x(q,r[o+3],r[o+4]),.00005f);
                    Assert.assertEquals(LightingCoordinates.screenY(r[o],r[o+1],r[o+2]),ActorShadowImageMapping.y(q,r[o+3],r[o+4]),.00005f);}
            }
        g.calibrate(Q,null,0,false,false);float[] untouched=r.clone();g.writeReceiver(r,Q);Assert.assertArrayEquals(untouched,r,0);
    }
    private static float[] interpolate(float[] r,float u,float v){
        int o=u<=v?0:24;float a=u<=v?1-v:1-u,b=u<=v?v-u:v,c=u<=v?u:u-v;
        return new float[]{a*r[o]+b*r[o+8]+c*r[o+16],a*r[o+1]+b*r[o+9]+c*r[o+17],a*r[o+2]+b*r[o+10]+c*r[o+18]};
    }
    @Test public void warmClipLookupTopologyChangesAndTwoLightTransformsAllocateNothing(){
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        games.pixscape.runtime.render.DynamicEntityRenderState dynamic=new games.pixscape.runtime.render.DynamicEntityRenderState(4);
        games.pixscape.runtime.render.TiledMapRenderState tiled=new games.pixscape.runtime.render.TiledMapRenderState(4);
        games.pixscape.runtime.render.FrameRenderQueue queue=new games.pixscape.runtime.render.FrameRenderQueue(4);
        games.pixscape.runtime.render.LayerStateSOA layers=new games.pixscape.runtime.render.LayerStateSOA(1);layers.enabled[0]=true;
        games.pixscape.runtime.system.LightingGeometrySystem geometry=new games.pixscape.runtime.system.LightingGeometrySystem(dynamic,tiled,queue,layers);
        com.artemis.World world=new com.artemis.World(new com.artemis.WorldConfigurationBuilder().with(geometry).build());
        try{
            int e=world.create(),slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;
            dynamic.x1[slot]=Q[0];dynamic.y1[slot]=Q[1];dynamic.x2[slot]=Q[2];dynamic.y2[slot]=Q[3];
            dynamic.x3[slot]=Q[4];dynamic.y3[slot]=Q[5];dynamic.x4[slot]=Q[6];dynamic.y4[slot]=Q[7];
            games.pixscape.runtime.component.AssetRefComponent ref=world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class).create(e);ref.assetId=42;
            games.pixscape.runtime.component.AnimationComponent animation=world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).create(e);
            SurfaceLightingComponent a=world.getMapper(SurfaceLightingComponent.class).create(e);a.shadowCaster=true;
            a.actorShadowClips=new IntMap<>();ObjectMap<String,ActorShadowProfile> clips=new ObjectMap<>();a.actorShadowClips.put(42,clips);
            clips.put("idle",new ActorShadowProfile(.1f,.15f,.2f));clips.put("walk",new ActorShadowProfile(.2f,.3f,.25f));
            world.process();geometry.prepareRuntimeAvailability();
            LightingFrame.Light first=new LightingFrame.Light(),second=new LightingFrame.Light();first.x=110;first.y=-90;first.z=120;second.x=-150;second.y=80;second.z=100;
            ActorShadowGeometry g=queue.lighting.entities.get(e).actorShadow;
            for(int i=0;i<50000;i++){
                animation.currentClip=(i&32)==0?"idle":"walk";world.process();g.forLight(first);g.forLight(second);
            }
            long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
            for(int i=0;i<5000;i++){
                animation.currentClip=(i&32)==0?"idle":"walk";world.process();g.forLight(first);g.forLight(second);
            }
            Assert.assertEquals(0,bean.getThreadAllocatedBytes(thread)-before);
            Assert.assertSame(g,queue.lighting.entities.get(e).actorShadow);
        }finally{world.dispose();}
    }
}
