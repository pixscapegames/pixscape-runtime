package games.pixscape.runtime.prefab;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.IdentityRegistry;

public final class PrefabSpawnService {
    private final World world;
    private final PrefabLoader loader;
    private final FileHandle runtimeProjectDir;
    private final RuntimeConfig config;
    private final IdentityRegistry identityRegistry;

    public PrefabSpawnService(World world,
                              PrefabLoader loader,
                              FileHandle runtimeProjectDir,
                              RuntimeConfig config,
                              IdentityRegistry identityRegistry) {
        this.world = world;
        this.loader = loader;
        this.runtimeProjectDir = runtimeProjectDir;
        this.config = config;
        this.identityRegistry = identityRegistry;
    }

    public PrefabInstance spawn(String prefabName, float x, float y) {
        String fileName = RuntimeFs.withExt(RuntimeFs.filenameOnly(prefabName), RuntimeFs.EXT_PREFAB);
        FileHandle file = config.prefabsRoot(runtimeProjectDir).child(fileName);
        return spawn(file, x, y);
    }

    public PrefabInstance spawn(FileHandle prefabFile, float x, float y) {
        PrefabAsset asset = loader.load(prefabFile);
        return spawn(asset, x, y, -1);
    }

    public PrefabInstance spawn(PrefabAsset asset, float x, float y, int overrideLayerIndex) {
        float[] bounds = computeBounds(asset);
        float originX = (bounds[0] + bounds[2]) * 0.5f;
        float originY = (bounds[1] + bounds[3]) * 0.5f;
        float dx = x - originX;
        float dy = y - originY;

        PrefabInstance instance = new PrefabInstance();

        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            int eid = world.create();
            instance.addMapping(src.sourceEntityId, eid);
            copyEntityComponents(src, eid, dx, dy, overrideLayerIndex);
        }

        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            int eid = instance.getEntityForLocalId(src.sourceEntityId);
            remapJointRefs(src, eid, instance);
        }

        world.process();
        return instance;
    }

    private float[] computeBounds(PrefabAsset asset) {
        float minX = 0f, minY = 0f, maxX = 0f, maxY = 0f;
        boolean hasTransform = false;
        for (PrefabAsset.PrefabEntityData entity : asset.entities) {
            if (entity.transform == null) continue;
            float x = entity.transform.x;
            float y = entity.transform.y;
            if (!hasTransform) {
                minX = maxX = x;
                minY = maxY = y;
                hasTransform = true;
            } else {
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private void copyEntityComponents(PrefabAsset.PrefabEntityData src, int eid, float dx, float dy, int overrideLayerIndex) {
        if (src.transform != null) {
            TransformComponent c = world.edit(eid).create(TransformComponent.class);
            c.x = src.transform.x + dx;
            c.y = src.transform.y + dy;
            c.rotationRad = src.transform.rotationRad;
            c.scaleX = src.transform.scaleX;
            c.scaleY = src.transform.scaleY;
            c.originX = src.transform.originX;
            c.originY = src.transform.originY;
        }
        if (src.entityIndex != null) {
            EntityIndexComponent c = world.edit(eid).create(EntityIndexComponent.class);
            c.layerIndex = overrideLayerIndex >= 0 ? overrideLayerIndex : src.entityIndex.layerIndex;
            c.zIndex = src.entityIndex.zIndex;
        }
        if (src.identity != null) {
            PixscapeIdentityComponent c = world.edit(eid).create(PixscapeIdentityComponent.class);
            c.name = src.identity.name;
            c.stableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
            if (identityRegistry != null) {
                identityRegistry.ensureStableId(eid);
            }
        }
        if (src.visibility != null) {
            VisibilityComponent c = world.edit(eid).create(VisibilityComponent.class);
            c.visible = src.visibility.visible;
        }
        if (src.dimensions != null) {
            DimensionsComponent c = world.edit(eid).create(DimensionsComponent.class);
            c.width = src.dimensions.width;
            c.height = src.dimensions.height;
        }
        if (src.textureRegion != null) {
            TextureRegionComponent c = world.edit(eid).create(TextureRegionComponent.class);
            c.u1 = src.textureRegion.u1;
            c.v1 = src.textureRegion.v1;
            c.u2 = src.textureRegion.u2;
            c.v2 = src.textureRegion.v2;
            c.pixW = src.textureRegion.pixW;
            c.pixH = src.textureRegion.pixH;
            c.valid = src.textureRegion.valid;
        }
        if (src.renderMaterial != null) {
            RenderMaterialComponent c = world.edit(eid).create(RenderMaterialComponent.class);
            c.shaderIdx = src.renderMaterial.shaderIdx;
            c.blendModeId = src.renderMaterial.blendModeId;
            c.textureHandle = src.renderMaterial.textureHandle;
            c.debugAtlasTag = src.renderMaterial.debugAtlasTag;
        }
        if (src.assetRef != null) {
            AssetRefComponent c = world.edit(eid).create(AssetRefComponent.class);
            c.assetId = src.assetRef.assetId;
            c.atlasTag = src.assetRef.atlasTag;
        }
        if (src.tint != null) {
            TintComponent c = world.edit(eid).create(TintComponent.class);
            c.rgba = src.tint.rgba;
        }
        if (src.animation != null) {
            AnimationComponent c = world.edit(eid).create(AnimationComponent.class);
            c.animation = src.animation.name;
            c.fps = src.animation.fps;
            c.playing = src.animation.playing;
            c.loop = src.animation.loop;
            c.stateTime = src.animation.stateTime;
            c.frame = src.animation.frame;
            c.currentClip = src.animation.currentClip;
        }
        if (src.physicsBody != null) {
            PhysicsBodyComponent c = world.edit(eid).create(PhysicsBodyComponent.class);
            c.type = src.physicsBody.type;
            c.fixedRotation = src.physicsBody.fixedRotation;
            c.bullet = src.physicsBody.bullet;
            c.allowSleep = src.physicsBody.allowSleep;
            c.awake = src.physicsBody.awake;
            c.enabled = src.physicsBody.enabled;
            c.gravityScale = src.physicsBody.gravityScale;
            c.linearDamping = src.physicsBody.linearDamping;
            c.angularDamping = src.physicsBody.angularDamping;
        }
        if (src.fixtures != null && src.fixtures.size > 0) {
            PhysicsFixturesComponent c = world.edit(eid).create(PhysicsFixturesComponent.class);
            for (FixtureDefData fixture : src.fixtures) c.fixtures.add(fixture);
        }
        if (src.joint != null) {
            PhysicsJointComponent c = world.edit(eid).create(PhysicsJointComponent.class);
            c.type = src.joint.type;
            c.aEid = src.joint.aEid;
            c.bEid = src.joint.bEid;
            c.collideConnected = src.joint.collideConnected;
            c.anchorAx = src.joint.anchorAx;
            c.anchorAy = src.joint.anchorAy;
            c.anchorBx = src.joint.anchorBx;
            c.anchorBy = src.joint.anchorBy;
        }
        if (src.distanceJoint != null) {
            var c = world.edit(eid).create(PhysicsDistanceJointComponent.class);
            c.lengthM = src.distanceJoint.lengthM; c.frequencyHz = src.distanceJoint.frequencyHz; c.dampingRatio = src.distanceJoint.dampingRatio;
        }
        if (src.revoluteJoint != null) {
            var c = world.edit(eid).create(PhysicsRevoluteJointComponent.class);
            c.enableLimit = src.revoluteJoint.enableLimit; c.enableMotor = src.revoluteJoint.enableMotor;
            c.lowerAngleRad = src.revoluteJoint.lowerAngleRad; c.upperAngleRad = src.revoluteJoint.upperAngleRad;
            c.motorSpeedRad = src.revoluteJoint.motorSpeedRad; c.maxMotorTorque = src.revoluteJoint.maxMotorTorque;
        }
        if (src.prismaticJoint != null) {
            var c = world.edit(eid).create(PhysicsPrismaticJointComponent.class);
            c.axisX = src.prismaticJoint.axisX; c.axisY = src.prismaticJoint.axisY;
            c.enableLimit = src.prismaticJoint.enableLimit; c.enableMotor = src.prismaticJoint.enableMotor;
            c.lowerTranslationM = src.prismaticJoint.lowerTranslationM; c.upperTranslationM = src.prismaticJoint.upperTranslationM;
            c.motorSpeedMps = src.prismaticJoint.motorSpeedMps; c.maxMotorForce = src.prismaticJoint.maxMotorForce;
        }
        if (src.wheelJoint != null) {
            var c = world.edit(eid).create(PhysicsWheelJointComponent.class);
            c.axisX = src.wheelJoint.axisX; c.axisY = src.wheelJoint.axisY; c.enableMotor = src.wheelJoint.enableMotor;
            c.motorSpeedRad = src.wheelJoint.motorSpeedRad; c.maxMotorTorque = src.wheelJoint.maxMotorTorque;
            c.frequencyHz = src.wheelJoint.frequencyHz; c.dampingRatio = src.wheelJoint.dampingRatio;
        }
        if (src.frictionJoint != null) {
            var c = world.edit(eid).create(PhysicsFrictionJointComponent.class);
            c.maxForce = src.frictionJoint.maxForce; c.maxTorque = src.frictionJoint.maxTorque;
        }
        if (src.motorJoint != null) {
            var c = world.edit(eid).create(PhysicsMotorJointComponent.class);
            c.linearOffsetX = src.motorJoint.linearOffsetX; c.linearOffsetY = src.motorJoint.linearOffsetY;
            c.angularOffsetRad = src.motorJoint.angularOffsetRad;
            c.maxForce = src.motorJoint.maxForce; c.maxTorque = src.motorJoint.maxTorque; c.correctionFactor = src.motorJoint.correctionFactor;
        }
        if (src.weldJoint != null) {
            var c = world.edit(eid).create(PhysicsWeldJointComponent.class);
            c.referenceAngleRad = src.weldJoint.referenceAngleRad; c.frequencyHz = src.weldJoint.frequencyHz; c.dampingRatio = src.weldJoint.dampingRatio;
        }
        if (src.pulleyJoint != null) {
            var c = world.edit(eid).create(PhysicsPulleyJointComponent.class);
            c.groundAx = src.pulleyJoint.groundAx; c.groundAy = src.pulleyJoint.groundAy; c.groundBx = src.pulleyJoint.groundBx; c.groundBy = src.pulleyJoint.groundBy;
            c.lengthAM = src.pulleyJoint.lengthAM; c.lengthBM = src.pulleyJoint.lengthBM; c.ratio = src.pulleyJoint.ratio;
        }
        if (src.gearJoint != null) {
            var c = world.edit(eid).create(PhysicsGearJointComponent.class);
            c.joint1Eid = src.gearJoint.joint1Eid; c.joint2Eid = src.gearJoint.joint2Eid; c.ratio = src.gearJoint.ratio;
        }
    }

    private void remapJointRefs(PrefabAsset.PrefabEntityData src, int jointEid, PrefabInstance instance) {
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).getSafe(jointEid, null);
        if (joint != null) {
            joint.aEid = instance.getEntityForLocalId(joint.aEid);
            joint.bEid = instance.getEntityForLocalId(joint.bEid);
        }
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).getSafe(jointEid, null);
        if (gear != null && src.gearJoint != null) {
            gear.joint1Eid = instance.getEntityForLocalId(src.gearJoint.joint1Eid);
            gear.joint2Eid = instance.getEntityForLocalId(src.gearJoint.joint2Eid);
        }
    }
}
