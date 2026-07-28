package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * Reusable, deeply detached authored state for one spatial-block owner.
 *
 * <p>This intentionally contains no ECS identity, repository index, cache, or native physics
 * state. It is suitable for history: every collection returned from it is a fresh deep copy.</p>
 */
public final class WorldBlockOwnerSnapshot {
    final int ownerStableId;
    final int nextSpatialBlockId;
    final Array<SpatialBlockData> blocks;
    final boolean hasBindings;
    final Array<BlockPhysicsBindingData> bindings;
    final boolean hasShapes;
    final Array<PhysicsShapeData> shapes;
    final boolean hasBody;
    final BodyState body;
    final boolean hasTransform;
    final TransformState transform;

    WorldBlockOwnerSnapshot(int ownerStableId, int nextSpatialBlockId,
                            Array<SpatialBlockData> blocks,
                            boolean hasBindings, Array<BlockPhysicsBindingData> bindings,
                            boolean hasShapes, Array<PhysicsShapeData> shapes,
                            boolean hasBody, BodyState body,
                            boolean hasTransform, TransformState transform) {
        this.ownerStableId = ownerStableId;
        this.nextSpatialBlockId = nextSpatialBlockId;
        this.blocks = copyBlocks(blocks);
        this.hasBindings = hasBindings;
        this.bindings = copyBindings(bindings);
        this.hasShapes = hasShapes;
        this.shapes = copyShapes(shapes);
        this.hasBody = hasBody;
        this.body = body != null ? body.copy() : null;
        this.hasTransform = hasTransform;
        this.transform = transform != null ? transform.copy() : null;
    }

    public int ownerStableId() { return ownerStableId; }
    public int nextSpatialBlockId() { return nextSpatialBlockId; }
    public Array<SpatialBlockData> blocks() { return copyBlocks(blocks); }
    public boolean hasBindings() { return hasBindings; }
    public Array<BlockPhysicsBindingData> bindings() { return copyBindings(bindings); }
    public boolean hasShapes() { return hasShapes; }
    public Array<PhysicsShapeData> shapes() { return copyShapes(shapes); }
    public boolean hasBody() { return hasBody; }
    public boolean hasTransform() { return hasTransform; }

    static Array<SpatialBlockData> copyBlocks(Array<SpatialBlockData> source) {
        Array<SpatialBlockData> result = new Array<>(SpatialBlockData[]::new);
        if (source != null) for (int i = 0; i < source.size; i++) result.add(source.get(i).copy());
        return result;
    }

    static Array<BlockPhysicsBindingData> copyBindings(Array<BlockPhysicsBindingData> source) {
        Array<BlockPhysicsBindingData> result = new Array<>(BlockPhysicsBindingData[]::new);
        if (source != null) for (int i = 0; i < source.size; i++) result.add(source.get(i).copy());
        return result;
    }

    static Array<PhysicsShapeData> copyShapes(Array<PhysicsShapeData> source) {
        Array<PhysicsShapeData> result = new Array<>(true, source != null ? source.size : 1,
                PhysicsShapeData.class);
        if (source != null) for (int i = 0; i < source.size; i++) result.add(source.get(i).copy());
        return result;
    }

    static final class BodyState {
        final int type;
        final boolean fixedRotation, bullet, allowSleep, awake;
        final float gravityScale, linearDamping, angularDamping;

        BodyState(int type, boolean fixedRotation, boolean bullet, boolean allowSleep,
                  boolean awake, float gravityScale, float linearDamping, float angularDamping) {
            this.type = type;
            this.fixedRotation = fixedRotation;
            this.bullet = bullet;
            this.allowSleep = allowSleep;
            this.awake = awake;
            this.gravityScale = gravityScale;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
        }

        BodyState copy() { return new BodyState(type, fixedRotation, bullet, allowSleep, awake,
                gravityScale, linearDamping, angularDamping); }
    }

    static final class TransformState {
        final float x, y, originX, originY, rotationRad, scaleX, scaleY;

        TransformState(float x, float y, float originX, float originY,
                       float rotationRad, float scaleX, float scaleY) {
            this.x = x;
            this.y = y;
            this.originX = originX;
            this.originY = originY;
            this.rotationRad = rotationRad;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        TransformState copy() { return new TransformState(x, y, originX, originY,
                rotationRad, scaleX, scaleY); }
    }
}
