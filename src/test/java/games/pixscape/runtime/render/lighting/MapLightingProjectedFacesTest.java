package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

public class MapLightingProjectedFacesTest {
    @Test public void lightingPolicyFollowsEachPartitionWithoutChangingGeometryOrExclusion(){
        TiledMapLayerData map=map(32,16);
        SpatialBlockData a=block(8,0,0,4,.5f,0,200),b=block(1,2,-2,.5f,4,0,200);
        a.structureId=b.structureId=7;
        LightingSurface before=receiver(map,QUAD,a,b);
        a.shadeUnlitFaces=false;
        LightingSurface after=receiver(map,QUAD,a,b);
        samePartition(before,after);
        Assert.assertArrayEquals(before.receiverBlockIds,after.receiverBlockIds);
        int changed=0,unchanged=0;
        for(int i=0;i<after.vertices.length;i+=24){
            int owner=after.receiverBlockIds[i/24];
            if(owner==8){Assert.assertEquals(-2f,after.receiverLightingMode(i),0);changed++;}
            if(owner==1){Assert.assertEquals(0f,after.receiverLightingMode(i),0);unchanged++;}
            Assert.assertEquals(before.receiverShadowGroup(i),after.receiverShadowGroup(i));
        }
        Assert.assertTrue(changed>0 && unchanged>0);
        for(int i=0;i<before.casterContributions.size;i++)Assert.assertArrayEquals(before.casterContributions.get(i).vertices,after.casterContributions.get(i).vertices,0);
        b.shadeUnlitFaces=false;
        LightingSurface all=receiver(map,QUAD,a,b);
        for(int i=0;i<all.vertices.length;i+=24)Assert.assertEquals(-2f,all.receiverLightingMode(i),0);
    }
    @Test public void policyRebuildFollowsAuthoredRevisionAndThenReusesTheCache(){
        TiledMapLayerData map=map(32,16);SpatialBlockData a=block(8,0,0,4,.5f,0,200);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(a);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        LightingSurface before=geometry.receiver(map,0,0,7,(byte)0,QUAD);
        a.shadeUnlitFaces=false;blocks.revision++;
        Assert.assertTrue(geometry.ensure(map,blocks));
        LightingSurface after=geometry.receiver(map,0,0,7,(byte)0,QUAD);
        Assert.assertNotSame(before,after);samePartition(before,after);
        for(int i=0;i<after.vertices.length;i+=24)Assert.assertEquals(-2f,after.receiverLightingMode(i),0);
        Assert.assertFalse(geometry.ensure(map,blocks));
        Assert.assertSame(after,geometry.receiver(map,0,0,7,(byte)0,QUAD));
    }
    @Test public void missingFieldDefaultsOnAndCopiesAndJsonPreserveExplicitOff(){
        com.badlogic.gdx.utils.Json json=new com.badlogic.gdx.utils.Json();
        SpatialBlockData old=json.fromJson(SpatialBlockData.class,"{id:7}");
        Assert.assertTrue(old.shadeUnlitFaces);
        old.shadeUnlitFaces=false;
        Assert.assertFalse(old.copy().shadeUnlitFaces);
        Assert.assertFalse(json.fromJson(SpatialBlockData.class,json.toJson(old)).shadeUnlitFaces);
        LightingSurface actor=new LightingSurface(new float[24]);actor.actor=true;
        Assert.assertEquals(-1f,actor.receiverLightingMode(0),0);
    }
    private static final float[] QUAD={-150,-100,-150,450,250,450,250,-100};

    @Test public void archivedDarkBandsUseTheFrontBoundaryAndKeepNativeProjection(){
        TiledMapLayerData map=map(256,128);
        SpatialBlockData perpendicular=block(7,25.765625f,16,.23064041f,4.9792404f,0,157.34424f);
        SpatialBlockData horizontal=block(2,18,20.74928f,7.9962654f,.22995949f,0,157.34424f);
        float[] quad={400,2700,400,3300,1200,3300,1200,2700};
        LightingSurface surface=receiver(map,quad,perpendicular,horizontal);
        for(float[] old:new float[][]{{3384.4578f,2591.908f,119.26597f},{3384.4578f,2591.908f,53.267242f},{3373.458f,2591.908f,124.76572f}}){
            float shift=old[0]-3362;
            assertAt(surface,quad,old,new float[]{3362,old[1]-shift,old[2]+shift,-1,0,0});
        }
        float[] control={3362,2558.45f,147.22516f};
        assertAt(surface,quad,control,new float[]{control[0],control[1],control[2],-1,0,0});
        samePartition(surface,receiver(map,quad,horizontal,perpendicular));
    }

    @Test public void archivedExteriorRectangleUsesYFaceInsteadOfTheLaterXFace(){
        TiledMapLayerData map=map(256,128);
        SpatialBlockData horizontal=block(19,24,16,6.9969482f,.23499489f,0,157.34424f);
        SpatialBlockData perpendicular=block(3,25.765625f,16,.23064041f,4.9792404f,0,157.34424f);
        float[] quad={1000,2500,1000,3100,1700,3100,1700,2500};
        LightingSurface surface=receiver(map,quad,horizontal,perpendicular);
        for(float[] old:new float[][]{{3362,2043.6997f,49.84993f},{3362,1995.7f,73.84988f}}){
            float shift=old[1]-1984;
            assertAt(surface,quad,old,new float[]{old[0]-shift,1984,old[2]+shift,0,-1,0});
        }
        samePartition(surface,receiver(map,quad,perpendicular,horizontal));
    }

    @Test public void archivedStructureOneUsesTheSamePhysicalFrontRule(){
        TiledMapLayerData map=map(256,128);
        SpatialBlockData horizontal=block(1,18,38.765625f,13,.22789764f,0,158);
        SpatialBlockData perpendicular=block(40,26.758629f,36,.24137115f,2.9519424f,0,158);
        float[] quad={-1800,4000,-1800,4500,-1000,4500,-1000,4000};
        LightingSurface surface=receiver(map,quad,horizontal,perpendicular);
        for(float[] old:new float[][]{{3489.1045f,4909.7007f,103.84955f},{3489.1045f,4909.7007f,73.84985f}}){
            float shift=old[1]-4898;
            assertAt(surface,quad,old,new float[]{old[0]-shift,4898,old[2]+shift,0,-1,0});
        }
        float[] control={3489.1045f,4891.7002f,82.85003f};
        assertAt(surface,quad,control,new float[]{control[0],control[1],control[2],-1,0,0});
        samePartition(surface,receiver(map,quad,perpendicular,horizontal));
    }

    @Test public void crossingDepthsSplitTheOverlapInsteadOfSortingWholeFaces(){
        TiledMapLayerData map=map(32,16);
        SpatialBlockData a=block(8,0,0,4,.5f,0,200),b=block(1,2,-2,.5f,4,0,200);
        LightingSurface surface=receiver(map,QUAD,a,b);
        assertAt(surface,QUAD,new float[]{39,-8,100},new float[]{39,-8,100,0,-1,0});
        assertAt(surface,QUAD,new float[]{40,-9,100},new float[]{40,-9,100,-1,0,0});
        Assert.assertEquals(8,ownerAt(surface,QUAD,39,-8,100));
        Assert.assertEquals(1,ownerAt(surface,QUAD,40,-9,100));
        Assert.assertEquals(2,surface.casterContributions.size);
        samePartition(surface,receiver(map,QUAD,b,a));
        verifyAgainstCameraRays(surface,QUAD,map,new SpatialBlockData[]{a,b});
    }

    @Test public void parallelSuperposedAndCrossingVolumesAreNotAssumedToBeAnL(){
        TiledMapLayerData map=map(32,16);
        SpatialBlockData[] blocks={block(91,0,0,5,.5f,0,70),block(3,0,2,4,.5f,10,110),
                block(52,2,-2,.5f,6,0,90),block(7,-1,1,3,.4f,140,30)};
        LightingSurface first=receiver(map,QUAD,blocks);
        verifyAgainstCameraRays(first,QUAD,map,blocks);
        int count=0;
        for(int a=0;a<4;a++)for(int b=0;b<4;b++)for(int c=0;c<4;c++)for(int d=0;d<4;d++){
            if(a==b||a==c||a==d||b==c||b==d||c==d)continue;
            samePartition(first,receiver(map,QUAD,blocks[a],blocks[b],blocks[c],blocks[d]));count++;
        }
        Assert.assertEquals(24,count);
    }

    @Test public void coplanarTiesAndExtrapolationDoNotDependOnIdentifiersOrInsertion(){
        TiledMapLayerData map=map(32,16);
        SpatialBlockData a=block(1,0,0,3,.5f,0,50),b=block(2,2,0,3,.5f,0,50);
        LightingSurface first=receiver(map,QUAD,a,b);
        Assert.assertNotNull(first.receiverVertices);
        a.id=800;b.id=3;a.structureId=39;b.structureId=72;
        samePartition(first,receiver(map,QUAD,b,a));
        coverage(first);
        // Completing the image must not modify a physical facet or source UV.
        for(int i=0;i<first.vertices.length;i+=8){
            boolean valid=first.vertices[i+5]!=0||first.vertices[i+6]!=0||first.vertices[i+7]!=0;
            if(valid)for(int k=0;k<8;k++)Assert.assertEquals(first.vertices[i+k],first.receiverVertices[i+k],0);
            else{Assert.assertEquals(first.vertices[i+3],first.receiverVertices[i+3],0);Assert.assertEquals(first.vertices[i+4],first.receiverVertices[i+4],0);}
        }
    }

    @Test public void nativeOffsetsMirroredAndSkewedQuadsKeepTheSamePhysicalSelection(){
        TiledMapLayerData map=map(32,16);SpatialBlockData a=block(1,0,0,5,.5f,0,80),b=block(2,2,-2,.5f,6,0,130);
        for(float[] q:new float[][]{{250,-100,250,450,-150,450,-150,-100},
                {-150,450,-150,-100,250,-100,250,450},{-200,-150,-100,400,300,500,200,-50}}){
            LightingSurface surface=receiver(map,q,a,b);samePartition(surface,receiver(map,q,b,a));
            verifyAgainstCameraRays(surface,q,map,new SpatialBlockData[]{a,b});
        }
    }

    @Test public void textureOnlyReuseNeverReplacesAnExplicitAssetSupport(){
        TiledMapLayerData map=map(32,16);float[] q={0,0,0,16,16,16,16,0};
        LocalSurfaceDescription mesh=new LocalSurfaceDescription();mesh.xyz=new float[]{-8,8,0,-8,8,16,0,0,0};
        mesh.uv=new float[]{0,0,0,1,1,0};mesh.normals=new float[]{-1,0,0,-1,0,0,-1,0,0};mesh.triangles=new int[]{0,1,2};
        TileSurfaceDescription description=new TileSurfaceDescription();description.assetId=7;description.geometry=mesh;
        com.badlogic.gdx.utils.Array<TileSurfaceDescription> descriptions=new com.badlogic.gdx.utils.Array<TileSurfaceDescription>();descriptions.add(description);
        MapLightingGeometry g=new MapLightingGeometry();g.descriptions(descriptions);g.ensure(map,null);
        LightingSurface local=g.receiver(map,0,0,7,(byte)0,q),automatic=g.receiver(map,0,0,8,(byte)0,q);
        Assert.assertEquals(local.forwardTopology.vertexCount*11,local.forwardAttributes.length);
        Assert.assertEquals(automatic.forwardTopology.vertexCount*11,automatic.forwardAttributes.length);
        Assert.assertNotSame(local,automatic);Assert.assertSame(automatic,g.receiver(map,0,0,9,(byte)0,q));
        Assert.assertNotSame(automatic,g.receiver(map,0,0,7,(byte)0,q));
    }

    @Test public void coplanarCasterSubmissionUsesGeometryWithoutChangingAnyFace(){
        TiledMapLayerData map=map(32,16);SpatialBlockData a=block(98,0,0,3,.5f,0,80),b=block(1,2,0,3,.5f,0,80);
        a.linkedTileRefsAuthored=b.linkedTileRefsAuthored=false; // Explicit opaque reference only.
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(a);blocks.blocks.add(b);
        MapLightingGeometry forward=new MapLightingGeometry();forward.ensure(map,blocks);
        blocks.blocks.reverse();blocks.revision++;MapLightingGeometry reverse=new MapLightingGeometry();reverse.ensure(map,blocks);
        Assert.assertEquals(12,forward.casters.size);Assert.assertEquals(12,reverse.casters.size);
        for(int i=0;i<12;i++)Assert.assertArrayEquals(forward.casters.get(i).vertices,reverse.casters.get(i).vertices,0);
        // Geometry is still exactly the union of the original per-volume six faces.
        int found=0;
        for(SpatialBlockData block:new SpatialBlockData[]{a,b}){
            SpatialBlocksComponent one=new SpatialBlocksComponent();one.blocks.add(block);MapLightingGeometry single=new MapLightingGeometry();single.ensure(map,one);
            for(LightingSurface face:single.casters){boolean present=false;for(LightingSurface candidate:forward.casters)present|=java.util.Arrays.equals(face.vertices,candidate.vertices);Assert.assertTrue(present);found++;}
        }
        Assert.assertEquals(12,found);Assert.assertSame(b,blocks.blocks.first());
    }

    @Test public void cacheTracksGeometryAssociationsAndProjectionButNotRepeatedLookup(){
        TiledMapLayerData map=map(32,16);SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        SpatialBlockData a=block(1,0,0,3,.5f,0,50);blocks.blocks.add(a);
        MapLightingGeometry geometry=new MapLightingGeometry();Assert.assertTrue(geometry.ensure(map,blocks));
        LightingSurface first=geometry.receiver(map,0,0,7,(byte)0,QUAD);
        for(int i=0;i<1000;i++){Assert.assertFalse(geometry.ensure(map,blocks));Assert.assertSame(first,geometry.receiver(map,0,0,7,(byte)0,QUAD));}
        Assert.assertSame("A texture-only frame reuses the automatic partition",first,geometry.receiver(map,0,0,8,(byte)0,QUAD));
        float[] casters=first.casterSurface.vertices.clone();
        a.x+=.25f;blocks.revision++;Assert.assertTrue(geometry.ensure(map,blocks));
        Assert.assertNotSame(first,geometry.receiver(map,0,0,7,(byte)0,QUAD));
        Assert.assertFalse(java.util.Arrays.equals(casters,geometry.receiver(map,0,0,7,(byte)0,QUAD).casterSurface.vertices));
        a.linkedTileRefs.clear();blocks.revision++;Assert.assertTrue(geometry.ensure(map,blocks));
        LightingSurface unassociated=geometry.receiver(map,0,0,7,(byte)0,QUAD);
        for(int i=0;i<unassociated.vertices.length;i+=8)Assert.assertEquals(1,unassociated.vertices[i+7],0);
        SpatialBlocksComponent replacement=new SpatialBlocksComponent();replacement.revision=blocks.revision;
        replacement.blocks.add(block(2,2,0,.5f,3,0,70));Assert.assertTrue(geometry.ensure(map,replacement));
        map.originX++;Assert.assertTrue(geometry.ensure(map,replacement));
        map.defaultTileAltitude++;Assert.assertTrue(geometry.ensure(map,replacement));
        map.tileWidth=64;Assert.assertTrue(geometry.ensure(map,replacement));
        map.projection=TiledProjection.ORTHO;
        try{geometry.ensure(map,replacement);Assert.fail("A changed unsupported projection must not reuse stale geometry");}catch(IllegalArgumentException expected){}
    }

    @Test public void casterGeometryAndSingleOwnerReceiversStayUnchangedByExtraNonOverlappingMembership(){
        TiledMapLayerData map=map(32,16);SpatialBlockData a=block(1,0,0,3,.5f,0,50);
        a.linkedTileRefsAuthored=false; // Receiver-only membership, opaque reference.
        float[] quad={0,5,0,45,30,45,30,5};
        LightingSurface single=receiver(map,quad,a);
        SpatialBlockData remote=block(2,100,100,2,.5f,0,20);
        remote.linkedTileRefsAuthored=false;
        LightingSurface multiple=receiver(map,quad,a,remote);
        for(int y=0;y<9;y++)for(int x=0;x<9;x++){
            float u=(x+.37f)/9,v=(y+.63f)/9;
            float[] s=sample(single.vertices,u,v),m=sample(multiple.vertices,u,v);
            Assert.assertArrayEquals(s,m,.0001f);
        }
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(a);blocks.blocks.add(remote);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        float[][] original=new float[geometry.casters.size][];for(int i=0;i<original.length;i++)original[i]=geometry.casters.get(i).vertices.clone();
        geometry.receiver(map,0,0,7,(byte)0,quad);
        for(int i=0;i<original.length;i++)Assert.assertArrayEquals(original[i],geometry.casters.get(i).vertices,0);
    }

    private static TiledMapLayerData map(int w,int h){return new TiledMapLayerData(64,64,w,h,16,TiledProjection.ISO);}
    private static int ownerAt(LightingSurface s,float[] q,float x,float y,float z){
        float u=(LightingCoordinates.screenX(x,y)-q[0])/(q[6]-q[0]);
        float v=(LightingCoordinates.screenY(x,y,z)-q[1])/(q[3]-q[1]);
        for(int i=0;i<s.vertices.length;i+=24){float[] p=s.vertices;
            float ax=p[i+11]-p[i+3],ay=p[i+12]-p[i+4],bx=p[i+19]-p[i+3],by=p[i+20]-p[i+4];
            float det=ax*by-ay*bx;if(Math.abs(det)<1e-10f)continue;
            float a=((u-p[i+3])*by-(v-p[i+4])*bx)/det,b=(ax*(v-p[i+4])-ay*(u-p[i+3]))/det;
            if(a>=0&&b>=0&&a+b<=1)return s.receiverBlockIds[i/24];
        }throw new AssertionError("No receiver at witness");
    }
    private static SpatialBlockData block(int id,float x,float y,float w,float d,float z,float h){
        SpatialBlockData b=new SpatialBlockData();b.id=b.structureId=id;b.x=x;b.y=y;b.width=w;b.depth=d;b.altitude=z;b.height=h;
        b.shadowCaster=true;b.addLinkedTileRef(0,0,7);return b;
    }
    private static LightingSurface receiver(TiledMapLayerData map,float[] quad,SpatialBlockData... bs){
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();for(SpatialBlockData b:bs)blocks.blocks.add(b);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        LightingSurface result=geometry.receiver(map,0,0,7,(byte)0,quad);coverage(result);return result;
    }
    private static void samePartition(LightingSurface a,LightingSurface b){Assert.assertArrayEquals(a.vertices,b.vertices,0);Assert.assertArrayEquals(a.receiverVertices,b.receiverVertices,0);}
    private static void coverage(LightingSurface s){
        float area=0;for(int i=0;i<s.vertices.length;i+=24){float[] p=s.vertices;area+=Math.abs((p[i+11]-p[i+3])*(p[i+20]-p[i+4])-(p[i+19]-p[i+3])*(p[i+12]-p[i+4]))*.5f;}
        Assert.assertEquals("One image coverage, including explicitly extrapolated regions",1,area,.0001f);
    }
    private static float[] sample(float[] vertices,float u,float v){
        float[] result=null;
        for(int i=0;i<vertices.length;i+=24){
            double ax=vertices[i+11]-vertices[i+3],ay=vertices[i+12]-vertices[i+4],bx=vertices[i+19]-vertices[i+3],by=vertices[i+20]-vertices[i+4],det=ax*by-ay*bx;
            if(Math.abs(det)<1e-12)continue;
            double x=u-vertices[i+3],y=v-vertices[i+4],a=(x*by-y*bx)/det,b=(ax*y-ay*x)/det;
            if(a<1e-7||b<1e-7||a+b>1-1e-7)continue;
            float[] point=new float[6];for(int k=0;k<3;k++){point[k]=(float)(vertices[i+k]+a*(vertices[i+8+k]-vertices[i+k])+b*(vertices[i+16+k]-vertices[i+k]));point[k+3]=vertices[i+5+k];}
            Assert.assertNull("No duplicate interior image coverage at "+u+","+v,result);result=point;
        }
        Assert.assertNotNull("No image hole at "+u+","+v,result);return result;
    }
    private static void assertAt(LightingSurface s,float[] q,float[] old,float[] expected){
        float sx=LightingCoordinates.screenX(old[0],old[1]),sy=LightingCoordinates.screenY(old[0],old[1],old[2]);
        float u=(sx-q[0])/(q[6]-q[0]),v=(sy-q[1])/(q[3]-q[1]);float[] p=sample(s.vertices,u,v);
        Assert.assertArrayEquals(expected,p,.002f);
        Assert.assertEquals(sx,LightingCoordinates.screenX(p[0],p[1]),.001f);Assert.assertEquals(sy,LightingCoordinates.screenY(p[0],p[1],p[2]),.001f);
    }
    private static void verifyAgainstCameraRays(LightingSurface surface,float[] q,TiledMapLayerData map,SpatialBlockData[] blocks){
        int hits=0;
        for(int y=0;y<59;y++)for(int x=0;x<53;x++){
            float u=(x+.371f)/53,v=(y+.613f)/59,sx=q[0]+u*(q[6]-q[0])+v*(q[2]-q[0]),sy=q[1]+u*(q[7]-q[1])+v*(q[3]-q[1]);
            // Independent slab ray/box intersection from the viewer, not face ordering.
            double best=Double.POSITIVE_INFINITY;int normal=-1;
            for(SpatialBlockData b:blocks){
                float[] lo=new float[3],hi=new float[3];LightingCoordinates.mapPoint(map,b.x,b.y,b.altitude,lo,0);LightingCoordinates.mapPoint(map,b.x+b.width,b.y+b.depth,b.altitude+b.height,hi,0);
                double ox=sy-10000+sx*.5,oy=sy-10000-sx*.5;
                double tx=lo[0]-ox,ty=lo[1]-oy,tz=10000-hi[2],enter=Math.max(tx,Math.max(ty,tz));
                double exit=Math.min(hi[0]-ox,Math.min(hi[1]-oy,10000-lo[2]));
                if(enter<exit&&enter<best){best=enter;normal=enter==tx?0:enter==ty?1:2;}
            }
            if(normal<0)continue;
            float[] p=sample(surface.vertices,u,v);Assert.assertEquals(10000-best,p[2],.003);
            Assert.assertEquals(normal==0?-1:0,p[3],.0001);Assert.assertEquals(normal==1?-1:0,p[4],.0001);Assert.assertEquals(normal==2?1:0,p[5],.0001);hits++;
        }
        Assert.assertTrue(hits>30);
    }
}
