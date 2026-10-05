package games.pixscape.runtime.spatial;

import com.artemis.World;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.tiled.*;

import java.util.Arrays;

/** Isolated authored L walls: no project, scene, texture or durable ID allocation. */
final class SpatialCornerFixture implements AutoCloseable {
    static final String[] OPENINGS = {"OPEN_NORTH", "OPEN_SOUTH", "OPEN_WEST", "OPEN_EAST"};
    static final String[] SECTORS = {"NORTH", "SOUTH", "WEST", "EAST"};
    // Independent business oracle: the two lateral B approximations still need tile splitting.
    static final String[] EXPECTED = {"BFFF", "BFBB", "BFBF", "BFFB"};
    static final float[] DX = {0f, 0f, -25f, 25f};
    static final float[] DY = {18f, -18f, 0f, 0f};
    final TiledMapLayerData map = new TiledMapLayerData(10, 10, 200, 100, 4, TiledProjection.ISO);
    final SpatialBlocksComponent blocks = new SpatialBlocksComponent();
    final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
    final SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
    final SpatialTileOrderCache ranks = new SpatialTileOrderCache();
    final TiledMapRenderState tiled = new TiledMapRenderState(64);
    final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
    final SpatialActorCollector actors = new SpatialActorCollector();
    final SpatialFaceRelationSolver solver = new SpatialFaceRelationSolver();
    final SpatialBucketPlanner planner = new SpatialBucketPlanner();
    final SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
    final SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
    final DrawList draw = new DrawList(2);
    final World world = new World();
    final int actor, slot, ref;
    final float vx, vy;
    final int opening;

    SpatialCornerFixture(int opening, boolean light) {
        this.opening = opening;
        // ISO: +gx projects NE (+100,+50), +gy NW (-100,+50), +Y is up.
        int sx = opening == 0 || opening == 3 ? 1 : -1;
        int sy = opening == 0 || opening == 2 ? 1 : -1;
        float v = 4.5f, length = .42f, thickness = .002f;
        wall(1, Math.min(v, v + sx * length), v - thickness / 2f, length, thickness);
        wall(2, v - thickness / 2f, Math.min(v, v + sy * length), thickness, length);
        map.setTile(4, 4, 1);
        for (IntMap.Values<TileChunk> chunks = map.getChunks(); chunks.hasNext();) {
            TileChunk chunk = chunks.next();
            chunk.renderRefStartIndex = tiled.registerRefs(chunk.cellCount());
            chunk.renderRefCount = chunk.cellCount();
        }
        ref = map.tiledRenderRefForTile(4, 4);
        float[] p = new float[2]; map.projectSpatialPoint(v, v, 0f, p, 0);
        vx = p[0]; vy = p[1];
        tiled.enabled[ref] = tiled.visible[ref] = true;
        tiled.x1[ref] = tiled.x4[ref] = vx - 90f;
        tiled.x2[ref] = tiled.x3[ref] = vx + 90f;
        tiled.y1[ref] = tiled.y2[ref] = vy - 35f;
        tiled.y3[ref] = tiled.y4[ref] = vy + 90f;
        compiled.ensure(blocks); ranks.ensure(8, map, blocks, compiled); faces.ensure(compiled, map);
        actor = world.create();
        world.getMapper(TransformComponent.class).create(actor);
        world.getMapper(EntityIndexComponent.class).create(actor);
        SpatialHeightComponent h = world.getMapper(SpatialHeightComponent.class).create(actor);
        h.height = light ? 1f : 40f;
        SpatialPhysicsFootprintComponent footprint = world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
        footprint.valid = true; footprint.radiusPx = .5f;
        if (light) world.getMapper(PointLightComponent.class).create(actor).radius = 60f;
        world.process();
        slot = ecs.acquireSlotForEntity(actor);
        ecs.layerIndex[slot] = 0;
        ecs.kind[slot] = RenderKind.SPRITE; ecs.textureHandle[slot] = 1;
        ecs.enabled[slot] = ecs.visible[slot] = true;
        ecs.blend[slot] = light ? BlendMode.ADDITIVE.id : BlendMode.OPAQUE.id;
        solver.setCaptureCandidates(true);
    }

    private void wall(int id, float x, float y, float width, float depth) {
        SpatialBlockData b = new SpatialBlockData();
        b.id = id; b.structureId = 1; b.x = x; b.y = y; b.width = width; b.depth = depth;
        b.height = 80f; b.beginAuthoredLinkedTileRefs(); b.addLinkedTileRef(4, 4, 1);
        blocks.blocks.add(b);
    }

    Outcome run(int sector, float radius, float visualRadius, int original) {
        return runAt(DX[sector], DY[sector], radius, visualRadius, original);
    }

    Outcome runAt(float dx, float dy, float radius, float visualRadius, int original) {
        TransformComponent t = world.getMapper(TransformComponent.class).get(actor);
        t.x = vx + dx; t.y = vy + dy;
        PointLightComponent point = world.getMapper(PointLightComponent.class).get(actor);
        if (point != null) point.radius = visualRadius;
        games.pixscape.runtime.component.light.ConeLightComponent cone = world
                .getMapper(games.pixscape.runtime.component.light.ConeLightComponent.class).get(actor);
        if (cone != null) cone.radius = visualRadius;
        world.getMapper(SpatialPhysicsFootprintComponent.class).get(actor).radiusPx = radius;
        ecs.x1[slot] = ecs.x4[slot] = t.x - visualRadius;
        ecs.x2[slot] = ecs.x3[slot] = t.x + visualRadius;
        ecs.y1[slot] = ecs.y2[slot] = t.y - visualRadius;
        ecs.y3[slot] = ecs.y4[slot] = t.y + visualRadius;
        draw.clearEntries();
        if (original == 0) draw.addEcsSlot(slot);
        draw.addTiledSlot(ref);
        if (original == 1) draw.addEcsSlot(slot);
        actors.collect(draw, ecs, new boolean[]{true}, world.getEntityManager(),
                world.getMapper(EntityIndexComponent.class), world.getMapper(TransformComponent.class),
                world.getMapper(SpatialHeightComponent.class), world.getMapper(SpatialPhysicsFootprintComponent.class));
        snapshot.build(draw, ecs.getRenderCapacity(), actors);
        int[] index = new int[tiled.getCapacity()]; Arrays.fill(index, -1); index[ref] = original == 0 ? 1 : 0;
        new SpatialFaceAnchorResolver().resolve(faces, index, snapshot.drawIndexToBucketBefore,
                snapshot.drawIndexToBucketAfter, draw.size);
        solver.solveVisual(actors, faces, new SpatialVisualAnchorSelector(), ecs, tiled, map, 0f, 0f);
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);
        planner.addRelations(actors, faces, solver, 8); planner.finish(actors);
        composer.compose(draw, actors, planner, snapshot);
        return new Outcome(solver.visualCandidateCount, solver.relationCount, planner.actorLowerBound[0],
                planner.actorUpperBound[0], planner.actorBucket[0], planner.unresolvedConstraintCount(),
                planner.actorOrderingFallbackCount());
    }

    @Override public void close() { world.dispose(); }

    static final class Outcome {
        final int candidates, relations, lower, upper, bucket, conflicts, fallbacks;
        Outcome(int c, int r, int l, int u, int b, int x, int f) {
            candidates=c; relations=r; lower=l; upper=u; bucket=b; conflicts=x; fallbacks=f;
        }
        char side() { return conflicts > 0 ? 'X' : bucket == 0 ? 'B' : 'F'; }
        @Override public String toString() {
            return side()+" candidates="+candidates+" relations="+relations+" L="+lower+" U="+upper
                    +" bucket="+bucket+" conflicts="+conflicts+" fallbacks="+fallbacks;
        }
    }
}
