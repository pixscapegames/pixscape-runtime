package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.tiled.*;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SpatialBlockLinkActorTest {
    @Test public void actorsCrossingTheExplicitJunctionSeeTheSameFacesAsOneContinuousWall() {
        TiledMapLayerData map=new TiledMapLayerData(8,4,64,32,4,TiledProjection.ISO);
        for(int y=0;y<4;y++)for(int x=0;x<8;x++)map.setTile(x,y,1);
        SpatialBlocksComponent linked=new SpatialBlocksComponent();
        linked.blocks.add(wall(1,1,3));linked.blocks.add(wall(2,4,2));linked.links.add(new SpatialBlockLink(1,2,2,1));
        SpatialBlocksComponent continuous=new SpatialBlocksComponent();continuous.blocks.add(wall(1,1,5));
        SpatialProjectedFaceCache actual=project(linked,map), expected=project(continuous,map);
        assertEquals(expected.faceCount,actual.faceCount);assertEquals(expected.anchorCount,actual.anchorCount);
        for(float x=3.5f;x<=4.5f;x+=.05f) {
            assertArrayEquals("front of junction x="+x,signature(actual,map,x,.8f),signature(expected,map,x,.8f));
            assertArrayEquals("behind junction x="+x,signature(actual,map,x,1.5f),signature(expected,map,x,1.5f));
        }
    }

    private static int[] signature(SpatialProjectedFaceCache faces,TiledMapLayerData map,float x,float y) {
        SpatialActorCollector actors=new SpatialActorCollector();actors.actorCount=1;
        actors.actorSlot=new int[]{0};actors.actorEntityId=new int[]{1};actors.actorStableOrder=new int[]{1};actors.actorDrawIndex=new int[]{2};
        float[] point=new float[2];map.projectSpatialPoint(x,y,0,point,0);
        actors.actorCircleX=new float[]{point[0]};actors.actorCircleY=new float[]{point[1]};actors.actorCircleRadius=new float[]{.5f};
        actors.actorAltitude=new float[]{0};actors.actorHeight=new float[]{32};
        SpatialFaceRelationSolver solver=new SpatialFaceRelationSolver();solver.solve(actors,faces);
        DrawList draw=new DrawList(9);for(int i=0;i<8;i++){draw.addTiledSlot(i+100);if(i==2)draw.addEcsSlot(0);}
        SpatialFrameSnapshotBuilder snapshot=new SpatialFrameSnapshotBuilder();snapshot.build(draw,1,actors);
        SpatialBucketPlanner planner=new SpatialBucketPlanner();planner.begin(actors,snapshot.actorOriginalBucket,snapshot.bucketCount);planner.addRelations(actors,faces,solver);planner.finish(actors);
        SpatialBucketDrawListComposer composer=new SpatialBucketDrawListComposer();composer.compose(draw,actors,planner,snapshot);
        int[] nonActor=new int[8];int at=0;for(int i=0;i<composer.composedSize;i++)if(composer.composedDomains[i]!=RenderSourceDomain.SOURCE_ECS)nonActor[at++]=composer.composedSlots[i];
        assertArrayEquals(new int[]{100,101,102,103,104,105,106,107},nonActor);
        int[] signature=new int[solver.relationCount*2+2];signature[0]=planner.actorBucket[0];signature[1]=planner.unresolvedConstraintCount();
        for(int i=0;i<solver.relationCount;i++){signature[i*2+2]=solver.relationFaceIndex[i];signature[i*2+3]=solver.relationType[i];}return signature;
    }

    private static SpatialProjectedFaceCache project(SpatialBlocksComponent blocks,TiledMapLayerData map){
        SpatialCompiledLayerCache compiled=new SpatialCompiledLayerCache();compiled.ensure(blocks);
        SpatialProjectedFaceCache faces=new SpatialProjectedFaceCache();faces.ensure(compiled,map);
        for(int anchor=0;anchor<faces.anchorCount;anchor++){faces.anchorResolved[anchor]=true;faces.anchorBeforeBucket[anchor]=2;faces.anchorAfterBucket[anchor]=3;}return faces;
    }
    private static SpatialBlockData wall(int id,int x,int width){SpatialBlockData wall=new SpatialBlockData();wall.id=id;wall.structureId=1;wall.x=x;wall.y=1;wall.width=width;wall.depth=.3f;wall.height=90;wall.beginAuthoredLinkedTileRefs();for(int gx=x;gx<x+width;gx++)wall.addLinkedTileRef(gx,1,1);return wall;}
}
