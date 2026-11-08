package games.pixscape.runtime.system;

import games.pixscape.runtime.physics.CompiledFixtureData;

/** Test-only access to the existing native materialization observer. */
public final class SpawnPhysicsProbe implements Box2dSyncSystem.TestObserver {
    public int bodies, fixtures, joints, eligibility, cacheChecks;
    public int failBody, failFixture, failJoint;

    public SpawnPhysicsProbe(Box2dSyncSystem system) { system.setTestObserver(this); }
    @Override public void onBodyEligibilityCheck() { eligibility++; }
    @Override public void onBodyCacheCheck() { cacheChecks++; }
    @Override public void onBodyCompile() { }
    @Override public void onShapeCompile() { }
    @Override public void onPolygonDecomposition() { }
    @Override public void onBodyRebuild() {
        if (++bodies == failBody) throw new IllegalStateException("Injected body creation failure");
    }
    @Override public void beforeCreateFixture(int entity, CompiledFixtureData fixture) {
        if (++fixtures == failFixture) throw new IllegalStateException("Injected fixture creation failure");
    }
    @Override public void onFixtureProvenanceCreated(int entity, CompiledFixtureData fixture) { }
    @Override public void beforeCreateOrRebuildJoint(int entity) {
        if (++joints == failJoint) throw new IllegalStateException("Injected joint creation failure");
    }
}
