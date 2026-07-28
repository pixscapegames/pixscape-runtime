package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeIdAllocatorTest {
    @Test
    public void allocatesFirstAndSuccessiveIdsFromPersistentState() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);

        Assert.assertEquals(1, allocator.allocateNewPhysicsShapeId());
        Assert.assertEquals(2, allocator.allocateNewPhysicsShapeId());
        Assert.assertEquals(3, meta.nextPhysicsShapeId);
    }

    @Test
    public void restoredIdDoesNotAdvanceAllocator() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 8;
        PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);

        allocator.validateRestoredPhysicsShapeId(7);

        Assert.assertEquals(8, allocator.nextPhysicsShapeId());
    }

    @Test
    public void duplicateSimulationAlwaysAllocatesFreshId() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 12;
        PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);
        allocator.validateRestoredPhysicsShapeId(4);

        int duplicateId = allocator.allocateNewPhysicsShapeId();

        Assert.assertEquals(12, duplicateId);
        Assert.assertNotEquals(4, duplicateId);
    }

    @Test
    public void reloadKeepsHighWaterAndDoesNotRecycleDeletedId() {
        SceneMetaRuntime firstSession = new SceneMetaRuntime();
        PhysicsShapeIdAllocator firstAllocator = new PhysicsShapeIdAllocator(firstSession);
        Assert.assertEquals(1, firstAllocator.allocateNewPhysicsShapeId());
        Assert.assertEquals(2, firstAllocator.allocateNewPhysicsShapeId());

        SceneMetaRuntime reloaded = new SceneMetaRuntime(firstSession);
        PhysicsShapeIdAllocator reloadedAllocator = new PhysicsShapeIdAllocator(reloaded);

        Assert.assertEquals(3, reloadedAllocator.allocateNewPhysicsShapeId());
        Assert.assertEquals(4, reloaded.nextPhysicsShapeId);
    }

    @Test
    public void sceneMetadataReadsAndCopiesPersistentHighWater() {
        SceneMetaRuntime parsed = SceneMetaRuntime.fromJson(
                new JsonReader().parse(
                        "{\"sceneSchemaVersion\":2,"
                                + "\"nextEntityStableId\":1,"
                                + "\"nextPhysicsShapeId\":37}"),
                "scene");
        SceneMetaRuntime copied = new SceneMetaRuntime(parsed);

        Assert.assertEquals(37, parsed.nextPhysicsShapeId);
        Assert.assertEquals(37, copied.nextPhysicsShapeId);
    }

    @Test
    public void sceneMetadataSerializesPersistentHighWater() {
        SceneMetaRuntime source = new SceneMetaRuntime("scene", "scene.json");
        source.nextEntityStableId = 2;
        source.nextPhysicsShapeId = 91;

        Json json = new Json();
        json.setUsePrototypes(false);
        String serialized = json.toJson(source);
        SceneMetaRuntime restored = SceneMetaRuntime.fromJson(
                new JsonReader().parse(serialized), "fallback");

        Assert.assertTrue(serialized, serialized.contains("nextPhysicsShapeId:91"));
        Assert.assertTrue(serialized, serialized.contains("sceneSchemaVersion:2"));
        Assert.assertEquals(91, restored.nextPhysicsShapeId);
    }

    @Test
    public void rejectsInvalidHighWater() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 0;

        expectFailure(new Runnable() {
            @Override
            public void run() {
                new PhysicsShapeIdAllocator(meta);
            }
        }, "strictly positive");
    }

    @Test
    public void rejectsPersistedIdCollisionWithHighWater() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 5;
        final PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);

        expectFailure(new Runnable() {
            @Override
            public void run() {
                allocator.validatePersistedPhysicsShapeIds(new int[]{1, 5});
            }
        }, "lower than nextPhysicsShapeId");
    }

    @Test
    public void rejectsDuplicateAndNonPositivePersistedIds() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 5;
        final PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);

        expectFailure(new Runnable() {
            @Override
            public void run() {
                allocator.validatePersistedPhysicsShapeIds(new int[]{1, 1});
            }
        }, "Duplicate");
        expectFailure(new Runnable() {
            @Override
            public void run() {
                allocator.validatePersistedPhysicsShapeIds(new int[]{0});
            }
        }, "strictly positive");
    }

    @Test
    public void refusesAllocationAtIntegerMaxValueWithoutOverflow() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = Integer.MAX_VALUE;
        final PhysicsShapeIdAllocator allocator = new PhysicsShapeIdAllocator(meta);

        expectFailure(new Runnable() {
            @Override
            public void run() {
                allocator.allocateNewPhysicsShapeId();
            }
        }, "exhausted");
        Assert.assertEquals(Integer.MAX_VALUE, meta.nextPhysicsShapeId);
    }

    private static void expectFailure(Runnable action, String messagePart) {
        try {
            action.run();
            Assert.fail("Expected failure containing: " + messagePart);
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains(messagePart));
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains(messagePart));
        }
    }
}
