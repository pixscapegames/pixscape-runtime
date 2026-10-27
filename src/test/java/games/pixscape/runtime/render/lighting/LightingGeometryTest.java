package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

public class LightingGeometryTest {
    @Test public void onlyOrdinaryImagesPublishSimpleQuadTopologyAndRefreshEveryCorner(){
        games.pixscape.runtime.render.DynamicEntityRenderState dynamic=new games.pixscape.runtime.render.DynamicEntityRenderState(4);
        games.pixscape.runtime.render.TiledMapRenderState tiled=new games.pixscape.runtime.render.TiledMapRenderState(4);
        games.pixscape.runtime.render.FrameRenderQueue queue=new games.pixscape.runtime.render.FrameRenderQueue(4);
        games.pixscape.runtime.render.LayerStateSOA layers=new games.pixscape.runtime.render.LayerStateSOA(1);layers.enabled[0]=true;
        com.artemis.World world=new com.artemis.World(new com.artemis.WorldConfigurationBuilder().with(
                new games.pixscape.runtime.system.LightingGeometrySystem(dynamic,tiled,queue,layers)).build());
        try {
            int e=world.create(),slot=dynamic.acquireSlotForEntity(e);dynamic.enabled[slot]=true;
            float[] q={-7,3,-4,19,18,22,22,3};
            for(int moved=-1;moved<4;moved++){
                if(moved>=0){q[moved*2]+=3;q[moved*2+1]-=2;}
                dynamic.x1[slot]=q[0];dynamic.y1[slot]=q[1];dynamic.x2[slot]=q[2];dynamic.y2[slot]=q[3];
                dynamic.x3[slot]=q[4];dynamic.y3[slot]=q[5];dynamic.x4[slot]=q[6];dynamic.y4[slot]=q[7];world.process();
                LightingSurface s=queue.lighting.entities.get(e);Assert.assertTrue(s.imageQuad);Assert.assertEquals(48,s.vertices.length);
                Assert.assertTrue("ECS provenance does not require Spatial membership",s.actor);
                Assert.assertNull("Dynamic receivers retain per-frame forward attributes",s.forwardAttributes);
                Assert.assertFalse(s.shadowCaster);Assert.assertEquals(0,queue.lighting.casters.size);
                int[] corners={0,1,2,0,2,3};for(int i=0;i<6;i++){
                    Assert.assertEquals(q[corners[i]*2],LightingCoordinates.screenX(s.vertices[i*8],s.vertices[i*8+1]),.0001f);
                    Assert.assertEquals(q[corners[i]*2+1],LightingCoordinates.screenY(s.vertices[i*8],s.vertices[i*8+1],s.vertices[i*8+2]),.0001f);
                }
            }
            games.pixscape.runtime.component.SurfaceLightingComponent authored=world.getMapper(games.pixscape.runtime.component.SurfaceLightingComponent.class).create(e);
            authored.shadowCaster=true;authored.receiveLight=false;world.process();
            LightingSurface s=queue.lighting.entities.get(e);Assert.assertTrue(s.imageQuad);Assert.assertFalse(s.receiveLight);
            Assert.assertEquals(1,queue.lighting.casters.size);Assert.assertSame(s,queue.lighting.casters.first().surface);
            LocalSurfaceDescription local=new LocalSurfaceDescription();local.xyz=new float[]{0,0,0,1,0,0,0,1,0};
            local.uv=new float[]{0,0,1,0,0,1};local.normals=new float[]{0,0,1,0,0,1,0,0,1};local.triangles=new int[]{0,1,2};
            Assert.assertFalse(LocalSurfaceCompiler.compile(local,0,0,0).imageQuad);
            TiledMapLayerData map=new TiledMapLayerData(4,4,32,16,4,TiledProjection.ISO);
            MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,null);
            Assert.assertFalse(geometry.receiver(map,0,0,7,(byte)0,q).imageQuad);
            Assert.assertFalse("Tiled receivers belong to decor",geometry.receiver(map,0,0,7,(byte)0,q).actor);
            games.pixscape.runtime.component.DimensionsComponent dim=world.getMapper(games.pixscape.runtime.component.DimensionsComponent.class).create(e);
            dim.width=dim.height=2;
            local.xyz=new float[]{-.5f,.5f,0,.5f,-.5f,0,-.5f,.5f,2};
            authored.description=local;world.process();Assert.assertFalse(queue.lighting.entities.get(e).imageQuad);
            Assert.assertTrue("A custom support keeps its entity provenance",queue.lighting.entities.get(e).actor);
            authored.description=null;world.process();Assert.assertTrue(queue.lighting.entities.get(e).imageQuad);
        } finally {world.dispose();}
    }
    @Test public void absoluteAltitudeAndImageProjectionRemainConsistent(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        map.originX=17;map.originY=-36;map.defaultTileAltitude=128;
        float[] xyz=new float[3],image=new float[2];
        for(float plane:new float[]{0,64,128})for(float altitude:new float[]{-20,128,300}){
            map.defaultTileAltitude=plane;
            LightingCoordinates.mapPoint(map,5.25f,8.5f,altitude,xyz,0);
            map.projectSpatialPoint(5.25f,8.5f,altitude,image,0);
            Assert.assertEquals(image[0],LightingCoordinates.screenX(xyz[0],xyz[1]),.001f);
            Assert.assertEquals(image[1],LightingCoordinates.screenY(xyz[0],xyz[1],xyz[2]),.001f);
            Assert.assertEquals(altitude,xyz[2],0);
        }
    }
    @Test public void staticReceiversAreReusedAndRelevantChangesInvalidate(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        MapLightingGeometry geometry=new MapLightingGeometry();
        Assert.assertTrue(geometry.ensure(map,null));float[] quad={0,0,0,128,256,128,256,0};
        LightingSurface first=geometry.receiver(map,0,0,7,(byte)0,quad);
        Assert.assertFalse(geometry.ensure(map,null));
        Assert.assertSame(first,geometry.receiver(map,0,0,7,(byte)0,quad));
        map.defaultTileAltitude=128;Assert.assertTrue(geometry.ensure(map,null));
        LightingSurface raised=geometry.receiver(map,0,0,7,(byte)0,quad);
        Assert.assertNotSame(first,raised);
        for(int i=0;i<raised.vertices.length;i+=8)Assert.assertEquals(128,raised.vertices[i+2],0);
    }
    @Test public void tiledReceiversPrepareExactImmutableForwardAttributesAndRebuildWithGeometry(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        MapLightingGeometry geometry=new MapLightingGeometry();
        Assert.assertTrue(geometry.ensure(map,null));float[] quad={0,0,0,128,256,128,256,0};
        LightingSurface first=geometry.receiver(map,0,0,7,(byte)0,quad);
        assertForwardAttributesMatchFallback(first);
        Assert.assertEquals(first.forwardTopology.vertexCount*11,first.forwardAttributes.length);
        Assert.assertSame(first.forwardAttributes,geometry.receiver(map,0,0,7,(byte)0,quad).forwardAttributes);
        map.defaultTileAltitude=128;Assert.assertTrue(geometry.ensure(map,null));
        LightingSurface raised=geometry.receiver(map,0,0,7,(byte)0,quad);
        Assert.assertNotSame(first,raised);Assert.assertNotSame(first.forwardAttributes,raised.forwardAttributes);
        assertForwardAttributesMatchFallback(raised);
    }
    @Test public void tiledImmutableAttributesLeaveTextureFramesAndShadowPolicyDynamic(){
        TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
        MapLightingGeometry geometry=new MapLightingGeometry();
        Assert.assertTrue(geometry.ensure(map,null));float[] quad={0,0,0,32,64,32,64,0};
        LightingSurface receiver=geometry.receiver(map,0,0,7,(byte)0,quad);
        float[] attributes=receiver.forwardAttributes.clone();
        Assert.assertSame("Texture-only frame changes reuse physical attributes",receiver,geometry.receiver(map,0,0,8,(byte)0,quad));
        int triangles=receiver.vertices.length/24;
        receiver.receiverExclusionIds=new int[triangles];receiver.receiverShadowGroups=new int[triangles];receiver.receiverSpriteShading=new boolean[triangles];
        for(int i=0;i<triangles;i++){receiver.receiverExclusionIds[i]=40+i;receiver.receiverShadowGroups[i]=80+i;receiver.receiverSpriteShading[i]=(i&1)==0;}
        for(int i=0;i<receiver.forwardTopology.vertexCount;i++){
            int source=receiver.forwardTopology.source[i];if(source<0)continue;
            int triangle=source/24;
            Assert.assertEquals(receiver.receiverShadowGroups[triangle],receiver.receiverShadowGroup(source));
            Assert.assertEquals(receiver.receiverSpriteShading[triangle]?-2f:0f,receiver.receiverLightingMode(source),0);
        }
        Assert.assertArrayEquals("Shadow policy remains outside the immutable physical overlay",attributes,receiver.forwardAttributes,0);
    }
    @Test public void changingVolumeAltitudeNeverMovesItsCommonXYAndCustomMeshUsesDrawnPlane(){
        for(TiledProjection projection:TiledProjection.values())for(int width:new int[]{32,128,256}) {
            TiledMapLayerData map=new TiledMapLayerData(8,8,width,width/2,4,projection);
            map.defaultTileAltitude=80;map.originX=17;map.originY=122;
            float[] low=new float[3],high=new float[3];
            LightingCoordinates.mapPoint(map,2.5f,3.75f,12,low,0);
            LightingCoordinates.mapPoint(map,2.5f,3.75f,312,high,0);
            Assert.assertEquals(low[0],high[0],.0001f);Assert.assertEquals(low[1],high[1],.0001f);
            Assert.assertEquals(300,high[2]-low[2],0);
            LocalSurfaceDescription mesh=new LocalSurfaceDescription();
            mesh.xyz=new float[]{0,0,7,2,0,7,0,2,7};mesh.uv=new float[]{0,0,1,0,0,1};
            mesh.normals=new float[]{0,0,1,0,0,1,0,0,1};mesh.triangles=new int[]{0,1,2};
            LightingCoordinates.mapPoint(map,2,3,map.defaultTileAltitude,low,0);
            LightingSurface surface=LocalSurfaceCompiler.compile(mesh,low[0],low[1],low[2]);
            Assert.assertEquals(87,surface.vertices[2],0);
        }
        TiledMapLayerData map=new TiledMapLayerData(8,8,32,16,4,TiledProjection.ISO);map.defaultTileAltitude=80;
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();SpatialBlockData wall=block(1,1,1,2,.2f,70);
        wall.altitude=120;wall.shadowCaster=true;blocks.blocks.add(wall);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(LightingSurface caster:geometry.casters)for(int i=2;i<caster.vertices.length;i+=8){min=Math.min(min,caster.vertices[i]);max=Math.max(max,caster.vertices[i]);}
        Assert.assertEquals(120,min,0);Assert.assertEquals(190,max,0);
    }
    @Test public void coplanarBandCoordinatesAreBitExactAcrossEndpointsAndAltitudes(){
        TiledMapLayerData map=new TiledMapLayerData(64,64,256,128,16,TiledProjection.ISO);
        map.defaultTileAltitude=158.07935f;float[] start=new float[3],end=new float[3];
        LightingCoordinates.mapPoint(map,30.765005f,16,0,start,0);
        for(float y:new float[]{16,28.981766f,37.232086f})for(float z:new float[]{0,158.07935f,1000000}){
            LightingCoordinates.mapPoint(map,30.765005f,y,z,end,0);
            Assert.assertEquals(Float.floatToIntBits(start[0]),Float.floatToIntBits(end[0]));
        }
    }
    @Test public void casterFlagsAreAuthoredAndAllMembershipsSurvive(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        SpatialBlockData a=block(1,0,0,1,1,128),b=block(2,1,0,1,1,160);
        a.addLinkedTileRef(0,0,7);b.addLinkedTileRef(0,0,7);blocks.blocks.add(a);blocks.blocks.add(b);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        Assert.assertEquals(0,geometry.casters.size);
        float[] quad={-256,-128,-256,512,512,512,512,-128};
        LightingSurface receiver=geometry.receiver(map,0,0,7,(byte)0,quad);
        boolean first=false,second=false;
        for(int i=0;i<receiver.vertices.length;i+=8){float z=receiver.vertices[i+2];first|=Math.abs(z-128)<.01f;second|=Math.abs(z-160)<.01f;}
        Assert.assertTrue(first);Assert.assertTrue(second);
        a.shadowCaster=true;blocks.revision++;geometry.ensure(map,blocks);
        Assert.assertEquals(0,geometry.casters.size);
        Assert.assertEquals(1,geometry.receiver(map,0,0,7,(byte)0,quad).casterContributions.size);
        b.lightOccluder=true;blocks.revision++;geometry.ensure(map,blocks);
        Assert.assertEquals(0,geometry.casters.size);
        Assert.assertEquals(2,geometry.receiver(map,0,0,7,(byte)0,quad).casterContributions.size);
    }
    @Test public void linkedSilhouetteCasterContributesWithoutDiagnosticOverrides(){
        TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();SpatialBlockData barrel=block(1,2.1f,3.2f,.75f,.63f,54.244385f);
        barrel.shadowCaster=true;barrel.silhouetteShadowCaster=true;barrel.addLinkedTileRef(2,3,1885);blocks.blocks.add(barrel);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        Assert.assertEquals("The white static volume must be removed in B",0,geometry.casters.size);
        float[] quad=new float[8];map.tileToRenderQuad(2,3,quad);LightingSurface tile=geometry.receiver(map,2,3,1885,(byte)0,quad);
        Assert.assertTrue(tile.shadowCaster);Assert.assertNotNull(tile.casterSurface);
        LightingSurface caster=tile.casterSurface;Assert.assertTrue(caster.vertices.length>0);
        for(int i=0;i<caster.vertices.length;i+=8){
            Assert.assertTrue(caster.vertices[i+3]>=-.000001f&&caster.vertices[i+3]<=1.000001f);
            Assert.assertTrue(caster.vertices[i+4]>=-.000001f&&caster.vertices[i+4]<=1.000001f);
            Assert.assertEquals(quad[0]+caster.vertices[i+3]*(quad[6]-quad[0])+caster.vertices[i+4]*(quad[2]-quad[0]),
                    LightingCoordinates.screenX(caster.vertices[i],caster.vertices[i+1]),.0001f);
            Assert.assertEquals(quad[1]+caster.vertices[i+3]*(quad[7]-quad[1])+caster.vertices[i+4]*(quad[3]-quad[1]),
                    LightingCoordinates.screenY(caster.vertices[i],caster.vertices[i+1],caster.vertices[i+2]),.0001f);
        }
    }
    @Test public void authoredNonCasterIsExcludedWhileAnotherLinkedCasterStillContributes(){
        TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        SpatialBlockData excluded=block(44,2.1f,3.2f,.75f,.63f,54.244385f);
        excluded.silhouetteShadowCaster=true;excluded.addLinkedTileRef(2,3,1885);blocks.blocks.add(excluded);
        SpatialBlockData included=block(45,3.1f,3.2f,.75f,.63f,54.244385f);
        included.shadowCaster=true;included.silhouetteShadowCaster=true;included.addLinkedTileRef(3,3,1885);blocks.blocks.add(included);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        float[] excludedQuad=new float[8];map.tileToRenderQuad(2,3,excludedQuad);LightingSurface excludedTile=geometry.receiver(map,2,3,1885,(byte)0,excludedQuad);
        Assert.assertFalse(excludedTile.shadowCaster);Assert.assertNull(excludedTile.casterSurface);
        Assert.assertTrue("The directly excluded volume keeps normal receiver coverage",excludedTile.vertices.length>0);
        Assert.assertEquals("No full-volume caster is admitted",0,geometry.casters.size);
        float[] includedQuad=new float[8];map.tileToRenderQuad(3,3,includedQuad);LightingSurface includedTile=geometry.receiver(map,3,3,1885,(byte)0,includedQuad);
        Assert.assertTrue("The fixture must prove that a real linked caster still contributes",includedTile.shadowCaster);
        Assert.assertNotNull(includedTile.casterSurface);
    }
    @Test public void barrelSupportCoversTheRecordedOpaqueImageExtent(){
        // Real copied-scene support: tile (26,29), 256x512 bottom-centred image, block 44.
        TiledMapLayerData map=new TiledMapLayerData(50,50,256,128,16,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();SpatialBlockData barrel=block(44,26.153797f,29.179745f,.7476578f,.62960434f,54.244385f);
        barrel.shadowCaster=true;barrel.silhouetteShadowCaster=true;barrel.addLinkedTileRef(26,29,1885);blocks.blocks.add(barrel);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        // Native tileset profile: image is 256x512 and anchored at the cell's bottom centre.
        float cellX=(26-29)*128f,cellY=(26+29)*64f;
        float[] quad={cellX,cellY,cellX,cellY+512,cellX+256,cellY+512,cellX+256,cellY};
        LightingSurface caster=geometry.receiver(map,26,29,1885,(byte)0,quad).casterSurface;
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        for(int i=0;i<caster.vertices.length;i+=8){minU=Math.min(minU,caster.vertices[i+3]);maxU=Math.max(maxU,caster.vertices[i+3]);minV=Math.min(minV,caster.vertices[i+4]);maxV=Math.max(maxV,caster.vertices[i+4]);}
        // Alpha>=.5 bounds measured from the real source: x=[66,189], v=[41,154] / 511.
        Assert.assertTrue(minU<=66f/255);Assert.assertTrue(maxU>=189f/255);
        Assert.assertTrue(minV<=41f/511);Assert.assertTrue(maxV>=154f/511);
    }
    @Test public void silhouetteCasterAdmitsSeveralAuthoredCellContributions(){
        TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();SpatialBlockData block=block(1,1,1,1,1,32);
        block.shadowCaster=true;block.silhouetteShadowCaster=true;block.addLinkedTileRef(1,1,7);block.addLinkedTileRef(2,1,7);blocks.blocks.add(block);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        Assert.assertEquals(0,geometry.casters.size);
    }
    @Test public void casterIndexDoesNotDependOnVisibleImagesAndMarginDoesNotChangeRadius(){
        LightingFrame frame=new LightingFrame();LightingFrame.Light light=new LightingFrame.Light();light.radius=128;
        LightingFrame.Caster caster=new LightingFrame.Caster();caster.minX=200;caster.maxX=210;caster.minY=-10;caster.maxY=10;caster.minZ=0;caster.maxZ=100;
        frame.casters.add(caster);frame.staticCasterCount=1;frame.casterIndex.rebuild(frame,1);
        frame.casterIndex.query(frame,light,256,1);Assert.assertEquals(1,frame.casterIndex.candidates.size);
        Assert.assertEquals(128,light.radius,0);
        frame.casterIndex.query(frame,light,0,1);Assert.assertEquals(0,frame.casterIndex.candidates.size);
    }
    @Test public void localDescriptionRejectsInvalidTopologyAndCopiesAuthoredValues(){
        LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[]{0,0,0,1,0,0,0,1,0};
        d.uv=new float[]{0,0,1,0,0,1};d.normals=new float[]{0,0,1,0,0,1,0,0,1};d.triangles=new int[]{0,1,2};
        d.validate();LocalSurfaceDescription copy=d.copy();d.xyz[0]=42;Assert.assertEquals(0,copy.xyz[0],0);
        d.triangles[2]=3;try{d.validate();Assert.fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void partialLocalMeshRetainsUnlitCoverageAndRejectsOverlaps(){
        LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[]{0,0,0,1,0,0,0,1,0};
        d.uv=new float[]{0,0,1,0,0,1};d.normals=new float[]{0,0,1,0,0,1,0,0,1};d.triangles=new int[]{0,1,2};
        LightingSurface surface=LocalSurfaceCompiler.compile(d,0,0,0);float area=0;boolean missing=false;
        for(int i=0;i<surface.vertices.length;i+=24){float[] v=surface.vertices;
            area+=Math.abs((v[i+11]-v[i+3])*(v[i+20]-v[i+4])-(v[i+19]-v[i+3])*(v[i+12]-v[i+4]))*.5f;
            missing|=v[i+5]==0&&v[i+6]==0&&v[i+7]==0;
        }
        Assert.assertEquals(1,area,.0001f);Assert.assertTrue(missing);
        d.triangles=new int[]{0,1,2,0,1,2};
        try{LocalSurfaceCompiler.compile(d,0,0,0);Assert.fail();}catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("overlap"));}
    }
    @Test public void coverageCompletionPreservesEveryValidReceiverAndTheCasterGeometry(){
        TiledMapLayerData map=new TiledMapLayerData(4,4,32,16,4,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();SpatialBlockData wall=block(1,0,0,2,.25f,16);
        wall.shadowCaster=true;wall.addLinkedTileRef(0,0,7);blocks.blocks.add(wall);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        float[] quad={-64,-32,-64,64,96,64,96,-32};LightingSurface surface=geometry.receiver(map,0,0,7,(byte)0,quad);
        float[] original=surface.vertices.clone();Assert.assertNotNull(surface.receiverVertices);Assert.assertEquals(original.length,surface.receiverVertices.length);
        int valid=0,completed=0;
        for(int i=0;i<original.length;i+=8){
            boolean missing=original[i+5]==0&&original[i+6]==0&&original[i+7]==0;float[] p=surface.receiverVertices;
            if(!missing){valid++;for(int k=0;k<8;k++)Assert.assertEquals(Float.floatToRawIntBits(original[i+k]),Float.floatToRawIntBits(p[i+k]));}
            else{completed++;Assert.assertTrue(p[i+5]*p[i+5]+p[i+6]*p[i+6]+p[i+7]*p[i+7]>.5f);}
            Assert.assertEquals(original[i+3],p[i+3],0);Assert.assertEquals(original[i+4],p[i+4],0);
            Assert.assertEquals(quad[0]+p[i+3]*(quad[6]-quad[0])+p[i+4]*(quad[2]-quad[0]),LightingCoordinates.screenX(p[i],p[i+1]),.001f);
            Assert.assertEquals(quad[1]+p[i+3]*(quad[7]-quad[1])+p[i+4]*(quad[3]-quad[1]),LightingCoordinates.screenY(p[i],p[i+1],p[i+2]),.001f);
        }
        Assert.assertTrue(valid>0);Assert.assertTrue(completed>0);Assert.assertArrayEquals(original,surface.vertices,0);
        Assert.assertSame(surface,geometry.receiver(map,0,0,7,(byte)0,quad));Assert.assertEquals(0,geometry.casters.size);
        Assert.assertNull(surface.casterSurface.receiverVertices);
        Assert.assertFalse("Compiled map volumes belong to decor",surface.casterSurface.actor);
    }
    @Test public void fullyDescribedGroundNeedsNoCompletionAndPartialLocalSurfacesKeepTheirValidVertices(){
        TiledMapLayerData map=new TiledMapLayerData(4,4,32,16,4,TiledProjection.ISO);map.defaultTileAltitude=42;
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,null);float[] quad={0,0,0,32,64,32,64,0};
        LightingSurface ground=geometry.receiver(map,0,0,7,(byte)0,quad);Assert.assertNull(ground.receiverVertices);
        for(int i=2;i<ground.vertices.length;i+=8)Assert.assertEquals(42,ground.vertices[i],0);
        LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[]{0,0,0,32,0,0,0,0,16};d.uv=new float[]{0,0,1,0,0,1};
        d.normals=new float[]{0,-1,0,0,-1,0,0,-1,0};d.triangles=new int[]{0,1,2};
        LightingSurface local=LocalSurfaceCompiler.compile(d,3,7,11);Assert.assertNotNull(local.receiverVertices);
        for(int i=0;i<24;i++)Assert.assertEquals(Float.floatToRawIntBits(local.vertices[i]),Float.floatToRawIntBits(local.receiverVertices[i]));
    }
    private static SpatialBlockData block(int id,float x,float y,float w,float depth,float height){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=id;b.x=x;b.y=y;b.width=w;b.depth=depth;b.height=height;return b;
    }
    private static void assertForwardAttributesMatchFallback(LightingSurface surface){
        Assert.assertNotNull(surface.forwardAttributes);
        float[] physical=surface.receiverVertices==null?surface.vertices:surface.receiverVertices;
        for(int i=0;i<surface.forwardTopology.vertexCount;i++){
            int source=surface.forwardTopology.source[i];
            if(source<0){
                for(int component=0;component<11;component++)Assert.assertEquals(0,surface.forwardAttributes[i*11+component],0);
                continue;
            }
            int weights=i*5,attributes=i*11;
            float a=surface.forwardTopology.weights[weights+1],b=surface.forwardTopology.weights[weights+2],c=1-a-b;
            for(int component=0;component<3;component++){
                Assert.assertEquals(a*physical[source+component]+b*physical[source+8+component]+c*physical[source+16+component],surface.forwardAttributes[attributes+component],0);
                Assert.assertEquals(a*physical[source+5+component]+b*physical[source+13+component]+c*physical[source+21+component],surface.forwardAttributes[attributes+3+component],0);
                int curve=source/8*3;
                Assert.assertEquals(surface.curveNormals==null?0:a*surface.curveNormals[curve+component]+b*surface.curveNormals[curve+3+component]+c*surface.curveNormals[curve+6+component],surface.forwardAttributes[attributes+8+component],0);
            }
            Assert.assertEquals(a*physical[source+3]+b*physical[source+11]+c*physical[source+19],surface.forwardAttributes[attributes+6],0);
            Assert.assertEquals(a*physical[source+4]+b*physical[source+12]+c*physical[source+20],surface.forwardAttributes[attributes+7],0);
        }
    }
}
