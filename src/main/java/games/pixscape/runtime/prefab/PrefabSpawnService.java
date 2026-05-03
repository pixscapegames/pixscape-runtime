package games.pixscape.runtime.prefab;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;

public final class PrefabSpawnService {
    private static final String LOG_TAG = "PrefabSpawnService";
    private static final boolean DEBUG = Boolean.getBoolean("pixscape.debug.prefabSpawn");
    private final World world;
    private final PrefabLoader loader;
    private final FileHandle runtimeProjectDir;
    private final RuntimeConfig config;
    private final IdentityRegistry identityRegistry;
    private final AtlasRuntimeService atlasRuntimeService;

    public PrefabSpawnService(World world,
                              PrefabLoader loader,
                              FileHandle runtimeProjectDir,
                              RuntimeConfig config,
                              IdentityRegistry identityRegistry,
                              AtlasRuntimeService atlasRuntimeService) {
        this.world = world;
        this.loader = loader;
        this.runtimeProjectDir = runtimeProjectDir;
        this.config = config;
        this.identityRegistry = identityRegistry;
        this.atlasRuntimeService = atlasRuntimeService;
    }

    public PrefabInstance spawn(String prefabName, float x, float y) {
        String fileName = RuntimeFs.withExt(RuntimeFs.filenameOnly(prefabName), RuntimeFs.EXT_PREFAB);
        FileHandle file = config.prefabsRoot(runtimeProjectDir).child(fileName);
        return spawn(file, x, y);
    }

    public PrefabInstance spawn(FileHandle prefabFile, float x, float y) {
        debug("Loading prefab file: " + (prefabFile != null ? prefabFile.path() : "<null>"));
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
        debug("Spawning prefab entities: count=" + asset.entities.size());

        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            int eid = world.create();
            instance.addMapping(src.sourceEntityId, eid);
            copyEntityComponents(src, eid, dx, dy, overrideLayerIndex);
            markSpawnDirty(eid);
            debugVisualEntity(src, eid);
        }

        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            int eid = instance.getEntityForLocalId(src.sourceEntityId);
            remapJointRefs(src, eid, instance);
        }

        refreshRuntimeSubscriptions();
        markSpawnedVisualsDirtyAfterRefresh(asset, instance);
        debugFinalVisualSync(asset, instance);
        debugCompareWithSceneSprite(asset, instance);

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
            rebindVisualRegion(eid, c.assetId, c.atlasTag);
        }
        ensureVisualOrientedBounds(src, eid);
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

    private void rebindVisualRegion(int eid, int assetId, String atlasTag) {
        TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class).has(eid)
                ? world.getMapper(TextureRegionComponent.class).get(eid)
                : world.getMapper(TextureRegionComponent.class).create(eid);
        RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class).has(eid)
                ? world.getMapper(RenderMaterialComponent.class).get(eid)
                : world.getMapper(RenderMaterialComponent.class).create(eid);

        AtlasRuntimeService.CachedRegion cached = atlasRuntimeService != null
                ? atlasRuntimeService.resolveCached(assetId, atlasTag)
                : null;
        boolean resolved = cached != null;
        if (!resolved) {
            tr.valid = false;
            mat.textureHandle = 0;
            warn("Missing cached atlas region for assetId=" + assetId + " atlasTag=" + atlasTag);
            debug("resolveCached assetId=" + assetId + " atlasTag=" + atlasTag + " resolved=false textureHandle=0");
            return;
        }

        tr.u1 = cached.u1;
        tr.v1 = cached.v1;
        tr.u2 = cached.u2;
        tr.v2 = cached.v2;
        tr.pixW = cached.pixW;
        tr.pixH = cached.pixH;
        tr.valid = true;
        mat.textureHandle = cached.textureHandle;
        mat.debugAtlasTag = atlasTag;
        debug("resolveCached assetId=" + assetId + " atlasTag=" + atlasTag
                + " resolved=true textureHandle=" + mat.textureHandle);
    }

    private void refreshRuntimeSubscriptions() {
        // Artemis only updates aspect subscriptions/listeners during world.process().
        // We run one zero-delta process pass to flush entity/component insertion into runtime subscriptions.
        float previousDelta = world.getDelta();
        world.setDelta(0f);
        try {
            world.process();
        } finally {
            world.setDelta(previousDelta);
        }
    }

    private void markSpawnDirty(int eid) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;
        dirty.geometry(eid, GeometryDirty.ALL);
        dirty.material(eid);
        dirty.color(eid);
        dirty.order(eid);
        dirty.layer(eid);
    }

    private void markSpawnedVisualsDirtyAfterRefresh(PrefabAsset asset, PrefabInstance instance) {
        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            if (!isVisualSpriteData(src)) continue;
            int eid = instance.getEntityForLocalId(src.sourceEntityId);
            markSpawnDirty(eid);
        }
    }

    private void ensureVisualOrientedBounds(PrefabAsset.PrefabEntityData src, int eid) {
        boolean hasVisualSpriteData = isVisualSpriteData(src);
        boolean shouldHaveBounds = hasVisualSpriteData || src.boundsFlags != null;
        if (!shouldHaveBounds) return;

        var boundsMapper = world.getMapper(OrientedBoundsComponent.class);
        if (!boundsMapper.has(eid)) {
            boundsMapper.create(eid);
        }
    }

    private int materialTextureHandle(int eid) {
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).getSafe(eid, null);
        return material != null ? material.textureHandle : -1;
    }

    private void debugVisualEntity(PrefabAsset.PrefabEntityData src, int eid) {
        boolean hasVisualSpriteData = isVisualSpriteData(src);
        if (!hasVisualSpriteData) return;

        String localId = String.valueOf(src.sourceEntityId);
        boolean hasOrientedBounds = world.getMapper(OrientedBoundsComponent.class).has(eid);
        boolean hasTransform = world.getMapper(TransformComponent.class).has(eid);
        boolean hasDimensions = world.getMapper(DimensionsComponent.class).has(eid);
        boolean hasTextureRegion = world.getMapper(TextureRegionComponent.class).has(eid);
        boolean hasRenderMaterial = world.getMapper(RenderMaterialComponent.class).has(eid);
        boolean hasVisibility = world.getMapper(VisibilityComponent.class).has(eid);
        int textureHandle = materialTextureHandle(eid);
        boolean textureValid = textureHandle != 0;

        debug("Visual entity mapped localId=" + localId
                + " -> eid=" + eid
                + " hasOrientedBounds=" + hasOrientedBounds
                + " hasTransform=" + hasTransform
                + " hasDimensions=" + hasDimensions
                + " hasTextureRegion=" + hasTextureRegion
                + " hasRenderMaterial=" + hasRenderMaterial
                + " hasVisibility=" + hasVisibility
                + " textureHandle=" + textureHandle
                + " textureValid=" + textureValid);
    }


    private boolean isVisualSpriteData(PrefabAsset.PrefabEntityData src) {
        return src.dimensions != null
                && (src.textureRegion != null || src.assetRef != null)
                && src.renderMaterial != null
                && src.visibility != null;
    }

    private void debugFinalVisualSync(PrefabAsset asset, PrefabInstance instance) {
        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            if (!isVisualSpriteData(src)) continue;
            int eid = instance.getEntityForLocalId(src.sourceEntityId);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).getSafe(eid, null);
            boolean hasRender = world.getMapper(OrientedBoundsComponent.class).has(eid)
                    && world.getMapper(RenderMaterialComponent.class).has(eid)
                    && world.getMapper(VisibilityComponent.class).has(eid)
                    && (world.getMapper(TextureRegionComponent.class).has(eid)
                    || world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).has(eid)
                    || world.getMapper(games.pixscape.runtime.component.light.ConeLightComponent.class).has(eid));
            debug("Post-sync visual eid=" + eid
                    + " layerIndex=" + (index != null ? index.layerIndex : Integer.MIN_VALUE)
                    + " hasRenderComponents=" + hasRender
                    + " markedDirtyAfterFlush=true");
        }
    }

    private void debugCompareWithSceneSprite(PrefabAsset asset, PrefabInstance instance) {
        if (!DEBUG) return;
        RenderSubmitSystem submit = world.getSystem(RenderSubmitSystem.class);
        if (submit == null) return;

        int prefabEid = -1;
        for (PrefabAsset.PrefabEntityData src : asset.entities) {
            if (isVisualSpriteData(src)) {
                prefabEid = instance.getEntityForLocalId(src.sourceEntityId);
                break;
            }
        }
        if (prefabEid < 0) return;

        int sceneEid = -1;
        int max = submit.getState().maxEntityId();
        for (int e = 0; e <= max; e++) {
            if (!world.getEntityManager().isActive(e) || e == prefabEid) continue;
            if (!world.getMapper(VisibilityComponent.class).has(e)) continue;
            if (!world.getMapper(OrientedBoundsComponent.class).has(e)) continue;
            if (!world.getMapper(RenderMaterialComponent.class).has(e)) continue;
            if (!world.getMapper(TextureRegionComponent.class).has(e)) continue;
            if (!world.getMapper(EntityIndexComponent.class).has(e)) continue;
            if (submit.getState().enabled[e] && submit.getState().visible[e]) { sceneEid = e; break; }
        }

        logEntityRenderSnapshot("scene", sceneEid, submit.getState());
        logEntityRenderSnapshot("prefab", prefabEid, submit.getState());
    }

    private void logEntityRenderSnapshot(String label, int eid, games.pixscape.runtime.render.RenderStateSOA state) {
        if (eid < 0) {
            debug("compare " + label + " entity not found");
            return;
        }
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).getSafe(eid, null);
        TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class).getSafe(eid, null);
        RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class).getSafe(eid, null);
        VisibilityComponent vis = world.getMapper(VisibilityComponent.class).getSafe(eid, null);
        debug("compare " + label + " eid=" + eid
                + " active=" + world.getEntityManager().isActive(eid)
                + " layer=" + (index != null ? index.layerIndex : Integer.MIN_VALUE)
                + " z=" + (index != null ? index.zIndex : Integer.MIN_VALUE)
                + " trValid=" + (tr != null && tr.valid)
                + " texHandle=" + (mat != null ? mat.textureHandle : -1)
                + " vis=" + (vis != null && vis.visible)
                + " inView=" + (vis != null && vis.inView)
                + " culled=" + (vis != null && vis.culledByFrustum)
                + " soaEnabled=" + (eid < state.enabled.length && state.enabled[eid])
                + " soaVisible=" + (eid < state.visible.length && state.visible[eid])
                + " soaKind=" + (eid < state.kind.length ? state.kind[eid] : -1));
    }

    private static void debug(String message) {
        if (!DEBUG || Gdx.app == null) return;
        Gdx.app.log(LOG_TAG, message);
    }

    private static void warn(String message) {
        if (Gdx.app != null) {
            Gdx.app.error(LOG_TAG, message);
        }
    }
}
