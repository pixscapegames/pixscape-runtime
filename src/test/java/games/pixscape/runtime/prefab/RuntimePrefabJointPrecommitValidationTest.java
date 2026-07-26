package games.pixscape.runtime.prefab;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsPrismaticJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRevoluteJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class RuntimePrefabJointPrecommitValidationTest {
    @Test
    public void everyInvalidJointGraphFailsBeforeTargetPublication() {
        for (InvalidGraph invalidGraph : InvalidGraph.values()) {
            Fixture fixture = new Fixture();
            try {
                invalidGraph.mutate(fixture);
                int activeBefore = fixture.activeEntityCount();
                int highWaterBefore = fixture.meta.nextPhysicsShapeId;
                fixture.sentinel.processCount = 0;

                try {
                    fixture.spawner.spawn(
                            fixture.world, fixture.fragment, 0f, 0f);
                    Assert.fail(invalidGraph + " must be rejected before commit.");
                } catch (IllegalArgumentException expected) {
                    Assert.assertTrue(
                            invalidGraph + " must provide a joint diagnostic.",
                            expected.getMessage().contains("joint")
                                    || expected.getMessage().contains("Joint"));
                }

                Assert.assertEquals(
                        invalidGraph + " published target entities.",
                        activeBefore,
                        fixture.activeEntityCount());
                Assert.assertEquals(
                        invalidGraph + " processed the target World.",
                        0,
                        fixture.sentinel.processCount);
                Assert.assertTrue(
                        invalidGraph + " rewound or failed to consume high-water.",
                        fixture.meta.nextPhysicsShapeId > highWaterBefore);
            } finally {
                fixture.world.dispose();
            }
        }
    }

    private enum InvalidGraph {
        DISTANCE_WITHOUT_SPECIFIC {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = PhysicsJointComponent.TYPE_DISTANCE;
            }
        },
        UNKNOWN_TYPE {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = 999;
            }
        },
        ENDPOINT_WITHOUT_BODY {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.aEid = f.gearEntity;
            }
        },
        ENDPOINT_WITHOUT_SHAPES {
            @Override
            void mutate(Fixture f) {
                f.world.getMapper(PhysicsShapesComponent.class).remove(f.bodyA);
            }
        },
        GEAR_SELF_REFERENCE {
            @Override
            void mutate(Fixture f) {
                f.gear.joint1Eid = f.gearEntity;
            }
        },
        GEAR_DUPLICATE_DEPENDENCY {
            @Override
            void mutate(Fixture f) {
                f.gear.joint2Eid = f.gear.joint1Eid;
            }
        },
        GEAR_REFERENCES_DISTANCE {
            @Override
            void mutate(Fixture f) {
                f.revoluteBase.type = PhysicsJointComponent.TYPE_DISTANCE;
                f.world.getMapper(PhysicsRevoluteJointComponent.class)
                        .remove(f.revoluteEntity);
                f.world.getMapper(PhysicsDistanceJointComponent.class)
                        .create(f.revoluteEntity);
            }
        },
        GEAR_REFERENCES_GEAR {
            @Override
            void mutate(Fixture f) {
                int otherGear = f.createGear(
                        f.bodyA,
                        f.bodyC,
                        f.revoluteEntity,
                        f.prismaticEntity);
                f.fragment.entities.add(otherGear);
                f.gear.joint1Eid = otherGear;
            }
        },
        GEAR_REFERENCES_NON_JOINT {
            @Override
            void mutate(Fixture f) {
                f.gear.joint1Eid = f.bodyA;
            }
        },
        GEAR_SOURCE_WITHOUT_SPECIFIC {
            @Override
            void mutate(Fixture f) {
                f.world.getMapper(PhysicsRevoluteJointComponent.class)
                        .remove(f.revoluteEntity);
            }
        };

        abstract void mutate(Fixture fixture);
    }

    private static final class Fixture {
        final SentinelSystem sentinel = new SentinelSystem();
        final World world = new World(new WorldConfigurationBuilder()
                .with(
                        new WorldSerializationManager(),
                        new DirtyTrackerSystem(32),
                        sentinel)
                .build());
        final SceneMetaRuntime meta = new SceneMetaRuntime();
        final RuntimePrefabFragmentSpawner spawner =
                new RuntimePrefabFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        final RuntimePrefabFragment fragment = new RuntimePrefabFragment();
        int nextSourceShapeId = 1;
        final int bodyA = createBody();
        final int bodyB = createBody();
        final int bodyC = createBody();
        final int revoluteEntity =
                createJoint(PhysicsJointComponent.TYPE_REVOLUTE, bodyA, bodyB);
        final PhysicsJointComponent revoluteBase =
                world.getMapper(PhysicsJointComponent.class).get(revoluteEntity);
        final int prismaticEntity =
                createJoint(PhysicsJointComponent.TYPE_PRISMATIC, bodyB, bodyC);
        final int gearEntity =
                createGear(bodyA, bodyC, revoluteEntity, prismaticEntity);
        final PhysicsGearJointComponent gear =
                world.getMapper(PhysicsGearJointComponent.class).get(gearEntity);

        Fixture() {
            meta.physicsEnabled = true;
            fragment.entities.add(bodyA);
            fragment.entities.add(bodyB);
            fragment.entities.add(bodyC);
            fragment.entities.add(revoluteEntity);
            fragment.entities.add(prismaticEntity);
            fragment.entities.add(gearEntity);
            world.process();
            sentinel.processCount = 0;
        }

        int createBody() {
            int entityId = world.create();
            world.getMapper(TransformComponent.class).create(entityId);
            world.getMapper(PhysicsBodyComponent.class).create(entityId);
            PhysicsShapesComponent shapes =
                    world.getMapper(PhysicsShapesComponent.class).create(entityId);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.directGeometry = new PhysicsDirectGeometryData();
            shape.physicsShapeId = nextSourceShapeId++;
            shape.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_BOX;
            shapes.add(shape);
            return entityId;
        }

        int createJoint(int type, int bodyAId, int bodyBId) {
            int entityId = world.create();
            PhysicsJointComponent base =
                    world.getMapper(PhysicsJointComponent.class).create(entityId);
            base.type = type;
            base.aEid = bodyAId;
            base.bEid = bodyBId;
            if (type == PhysicsJointComponent.TYPE_REVOLUTE) {
                world.getMapper(PhysicsRevoluteJointComponent.class)
                        .create(entityId);
            } else if (type == PhysicsJointComponent.TYPE_PRISMATIC) {
                world.getMapper(PhysicsPrismaticJointComponent.class)
                        .create(entityId);
            }
            return entityId;
        }

        int createGear(
                int bodyAId,
                int bodyBId,
                int joint1EntityId,
                int joint2EntityId) {
            int entityId =
                    createJoint(PhysicsJointComponent.TYPE_GEAR, bodyAId, bodyBId);
            PhysicsGearJointComponent gear =
                    world.getMapper(PhysicsGearJointComponent.class)
                            .create(entityId);
            gear.joint1Eid = joint1EntityId;
            gear.joint2Eid = joint2EntityId;
            return entityId;
        }

        int activeEntityCount() {
            return world.getAspectSubscriptionManager()
                    .get(Aspect.all())
                    .getEntities()
                    .size();
        }
    }

    private static final class SentinelSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }
}
