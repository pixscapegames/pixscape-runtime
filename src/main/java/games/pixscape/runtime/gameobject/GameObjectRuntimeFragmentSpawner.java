package games.pixscape.runtime.gameobject;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.util.List;
import java.util.Map;

/** Builds a real ECS Game Object hierarchy from authored asset-local data. */
public final class GameObjectRuntimeFragmentSpawner {
    private final IdentityRegistry identityRegistry;
    private final SceneMetaRuntime sceneMeta;
    private final AtlasRuntimeService atlasRuntimeService;
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();

    public GameObjectRuntimeFragmentSpawner(
            IdentityRegistry identityRegistry,
            SceneMetaRuntime sceneMeta,
            AtlasRuntimeService atlasRuntimeService) {
        if (identityRegistry == null) throw new IllegalArgumentException("identityRegistry must not be null");
        if (atlasRuntimeService == null) throw new IllegalArgumentException("atlasRuntimeService must not be null");
        this.identityRegistry = identityRegistry;
        this.sceneMeta = sceneMeta;
        this.atlasRuntimeService = atlasRuntimeService;
    }

    public SpawnResult spawn(
            World world, GameObjectRuntimeFragment fragment,
            float rootOffsetX, float rootOffsetY) {
        GameObjectRuntimeFragment.requireCurrentSchema(fragment);
        return spawnAsset(world, fragment.toAsset(), fragment.sourceAssetId,
                rootOffsetX, rootOffsetY);
    }

    public SpawnResult spawnAsset(
            World world, GameObjectAsset asset, String sourceAssetId,
            float rootOffsetX, float rootOffsetY) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        loader.validate(asset, null);
        if (sceneMeta != null && !sceneMeta.physicsEnabled && containsAuthoredPhysics(asset)) {
            throw new IllegalStateException(
                    "Cannot spawn a Game Object with authored Physics while scene Physics is disabled.");
        }
        identityRegistry.bind(world, sceneMeta);
        identityRegistry.rebuild();

        IntIntMap sourceToStable = allocateStableIds(asset.entities);
        IntMap<PreparedAssetBinding> bindings = prepareAssetBindings(asset.entities);
        IntMap<PreparedPhysics> physics = preparePhysics(world, asset.entities);
        IntArray created = new IntArray(false, asset.entities.size() + asset.joints.size());
        IntIntMap sourceToEntity = new IntIntMap(asset.entities.size());
        int rootEntityId = -1;
        try {
            List<GameObjectAsset.GameObjectEntityData> ordered = topologicalOrder(asset);
            for (int i = 0; i < ordered.size(); i++) {
                GameObjectAsset.GameObjectEntityData data = ordered.get(i);
                int entityId = world.create();
                created.add(entityId);
                sourceToEntity.put(data.sourceEntityId, entityId);
                apply(world, entityId, data, asset.rootSourceEntityId,
                        sourceAssetId, sourceToStable, bindings.get(data.sourceEntityId),
                        physics.get(data.sourceEntityId), rootOffsetX, rootOffsetY);
                if (data.sourceEntityId == asset.rootSourceEntityId) rootEntityId = entityId;
            }
            createJoints(world, asset.joints, sourceToEntity, rootEntityId, created);
            markCreatedDirty(world, created);
            return new SpawnResult(toBag(created), rootEntityId);
        } catch (RuntimeException failure) {
            rollback(world, created);
            throw failure;
        } catch (Error failure) {
            rollback(world, created);
            throw failure;
        }
    }

    private static boolean containsAuthoredPhysics(GameObjectAsset asset) {
        if (!asset.joints.isEmpty()) return true;
        for (int i = 0; i < asset.entities.size(); i++) {
            GameObjectAsset.GameObjectEntityData entity = asset.entities.get(i);
            if (entity.physicsBody != null || !entity.physicsShapes.isEmpty()) return true;
        }
        return false;
    }

    /** Publishes standalone Scene joints only after every hierarchy Body endpoint exists. */
    private void createJoints(World world, List<GameObjectAsset.GameObjectJointData> joints,
                              IntIntMap sourceToEntity, int rootEntityId, IntArray created) {
        if (joints.isEmpty()) return;
        IntIntMap localJointToEntity = new IntIntMap(joints.size());
        for (int i = 0; i < joints.size(); i++) {
            GameObjectAsset.GameObjectJointData source = joints.get(i);
            int jointEntityId = world.create();
            created.add(jointEntityId);
            localJointToEntity.put(source.jointLocalId, jointEntityId);
            identityRegistry.ensureStableId(jointEntityId);
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(jointEntityId);
            base.type = source.type;
            base.aEid = sourceToEntity.get(source.bodyALocalEntityId, -1);
            base.bEid = sourceToEntity.get(source.bodyBLocalEntityId, -1);
            base.collideConnected = source.collideConnected;
            base.anchorAx = source.anchorAx; base.anchorAy = source.anchorAy;
            base.anchorBx = source.anchorBx; base.anchorBy = source.anchorBy;
            applyTypedJoint(world, jointEntityId, source, rootEntityId);
        }
        for (int i = 0; i < joints.size(); i++) {
            GameObjectAsset.GameObjectJointData source = joints.get(i);
            if (source.type != PhysicsJointComponent.TYPE_GEAR) continue;
            PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class)
                    .get(localJointToEntity.get(source.jointLocalId, -1));
            gear.joint1Eid = localJointToEntity.get(source.gear.jointALocalId, -1);
            gear.joint2Eid = localJointToEntity.get(source.gear.jointBLocalId, -1);
        }
    }

    private void applyTypedJoint(World world, int entityId,
                                 GameObjectAsset.GameObjectJointData source, int rootEntityId) {
        switch (source.type) {
            case PhysicsJointComponent.TYPE_DISTANCE: {
                PhysicsDistanceJointComponent value = world.getMapper(PhysicsDistanceJointComponent.class).create(entityId);
                value.lengthM = source.distance.lengthM; value.frequencyHz = source.distance.frequencyHz;
                value.dampingRatio = source.distance.dampingRatio; break;
            }
            case PhysicsJointComponent.TYPE_REVOLUTE: {
                PhysicsRevoluteJointComponent value = world.getMapper(PhysicsRevoluteJointComponent.class).create(entityId);
                value.enableLimit = source.revolute.enableLimit; value.lowerAngleRad = source.revolute.lowerAngleRad;
                value.upperAngleRad = source.revolute.upperAngleRad; value.enableMotor = source.revolute.enableMotor;
                value.motorSpeedRad = source.revolute.motorSpeedRad; value.maxMotorTorque = source.revolute.maxMotorTorque; break;
            }
            case PhysicsJointComponent.TYPE_PRISMATIC: {
                PhysicsPrismaticJointComponent value = world.getMapper(PhysicsPrismaticJointComponent.class).create(entityId);
                value.axisX = source.prismatic.axisX; value.axisY = source.prismatic.axisY;
                value.enableLimit = source.prismatic.enableLimit; value.lowerTranslationM = source.prismatic.lowerTranslationM;
                value.upperTranslationM = source.prismatic.upperTranslationM; value.enableMotor = source.prismatic.enableMotor;
                value.motorSpeedMps = source.prismatic.motorSpeedMps; value.maxMotorForce = source.prismatic.maxMotorForce; break;
            }
            case PhysicsJointComponent.TYPE_PULLEY: {
                PhysicsPulleyJointComponent value = world.getMapper(PhysicsPulleyJointComponent.class).create(entityId);
                float[] a = rootLocalMetersToWorldMeters(world, rootEntityId,
                        source.pulley.groundAnchorALocalX, source.pulley.groundAnchorALocalY);
                float[] b = rootLocalMetersToWorldMeters(world, rootEntityId,
                        source.pulley.groundAnchorBLocalX, source.pulley.groundAnchorBLocalY);
                value.groundAx = a[0]; value.groundAy = a[1]; value.groundBx = b[0]; value.groundBy = b[1];
                value.lengthAM = source.pulley.lengthAM; value.lengthBM = source.pulley.lengthBM; value.ratio = source.pulley.ratio; break;
            }
            case PhysicsJointComponent.TYPE_GEAR: {
                PhysicsGearJointComponent value = world.getMapper(PhysicsGearJointComponent.class).create(entityId);
                value.ratio = source.gear.ratio; break;
            }
            case PhysicsJointComponent.TYPE_WHEEL: {
                PhysicsWheelJointComponent value = world.getMapper(PhysicsWheelJointComponent.class).create(entityId);
                value.frequencyHz = source.wheel.frequencyHz; value.dampingRatio = source.wheel.dampingRatio;
                value.enableMotor = source.wheel.enableMotor; value.motorSpeedRad = source.wheel.motorSpeedRad;
                value.maxMotorTorque = source.wheel.maxMotorTorque; value.axisX = source.wheel.axisX; value.axisY = source.wheel.axisY; break;
            }
            case PhysicsJointComponent.TYPE_WELD: {
                PhysicsWeldJointComponent value = world.getMapper(PhysicsWeldJointComponent.class).create(entityId);
                value.referenceAngleRad = source.weld.referenceAngleRad; value.frequencyHz = source.weld.frequencyHz;
                value.dampingRatio = source.weld.dampingRatio; break;
            }
            case PhysicsJointComponent.TYPE_FRICTION: {
                PhysicsFrictionJointComponent value = world.getMapper(PhysicsFrictionJointComponent.class).create(entityId);
                value.maxForce = source.friction.maxForce; value.maxTorque = source.friction.maxTorque; break;
            }
            case PhysicsJointComponent.TYPE_MOTOR: {
                PhysicsMotorJointComponent value = world.getMapper(PhysicsMotorJointComponent.class).create(entityId);
                value.linearOffsetX = source.motor.linearOffsetX; value.linearOffsetY = source.motor.linearOffsetY;
                value.angularOffsetRad = source.motor.angularOffsetRad; value.maxForce = source.motor.maxForce;
                value.maxTorque = source.motor.maxTorque; value.correctionFactor = source.motor.correctionFactor; break;
            }
            default: throw new IllegalArgumentException("Unsupported Game Object joint type " + source.type);
        }
    }

    private float[] rootLocalMetersToWorldMeters(World world, int rootEntityId, float localX, float localY) {
        TransformComponent root = world.getMapper(TransformComponent.class).get(rootEntityId);
        float ppm = sceneMeta != null && sceneMeta.pixelsPerMeter > 0f ? sceneMeta.pixelsPerMeter : 100f;
        float localWuX = localX * ppm;
        float localWuY = localY * ppm;
        float cos = com.badlogic.gdx.math.MathUtils.cos(root.rotationRad);
        float sin = com.badlogic.gdx.math.MathUtils.sin(root.rotationRad);
        float frameX = root.x + root.originX - cos * root.scaleX * root.originX
                + sin * root.scaleY * root.originY;
        float frameY = root.y + root.originY - sin * root.scaleX * root.originX
                - cos * root.scaleY * root.originY;
        return new float[]{(frameX + cos * root.scaleX * localWuX - sin * root.scaleY * localWuY) / ppm,
                (frameY + sin * root.scaleX * localWuX + cos * root.scaleY * localWuY) / ppm};
    }

    private IntIntMap allocateStableIds(List<GameObjectAsset.GameObjectEntityData> entities) {
        IntIntMap result = new IntIntMap(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            GameObjectAsset.GameObjectEntityData data = entities.get(i);
            result.put(data.sourceEntityId, identityRegistry.allocateStableId());
        }
        return result;
    }

    private IntMap<PreparedAssetBinding> prepareAssetBindings(
            List<GameObjectAsset.GameObjectEntityData> entities) {
        IntMap<PreparedAssetBinding> result = new IntMap<PreparedAssetBinding>();
        for (int i = 0; i < entities.size(); i++) {
            GameObjectAsset.GameObjectEntityData data = entities.get(i);
            if (data.assetRef == null) continue;
            AtlasAssetBinding binding = atlasRuntimeService.resolveBinding(
                    data.assetRef.assetId, data.assetRef.atlasTag);
            result.put(data.sourceEntityId, new PreparedAssetBinding(binding));
        }
        return result;
    }

    private IntMap<PreparedPhysics> preparePhysics(
            World world, List<GameObjectAsset.GameObjectEntityData> entities) {
        IntMap<PreparedPhysics> result = new IntMap<PreparedPhysics>();
        PhysicsService physicsService = new PhysicsService(world, null, sceneMeta);
        for (int i = 0; i < entities.size(); i++) {
            GameObjectAsset.GameObjectEntityData data = entities.get(i);
            if (data.physicsBody == null) continue;
            Array<PhysicsShapeData> shapes = new Array<PhysicsShapeData>(
                    true, data.physicsShapes.size(), PhysicsShapeData.class);
            for (int shapeIndex = 0; shapeIndex < data.physicsShapes.size(); shapeIndex++) {
                GameObjectAsset.PhysicsShapeData source = data.physicsShapes.get(shapeIndex);
                PhysicsShapeData shape = new PhysicsShapeData();
                shape.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
                shape.geometry = source.geometry != null ? source.geometry.copy() : null;
                shape.density = source.density;
                shape.friction = source.friction;
                shape.restitution = source.restitution;
                shape.sensor = source.sensor;
                shape.categoryBits = source.categoryBits;
                shape.maskBits = source.maskBits;
                shape.groupIndex = source.groupIndex;
                shape.enabled = source.enabled;
                shape.spatialFootprint = source.spatialFootprint;
                shapes.add(shape);
            }
            result.put(data.sourceEntityId, new PreparedPhysics(
                    copyBody(data.physicsBody), PhysicsService.prepareBodyCandidate(shapes)));
        }
        return result;
    }

    private static List<GameObjectAsset.GameObjectEntityData> topologicalOrder(GameObjectAsset asset) {
        java.util.ArrayList<GameObjectAsset.GameObjectEntityData> ordered =
                new java.util.ArrayList<GameObjectAsset.GameObjectEntityData>(asset.entities.size());
        IntMap<GameObjectAsset.GameObjectEntityData> byId =
                new IntMap<GameObjectAsset.GameObjectEntityData>(asset.entities.size());
        for (int i = 0; i < asset.entities.size(); i++) {
            GameObjectAsset.GameObjectEntityData data = asset.entities.get(i);
            byId.put(data.sourceEntityId, data);
        }
        appendChildren(asset.rootSourceEntityId, byId, asset.entities, ordered);
        return ordered;
    }

    private static void appendChildren(
            int sourceId, IntMap<GameObjectAsset.GameObjectEntityData> byId,
            List<GameObjectAsset.GameObjectEntityData> all,
            List<GameObjectAsset.GameObjectEntityData> ordered) {
        ordered.add(byId.get(sourceId));
        for (int i = 0; i < all.size(); i++) {
            GameObjectAsset.GameObjectEntityData candidate = all.get(i);
            if (candidate.parentSourceEntityId == sourceId) {
                appendChildren(candidate.sourceEntityId, byId, all, ordered);
            }
        }
    }

    private static void apply(
            World world, int entityId, GameObjectAsset.GameObjectEntityData data,
            int rootSourceId, String sourceAssetId, IntIntMap sourceToStable,
            PreparedAssetBinding preparedBinding, PreparedPhysics preparedPhysics,
            float rootOffsetX, float rootOffsetY) {
        if (data.transform != null) {
            TransformComponent value = world.getMapper(TransformComponent.class).create(entityId);
            value.x = data.transform.x + (data.sourceEntityId == rootSourceId ? rootOffsetX : 0f);
            value.y = data.transform.y + (data.sourceEntityId == rootSourceId ? rootOffsetY : 0f);
            value.rotationRad = data.transform.rotationRad;
            value.scaleX = data.transform.scaleX;
            value.scaleY = data.transform.scaleY;
            value.originX = data.transform.originX;
            value.originY = data.transform.originY;
            value.refreshCaches();
        }
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entityId);
        index.layerIndex = 0;
        index.zIndex = data.entityIndex != null ? data.entityIndex.zIndex : 0;
        if (data.sourceEntityId == rootSourceId) {
            world.getMapper(LayerComponent.class).create(entityId).layerIndex = 0;
        }
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entityId);
        identity.stableId = sourceToStable.get(data.sourceEntityId, -1);
        identity.name = data.identity != null && data.identity.name != null ? data.identity.name : "";
        if (data.gameObject != null) {
            GameObjectComponent gameObject = world.getMapper(GameObjectComponent.class).create(entityId);
            gameObject.sourceAssetId = data.sourceEntityId == rootSourceId && sourceAssetId != null
                    ? sourceAssetId : "";
        }
        if (data.parentSourceEntityId != -1) {
            world.getMapper(GameObjectMemberComponent.class).create(entityId).parentStableId =
                    sourceToStable.get(data.parentSourceEntityId, -1);
        }
        if (data.tags != null) {
            PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).create(entityId);
            for (int i = 0; i < data.tags.values.size(); i++) tags.tags.add(data.tags.values.get(i));
        }
        if (data.customProperties != null) {
            world.getMapper(CustomPropertiesComponent.class).create(entityId).properties =
                    remapProperties(data.customProperties, sourceToStable);
        }
        if (data.visibility != null) world.getMapper(VisibilityComponent.class).create(entityId).visible = data.visibility.visible;
        if (data.boundsFlags != null) {
            if (data.boundsFlags.hasAabb) world.getMapper(AABBComponent.class).create(entityId);
            if (data.boundsFlags.hasObb) world.getMapper(OrientedBoundsComponent.class).create(entityId);
        }
        if (data.dimensions != null) {
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entityId);
            dimensions.width = data.dimensions.width; dimensions.height = data.dimensions.height;
        }
        if (data.quadDeform != null) {
            QuadDeformComponent quad = world.getMapper(QuadDeformComponent.class).create(entityId);
            quad.blX = data.quadDeform.blX; quad.blY = data.quadDeform.blY;
            quad.brX = data.quadDeform.brX; quad.brY = data.quadDeform.brY;
            quad.trX = data.quadDeform.trX; quad.trY = data.quadDeform.trY;
            quad.tlX = data.quadDeform.tlX; quad.tlY = data.quadDeform.tlY;
        }
        RenderMaterialComponent material = null;
        if (data.renderMaterial != null || data.assetRef != null) {
            material = world.getMapper(RenderMaterialComponent.class).create(entityId);
            if (data.renderMaterial != null) {
                material.shaderIdx = data.renderMaterial.shaderIdx;
                material.blendModeId = data.renderMaterial.blendModeId;
            }
        }
        if (data.assetRef != null) {
            AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
            assetRef.assetId = data.assetRef.assetId; assetRef.atlasTag = data.assetRef.atlasTag;
            publishBinding(world, entityId, material, preparedBinding);
        }
        if (data.tint != null) world.getMapper(TintComponent.class).create(entityId).rgba = data.tint.rgba;
        if (data.animation != null) {
            AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
            animation.animationAssetIds.addAll(data.animation.animationAssetIds);
            animation.fps = data.animation.fps; animation.playing = data.animation.playing;
            animation.loop = data.animation.loop; animation.stateTime = data.animation.stateTime;
            animation.frame = data.animation.frame;
            animation.currentClip = data.animation.currentClip != null ? data.animation.currentClip : "";
        }
        if (data.shaderParams != null) {
            ShaderParamsComponent params = world.getMapper(ShaderParamsComponent.class).create(entityId);
            for (Map.Entry<String, Float> entry : data.shaderParams.floats.entrySet()) {
                params.floats.add(new ShaderFloatParam(entry.getKey(), entry.getValue()));
            }
        }
        if (data.repeat != null) {
            RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entityId);
            repeat.repeatX = data.repeat.repeatX; repeat.repeatY = data.repeat.repeatY;
        }
        if (data.pointLight != null) {
            PointLightComponent light = world.getMapper(PointLightComponent.class).create(entityId);
            light.r = data.pointLight.r; light.g = data.pointLight.g; light.b = data.pointLight.b;
            light.intensity = data.pointLight.intensity; light.radius = data.pointLight.radius;
            light.falloff = data.pointLight.falloff; light.enabled = data.pointLight.enabled;
        }
        if (data.coneLight != null) {
            ConeLightComponent light = world.getMapper(ConeLightComponent.class).create(entityId);
            light.r = data.coneLight.r; light.g = data.coneLight.g; light.b = data.coneLight.b;
            light.intensity = data.coneLight.intensity; light.radius = data.coneLight.radius;
            light.coneAngleDeg = data.coneLight.coneAngleDeg; light.rotationDeg = data.coneLight.rotationDeg;
            light.softness = data.coneLight.softness; light.falloff = data.coneLight.falloff;
            light.enabled = data.coneLight.enabled;
        }
        if (data.spatialHeight != null) {
            SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(entityId);
            height.altitude = data.spatialHeight.altitude;
            height.height = data.spatialHeight.height;
        }
        if (preparedPhysics != null) {
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
            body.type = preparedPhysics.body.type;
            body.fixedRotation = preparedPhysics.body.fixedRotation;
            body.bullet = preparedPhysics.body.bullet;
            body.allowSleep = preparedPhysics.body.allowSleep;
            body.awake = preparedPhysics.body.awake;
            body.gravityScale = preparedPhysics.body.gravityScale;
            body.linearDamping = preparedPhysics.body.linearDamping;
            body.angularDamping = preparedPhysics.body.angularDamping;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entityId);
            PhysicsCompiledFixturesComponent compiled =
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
            PhysicsService.publishPreparedCandidate(shapes, compiled, preparedPhysics.candidate);
        }
    }

    private static void publishBinding(World world, int entityId,
                                       RenderMaterialComponent material,
                                       PreparedAssetBinding prepared) {
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).create(entityId);
        if (prepared == null || prepared.binding == null) {
            region.valid = false; material.textureHandle = 0; return;
        }
        AtlasRegionMetadata metadata = prepared.binding.metadata();
        region.u1 = metadata.u1(); region.v1 = metadata.v1();
        region.u2 = metadata.u2(); region.v2 = metadata.v2();
        region.pixW = metadata.pixelWidth(); region.pixH = metadata.pixelHeight();
        region.valid = true; material.textureHandle = metadata.textureHandle();
    }

    private static PropertySet remapProperties(PropertySet source, IntIntMap sourceToStable) {
        PropertySet result = new PropertySet(source.size());
        Array<String> names = new Array<String>();
        source.copyNamesTo(names);
        for (int i = 0; i < names.size; i++) {
            String name = names.get(i);
            PropertyValue value = source.valueCopy(name);
            if (value.type() == PropertyType.OBJECT) {
                int sourceId = value.asObjectStableId();
                result.putObjectStableId(name, sourceId == -1 ? -1 : sourceToStable.get(sourceId, -1));
            } else if (value.type() == PropertyType.CLASS) {
                result.putClass(name, value.className(), remapProperties(value.classPropertiesCopy(), sourceToStable));
            } else result.put(name, value);
        }
        return result;
    }

    private static void rollback(World world, IntArray created) {
        for (int i = created.size - 1; i >= 0; i--) {
            int entityId = created.get(i);
            IdentityRegistry.unindexEntityImmediately(world, entityId);
            if (world.getEntityManager().isActive(entityId)) world.delete(entityId);
        }
    }

    private static void markCreatedDirty(World world, IntArray created) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;
        for (int i = 0; i < created.size; i++) {
            dirty.mark(created.get(i), DirtyBits.GEOMETRY | DirtyBits.MATERIAL
                    | DirtyBits.COLOR | DirtyBits.ORDER | DirtyBits.LAYER);
            if (world.getMapper(PhysicsBodyComponent.class).has(created.get(i))) {
                dirty.physics(created.get(i), games.pixscape.runtime.render.PhysicsDirtyBits.ALL);
            }
            if (world.getMapper(PhysicsJointComponent.class).has(created.get(i))) {
                dirty.joint(created.get(i), JointDirtyBits.ALL);
            }
        }
    }

    private static com.artemis.utils.IntBag toBag(IntArray values) {
        com.artemis.utils.IntBag bag = new com.artemis.utils.IntBag(values.size);
        for (int i = 0; i < values.size; i++) bag.add(values.get(i));
        return bag;
    }

    private static final class PreparedAssetBinding {
        final AtlasAssetBinding binding;
        PreparedAssetBinding(AtlasAssetBinding binding) { this.binding = binding; }
    }

    private static PhysicsBodyComponent copyBody(GameObjectAsset.PhysicsBodyData source) {
        PhysicsBodyComponent result = new PhysicsBodyComponent();
        result.type = source.type;
        result.fixedRotation = source.fixedRotation;
        result.bullet = source.bullet;
        result.allowSleep = source.allowSleep;
        result.awake = source.awake;
        result.gravityScale = source.gravityScale;
        result.linearDamping = source.linearDamping;
        result.angularDamping = source.angularDamping;
        return result;
    }

    private static final class PreparedPhysics {
        final PhysicsBodyComponent body;
        final PreparedPhysicsBodyCandidate candidate;

        PreparedPhysics(PhysicsBodyComponent body,
                        PreparedPhysicsBodyCandidate candidate) {
            this.body = body;
            this.candidate = candidate;
        }
    }
}
