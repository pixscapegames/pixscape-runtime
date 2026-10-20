package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileAlphaCompilationTest {
    private static final float[] LARGE={-512,-256,-512,512,512,512,512,-256};
    private static TiledMapLayerData map(){TiledMapLayerData m=new TiledMapLayerData();m.projection=TiledProjection.ISO;m.tileWidth=64;m.tileHeight=32;return m;}
    private static SpatialBlockData wall(int id,float w,float d){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.width=w;b.depth=d;b.height=32;b.shadowCaster=true;
        for(int y=0;y<(int)Math.ceil(d);y++)for(int x=0;x<(int)Math.ceil(w);x++)b.addLinkedTileRef(x,y,7);
        return b;
    }
    private static MapLightingGeometry geometry(TiledMapLayerData map,TileShadowMode mode,SpatialBlockData... values){
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();for(SpatialBlockData b:values)blocks.blocks.add(b);
        MapLightingGeometry g=new MapLightingGeometry();g.mode(mode);assertTrue(g.ensure(map,blocks));return g;
    }
    private static double area(MapLightingGeometry g){
        double area=0;
        for(LightingSurface s:g.casters)for(int i=0;i<s.vertices.length;i+=24){float[] p=s.vertices;
            double ax=p[i+8]-p[i],ay=p[i+9]-p[i+1],az=p[i+10]-p[i+2],bx=p[i+16]-p[i],by=p[i+17]-p[i+1],bz=p[i+18]-p[i+2];
            double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;area+=Math.sqrt(x*x+y*y+z*z)*.5;
        }return area;
    }
    @Test public void oneCellCannotDeleteASharedLongWallAndRemovingTheImageRestoresIt(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        double before=area(g);assertEquals(10240,before,.01);
        LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE);assertNotNull(s.alphaSheet);assertTrue(s.shadowCaster);
        assertTrue(g.finishAlphaSheets(m));assertEquals(7577.6,area(g),.05);
        assertTrue(g.casters.size<=6);assertEquals(1,g.coverage().tiles);assertTrue(g.coverage().replacedPatches>0);
        for(int i=0;i<1000;i++){assertSame(s,g.receiver(m,0,0,8,(byte)0,LARGE));assertFalse(g.finishAlphaSheets(m));}
        g.forget(0,0);assertTrue(g.finishAlphaSheets(m));assertEquals(before,area(g),0);
        g.forget(0,0);assertFalse(g.finishAlphaSheets(m));
    }
    @Test public void replacingAllCellsRemovesExactlyTheirCoveredOpaqueFaces(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_PLANE,wall(1,4,.2f));
        for(int x=0;x<4;x++)g.receiver(m,x,0,7,(byte)0,LARGE);
        g.finishAlphaSheets(m);assertEquals(0,area(g),.01);assertEquals(0,g.casters.size);
    }
    @Test public void uncoveredImagePatchesAndUnlinkedVolumesStayOpaque(){
        TiledMapLayerData m=map();SpatialBlockData a=wall(1,4,.2f),b=wall(2,1,1);b.x=10;b.linkedTileRefs.clear();
        MapLightingGeometry g=geometry(m,TileShadowMode.V3_PLANE,a,b);double before=area(g);
        float[] q={20,-10,20,40,40,40,40,-10};g.receiver(m,0,0,7,(byte)0,q);g.finishAlphaSheets(m);
        assertTrue(area(g)<before);assertTrue(area(g)>before-2662.4);
        boolean found=false;for(LightingSurface face:g.casters)if(face.sourceBlockId==2){found=true;assertEquals(48,face.vertices.length);}assertTrue(found);
    }
    @Test public void fixedAdjacentWallSheetsKeepTheirContactAndSharedEdgeAcrossEveryLight(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        float[][] q={{28.8f,1.6f,28.8f,33.6f,60.8f,49.6f,60.8f,17.6f},{60.8f,17.6f,60.8f,49.6f,92.8f,65.6f,92.8f,33.6f}};
        LightingSurface a=g.receiver(m,0,0,7,(byte)0,q[0]),b=g.receiver(m,1,0,7,(byte)0,q[1]);
        assertNotNull(a.alphaSheet);assertNotNull(b.alphaSheet);
        for(int ia:new int[]{16,32,40})for(int ib:new int[]{0,8,24})if(a.vertices[ia+4]==b.vertices[ib+4])
            for(int k=0;k<3;k++)assertEquals(a.vertices[ia+k],b.vertices[ib+k],.00005f);
        LightingFrame.Light l=new LightingFrame.Light();float[] frozen=a.vertices.clone();
        for(int i=0;i<720;i++){l.x=(float)Math.cos(i*.02)*500;l.y=(float)Math.sin(i*.02)*500;l.z=i%3*100;
            assertSame(a.vertices,a.alphaSheet.forLight(l));assertArrayEquals(frozen,a.vertices,0);
        }
        assertEquals(1,a.alphaSheet.transformations);
        // Inverse-transpose Ng of Y + 0.1*(Z-contactZ), before any normal map.
        assertEquals(0,a.vertices[5],0);assertEquals(1/Math.sqrt(1.01),a.vertices[6],.00001);assertEquals(-.1/Math.sqrt(1.01),a.vertices[7],.00001);
        assertEquals(0,a.vertices[2],.00001);assertEquals(-12.8,a.vertices[1],.00005);
    }
    @Test public void progressiveHasOneScratchAndContinuousPlanarTransform(){
        float[] data={0,0,0,0,0,0,1,0,10,0,20,1,1,0,1,0,0,0,20,0,1,0,1,0};
        TileAlphaSheet s=new TileAlphaSheet(data,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,24,0,0,0,0,1,0,20,2)},TileShadowMode.V3_PROGRESSIVE);
        LightingFrame.Light l=new LightingFrame.Light();float[] scratch=null;float previous=Float.NaN;
        for(int i=-1000;i<=1000;i++){l.x=100;l.y=i*.001f;l.z=10;float[] v=s.forLight(l);if(scratch==null)scratch=v;else assertSame(scratch,v);
            if(previous==previous)assertTrue(Math.abs(previous-v[9])<.001f);previous=v[9];
            assertEquals(0,v[0],0);assertEquals(0,v[1],0);assertEquals(20,v[10],0);assertEquals(1,v[11],0);
            assertEquals(0,v[5]*v[8]+v[6]*v[9]+v[7]*v[10],.0001);
        }
        assertNotSame(data,scratch);assertSame(scratch,s.forLight(l));
    }
    @Test public void cornerAdmissionIsDeterministicAndInclinedAmbiguityIsExplicit(){
        TiledMapLayerData m=map();SpatialBlockData a=wall(1,4,.2f),b=wall(2,.2f,4);
        for(TileShadowMode mode:new TileShadowMode[]{TileShadowMode.V3_PLANE,TileShadowMode.V3_FIXED,TileShadowMode.V3_PROGRESSIVE}){
            MapLightingGeometry g=geometry(m,mode,a,b),h=geometry(m,mode,b,a);
            LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE),t=h.receiver(m,0,0,7,(byte)0,LARGE);
            assertArrayEquals(s.vertices,t.vertices,0);g.finishAlphaSheets(m);h.finishAlphaSheets(m);
            assertEquals(area(g),area(h),.00001);assertTrue(g.coverage().reasons.size>0);
            if(mode!=TileShadowMode.V3_PLANE)assertTrue(g.coverage().fallback>0);
        }
    }
    @Test public void flipsKeepTheirActualUvAndModeChangesRebuildOnce(){
        TiledMapLayerData m=map();SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(wall(1,4,.2f));
        MapLightingGeometry g=new MapLightingGeometry();g.mode(TileShadowMode.V3_FIXED);g.ensure(m,blocks);
        float[] mirror={512,-256,512,512,-512,512,-512,-256};LightingSurface s=g.receiver(m,0,0,7,(byte)1,mirror);assertNotNull(s.alphaSheet);
        LightingSurface v2=geometry(m,TileShadowMode.V2,blocks.blocks.first()).receiver(m,0,0,7,(byte)0,mirror);
        assertEquals(v2.vertices.length,s.vertices.length);
        for(int i=0;i<s.vertices.length;i+=8){assertEquals(v2.vertices[i+3],s.vertices[i+3],0);assertEquals(v2.vertices[i+4],s.vertices[i+4],0);}
        for(int i=0;i<100;i++)assertFalse(g.ensure(m,blocks));
        g.mode(TileShadowMode.V3_PLANE);assertTrue(g.ensure(m,blocks));assertNotSame(s,g.receiver(m,0,0,7,(byte)1,mirror));
        g.mode(TileShadowMode.V2);assertTrue(g.ensure(m,blocks));assertNull(g.receiver(m,0,0,7,(byte)0,LARGE).alphaSheet);
    }
    @Test public void hundredsOfRealReceiverCellsReplaceOnlyTheirOwnSparseRows(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,320,.2f));
        for(int x=0;x<320;x+=2){float[] q=LARGE.clone();for(int i=0;i<q.length;i+=2){q[i]+=x*32;q[i+1]+=x*16;}assertNotNull(g.receiver(m,x,0,7,(byte)0,q).alphaSheet);}
        g.finishAlphaSheets(m);assertEquals(160,g.coverage().tiles);assertTrue(area(g)>0);assertTrue(g.casters.size<=6);
    }
    @Test public void stackedContactsAcrossMapsRetainV2ButDisjointAndSameLevelWallsDoNot(){
        TiledMapLayerData a=map(),b=map();SpatialBlocksComponent low=new SpatialBlocksComponent(),high=new SpatialBlocksComponent();
        low.blocks.add(wall(1,4,.2f));SpatialBlockData upper=wall(2,4,.2f);upper.altitude=32.5f;high.blocks.add(upper);
        TileAlphaContactIndex index=new TileAlphaContactIndex();index.begin();index.check(7,a,low);index.check(8,b,high);assertTrue(index.needsRebuild());
        index.clear();index.add(7,a,low);index.add(8,b,high);index.compile();assertNotNull(index.contacts(7).rejection(1,0,0));assertNotNull(index.contacts(8).rejection(2,0,0));
        index.begin();index.check(7,a,low);index.check(8,b,high);assertFalse(index.needsRebuild());
        MapLightingGeometry g=geometry(a,TileShadowMode.V3_FIXED,low.blocks.first());g.contacts(index.contacts(7));g.ensure(a,low);
        assertNull(g.receiver(a,0,0,7,(byte)0,LARGE).alphaSheet);g.finishAlphaSheets(a);assertEquals(10240,area(g),.01);
        upper.x=20;high.revision++;index.begin();index.check(7,a,low);index.check(8,b,high);assertTrue(index.needsRebuild());
        index.clear();index.add(7,a,low);index.add(8,b,high);index.compile();assertNull(index.contacts(7).rejection(1));assertNull(index.contacts(8).rejection(2));
        upper.x=0;upper.altitude=0;high.revision++;index.clear();index.add(7,a,low);index.add(8,b,high);index.compile();assertNull(index.contacts(7).rejection(1));
        index.begin();index.check(7,a,low);assertTrue(index.needsRebuild());
    }
    @Test public void disabledCasterMembershipNeverInventsAnAlphaCaster(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(1,4,.2f);b.shadowCaster=false;
        for(TileShadowMode mode:TileShadowMode.values()){
            MapLightingGeometry g=geometry(m,mode,b);LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE);
            assertFalse(s.shadowCaster);assertNull(s.alphaSheet);g.finishAlphaSheets(m);assertEquals(0,g.casters.size);
        }
    }
    @Test public void animationWithChangingPlacementUsesPreparedV2FramesAndConstantTimeSelection(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        float[] second=LARGE.clone();second[2]+=10;second[4]+=10;
        g.prepareAnimation(m,0,0,42,(byte)0,new int[]{100,101,100},new float[][]{LARGE,second,LARGE});
        assertTrue(g.animationPrepared(0,0,42,(byte)0));assertFalse(g.animationPrepared(0,0,43,(byte)0));
        LightingSurface a=g.receiver(m,0,0,42,100,(byte)0,LARGE),b=g.receiver(m,0,0,42,101,(byte)0,second);
        assertNotSame(a,b);assertNull(a.alphaSheet);assertNull(b.alphaSheet);assertFalse(a.shadowCaster);assertFalse(b.shadowCaster);
        assertTrue(g.finishAlphaSheets(m));assertEquals(10240,area(g),.01);
        for(int i=0;i<10000;i++){assertSame(a,g.receiver(m,0,0,42,100,(byte)0,LARGE));assertSame(b,g.receiver(m,0,0,42,101,(byte)0,second));assertFalse(g.finishAlphaSheets(m));}
        assertTrue(g.coverage().reasons.size>0);
        try{g.receiver(m,0,0,42,102,(byte)0,LARGE);fail("Unprepared animation frame must be explicit");}catch(IllegalStateException expected){}
        g.forget(0,0);assertFalse(g.animationPrepared(0,0,42,(byte)0));
    }
    @Test public void animationWithStablePlacementSharesOneAlphaCarrierAcrossSourcePages(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        g.prepareAnimation(m,0,0,42,(byte)0,new int[]{100,101},new float[][]{LARGE,LARGE.clone()});
        LightingSurface a=g.receiver(m,0,0,42,100,(byte)0,LARGE);assertNotNull(a.alphaSheet);
        assertSame(a,g.receiver(m,0,0,42,101,(byte)0,LARGE));assertTrue(g.animationPrepared(0,0,42,(byte)0));
    }
    @Test public void zeroHeightPartialFloorRemovesBothCoplanarOpaqueSides(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(1,1,1);b.height=0;
        MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,b);assertEquals(2048,area(g),.01);
        LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE);assertNotNull(s.alphaSheet);g.finishAlphaSheets(m);assertEquals(0,area(g),.01);
    }
    @Test public void shortThinWallsUseTheirAxialSupportWithoutChangingTheLegacyCarrierChoice(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(1,1,.2f);MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,b);
        LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE);assertNotNull(s.alphaSheet);assertEquals(0,s.alphaSheet.parts[0].nx,0);assertEquals(1,s.alphaSheet.parts[0].ny,0);
        com.badlogic.gdx.utils.Array<SpatialBlockData> owners=new com.badlogic.gdx.utils.Array<SpatialBlockData>();owners.add(b);
        assertEquals(TileShadowSupport.BILLBOARD,new TileShadowSupport(m,0,0,LARGE,owners,s).orientation);
    }
    @Test public void authoredCurvedSupportKeepsPriorityAndNeverGetsAGenericSheet(){
        TiledMapLayerData m=map();LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[18];d.uv=new float[12];d.normals=new float[18];
        for(int c=0;c<3;c++)for(int v=0;v<2;v++){
            int k=c*2+v;float u=c*.5f,sag=c==1?2:0;
            d.xyz[k*3]=-sag+u*16-16;d.xyz[k*3+1]=-sag-u*16+16;d.xyz[k*3+2]=v*32+sag;
            d.uv[k*2]=u;d.uv[k*2+1]=v;d.normals[k*3]=d.normals[k*3+1]=(float)Math.sqrt(.5);
        }
        d.triangles=new int[]{0,1,3,0,3,2,2,3,5,2,5,4};d.shadowCaster=true;d.twoSided=true;
        TileSurfaceDescription t=new TileSurfaceDescription();t.assetId=7;t.geometry=d;
        com.badlogic.gdx.utils.Array<TileSurfaceDescription> descriptions=new com.badlogic.gdx.utils.Array<TileSurfaceDescription>();descriptions.add(t);
        MapLightingGeometry g=new MapLightingGeometry();g.mode(TileShadowMode.V3_FIXED);g.descriptions(descriptions);g.ensure(m,null);
        LightingSurface s=g.receiver(m,0,0,7,(byte)0,new float[]{0,0,0,32,32,32,32,0});
        assertNull(s.alphaSheet);assertTrue(s.shadowCaster);assertTrue(s.twoSided);assertEquals(96,s.vertices.length);g.finishAlphaSheets(m);assertEquals(1,g.coverage().authored);assertEquals(0,g.coverage().vertical);
        assertEquals(2,s.vertices[42],0); // Authored curvature remains in the middle column's lower vertex.
    }
    @Test public void duplicateBlockIdentitiesAreRejectedBeforeReplacementIndexesPublish(){
        try{geometry(map(),TileShadowMode.V3_FIXED,wall(1,4,.2f),wall(1,.2f,4));fail("Duplicate volume identities must be explicit");}catch(IllegalArgumentException expected){}
    }
    @Test public void temporaryMaterialLossRestoresOpaqueCoverageAndRebindsTheSamePreparedGeometry(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        LightingSurface s=g.receiver(m,0,0,7,(byte)0,LARGE);g.finishAlphaSheets(m);assertTrue(area(g)<10240);
        g.suspend(0,0);g.finishAlphaSheets(m);assertEquals(10240,area(g),.01);
        for(int i=0;i<100;i++){g.suspend(0,0);assertFalse(g.finishAlphaSheets(m));}
        assertSame(s,g.receiver(m,0,0,7,(byte)0,LARGE));g.finishAlphaSheets(m);assertTrue(area(g)<10240);
    }
    @Test public void unresolvedAnimationMaterialHasANegativeCertificationAndPreparedV2Selection(){
        TiledMapLayerData m=map();MapLightingGeometry g=geometry(m,TileShadowMode.V3_FIXED,wall(1,4,.2f));
        g.deferAnimation(m,0,0,42,100,(byte)0,LARGE);assertTrue(g.animationPrepared(0,0,42,(byte)0));
        LightingSurface a=g.receiver(m,0,0,42,100,(byte)0,LARGE);float[] q=LARGE.clone();q[3]+=20;q[5]+=20;
        LightingSurface b=g.receiver(m,0,0,42,101,(byte)0,q);assertNull(a.alphaSheet);assertNull(b.alphaSheet);g.finishAlphaSheets(m);
        for(int i=0;i<1000;i++){assertSame(a,g.receiver(m,0,0,42,100,(byte)0,LARGE));assertSame(b,g.receiver(m,0,0,42,101,(byte)0,q));assertFalse(g.finishAlphaSheets(m));}
        g.suspend(0,0);g.finishAlphaSheets(m);assertTrue(g.animationPrepared(0,0,42,(byte)0));
        g.prepareAnimation(m,0,0,42,(byte)0,new int[]{100,101},new float[][]{LARGE,LARGE});
        assertNotNull(g.receiver(m,0,0,42,100,(byte)0,LARGE).alphaSheet);
    }
    @Test public void horizontalProgressiveSheetsNeedNoScratchAndUnchangedCoefficientsDoNotRewriteVertices(){
        float[] floor={0,0,0,0,0,0,0,1,10,0,0,1,0,0,0,1,0,10,0,0,1,0,0,1};
        TileAlphaSheet horizontal=new TileAlphaSheet(floor,new TileAlphaSheet.Part[0],TileShadowMode.V3_PROGRESSIVE);
        LightingFrame.Light l=new LightingFrame.Light();for(int i=0;i<100;i++){l.x=i;l.y=i;l.z=i;assertSame(floor,horizontal.forLight(l));}assertEquals(0,horizontal.transformations);
        float[] v={0,0,0,0,0,0,1,0,10,0,20,1,1,0,1,0,0,0,20,0,1,0,1,0};
        TileAlphaSheet vertical=new TileAlphaSheet(v,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,24,0,0,0,0,1,0,20,2)},TileShadowMode.V3_PROGRESSIVE);
        for(int i=0;i<100;i++){l.x=0;l.y=100+i;l.z=10;assertSame(v,vertical.forLight(l));}assertEquals(0,vertical.transformations);
        l.x=100;l.y=0;l.z=10;float[] inclined=vertical.forLight(l);assertNotSame(v,inclined);assertEquals(1,vertical.transformations);
        l.x=200;assertSame(inclined,vertical.forLight(l));assertEquals(1,vertical.transformations);
        l.y=200;assertSame(v,vertical.forLight(l));assertEquals(1,vertical.transformations);
    }
}
