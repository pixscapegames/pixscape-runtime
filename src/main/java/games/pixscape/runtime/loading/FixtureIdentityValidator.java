package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;

/** Validates the persisted scene-global fixture identity contract without repairing data. */
public final class FixtureIdentityValidator {
    private FixtureIdentityValidator() {
    }

    public static void validate(World world, SceneMetaRuntime meta, String sceneLabel) {
        if (world == null) throw new IllegalArgumentException("World is required for fixture identity validation.");
        String scene = sceneLabel != null ? sceneLabel : meta != null && meta.name != null ? meta.name : "<unnamed>";
        if (meta == null) fail(scene, -1, 0, "scene metadata is missing");
        if (meta.nextFixtureId <= 0) {
            fail(scene, -1, meta.nextFixtureId, "nextFixtureId must be strictly positive");
        }

        IntIntMap fixtureBodies = new IntIntMap();
        ComponentMapper<PhysicsFixturesComponent> mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        IntBag fixtureEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsFixturesComponent.class)).getEntities();
        int[] fixtureData = fixtureEntities.getData();
        int maxFixtureId = 0;

        for (int i = 0; i < fixtureEntities.size(); i++) {
            int body = fixtureData[i];
            PhysicsFixturesComponent fixtures = mFixtures.get(body);
            if (fixtures == null || fixtures.fixtures == null) continue;
            for (int j = 0; j < fixtures.fixtures.size; j++) {
                FixtureDefData fixture = fixtures.fixtures.get(j);
                if (fixture == null) continue;
                int id = fixture.fixtureId;
                if (id <= 0) fail(scene, body, id, "fixtureId must be strictly positive");
                if (fixtureBodies.containsKey(id)) {
                    fail(scene, body, id,
                            "duplicate fixtureId; firstBody=" + fixtureBodies.get(id, -1));
                }
                fixtureBodies.put(id, body);
                if (id > maxFixtureId) maxFixtureId = id;
            }
        }

        if (meta.nextFixtureId <= maxFixtureId) {
            fail(scene, -1, maxFixtureId,
                    "nextFixtureId=" + meta.nextFixtureId + " must be greater than every persisted fixtureId");
        }

        IntSet blockClaims = new IntSet();
        ComponentMapper<SpatialBlocksComponent> mBlocks = world.getMapper(SpatialBlocksComponent.class);
        IntBag blockEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class)).getEntities();
        int[] blockData = blockEntities.getData();
        for (int i = 0; i < blockEntities.size(); i++) {
            int body = blockData[i];
            SpatialBlocksComponent blocks = mBlocks.get(body);
            if (blocks == null || blocks.blocks == null) continue;
            for (int j = 0; j < blocks.blocks.size; j++) {
                SpatialBlockData block = blocks.blocks.get(j);
                if (block == null) continue;
                if (block.fixtureId < 0) fail(scene, body, block.fixtureId, "spatial block fixtureId is negative; blockId=" + block.id);
                if (!block.physicsCollision) {
                    if (block.fixtureId != 0) fail(scene, body, block.fixtureId, "non-collision spatial block owns a fixture; blockId=" + block.id);
                    continue;
                }
                if (block.fixtureId == 0) fail(scene, body, 0, "collision spatial block has no fixture; blockId=" + block.id);
                if (fixtureBodies.get(block.fixtureId, -1) != body) {
                    fail(scene, body, block.fixtureId, "spatial block fixture is missing from its body; blockId=" + block.id);
                }
                if (!blockClaims.add(block.fixtureId)) {
                    fail(scene, body, block.fixtureId, "fixture is claimed by multiple spatial blocks; blockId=" + block.id);
                }
            }
        }
    }

    private static void fail(String scene, int body, int fixtureId, String reason) {
        throw new IllegalStateException(
                "Invalid fixture identity state: scene=" + scene + ", body=" + body
                        + ", fixtureId=" + fixtureId + ", reason=" + reason);
    }
}
