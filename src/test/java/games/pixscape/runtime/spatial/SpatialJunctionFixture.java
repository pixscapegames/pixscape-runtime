package games.pixscape.runtime.spatial;

import com.artemis.World;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.helper.QuadGeometryHelper;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.PhysicsSpatialFootprintProjector;
import games.pixscape.runtime.tiled.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Checkout-independent numeric snapshot of map 215 and its eleven saved actors. */
final class SpatialJunctionFixture implements AutoCloseable {
    static final int[] IDS = {198,575,603,604,605,606,607,608,609,610,611};
    final Json json = new Json();
    final JsonValue data;
    final World world = new World();
    final TiledMapLayerData map = new TiledMapLayerData(50,50,256,128,16,TiledProjection.ISO);
    final SpatialBlocksComponent blocks = new SpatialBlocksComponent();
    final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
    final SpatialTileOrderCache ranks = new SpatialTileOrderCache();
    final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
    final TiledMapRenderState tiled = new TiledMapRenderState(4096);
    final DynamicEntityRenderState ecs = new DynamicEntityRenderState(11);
    final SpatialActorCollector actors = new SpatialActorCollector();
    final SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
    final SpatialBucketPlanner planner = new SpatialBucketPlanner();
    final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
    final SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
    final DrawList tiles = new DrawList(142), draw = new DrawList(153);
    final IntMap<Integer> entities = new IntMap<>();
    final IntMap<JsonValue> actorData = new IntMap<>();

    SpatialJunctionFixture() {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/spatial/junction-scene-fixture.json"), StandardCharsets.UTF_8)) {
            data = new JsonReader().parse(reader);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        json.setIgnoreUnknownFields(true);
        for (JsonValue t = data.get("tiles").child; t != null; t = t.next)
            map.setTile(t.getInt("gx"),t.getInt("gy"),t.getInt("asset"));
        map.setVisualPadding(256,256,512,512);
        for (JsonValue b = data.get("blocks").child; b != null; b = b.next) {
            SpatialBlockData block = new SpatialBlockData();
            block.id=b.getInt("id"); block.structureId=b.getInt("structureId");
            block.x=b.getFloat("x"); block.y=b.getFloat("y"); block.width=b.getFloat("width");
            block.depth=b.getFloat("depth"); block.altitude=b.getFloat("altitude",0); block.height=b.getFloat("height");
            block.beginAuthoredLinkedTileRefs();
            for (JsonValue r=b.get("linkedTileRefs").child;r!=null;r=r.next)
                block.addLinkedTileRef(r.getInt("gx"),r.getInt("gy"),r.getInt("tileAssetId"));
            blocks.blocks.add(block);
        }
        compiled.ensure(blocks); ranks.ensure(215,map,blocks,compiled);
        for (IntMap.Values<TileChunk> chunks=map.getChunks();chunks.hasNext();) {
            TileChunk chunk=chunks.next(); chunk.renderRefStartIndex=tiled.registerRefs(chunk.cellCount());
            chunk.renderRefCount=chunk.cellCount();
        }
        faces.ensure(compiled,map);
        int[] refs=new int[ranks.tileOrderNodeCount]; Arrays.fill(refs,-1);
        for (JsonValue t=data.get("tiles").child;t!=null;t=t.next) {
            int gx=t.getInt("gx"),gy=t.getInt("gy"),ref=map.tiledRenderRefForTile(gx,gy);
            refs[ranks.rank(gx,gy)]=ref; tiled.enabled[ref]=tiled.visible[ref]=true;
            float[] q=t.get("quad").asFloatArray();
            tiled.x1[ref]=q[0];tiled.y1[ref]=q[1];tiled.x2[ref]=q[2];tiled.y2[ref]=q[3];
            tiled.x3[ref]=q[4];tiled.y3[ref]=q[5];tiled.x4[ref]=q[6];tiled.y4[ref]=q[7];
        }
        for(int ref:refs)tiles.addTiledSlot(ref);
        PhysicsSpatialFootprintProjector projector=new PhysicsSpatialFootprintProjector();
        for(JsonValue a=data.get("actors").child;a!=null;a=a.next) {
            int id=a.getInt("id"),entity=world.create();entities.put(id,entity);actorData.put(id,a);
            json.readFields(world.getMapper(TransformComponent.class).create(entity),a.get("transform"));
            json.readFields(world.getMapper(SpatialHeightComponent.class).create(entity),a.get("height"));
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId=id;
            if(a.getBoolean("point"))world.getMapper(PointLightComponent.class).create(entity);
            JsonValue shape=a.get("shape"),g=shape.get("geometry");
            CompiledFixtureData f=new CompiledFixtureData(); f.shapeType=g.getInt("shapeType");
            f.radius=g.getFloat("radius");f.offsetX=g.getFloat("offsetX",0);f.offsetY=g.getFloat("offsetY",0);
            f.physicsShapeId=shape.getInt("physicsShapeId");f.spatialFootprint=true;
            Array<CompiledFixtureData> fs=new Array<>();fs.add(f);
            projector.publish(world.getMapper(SpatialPhysicsFootprintComponent.class).create(entity),projector.prepare(fs,1,100));
            int slot=ecs.acquireSlotForEntity(entity);ecs.layerIndex[slot]=0;
            ecs.kind[slot]=RenderKind.SPRITE;ecs.textureHandle[slot]=1;ecs.enabled[slot]=ecs.visible[slot]=true;
        }
        world.process();solver.setCaptureCandidates(true);
    }

    void run(int id) { run(new int[]{id},0,0,Float.NaN); }
    void run(int[] ids,float dx,float dy,float radius) {
        draw.clearEntries();for(int i=0;i<tiles.size;i++)draw.addTiledSlot(tiles.get(i));
        for(int id:ids) {
            int entity=entities.get(id),slot=ecs.acquireSlotForEntity(entity);JsonValue a=actorData.get(id);
            TransformComponent t=world.getMapper(TransformComponent.class).get(entity);
            json.readFields(t,a.get("transform"));t.x+=dx;t.y+=dy;
            SpatialPhysicsFootprintComponent foot=world.getMapper(SpatialPhysicsFootprintComponent.class).get(entity);
            foot.radiusPx=Float.isNaN(radius)?a.get("shape").get("geometry").getFloat("radius")*100:radius;
            OrientedBoundsComponent bounds=json.readValue(OrientedBoundsComponent.class,a.get("bounds"));
            bounds.cx+=dx;bounds.cy+=dy;float[] q=new float[8];QuadGeometryHelper.toWorldCorners(bounds,t,null,q);
            ecs.x1[slot]=q[0];ecs.y1[slot]=q[1];ecs.x2[slot]=q[6];ecs.y2[slot]=q[7];
            ecs.x3[slot]=q[4];ecs.y3[slot]=q[5];ecs.x4[slot]=q[2];ecs.y4[slot]=q[3];draw.addEcsSlot(slot);
        }
        actors.collect(draw,ecs,new boolean[]{true},world.getEntityManager(),world.getMapper(EntityIndexComponent.class),
                world.getMapper(TransformComponent.class),world.getMapper(SpatialHeightComponent.class),
                world.getMapper(SpatialPhysicsFootprintComponent.class),world.getMapper(PixscapeIdentityComponent.class));
        snapshot.build(draw,ecs.getRenderCapacity(),actors);
        int[] index=new int[tiled.getCapacity()];Arrays.fill(index,-1);
        for(int i=0;i<tiles.size;i++)index[tiles.get(i)]=i;
        new SpatialFaceAnchorResolver().resolve(faces,index,snapshot.drawIndexToBucketBefore,snapshot.drawIndexToBucketAfter,draw.size);
        solver.solveVisual(actors,faces,new SpatialVisualAnchorSelector(),ecs,tiled,map,0,0);
        planner.begin(actors,snapshot.actorOriginalBucket,snapshot.bucketCount);
        planner.addRelations(actors,faces,solver,215);planner.finish(actors);composer.compose(draw,actors,planner,snapshot);
    }
    int before(int gx,int gy) { return faces.anchorBeforeBucket[faces.anchorForCell(gx,gy)]; }
    int after(int gx,int gy) { return faces.anchorAfterBucket[faces.anchorForCell(gx,gy)]; }
    boolean hasRelation(int gx,int gy,byte type) {
        int anchor=faces.anchorForCell(gx,gy);
        for(int r=0;r<solver.relationCount;r++)if(solver.relationAnchorIndex[r]==anchor&&solver.relationType[r]==type)return true;
        return false;
    }
    @Override public void close() { world.dispose(); }
}
