package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class PixscapeEngineActiveScenePhysicsIdentityTest {
    @Test
    public void explicitActiveSceneOwnsPrefabShapeAllocation() throws Exception {
        SceneMetaRuntime sceneA = new SceneMetaRuntime("A", "a.json");
        sceneA.nextPhysicsShapeId = 5;
        SceneMetaRuntime sceneB = new SceneMetaRuntime("B", "b.json");
        sceneB.nextPhysicsShapeId = 100;
        RuntimeConfig config = new RuntimeConfig();
        config.scenes.put("A", sceneA);
        config.scenes.put("B", sceneB);
        config.currentSceneName = "A";

        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(32))
                .build());
        WorldSerializationManager serialization =
                world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        int sourceEntity = world.create();
        world.getMapper(TransformComponent.class).create(sourceEntity);
        PhysicsShapesComponent sourceShapes =
                world.getMapper(PhysicsShapesComponent.class).create(sourceEntity);
        PhysicsShapeData sourceShape = new PhysicsShapeData();
        sourceShape.physicsShapeId = 10;
        sourceShape.shapeType = PhysicsShapeData.SHAPE_BOX;
        sourceShapes.add(sourceShape);
        world.process();
        SaveFileFormat fragment = new SaveFileFormat();
        fragment.entities.add(sourceEntity);

        PixscapeEngine engine = new PixscapeEngine();
        set(engine, "cfg", config);
        set(engine, "world", world);
        set(engine, "sceneLoaded", true);
        set(engine, "activeSceneMeta", sceneB);

        SpawnResult result = engine.spawnPrefabFragment(fragment, 0f, 0f);
        PhysicsShapeData spawnedShape = world.getMapper(PhysicsShapesComponent.class)
                .get(result.createdEntityIds().get(0)).shapes.first();

        Assert.assertSame(sceneB, engine.getActiveSceneMeta());
        Assert.assertEquals(100, spawnedShape.physicsShapeId);
        Assert.assertEquals(101, sceneB.nextPhysicsShapeId);
        Assert.assertEquals(5, sceneA.nextPhysicsShapeId);
    }

    private static void set(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
