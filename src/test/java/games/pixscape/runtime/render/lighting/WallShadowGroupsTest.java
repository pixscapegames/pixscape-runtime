package games.pixscape.runtime.render.lighting;

import org.junit.Test;
import static org.junit.Assert.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;

public class WallShadowGroupsTest {
    private static SpatialBlockData wall(int id,int structure,float x,float y,float w,float d,float z){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=structure;b.x=x;b.y=y;b.width=w;b.depth=d;
        b.altitude=z;b.height=128;b.addLinkedTileRef((int)x,(int)y,1);return b;
    }
    private static TiledMapLayerData map(){return new TiledMapLayerData(64,64,256,128,16,TiledProjection.ISO);}
    @Test public void connectedWallsAndStoreysNotWholeOwnerOrStructure(){
        var ids=new ShadowExclusionIdentities();var groups=new WallShadowGroups();var base=new SpatialBlocksComponent();
        base.blocks.add(wall(1,1,0,0,4,.25f,0));base.blocks.add(wall(2,1,4,0,.25f,3,0));
        base.blocks.add(wall(3,1,20,0,4,.25f,0)); // same structure, disconnected
        base.blocks.add(wall(4,2,4,0,.25f,3,0)); // overlap alone does not group
        base.blocks.add(wall(5,1,-2,0,2,.25f,0)); // collinear continuation
        var upper=new SpatialBlocksComponent();upper.blocks.add(wall(1,9,1,0,2,.25f,128));
        upper.blocks.add(wall(2,9,1,1,2,.25f,128)); // different strip
        groups.add(10,map(),base,ids);groups.add(11,map(),upper,ids);groups.connectWalls();
        int a=groups.group(ids.idFor(10,1));
        assertNotEquals(a,groups.group(ids.idFor(10,2)));assertEquals(a,groups.group(ids.idFor(11,1)));
        assertEquals(a,groups.group(ids.idFor(10,5)));
        assertNotEquals(a,groups.group(ids.idFor(10,3)));assertNotEquals(a,groups.group(ids.idFor(11,2)));
        assertNotEquals(a,groups.group(ids.idFor(10,4)));
        assertEquals(0,groups.group(0));
    }
    @Test public void ownGroupIsSeparateFromIdentityAndLegacyIsUnchanged(){
        var s=new LightingSurface(new float[48]);s.selfShadowExclusionId=29;
        s.receiverExclusionIds=new int[]{18,0};
        assertEquals(29,s.casterShadowGroup());assertEquals(18,s.receiverShadowGroup(0));
        var g=new WallShadowGroups();g.connect(18,29);g.apply(s);
        assertEquals(29,s.selfShadowExclusionId);assertEquals(18,s.receiverExclusionId(0));
        assertEquals(18,s.casterShadowGroup());assertEquals(18,s.receiverShadowGroup(0));assertEquals(0,s.receiverShadowGroup(24));
    }
    @Test public void secondDepthSkipsAllSupportsOfGroupNotOnlyFirstIdentity(){
        var g=new WallShadowGroups();g.connect(18,29);g.connect(29,30);
        int[] ids={29,18,30,44};float[] depths={1,2,3,4};
        int first=g.group(ids[0]);float second=Float.POSITIVE_INFINITY;
        for(int i=0;i<ids.length;i++)if(g.group(ids[i])!=first)second=Math.min(second,depths[i]);
        assertEquals(4,second,0);assertNotEquals(first,g.group(44));
    }
}
