package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpatialBlockLinkTest {
    @Test public void explicitBorderCompilesRealUnionWithoutInternalSeam() {
        SpatialBlocksComponent data=data();
        CompiledSpatialStructure structure=SpatialStructureCompiler.compile(data.blocks,data.links,1);
        assertEquals(4,structure.segmentCount());
        assertEquals(1,structure.minX(),0); assertEquals(6,structure.maxX(),0);
        for(int i=0;i<structure.segmentCount();i++)assertFalse(structure.startX(i)==4 && structure.endX(i)==4);
        assertThrows(IllegalArgumentException.class,()->SpatialStructureCompiler.compile(data.blocks,1));
    }
    @Test public void stableCacheDoesNotRebuildOnRepeatedFrameLookups() {
        SpatialBlocksComponent data=data(); SpatialCompiledLayerCache cache=new SpatialCompiledLayerCache();
        assertTrue(cache.ensure(data)); int count=cache.compilationCount();
        for(int i=0;i<1000;i++)assertFalse(cache.ensure(data)); assertEquals(count,cache.compilationCount());
    }
    @Test public void malformedEndpointsFacesAndDuplicatesAreRejected() {
        SpatialBlocksComponent data=data(); data.links.add(data.links.first().copy());
        assertThrows(IllegalArgumentException.class,()->SpatialBlockLinks.validate(data.blocks,data.links)); data.links.pop();
        data.links.first().secondBlockId=99; assertThrows(IllegalArgumentException.class,()->SpatialBlockLinks.validate(data.blocks,data.links));
        data.links.first().secondBlockId=2; data.links.first().firstFace=SpatialBlockLink.MIN_X;
        assertThrows(IllegalArgumentException.class,()->SpatialBlockLinks.validate(data.blocks,data.links));
    }
    @Test public void remappingCopiesOnlyCompletePairsAndOldJsonDefaultsEmpty() {
        SpatialBlocksComponent data=data(); IntIntMap ids=new IntIntMap(); ids.put(1,11);ids.put(2,12);
        Array<SpatialBlockLink> remapped=SpatialBlockLinks.remap(data.links,ids);
        assertEquals(11,remapped.first().firstBlockId); assertEquals(12,remapped.first().secondBlockId);
        ids.remove(2,0); assertEquals(0,SpatialBlockLinks.remap(data.links,ids).size);
        assertEquals(0,new Json().fromJson(SpatialBlocksComponent.class,"{}").links.size);
    }
    private static SpatialBlocksComponent data() {
        SpatialBlocksComponent data=new SpatialBlocksComponent();
        for(int i=0;i<2;i++) { SpatialBlockData wall=new SpatialBlockData(); wall.id=i+1;wall.structureId=1;
            wall.x=i==0?1:4; wall.y=1;wall.width=i==0?3:2;wall.depth=.3f;wall.height=90;data.blocks.add(wall); }
        data.links.add(new SpatialBlockLink(1,2,SpatialBlockLink.MAX_X,SpatialBlockLink.MIN_X)); return data;
    }
}
