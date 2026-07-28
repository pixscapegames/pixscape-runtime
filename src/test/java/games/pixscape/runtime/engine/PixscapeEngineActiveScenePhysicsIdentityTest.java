package games.pixscape.runtime.engine;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.managers.WorldSerializationManager;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
import games.pixscape.runtime.service.AtlasRuntimeService;
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
        sceneB.physicsEnabled = true;
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
        sourceShape.geometry = new PhysicsGeometryData();
        sourceShape.physicsShapeId = 10;
        sourceShape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        sourceShapes.shapes.add(sourceShape);
        world.process();
        RuntimePrefabFragment fragment = new RuntimePrefabFragment();
        fragment.entities.add(sourceEntity);

        PixscapeEngine engine = new PixscapeEngine();
        set(engine, "cfg", config);
        set(engine, "world", world);
        set(engine, "sceneLoaded", true);
        set(engine, "activeSceneMeta", sceneB);
        set(engine, "atlasRuntimeService", new AtlasRuntimeService());

        SpawnResult result = engine.spawnPrefabFragment(fragment, 0f, 0f);
        PhysicsShapeData spawnedShape = world.getMapper(PhysicsShapesComponent.class)
                .get(result.createdEntityIds().get(0)).shapes.first();

        Assert.assertSame(sceneB, engine.getActiveSceneMeta());
        Assert.assertEquals(100, spawnedShape.physicsShapeId);
        Assert.assertEquals(101, sceneB.nextPhysicsShapeId);
        Assert.assertEquals(5, sceneA.nextPhysicsShapeId);
    }

    @Test
    public void invalidPrefabAssetIsNotPublishedByPublicEnginePath()
            throws Exception {
        SceneMetaRuntime scene = new SceneMetaRuntime("A", "a.json");
        scene.physicsEnabled = false;
        scene.nextEntityStableId = 71;
        scene.nextPhysicsShapeId = 19;
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(32))
                .build());
        int sourceEntity = world.create();
        AssetRefComponent assetRef =
                world.getMapper(AssetRefComponent.class).create(sourceEntity);
        assetRef.assetId = -1;
        assetRef.atlasTag = "main";
        world.getMapper(TextureRegionComponent.class).create(sourceEntity);
        world.getMapper(RenderMaterialComponent.class).create(sourceEntity);
        world.process();
        RuntimePrefabFragment fragment = new RuntimePrefabFragment();
        fragment.entities.add(sourceEntity);
        int activeBefore = world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size();

        PixscapeEngine engine = new PixscapeEngine();
        set(engine, "world", world);
        set(engine, "sceneLoaded", true);
        set(engine, "activeSceneMeta", scene);
        set(engine, "atlasRuntimeService", new AtlasRuntimeService());

        try {
            engine.spawnPrefabFragment(fragment, 0f, 0f);
            Assert.fail("Invalid prefab asset must be rejected.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("assetId"));
        }

        Assert.assertTrue(world.getEntityManager().isActive(sourceEntity));
        Assert.assertEquals(activeBefore, world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size());
        Assert.assertEquals(71, scene.nextEntityStableId);
        Assert.assertEquals(19, scene.nextPhysicsShapeId);
    }

    private static void set(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
