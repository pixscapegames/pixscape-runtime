package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.EntityId;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class PhysicsJointSerializationRemapTest {

    @Test
    public void jsonSerializationRemapsJointEntityReferences() throws Exception {
        GdxNativesLoader.load();

        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), dirty, sync)
                .build());

        PhysicsService physics = new PhysicsService(world, box2d);

        for (int i = 0; i < 25; i++) world.create();

        int bodyA = createBody(world, physics, 0f, 0f);
        int bodyB = createBody(world, physics, 100f, 0f);
        int bodyC = createBody(world, physics, 200f, 0f);

        int revolute = physics.createRevoluteJoint(bodyA, bodyB);
        int wheel = physics.createWheelJoint(bodyB, bodyC);
        int gear = physics.createGearJoint(revolute, wheel, 2f);

        world.process();

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(world));

        SaveFileFormat save = new SaveFileFormat();
        save.entities.add(gear);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, save);
        String json = out.toString(StandardCharsets.UTF_8);

        JsonValue root = new JsonReader().parse(json);
        JsonValue entities = root.get("entities");
        Assert.assertNotNull("Serialized JSON must contain root.entities", entities);

        Set<Integer> serializedBodyIds = new HashSet<>();
        Set<Integer> serializedJointIds = new HashSet<>();
        int wheelJointCount = 0;
        int gearJointCount = 0;

        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int eid = Integer.parseInt(entity.name);
            JsonValue components = entity.get("components");
            if (components == null) continue;
            if (components.has("PhysicsBodyComponent")) serializedBodyIds.add(eid);
            if (components.has("PhysicsJointComponent")) serializedJointIds.add(eid);
        }

        Assert.assertTrue("Body ids should be remapped to low serialized ids", serializedBodyIds.contains(0));
        Assert.assertFalse("Original body id must not survive as serialized id", serializedBodyIds.contains(bodyA));

        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int jointEid = Integer.parseInt(entity.name);
            JsonValue components = entity.get("components");
            if (components == null || !components.has("PhysicsJointComponent")) continue;

            JsonValue joint = components.get("PhysicsJointComponent");
            int type = joint.getInt("type");
            int aEid = joint.getInt("aEid");
            int bEid = joint.getInt("bEid");

            Assert.assertNotEquals("PhysicsJointComponent.aEid must not point to itself", jointEid, aEid);
            Assert.assertNotEquals("PhysicsJointComponent.bEid must not point to itself", jointEid, bEid);
            Assert.assertTrue("PhysicsJointComponent.aEid must point to serialized body", serializedBodyIds.contains(aEid));
            Assert.assertTrue("PhysicsJointComponent.bEid must point to serialized body", serializedBodyIds.contains(bEid));
            Assert.assertFalse("PhysicsJointComponent.aEid must not point to joint entity", serializedJointIds.contains(aEid));
            Assert.assertFalse("PhysicsJointComponent.bEid must not point to joint entity", serializedJointIds.contains(bEid));

            if (type == PhysicsJointComponent.TYPE_WHEEL) {
                wheelJointCount++;
                Assert.assertTrue("Wheel joint aEid must point to serialized body", serializedBodyIds.contains(aEid));
                Assert.assertTrue("Wheel joint bEid must point to serialized body", serializedBodyIds.contains(bEid));
            }

            if (type == PhysicsJointComponent.TYPE_GEAR) {
                gearJointCount++;
                JsonValue gearData = components.get("PhysicsGearJointComponent");
                Assert.assertNotNull("Gear joint entity must include PhysicsGearJointComponent", gearData);

                int j1 = gearData.getInt("joint1Eid");
                int j2 = gearData.getInt("joint2Eid");
                Assert.assertTrue("joint1Eid must point to serialized joint", serializedJointIds.contains(j1));
                Assert.assertTrue("joint2Eid must point to serialized joint", serializedJointIds.contains(j2));
                Assert.assertNotEquals("joint1Eid must not self-reference gear", jointEid, j1);
                Assert.assertNotEquals("joint2Eid must not self-reference gear", jointEid, j2);
                Assert.assertNotEquals("joint1Eid should be remapped (not old id)", revolute, j1);
                Assert.assertNotEquals("joint2Eid should be remapped (not old id)", wheel, j2);
            }
        }

        Assert.assertTrue("Serialized closure must include at least one wheel joint", wheelJointCount >= 1);
        Assert.assertTrue("Serialized closure must include at least one gear joint", gearJointCount >= 1);

        assertEntityIdAnnotation(PhysicsJointComponent.class, "aEid");
        assertEntityIdAnnotation(PhysicsJointComponent.class, "bEid");
        assertEntityIdAnnotation(PhysicsGearJointComponent.class, "joint1Eid");
        assertEntityIdAnnotation(PhysicsGearJointComponent.class, "joint2Eid");

        Assert.assertTrue("Test precondition: body ids should be non-trivial before serialization",
                bodyA >= 25 && bodyB >= 26 && bodyC >= 27 && gear > bodyC);
    }

    private static int createBody(World world, PhysicsService physics, float x, float y) {
        int eid = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(eid);
        t.x = x;
        t.y = y;
        physics.ensurePhysics(eid);
        return eid;
    }

    private static void assertEntityIdAnnotation(Class<?> type, String fieldName) throws Exception {
        Field f = type.getField(fieldName);
        Assert.assertTrue("Missing @EntityId on " + type.getSimpleName() + "." + fieldName,
                f.isAnnotationPresent(EntityId.class));
    }
}
