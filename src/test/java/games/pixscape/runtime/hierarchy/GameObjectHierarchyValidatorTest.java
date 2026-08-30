package games.pixscape.runtime.hierarchy;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GameObjectHierarchyValidatorTest {
    private World world;
    private IdentityRegistry identities;
    private SceneMetaRuntime meta;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 1000;
        identities = new IdentityRegistry();
        identities.bind(world, meta);
    }

    @After
    public void tearDown() {
        identities.bind(null, null);
        world.dispose();
    }

    @Test
    public void validMemberToRoot() {
        int root = gameObject(1);
        member(2, 1, false);

        validateAll();

        Assert.assertEquals(root, identities.findByStableId(1));
    }

    @Test
    public void missingParentIsRejected() {
        int child = member(2, 99, false);

        IllegalArgumentException failure = validateMemberFailure(child);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("does not resolve"));
    }

    @Test
    public void parentWithoutGameObjectComponentIsRejected() {
        entity(1);
        int child = member(2, 1, false);

        IllegalArgumentException failure = validateMemberFailure(child);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("is not a Game Object"));
    }

    @Test
    public void selfParentIsRejected() {
        int root = gameObject(1);
        world.getMapper(GameObjectMemberComponent.class).create(root).parentStableId = 1;

        IllegalArgumentException failure = validateMemberFailure(root);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("own parent"));
    }

    @Test
    public void memberWithoutStableIdentityIsRejected() {
        gameObject(1);
        int child = world.create();
        world.getMapper(TransformComponent.class).create(child);
        world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 1;

        IllegalArgumentException failure = validateMemberFailure(child);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("positive persistent stable ID"));
    }

    @Test
    public void twoLevelNestingIsValid() {
        gameObject(1);
        member(2, 1, true);
        member(3, 2, false);

        validateAll();
    }

    @Test
    public void deepNestingIsValid() {
        gameObject(1);
        for (int stableId = 2; stableId <= 64; stableId++) {
            member(stableId, stableId - 1, stableId < 64);
        }

        validateAll();
    }

    @Test
    public void directCycleIsRejected() {
        int first = member(1, 2, true);
        member(2, 1, true);

        IllegalArgumentException failure = validateMemberFailure(first);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("cycle"));
    }

    @Test
    public void indirectCycleIsRejected() {
        int first = member(1, 2, true);
        member(2, 3, true);
        member(3, 1, true);

        IllegalArgumentException failure = validateMemberFailure(first);

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("cycle"));
    }

    @Test
    public void stableIdLookupDoesNotFollowReusedEcsId() {
        int oldRoot = gameObject(1);
        int child = member(2, 1, false);
        processAndRebuild();
        new GameObjectHierarchyValidator(world, identities).validateMember(child);

        world.delete(oldRoot);
        world.process();
        int replacement = gameObject(3);
        processAndRebuild();

        Assert.assertEquals(oldRoot, replacement);
        Assert.assertEquals(-1, identities.findByStableId(1));
        Assert.assertEquals(replacement, identities.findByStableId(3));
        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new GameObjectHierarchyValidator(world, identities).validateMember(child));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("stableId 1"));
    }

    @Test
    public void parentedPhysicsIsRejectedBeforeWorldWritebackCanCorruptLocalTransform() {
        gameObject(1);
        int child = member(2, 1, false);
        world.getMapper(PhysicsBodyComponent.class).create(child);

        IllegalArgumentException failure = validateAllFailure();

        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("parented Physics"));
    }

    @Test
    public void physicsOnGameObjectRootIsRejectedUntilPhysicsHierarchyIntegration() {
        int root = gameObject(1);
        world.getMapper(PhysicsBodyComponent.class).create(root);

        IllegalArgumentException failure = validateAllFailure();

        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains("Physics on a Game Object"));
    }

    @Test
    public void rootsAreCompositionOnlyAndCannotCarryDrawableSpriteState() {
        int root = gameObject(1);
        world.getMapper(TextureRegionComponent.class).create(root);

        IllegalArgumentException failure = validateAllFailure();

        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains("composition-only"));
    }

    @Test
    public void rootsAndMembersRequireBoundedRenderOrderMetadata() {
        int root = gameObject(1);
        world.getMapper(EntityIndexComponent.class).remove(root);
        Assert.assertTrue(validateAllFailure().getMessage().contains("requires EntityIndexComponent"));

        world.getMapper(EntityIndexComponent.class).create(root);
        int child = member(2, 1, false);
        world.getMapper(EntityIndexComponent.class).get(child).zIndex = SortKey64.MAX_Z + 1;
        Assert.assertTrue(validateAllFailure().getMessage().contains("outside supported range"));
    }

    @Test
    public void tiledMapsParticlesAndSpatialActorsRemainExcluded() {
        gameObject(1);
        int tiled = member(2, 1, false);
        world.getMapper(TiledLayerComponent.class).create(tiled);
        Assert.assertTrue(validateAllFailure().getMessage().contains("Tiled Maps"));
        world.getMapper(TiledLayerComponent.class).remove(tiled);

        int particle = member(3, 1, false);
        world.getMapper(ParticleEmitterComponent.class).create(particle);
        Assert.assertTrue(validateAllFailure().getMessage().contains("particles"));
        world.getMapper(ParticleEmitterComponent.class).remove(particle);

        int spatial = member(4, 1, false);
        world.getMapper(SpatialShapesComponent.class).create(spatial);
        Assert.assertTrue(validateAllFailure().getMessage().contains("Spatial actors"));
    }

    private IllegalArgumentException validateMemberFailure(int entityId) {
        processAndRebuild();
        return Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new GameObjectHierarchyValidator(world, identities).validateMember(entityId));
    }

    private void validateAll() {
        processAndRebuild();
        IntBag all = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        new GameObjectHierarchyValidator(world, identities).validateEntities(all);
    }

    private IllegalArgumentException validateAllFailure() {
        processAndRebuild();
        IntBag all = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        return Assert.assertThrows(IllegalArgumentException.class,
                () -> new GameObjectHierarchyValidator(world, identities).validateEntities(all));
    }

    private void processAndRebuild() {
        world.process();
        identities.rebuild();
    }

    private int gameObject(int stableId) {
        return member(stableId, -1, true);
    }

    private int member(int stableId, int parentStableId, boolean gameObject) {
        int entity = entity(stableId);
        if (gameObject) world.getMapper(GameObjectComponent.class).create(entity);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = parentStableId;
        }
        return entity;
    }

    private int entity(int stableId) {
        int entity = world.create();
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entity);
        identity.stableId = stableId;
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        return entity;
    }
}
