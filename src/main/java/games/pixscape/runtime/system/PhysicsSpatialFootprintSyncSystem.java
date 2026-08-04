package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.service.PhysicsSpatialFootprintProjector;

/**
 * Cross-domain integration boundary from compiled physics caches to spatial Runtime footprints.
 */
public final class PhysicsSpatialFootprintSyncSystem extends BaseSystem {
    private float pixelsPerMeter;
    private boolean forceResync;
    private final PhysicsSpatialFootprintProjector projector =
            new PhysicsSpatialFootprintProjector();

    private ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled;
    private ComponentMapper<SpatialPhysicsFootprintComponent> mFootprint;
    private EntitySubscription compiledSubscription;
    private EntitySubscription footprintSubscription;
    private transient TestObserver testObserver;

    public PhysicsSpatialFootprintSyncSystem(float pixelsPerMeter) {
        validatePixelsPerMeter(pixelsPerMeter);
        this.pixelsPerMeter = pixelsPerMeter;
    }

    public void setPixelsPerMeter(float pixelsPerMeter) {
        validatePixelsPerMeter(pixelsPerMeter);
        if (this.pixelsPerMeter == pixelsPerMeter) return;
        this.pixelsPerMeter = pixelsPerMeter;
        forceResync = true;
    }

    @Override
    protected void initialize() {
        mCompiled = world.getMapper(PhysicsCompiledFixturesComponent.class);
        mFootprint = world.getMapper(SpatialPhysicsFootprintComponent.class);
        compiledSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsCompiledFixturesComponent.class));
        footprintSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialPhysicsFootprintComponent.class));
    }

    @Override
    protected void processSystem() {
        syncCompiledCaches();
        invalidateOrphanFootprints();
        forceResync = false;
    }

    private void syncCompiledCaches() {
        IntBag entities = compiledSubscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            PhysicsCompiledFixturesComponent compiled = mCompiled.get(entityId);
            SpatialPhysicsFootprintComponent footprint =
                    mFootprint.getSafe(entityId, null);

            if (compiled == null || !compiled.valid) {
                if (footprint != null
                        && (footprint.valid
                        || footprint.physicsGeneration != compiledGeneration(compiled)
                        || footprint.sourcePhysicsShapeId != 0
                        || footprint.invalidSpatialFootprint)) {
                    projector.invalidate(footprint, compiledGeneration(compiled));
                }
                continue;
            }
            if (!forceResync
                    && footprint != null
                    && footprint.physicsGeneration == compiled.generation) {
                continue;
            }

            PhysicsSpatialFootprintProjector.Projection projection =
                    projector.prepare(
                            compiled.fixtures,
                            compiled.generation,
                            pixelsPerMeter);
            if (testObserver != null) testObserver.onProjection();
            if (footprint == null) {
                footprint = mFootprint.create(entityId);
            }
            projector.publish(footprint, projection);
        }
    }

    private void invalidateOrphanFootprints() {
        IntBag entities = footprintSubscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            if (mCompiled.has(entityId)) continue;
            SpatialPhysicsFootprintComponent footprint = mFootprint.get(entityId);
            if (footprint != null
                    && (footprint.valid
                    || footprint.sourcePhysicsShapeId != 0
                    || footprint.invalidSpatialFootprint)) {
                projector.invalidate(footprint, footprint.physicsGeneration + 1);
            }
        }
    }

    private static int compiledGeneration(
            PhysicsCompiledFixturesComponent compiled) {
        return compiled != null ? compiled.generation : 0;
    }

    private static void validatePixelsPerMeter(float pixelsPerMeter) {
        if (Float.isNaN(pixelsPerMeter)
                || Float.isInfinite(pixelsPerMeter)
                || pixelsPerMeter <= 0f) {
            throw new IllegalArgumentException(
                    "pixelsPerMeter must be finite and positive.");
        }
    }

    void setTestObserver(TestObserver testObserver) {
        this.testObserver = testObserver;
    }

    interface TestObserver {
        void onProjection();
    }
}
