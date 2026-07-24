package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;

/**
 * Cross-domain integration boundary from compiled physics data to the Runtime spatial cache.
 */
public final class PhysicsSpatialFootprintProjector {
    /**
     * Builds a projection candidate outside the spatial hot path.
     *
     * <p>Temporary policy: when a body contains several compiled circles, the first non-sensor
     * circle in deterministic compiled/authored order supplies the footprint. This policy is
     * isolated here and is not a contract of either the physics cache or the spatial collector.</p>
     */
    public Projection prepare(
            Array<CompiledFixtureData> fixtures,
            int physicsGeneration,
            float pixelsPerMeter) {
        if (fixtures == null) {
            throw new IllegalArgumentException("Compiled fixtures are required.");
        }
        if (!Float.isFinite(pixelsPerMeter) || pixelsPerMeter <= 0f) {
            throw new IllegalArgumentException("pixelsPerMeter must be finite and positive.");
        }
        for (int i = 0; i < fixtures.size; i++) {
            CompiledFixtureData fixture = fixtures.get(i);
            if (fixture.shapeType == CompiledFixtureData.SHAPE_CIRCLE
                    && !fixture.sensor
                    && fixture.radius > 0f) {
                return new Projection(
                        true,
                        fixture.offsetX * pixelsPerMeter,
                        fixture.offsetY * pixelsPerMeter,
                        fixture.radius * pixelsPerMeter,
                        physicsGeneration);
            }
        }
        return new Projection(false, 0f, 0f, 0f, physicsGeneration);
    }

    public void publish(SpatialPhysicsFootprintComponent target, Projection projection) {
        if (target == null || projection == null) {
            throw new IllegalArgumentException("Spatial footprint target and projection are required.");
        }
        target.valid = projection.valid;
        target.localOffsetXPx = projection.localOffsetXPx;
        target.localOffsetYPx = projection.localOffsetYPx;
        target.radiusPx = projection.radiusPx;
        target.physicsGeneration = projection.physicsGeneration;
    }

    public void invalidate(SpatialPhysicsFootprintComponent target, int physicsGeneration) {
        if (target == null) return;
        target.valid = false;
        target.localOffsetXPx = 0f;
        target.localOffsetYPx = 0f;
        target.radiusPx = 0f;
        target.physicsGeneration = physicsGeneration;
    }

    public static final class Projection {
        private final boolean valid;
        private final float localOffsetXPx;
        private final float localOffsetYPx;
        private final float radiusPx;
        private final int physicsGeneration;

        private Projection(
                boolean valid,
                float localOffsetXPx,
                float localOffsetYPx,
                float radiusPx,
                int physicsGeneration) {
            this.valid = valid;
            this.localOffsetXPx = localOffsetXPx;
            this.localOffsetYPx = localOffsetYPx;
            this.radiusPx = radiusPx;
            this.physicsGeneration = physicsGeneration;
        }
    }
}
