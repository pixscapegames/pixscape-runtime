package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;

/**
 * Cross-domain integration boundary from compiled physics data to the Runtime spatial cache.
 */
public final class PhysicsSpatialFootprintProjector {
    /**
     * Builds a projection candidate outside the spatial hot path.
     *
     * <p>An explicit compiled Spatial footprint takes precedence. Legacy data with no explicit
     * footprint uses the first valid non-sensor circle in deterministic compiled order. This
     * compatibility fallback is isolated here and must be removed after old scenes are normalized.</p>
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
        CompiledFixtureData explicitFixture = null;
        int explicitCount = 0;
        for (int i = 0; i < fixtures.size; i++) {
            CompiledFixtureData fixture = fixtures.get(i);
            if (fixture != null && fixture.spatialFootprint) {
                explicitFixture = fixture;
                explicitCount++;
            }
        }
        if (explicitCount > 1) {
            return Projection.invalid(physicsGeneration, true);
        }
        if (explicitCount == 1) {
            return validFixture(explicitFixture)
                    ? Projection.of(explicitFixture, physicsGeneration, pixelsPerMeter, true)
                    : Projection.invalid(physicsGeneration, true);
        }

        for (int i = 0; i < fixtures.size; i++) {
            CompiledFixtureData fixture = fixtures.get(i);
            if (validFixture(fixture)) {
                return Projection.of(fixture, physicsGeneration, pixelsPerMeter, false);
            }
        }
        return Projection.invalid(physicsGeneration, false);
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
        target.sourcePhysicsShapeId = projection.sourcePhysicsShapeId;
        target.explicitOwnership = projection.explicitOwnership;
        target.invalidExplicitOwnership = projection.invalidExplicitOwnership;
    }

    public void invalidate(SpatialPhysicsFootprintComponent target, int physicsGeneration) {
        if (target == null) return;
        target.valid = false;
        target.localOffsetXPx = 0f;
        target.localOffsetYPx = 0f;
        target.radiusPx = 0f;
        target.physicsGeneration = physicsGeneration;
        target.sourcePhysicsShapeId = 0;
        target.explicitOwnership = false;
        target.invalidExplicitOwnership = false;
    }

    private static boolean validFixture(CompiledFixtureData fixture) {
        return fixture != null
                && fixture.physicsShapeId > 0
                && fixture.shapeType == PhysicsGeometryData.SHAPE_CIRCLE
                && !fixture.sensor
                && finitePositive(fixture.radius);
    }

    private static boolean finitePositive(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value > 0f;
    }

    public static final class Projection {
        private final boolean valid;
        private final float localOffsetXPx;
        private final float localOffsetYPx;
        private final float radiusPx;
        private final int physicsGeneration;
        private final int sourcePhysicsShapeId;
        private final boolean explicitOwnership;
        private final boolean invalidExplicitOwnership;

        private Projection(boolean valid, float localOffsetXPx, float localOffsetYPx,
                           float radiusPx, int physicsGeneration, int sourcePhysicsShapeId,
                           boolean explicitOwnership, boolean invalidExplicitOwnership) {
            this.valid = valid;
            this.localOffsetXPx = localOffsetXPx;
            this.localOffsetYPx = localOffsetYPx;
            this.radiusPx = radiusPx;
            this.physicsGeneration = physicsGeneration;
            this.sourcePhysicsShapeId = sourcePhysicsShapeId;
            this.explicitOwnership = explicitOwnership;
            this.invalidExplicitOwnership = invalidExplicitOwnership;
        }

        private static Projection of(CompiledFixtureData fixture, int physicsGeneration,
                                     float pixelsPerMeter, boolean explicitOwnership) {
            return new Projection(true,
                    fixture.offsetX * pixelsPerMeter,
                    fixture.offsetY * pixelsPerMeter,
                    fixture.radius * pixelsPerMeter,
                    physicsGeneration,
                    fixture.physicsShapeId,
                    explicitOwnership,
                    false);
        }

        private static Projection invalid(int physicsGeneration,
                                          boolean invalidExplicitOwnership) {
            return new Projection(false, 0f, 0f, 0f, physicsGeneration,
                    0, false, invalidExplicitOwnership);
        }

        public boolean hasInvalidExplicitOwnership() {
            return invalidExplicitOwnership;
        }
    }
}
