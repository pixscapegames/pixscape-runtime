package games.pixscape.runtime.api;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.animation.AnimationClipDef;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.gameobject.GameObjectRuntimeFragment;
import games.pixscape.runtime.particle.ParticleEffectPath;
import games.pixscape.runtime.gameobject.SpawnResult;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.PhysicsPoseAuthority;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API. Applications obtain the supported facade through
 * {@link PixscapeEngine#api()}.
 */
public final class PixscapeApiImpl implements PixscapeAPI {

    private static final class EntityReferenceTracker
            implements EntitySubscription.SubscriptionListener {
        private static final int INITIAL_CAPACITY = 64;

        private final PixscapeEngine engine;
        private World world;
        private EntitySubscription subscription;
        private int worldGeneration;
        private int[] entityGenerations = new int[INITIAL_CAPACITY];

        EntityReferenceTracker(PixscapeEngine engine) {
            this.engine = engine;
        }

        EntityHandle capture(int entityId) {
            bindCurrentWorld();
            int entityGeneration = 0;
            if (isActive(world, entityId)) {
                ensureCapacity(entityId);
                entityGeneration = entityGenerations[entityId];
                if (entityGeneration == 0) {
                    entityGeneration = 1;
                    entityGenerations[entityId] = entityGeneration;
                }
            }
            return new EntityHandle(
                    this, entityId, worldGeneration, entityGeneration);
        }

        World world(EntityHandle handle) {
            bindCurrentWorld();
            if (handle.worldGeneration != worldGeneration
                    || handle.entityGeneration == 0
                    || !isActive(world, handle.entityId)
                    || handle.entityId >= entityGenerations.length
                    || entityGenerations[handle.entityId] != handle.entityGeneration) {
                return null;
            }
            return world;
        }

        @Override
        public void inserted(IntBag entities) {
            int[] ids = entities.getData();
            for (int i = 0, n = entities.size(); i < n; i++) {
                int entityId = ids[i];
                ensureCapacity(entityId);
                if (entityGenerations[entityId] == 0) {
                    entityGenerations[entityId] = 1;
                }
            }
        }

        @Override
        public void removed(IntBag entities) {
            int[] ids = entities.getData();
            for (int i = 0, n = entities.size(); i < n; i++) {
                int entityId = ids[i];
                ensureCapacity(entityId);
                int next = entityGenerations[entityId] + 1;
                entityGenerations[entityId] = next != 0 ? next : 1;
            }
        }

        private void bindCurrentWorld() {
            World current = engine.getWorld();
            if (current == world) return;

            if (subscription != null) {
                subscription.removeSubscriptionListener(this);
                subscription = null;
            }
            world = current;
            worldGeneration++;
            if (worldGeneration == 0) worldGeneration = 1;
            entityGenerations = new int[INITIAL_CAPACITY];
            if (world != null) {
                subscription = world.getAspectSubscriptionManager().get(Aspect.all());
                subscription.addSubscriptionListener(this);
            }
        }

        private void ensureCapacity(int entityId) {
            if (entityId < entityGenerations.length) return;
            int capacity = entityGenerations.length;
            while (capacity <= entityId) capacity <<= 1;
            int[] grown = new int[capacity];
            System.arraycopy(entityGenerations, 0, grown, 0, entityGenerations.length);
            entityGenerations = grown;
        }

        private static boolean isActive(World world, int entityId) {
            if (world == null || entityId < 0) return false;
            try {
                return world.getEntity(entityId) != null
                        && world.getEntityManager().isActive(entityId);
            } catch (IndexOutOfBoundsException ignored) {
                return false;
            }
        }
    }

    private static final class EntityHandle {
        private final EntityReferenceTracker tracker;
        private final int entityId;
        private final int worldGeneration;
        private final int entityGeneration;

        EntityHandle(EntityReferenceTracker tracker,
                     int entityId,
                     int worldGeneration,
                     int entityGeneration) {
            this.tracker = tracker;
            this.entityId = entityId;
            this.worldGeneration = worldGeneration;
            this.entityGeneration = entityGeneration;
        }

        World world() {
            return tracker.world(this);
        }

        boolean exists() {
            return world() != null;
        }
    }

    private static World spriteCapabilityWorld(EntityHandle handle) {
        World world = handle.world();
        if (world == null) return null;
        int entityId = handle.entityId;
        return world.getMapper(TransformComponent.class).has(entityId)
                && world.getMapper(DimensionsComponent.class).has(entityId)
                && world.getMapper(AssetRefComponent.class).has(entityId)
                && world.getMapper(VisibilityComponent.class).has(entityId)
                && world.getMapper(EntityIndexComponent.class).has(entityId)
                && world.getMapper(TintComponent.class).has(entityId)
                ? world : null;
    }

    private static World animationCapabilityWorld(EntityHandle handle) {
        World world = spriteCapabilityWorld(handle);
        if (world == null) return null;
        int entityId = handle.entityId;
        return world.getMapper(AnimationComponent.class).has(entityId)
                && world.getMapper(TextureRegionComponent.class).has(entityId)
                && world.getMapper(RenderMaterialComponent.class).has(entityId)
                ? world : null;
    }

    private static World quadDeformCapabilityWorld(EntityHandle handle) {
        World world = spriteCapabilityWorld(handle);
        if (world == null) return null;
        int entityId = handle.entityId;
        return world.getMapper(OrientedBoundsComponent.class).has(entityId)
                && world.getMapper(AABBComponent.class).has(entityId)
                && world.getMapper(TextureRegionComponent.class).has(entityId)
                && world.getMapper(RenderMaterialComponent.class).has(entityId)
                ? world : null;
    }

    private static boolean hasQuadDeformation(QuadDeformComponent component) {
        return component != null
                && (component.blX != 0f || component.blY != 0f
                || component.brX != 0f || component.brY != 0f
                || component.trX != 0f || component.trY != 0f
                || component.tlX != 0f || component.tlY != 0f);
    }

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static void requireFinite(String operation, float value) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(
                    operation + " requires a finite value, got " + value + ".");
        }
    }

    private static void requireFinite(String operation, float first, float second) {
        if (!isFinite(first) || !isFinite(second)) {
            throw new IllegalArgumentException(
                    operation + " requires finite values, got " + first + ", " + second + ".");
        }
    }

    private final PixscapeEngine engine;
    private final EntityReferenceTracker entityReferences;
    private final SceneLayerResolver sceneLayers;
    private final ECSAPI ecs;
    private final EntitiesAPI entities;
    private final TiledAPI tiled;
    private final SpatialAPI spatial;
    private final GameObjectsAPI gameObjects;
    private final AssetsApiImpl assets;
    private final SpritesApiImpl sprites;
    private final AnimationsAPI animations;
    private final ParticlesAPI particles;
    private final PhysicsAPI physics;

    public PixscapeApiImpl(PixscapeEngine engine) {
        this.engine = engine;
        this.entityReferences = new EntityReferenceTracker(engine);
        this.sceneLayers = new SceneLayerResolver();
        this.ecs = new EcsApiImpl(engine);
        this.entities = new EntitiesApiImpl(
                engine, ecs, sceneLayers, entityReferences);
        this.tiled = new TiledApiImpl(engine, ecs, entities, entityReferences);
        this.spatial = new SpatialApiImpl(engine, sceneLayers);
        this.assets = new AssetsApiImpl(engine);
        this.sprites = new SpritesApiImpl(engine, entities, assets);
        this.animations = new AnimationsApiImpl(engine, entities, assets, sprites);
        this.particles = new ParticlesApiImpl(engine, entities);
        this.physics = new PhysicsApiImpl(engine);
        this.gameObjects = new GameObjectsApiImpl(engine, entities);
    }

    @Override
    public EntitiesAPI entities() {
        return entities;
    }

    @Override
    public TiledAPI tiled() {
        return tiled;
    }

    @Override
    public SpatialAPI spatial() {
        return spatial;
    }

    @Override
    public ECSAPI ecs() {
        return ecs;
    }

    @Override
    public AssetsAPI assets() {
        return assets;
    }

    @Override
    public SpritesAPI sprites() {
        return sprites;
    }

    @Override
    public AnimationsAPI animations() {
        return animations;
    }

    @Override
    public ParticlesAPI particles() {
        return particles;
    }

    @Override
    public PhysicsAPI physics() {
        return physics;
    }

    @Override
    public GameObjectsAPI gameObjects() {
        return gameObjects;
    }

    static final class PhysicsApiImpl implements PhysicsAPI {
        private static final float DEFAULT_PIXELS_PER_METER = 100f;
        private final PixscapeEngine engine;

        PhysicsApiImpl(PixscapeEngine engine) {
            this.engine = engine;
        }

        @Override
        public boolean isRunning() {
            World ecsWorld = engine.getWorld();
            SceneMetaRuntime meta = engine.getActiveSceneMeta();
            Box2dWorldService service = engine.getBox2dWorldService();
            Box2dSyncSystem sync = engine.getBox2dSyncSystem();
            return ecsWorld != null
                    && meta != null
                    && meta.physicsEnabled
                    && service != null
                    && !service.isDisposed()
                    && service.world != null
                    && sync != null
                    && sync.isEnabled()
                    && sync.isStepEnabled()
                    && sync.getBox2d() == service;
        }

        @Override
        public float pixelsPerMeter() {
            SceneMetaRuntime meta = engine.getActiveSceneMeta();
            Box2dWorldService service = engine.getBox2dWorldService();
            Box2dSyncSystem sync = engine.getBox2dSyncSystem();
            if (meta != null && meta.physicsEnabled
                    && service != null && !service.isDisposed() && service.world != null
                    && sync != null && sync.getBox2d() == service
                    && isValidScale(service.ppm)) {
                return service.ppm;
            }
            if (meta != null && isValidScale(meta.pixelsPerMeter)) {
                return meta.pixelsPerMeter;
            }
            return DEFAULT_PIXELS_PER_METER;
        }

        @Override
        public float parallaxX() {
            SceneMetaRuntime meta = engine.getActiveSceneMeta();
            return meta == null || Float.isNaN(meta.physicsParallaxX)
                    ? 1f : meta.physicsParallaxX;
        }

        @Override
        public float parallaxY() {
            SceneMetaRuntime meta = engine.getActiveSceneMeta();
            return meta == null || Float.isNaN(meta.physicsParallaxY)
                    ? 1f : meta.physicsParallaxY;
        }

        @Override
        public Vector2 removeParallax(Vector2 renderedWorldPosition,
                                      OrthographicCamera camera,
                                      Vector2 out) {
            if (renderedWorldPosition == null) {
                throw new IllegalArgumentException("renderedWorldPosition must not be null");
            }
            if (camera == null) {
                throw new IllegalArgumentException("camera must not be null");
            }
            if (out == null) {
                throw new IllegalArgumentException("out must not be null");
            }
            float renderedX = renderedWorldPosition.x;
            float renderedY = renderedWorldPosition.y;
            return out.set(
                    renderedX - (1f - parallaxX()) * camera.position.x,
                    renderedY - (1f - parallaxY()) * camera.position.y);
        }

        @Override
        public com.badlogic.gdx.physics.box2d.World box2dWorld() {
            Box2dWorldService service = engine.getBox2dWorldService();
            if (service == null || service.isDisposed()) return null;
            return service.world;
        }

        @Override
        public Body body(EntityRef entity) {
            if (entity == null || !entity.exists()) return null;
            World world = engine.getWorld();
            int entityId = entity.entityId();
            if (world == null || !isActive(world, entityId)) {
                return null;
            }
            PhysicsRuntimeBodyComponent runtimeBody = world
                    .getMapper(PhysicsRuntimeBodyComponent.class)
                    .getSafe(entityId, null);
            Body body = runtimeBody != null ? runtimeBody.body : null;
            com.badlogic.gdx.physics.box2d.World nativeWorld = box2dWorld();
            return body != null && nativeWorld != null && body.getWorld() == nativeWorld
                    ? body : null;
        }

        private static boolean isValidScale(float value) {
            return value > 0f && !Float.isNaN(value) && !Float.isInfinite(value);
        }

        private static boolean isActive(World world, int entityId) {
            if (entityId < 0) return false;
            try {
                return world.getEntityManager().isActive(entityId);
            } catch (IndexOutOfBoundsException ignored) {
                return false;
            }
        }
    }

    private static String currentAtlasTag(PixscapeEngine engine) {
        String tag = engine != null ? engine.getCurrentSceneAtlasTag() : null;
        return isBlank(tag) ? "main" : tag;
    }

    private static int assetIdFromRegionName(String regionName) {
        if (regionName == null) return -1;
        int marker = regionName.lastIndexOf("__a");
        if (marker < 0 || marker + 3 >= regionName.length()) return -1;
        int value = 0;
        for (int i = marker + 3; i < regionName.length(); i++) {
            char c = regionName.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static String normalizedName(String regionName) {
        if (regionName == null) return "";
        int marker = regionName.lastIndexOf("__a");
        String base = marker >= 0 ? regionName.substring(0, marker) : regionName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < base.length()) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base;
    }

    private static String normalizeLookupName(String name) {
        if (name == null) return null;
        String normalized = name.trim();
        if (normalized.isEmpty()) return null;
        normalized = normalized.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            normalized = normalized.substring(slash + 1);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        return normalized;
    }

    private static boolean matchesLookupName(String regionName, String lookupName) {
        return regionName != null && lookupName != null && regionName.equalsIgnoreCase(lookupName);
    }

    private static int createSpriteEntity(PixscapeEngine engine,
                                          AtlasAssetBinding binding,
                                          String atlasTag,
                                          float x,
                                          float y) {
        World world = requireWorld(engine);
        int e = world.create();

        TransformComponent transform = world.edit(e).create(TransformComponent.class);
        transform.x = x;
        transform.y = y;

        DimensionsComponent dimensions = world.edit(e).create(DimensionsComponent.class);
        dimensions.width = binding.metadata().pixelWidth();
        dimensions.height = binding.metadata().pixelHeight();

        world.edit(e).create(OrientedBoundsComponent.class);
        world.edit(e).create(AABBComponent.class);
        VisibilityComponent visibility = world.edit(e).create(VisibilityComponent.class);
        visibility.visible = true;
        visibility.culledByFrustum = false;
        visibility.inView = true;
        world.edit(e).create(EntityIndexComponent.class);
        world.edit(e).create(LayerComponent.class);
        world.edit(e).create(TintComponent.class);

        AssetRefComponent assetRef = world.edit(e).create(AssetRefComponent.class);
        assetRef.assetId = binding.assetId();
        assetRef.atlasTag = atlasTag;

        TextureRegionComponent textureRegion = world.edit(e).create(TextureRegionComponent.class);
        RenderMaterialComponent material = world.edit(e).create(RenderMaterialComponent.class);

        applySpriteBinding(binding, atlasTag, textureRegion, material);
        markSpawnDirty(world, e);
        return e;
    }

    private static void configureAnimationFromDef(PixscapeEngine engine, int entityId, AnimationDef def) {
        if (def == null) {
            throw new IllegalArgumentException("def must not be null");
        }

        World world = requireWorld(engine);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).has(entityId)
                ? world.getMapper(AnimationComponent.class).get(entityId)
                : world.getMapper(AnimationComponent.class).create(entityId);
        world.getMapper(AssetRefComponent.class).get(entityId).assetId = def.assetId();

        animation.animationAssetIds.clear();
        animation.animationAssetIds.add(def.assetId());
        animation.currentClip = def.currentClip();
        animation.fps = def.fps();
        animation.playing = true;
        animation.loop = true;
        animation.frame = -1;
        animation.stateTime = 0f;
        markSpawnDirty(world, entityId);
    }

    private static int createParticleEntity(PixscapeEngine engine, String effectPathOrName, float x, float y, boolean looping) {
        if (effectPathOrName == null || effectPathOrName.trim().isEmpty()) {
            throw new IllegalArgumentException("Particle effect path/name must not be blank.");
        }

        World world = requireWorld(engine);
        String effectPath = normalizeEffectPath(effectPathOrName);
        String atlasTag = currentAtlasTag(engine);
        requirePreparedParticle(world, effectPath, atlasTag);
        int e = world.create();

        TransformComponent transform = world.edit(e).create(TransformComponent.class);
        transform.x = x;
        transform.y = y;
        world.edit(e).create(LayerComponent.class);
        world.edit(e).create(EntityIndexComponent.class);
        VisibilityComponent visibility = world.edit(e).create(VisibilityComponent.class);
        visibility.visible = true;
        visibility.culledByFrustum = false;
        visibility.inView = true;

        ParticleEmitterComponent emitter = world.edit(e).create(ParticleEmitterComponent.class);
        emitter.effectPath = effectPath;
        emitter.atlasTag = atlasTag;
        emitter.looping = looping;
        emitter.autoRemoveWhenComplete = !looping;
        emitter.autoStart = true;
        emitter.paused = false;
        emitter.playRequested = true;

        markSpawnDirty(world, e);
        return e;
    }

    private static void requirePreparedParticle(
            World world, String effectPath, String atlasTag) {
        RenderParticleSyncSystem particles = world.getSystem(RenderParticleSyncSystem.class);
        if (particles == null) {
            throw new IllegalStateException(
                    "Required Runtime particle availability system is missing.");
        }
        particles.requirePrepared(atlasTag, effectPath);
    }

    private static String normalizeEffectPath(String effectPathOrName) {
        return ParticleEffectPath.normalize(effectPathOrName);
    }

    private static World requireWorld(PixscapeEngine engine) {
        World world = engine != null ? engine.getWorld() : null;
        if (world == null) {
            throw new IllegalStateException("World is not initialized. Call loadScene() first.");
        }
        return world;
    }

    private static void resolveSpriteRegion(PixscapeEngine engine,
                                            AssetRefComponent assetRef,
                                            TextureRegionComponent textureRegion,
                                            RenderMaterialComponent material) {
        AtlasAssetBinding binding = requireSpriteBinding(
                engine, assetRef.assetId, assetRef.atlasTag);
        applySpriteBinding(binding, assetRef.atlasTag, textureRegion, material);
    }

    private static AtlasAssetBinding requireSpriteBinding(PixscapeEngine engine,
                                                           int assetId,
                                                           String atlasTag) {
        AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
        if (atlasService == null) {
            throw new IllegalArgumentException(
                    "Asset #" + assetId + " is not available in current scene atlas. "
                            + "Add it to Runtime Availability before export."
            );
        }

        AtlasAssetBinding binding = atlasService.resolveBinding(assetId, atlasTag);
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Asset #" + assetId + " is not available in current scene atlas. "
                            + "Add it to Runtime Availability before export."
            );
        }
        return binding;
    }

    private static void applySpriteBinding(
            AtlasAssetBinding binding,
            String atlasTag,
            TextureRegionComponent textureRegion,
            RenderMaterialComponent material) {
        AtlasRegionMetadata metadata = binding.metadata();
        textureRegion.u1 = metadata.u1();
        textureRegion.v1 = metadata.v1();
        textureRegion.u2 = metadata.u2();
        textureRegion.v2 = metadata.v2();
        textureRegion.pixW = metadata.pixelWidth();
        textureRegion.pixH = metadata.pixelHeight();
        textureRegion.valid = true;
        material.textureHandle = metadata.textureHandle();
        material.debugAtlasTag = atlasTag;
    }

    private static void markSpawnDirty(World world, int entityId) {
        DirtyTrackerSystem dirty = world != null ? world.getSystem(DirtyTrackerSystem.class) : null;
        if (dirty == null) return;
        dirty.geometry(entityId, GeometryDirty.ALL);
        dirty.material(entityId);
        dirty.color(entityId);
        dirty.order(entityId);
        dirty.layer(entityId);
    }

    static final class EcsApiImpl implements ECSAPI {
        private final PixscapeEngine engine;

        EcsApiImpl(PixscapeEngine engine) {
            this.engine = engine;
        }

        @Override
        public World world() {
            return engine.getWorld();
        }

        @Override
        public <T extends Component> ComponentMapper<T> mapper(Class<T> componentType) {
            return engine.mapper(componentType);
        }

        @Override
        public <T extends BaseSystem> T system(Class<T> systemType) {
            return engine.system(systemType);
        }

        @Override
        public IdentityRegistry identityRegistry() {
            return engine.getIdentityRegistry();
        }

        @Override
        public TagRegistry tagRegistry() {
            return engine.getTagRegistry();
        }
    }

    static final class EntitiesApiImpl implements EntitiesAPI {
        private final PixscapeEngine engine;
        private final ECSAPI ecs;
        private final SceneLayerResolver sceneLayers;
        private final EntityReferenceTracker entityReferences;

        EntitiesApiImpl(PixscapeEngine engine, ECSAPI ecs,
                        SceneLayerResolver sceneLayers,
                        EntityReferenceTracker entityReferences) {
            this.engine = engine;
            this.ecs = ecs;
            this.sceneLayers = sceneLayers;
            this.entityReferences = entityReferences;
        }

        @Override
        public EntityRef ofEntityId(int entityId) {
            return new EntityRefImpl(
                    engine, ecs, sceneLayers, entityReferences.capture(entityId));
        }

        @Override
        public EntityRef ofStableId(int stableId) {
            return ofEntityId(engine.findEntityByStableId(stableId));
        }

        @Override
        public EntityRef requireEntityId(int entityId) {
            if (!existsEntityId(entityId)) {
                throw new IllegalStateException("Entity does not exist for entityId=" + entityId);
            }
            return ofEntityId(entityId);
        }

        @Override
        public EntityRef requireStableId(int stableId) {
            int entityId = entityIdOf(stableId);
            if (entityId < 0) {
                throw new IllegalStateException("Entity does not exist for stableId=" + stableId);
            }
            return ofEntityId(entityId);
        }

        @Override
        public EntityRef requireTag(String tag) {
            int entityId = engine.firstEntityByTag(tag);
            if (entityId < 0) {
                throw new IllegalStateException("Entity does not exist for tag='" + tag + "'");
            }
            return ofEntityId(entityId);
        }

        @Override
        public EntityRef[] findAllByTag(String tag) {
            IntArray entityIds = engine.getTagRegistry().get(tag);
            EntityRef[] refs = new EntityRef[entityIds.size];
            for (int i = 0; i < entityIds.size; i++) {
                refs[i] = new EntityRefImpl(
                        engine, ecs, sceneLayers,
                        entityReferences.capture(entityIds.get(i)));
            }
            return refs;
        }

        @Override
        public EntityRef requireName(String name) {
            int entityId = engine.firstEntityByName(name);
            if (entityId < 0) {
                throw new IllegalStateException("Entity does not exist for name='" + name + "'");
            }
            return ofEntityId(entityId);
        }

        @Override
        public int entityIdOf(int stableId) {
            return engine.findEntityByStableId(stableId);
        }

        @Override
        public int findEntityId(int stableId, int defaultValue) {
            int entityId = entityIdOf(stableId);
            return entityId >= 0 ? entityId : defaultValue;
        }

        @Override
        public int stableIdOf(int entityId) {
            return engine.getIdentityRegistry().getStableId(entityId);
        }

        @Override
        public int ensureStableId(int entityId) {
            return engine.getIdentityRegistry().ensureStableId(entityId);
        }

        @Override
        public boolean existsEntityId(int entityId) {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId);
        }

        @Override
        public boolean existsStableId(int stableId) {
            return entityIdOf(stableId) >= 0;
        }

        @Override
        public void destroy(EntityRef ref) {
            if (ref == null) return;
            ref.remove();
        }

        @Override
        public void destroyEntityId(int entityId) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return;
            world.delete(entityId);
        }

        @Override
        public void destroyStableId(int stableId) {
            destroyEntityId(entityIdOf(stableId));
        }
    }

    static final class EntityRefImpl implements EntityRef {
        private final PixscapeEngine engine;
        private final ECSAPI ecs;
        private final SceneLayerResolver sceneLayers;
        private final EntityHandle handle;
        private TransformFacade transform;
        private SpriteFacade sprite;
        private QuadDeformFacade quadDeform;
        private AuthoredGeometryFacade geometry;
        private AnimationFacade animation;
        private ParticleFacade particles;
        private ShaderFacade shader;
        private LightFacade light;
        private CustomProperties properties;
        private SpatialEntityFacade spatial;
        private RenderOrderFacade renderOrder;

        EntityRefImpl(PixscapeEngine engine, ECSAPI ecs,
                      SceneLayerResolver sceneLayers,
                      EntityHandle handle) {
            this.engine = engine;
            this.ecs = ecs;
            this.sceneLayers = sceneLayers;
            this.handle = handle;
        }

        @Override
        public int entityId() {
            return handle.entityId;
        }

        @Override
        public int stableId() {
            return handle.exists()
                    ? engine.getIdentityRegistry().getStableId(handle.entityId)
                    : -1;
        }

        @Override
        public boolean exists() {
            return handle.exists();
        }

        @Override
        public TransformFacade transform() {
            if (transform == null) transform = new TransformFacadeImpl(handle);
            return transform;
        }

        @Override
        public SpriteFacade sprite() {
            if (sprite == null) sprite = new SpriteFacadeImpl(engine, handle);
            return sprite;
        }

        @Override
        public QuadDeformFacade quadDeform() {
            if (quadDeform == null) quadDeform = new QuadDeformFacadeImpl(handle);
            return quadDeform;
        }

        @Override
        public AuthoredGeometryFacade geometry() {
            if (geometry == null) geometry = new AuthoredGeometryFacadeImpl(handle);
            return geometry;
        }

        @Override
        public AnimationFacade animation() {
            if (animation == null) animation = new AnimationFacadeImpl(engine, handle);
            return animation;
        }

        @Override
        public ParticleFacade particles() {
            if (particles == null) particles = new ParticleFacadeImpl(handle);
            return particles;
        }

        @Override
        public ShaderFacade shader() {
            if (shader == null) shader = new ShaderFacadeImpl(handle);
            return shader;
        }

        @Override
        public LightFacade light() {
            if (light == null) light = new LightFacadeImpl(handle);
            return light;
        }

        @Override
        public CustomProperties properties() {
            if (properties == null) properties = new CustomPropertiesImpl(handle);
            return properties;
        }

        @Override
        public SpatialEntityFacade spatial() {
            if (spatial == null) spatial = new SpatialEntityFacadeImpl(sceneLayers, handle);
            return spatial;
        }

        @Override
        public RenderOrderFacade renderOrder() {
            if (renderOrder == null) {
                renderOrder = new RenderOrderFacadeImpl(
                        sceneLayers, handle);
            }
            return renderOrder;
        }

        @Override
        public ECSAPI ecs() {
            return ecs;
        }

        @Override
        public void remove() {
            World world = handle.world();
            if (world != null) HierarchyRemoval.schedule(world, handle.entityId);
        }
    }

    static final class CustomPropertiesImpl implements CustomProperties {
        private final EntityHandle handle;

        CustomPropertiesImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public int size() {
            PropertySet properties = properties();
            return properties != null ? properties.size() : 0;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean contains(String name) {
            PropertySet properties = properties();
            return properties != null && properties.contains(name);
        }

        @Override
        public PropertyType typeOf(String name) {
            PropertySet properties = properties();
            return properties != null ? properties.typeOf(name) : null;
        }

        @Override
        public String getString(String name, String fallback) {
            PropertySet properties = properties();
            return properties != null ? properties.getString(name, fallback) : fallback;
        }

        @Override
        public boolean getBoolean(String name, boolean fallback) {
            PropertySet properties = properties();
            return properties != null ? properties.getBoolean(name, fallback) : fallback;
        }

        @Override
        public int getInt(String name, int fallback) {
            PropertySet properties = properties();
            return properties != null ? properties.getInt(name, fallback) : fallback;
        }

        @Override
        public float getFloat(String name, float fallback) {
            PropertySet properties = properties();
            return properties != null ? properties.getFloat(name, fallback) : fallback;
        }

        @Override
        public int getColorRgba8888(String name, int fallback) {
            PropertySet properties = properties();
            return properties != null ? properties.getColorRgba8888(name, fallback) : fallback;
        }

        @Override
        public int getObjectStableId(String name, int fallbackStableId) {
            PropertySet properties = properties();
            return properties != null
                    ? properties.getObjectStableId(name, fallbackStableId)
                    : fallbackStableId;
        }

        @Override
        public ClassProperty getClassValue(String name) {
            PropertySet properties = properties();
            return properties != null ? properties.getClassValue(name) : null;
        }

        private PropertySet properties() {
            World world = handle.world();
            if (world == null) return null;
            CustomPropertiesComponent component = world
                    .getMapper(CustomPropertiesComponent.class)
                    .getSafe(handle.entityId, null);
            return component != null ? component.properties : null;
        }

    }

    static final class RenderOrderFacadeImpl implements RenderOrderFacade {
        private final SceneLayerResolver sceneLayers;
        private final EntityHandle handle;
        private LayerComponent validatedLayer;
        private EntityIndexComponent validatedEntityIndex;

        RenderOrderFacadeImpl(SceneLayerResolver sceneLayers,
                              EntityHandle handle) {
            this.sceneLayers = sceneLayers;
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return isGameObjectMember() ? resolveEntityIndex() : resolveIndependentComponents();
        }

        @Override
        public int layerIndex() {
            if (isGameObjectMember()) {
                return resolveEntityIndex() ? effectiveMemberLayerIndex() : -1;
            }
            return resolveIndependentComponents() ? validatedEntityIndex.layerIndex : -1;
        }

        @Override
        public int zIndex() {
            if (isGameObjectMember()) return resolveEntityIndex() ? validatedEntityIndex.zIndex : 0;
            return resolveIndependentComponents() ? validatedEntityIndex.zIndex : 0;
        }

        @Override
        public RenderOrderFacade layerIndex(int layerIndex) {
            if (!resolveEntityIndex()) return this;
            requireIndependentLayer("layerIndex(int)");
            if (!resolveIndependentComponents()) return this;
            validateZIndex(validatedEntityIndex.zIndex, "layerIndex(int)");
            int resolved = layers().requireLayerIndex(layerIndex);
            apply(resolved, validatedEntityIndex.zIndex);
            return this;
        }

        @Override
        public RenderOrderFacade zIndex(int zIndex) {
            if (isGameObjectMember()) {
                if (!resolveEntityIndex()) return this;
            } else if (!resolveIndependentComponents()) return this;
            validateZIndex(zIndex, "zIndex(int)");
            if (isGameObjectMember()) {
                applyLocalZ(zIndex);
                return this;
            }
            apply(validatedEntityIndex.layerIndex, zIndex);
            return this;
        }

        @Override
        public RenderOrderFacade set(int layerIndex, int zIndex) {
            if (!resolveEntityIndex()) return this;
            requireIndependentLayer("set(int, int)");
            if (!resolveIndependentComponents()) return this;
            int resolved = layers().requireLayerIndex(layerIndex);
            validateZIndex(zIndex, "set(int, int)");
            apply(resolved, zIndex);
            return this;
        }

        private boolean resolveEntityIndex() {
            World world = handle.world();
            if (world == null) {
                validatedLayer = null;
                validatedEntityIndex = null;
                return false;
            }
            validatedEntityIndex = world.getMapper(EntityIndexComponent.class)
                    .getSafe(handle.entityId, null);
            return validatedEntityIndex != null;
        }

        private boolean resolveIndependentComponents() {
            if (!resolveEntityIndex()) return false;
            validatedLayer = handle.world().getMapper(LayerComponent.class)
                    .getSafe(handle.entityId, null);
            return validatedLayer != null;
        }

        private SceneLayerResolver layers() {
            sceneLayers.bind(handle.world());
            return sceneLayers;
        }

        private void validateZIndex(int zIndex, String operation) {
            if (zIndex < SortKey64.MIN_Z || zIndex > SortKey64.MAX_Z) {
                throw new IllegalArgumentException("zIndex " + zIndex
                        + " is outside the supported range [" + SortKey64.MIN_Z
                        + ", " + SortKey64.MAX_Z + "] for render-order operation "
                        + operation + ".");
            }
        }

        private void requireIndependentLayer(String operation) {
            if (isGameObjectMember()) {
                throw new IllegalStateException("Render-order operation " + operation
                        + " cannot change the global layer of a Game Object member; "
                        + "the top-level root owns effective Layer placement.");
            }
        }

        private boolean isGameObjectMember() {
            World world = handle.world();
            return world != null && world.getMapper(GameObjectMemberComponent.class)
                    .has(handle.entityId);
        }

        private int effectiveMemberLayerIndex() {
            World world = handle.world();
            if (world == null) return -1;
            IdentityRegistry identities = IdentityRegistry.boundTo(world);
            if (identities == null) return -1;
            int entityId = handle.entityId;
            ComponentMapper<GameObjectMemberComponent> members =
                    world.getMapper(GameObjectMemberComponent.class);
            for (int depth = 0; depth < 256; depth++) {
                GameObjectMemberComponent member = members.getSafe(entityId, null);
                if (member == null) {
                    EntityIndexComponent rootIndex = world.getMapper(EntityIndexComponent.class)
                            .getSafe(entityId, null);
                    return rootIndex != null ? rootIndex.layerIndex : -1;
                }
                entityId = identities.findByStableId(member.parentStableId);
                if (entityId < 0) return -1;
            }
            return -1;
        }

        private void applyLocalZ(int zIndex) {
            if (validatedEntityIndex.zIndex == zIndex) return;
            validatedEntityIndex.zIndex = zIndex;
            World world = handle.world();
            DirtyTrackerSystem dirty = world != null ? world.getSystem(DirtyTrackerSystem.class) : null;
            if (dirty != null) dirty.order(handle.entityId);
        }

        private void apply(int layerIndex, int zIndex) {
            boolean layerChanged = validatedEntityIndex.layerIndex != layerIndex
                    || validatedLayer.layerIndex != layerIndex;
            boolean orderChanged = validatedEntityIndex.zIndex != zIndex;
            if (!layerChanged && !orderChanged) return;

            validatedLayer.layerIndex = layerIndex;
            validatedEntityIndex.layerIndex = layerIndex;
            validatedEntityIndex.zIndex = zIndex;

            World world = handle.world();
            DirtyTrackerSystem dirty = world != null ? world.getSystem(DirtyTrackerSystem.class) : null;
            if (dirty == null) return;
            if (layerChanged) {
                dirty.layer(handle.entityId);
                dirty.order(handle.entityId);
            } else if (orderChanged) {
                dirty.order(handle.entityId);
            }
        }

    }

    static final class TransformFacadeImpl implements TransformFacade {
        private final EntityHandle handle;

        TransformFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public float x() {
            TransformComponent t = t(false);
            return t != null ? t.x : 0f;
        }

        @Override
        public float y() {
            TransformComponent t = t(false);
            return t != null ? t.y : 0f;
        }

        @Override
        public float rotationRad() {
            TransformComponent t = t(false);
            return t != null ? t.rotationRad : 0f;
        }

        @Override
        public float scaleX() {
            TransformComponent t = t(false);
            return t != null ? t.scaleX : 1f;
        }

        @Override
        public float scaleY() {
            TransformComponent t = t(false);
            return t != null ? t.scaleY : 1f;
        }

        @Override
        public TransformFacade setPosition(float x, float y) {
            if (handle.world() == null) return this;
            requireFinite("Transform position", x, y);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t == null) return this;
            if (t.x != x || t.y != y) {
                t.x = x;
                t.y = y;
                markGeometry(GeometryDirty.POSITION);
            }
            return this;
        }

        @Override
        public TransformFacade setX(float x) {
            if (handle.world() == null) return this;
            requireFinite("Transform x", x);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && t.x != x) {
                t.x = x;
                markGeometry(GeometryDirty.POSITION);
            }
            return this;
        }

        @Override
        public TransformFacade setY(float y) {
            if (handle.world() == null) return this;
            requireFinite("Transform y", y);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && t.y != y) {
                t.y = y;
                markGeometry(GeometryDirty.POSITION);
            }
            return this;
        }

        @Override
        public TransformFacade moveBy(float dx, float dy) {
            if (handle.world() == null) return this;
            requireFinite("Transform movement", dx, dy);
            requirePhysicsMutationAllowed();
            if (dx != 0f || dy != 0f) {
                TransformComponent t = t(true);
                if (t != null) {
                    float x = t.x + dx;
                    float y = t.y + dy;
                    requireFinite("Transform position", x, y);
                    t.x = x;
                    t.y = y;
                    markGeometry(GeometryDirty.POSITION);
                }
            }
            return this;
        }

        @Override
        public TransformFacade setRotationRad(float radians) {
            if (handle.world() == null) return this;
            requireFinite("Transform rotation", radians);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && t.rotationRad != radians) {
                t.rotationRad = radians;
                markGeometry(GeometryDirty.ROTATION);
            }
            return this;
        }

        @Override
        public TransformFacade rotateByRad(float radians) {
            if (handle.world() == null) return this;
            requireFinite("Transform rotation delta", radians);
            requirePhysicsMutationAllowed();
            if (radians != 0f) {
                TransformComponent t = t(true);
                if (t != null) {
                    float rotation = t.rotationRad + radians;
                    requireFinite("Transform rotation", rotation);
                    t.rotationRad = rotation;
                    markGeometry(GeometryDirty.ROTATION);
                }
            }
            return this;
        }

        @Override
        public TransformFacade setScale(float uniform) {
            return setScale(uniform, uniform);
        }

        @Override
        public TransformFacade setScale(float sx, float sy) {
            if (handle.world() == null) return this;
            requireFinite("Transform scale", sx, sy);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && (t.scaleX != sx || t.scaleY != sy)) {
                t.scaleX = sx;
                t.scaleY = sy;
                markGeometry(GeometryDirty.SCALE);
            }
            return this;
        }

        @Override
        public TransformFacade setScaleX(float sx) {
            if (handle.world() == null) return this;
            requireFinite("Transform scale x", sx);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && t.scaleX != sx) {
                t.scaleX = sx;
                markGeometry(GeometryDirty.SCALE);
            }
            return this;
        }

        @Override
        public TransformFacade setScaleY(float sy) {
            if (handle.world() == null) return this;
            requireFinite("Transform scale y", sy);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && t.scaleY != sy) {
                t.scaleY = sy;
                markGeometry(GeometryDirty.SCALE);
            }
            return this;
        }

        @Override
        public TransformFacade setOrigin(float ox, float oy) {
            if (handle.world() == null) return this;
            requireFinite("Transform origin", ox, oy);
            requirePhysicsMutationAllowed();
            TransformComponent t = t(true);
            if (t != null && (t.originX != ox || t.originY != oy)) {
                t.originX = ox;
                t.originY = oy;
                markGeometry(GeometryDirty.ORIGIN);
            }
            return this;
        }

        private TransformComponent t(boolean create) {
            World world = handle.world();
            if (world == null) return null;
            ComponentMapper<TransformComponent> mapper = world.getMapper(TransformComponent.class);
            if (create) {
                return mapper.has(handle.entityId)
                        ? mapper.get(handle.entityId)
                        : mapper.create(handle.entityId);
            }
            return mapper.getSafe(handle.entityId, null);
        }

        private void markGeometry(int subMask) {
            World world = handle.world();
            DirtyTrackerSystem dirty = world != null
                    ? world.getSystem(DirtyTrackerSystem.class) : null;
            if (dirty != null) dirty.geometry(handle.entityId, subMask);
        }

        private void requirePhysicsMutationAllowed() {
            World world = handle.world();
            if (world == null) return;
            PhysicsPoseAuthority authority = world.getSystem(PhysicsPoseAuthority.class);
            if (authority == null || !authority.isRuntimePhysics()) return;

            boolean directBody = world.getMapper(PhysicsBodyComponent.class).has(handle.entityId);
            GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
            if (directBody || (hierarchy != null && hierarchy.containsPhysicsInSubtree(handle.entityId))) {
                throw new IllegalStateException(
                        "Transform mutation is not allowed while Runtime Physics owns this Body "
                                + "or a descendant Body.");
            }
        }
    }

    static final class SpatialApiImpl implements SpatialAPI {
        private final PixscapeEngine engine;
        private final SceneLayerResolver sceneLayers;

        SpatialApiImpl(PixscapeEngine engine, SceneLayerResolver sceneLayers) {
            this.engine = engine;
            this.sceneLayers = sceneLayers;
        }

        @Override
        public boolean isLayerEnabled(int layerIndex) {
            World world = engine.getWorld();
            if (world == null) return false;
            sceneLayers.bind(world);
            return sceneLayers.isLayerSpatialEnabled(layerIndex);
        }

        @Override
        public SpatialAPI setLayerEnabled(int layerIndex, boolean enabled) {
            World world = engine.getWorld();
            if (world == null) return this;
            sceneLayers.bind(world);
            sceneLayers.setLayerSpatialEnabled(layerIndex, enabled);
            return this;
        }
    }

    static final class SpatialEntityFacadeImpl implements SpatialEntityFacade {
        private final SceneLayerResolver sceneLayers;
        private final EntityHandle handle;

        SpatialEntityFacadeImpl(SceneLayerResolver sceneLayers, EntityHandle handle) {
            this.sceneLayers = sceneLayers;
            this.handle = handle;
        }

        @Override
        public boolean enabled() {
            return comp(false) != null;
        }

        @Override
        public SpatialEntityFacade enable() {
            comp(true);
            return this;
        }

        @Override
        public SpatialEntityFacade disable() {
            World world = handle.world();
            if (world == null) return this;
            ComponentMapper<SpatialHeightComponent> mapper = world.getMapper(SpatialHeightComponent.class);
            if (mapper.has(handle.entityId)) mapper.remove(handle.entityId);
            return this;
        }

        @Override
        public float altitude() {
            SpatialHeightComponent c = comp(false);
            return c != null ? c.altitude : 0f;
        }

        @Override
        public float height() {
            SpatialHeightComponent c = comp(false);
            return c != null ? c.height : 0f;
        }

        @Override
        public SpatialEntityFacade setAltitude(float altitude) {
            if (handle.world() == null) return this;
            requireFinite("Spatial altitude", altitude);
            SpatialHeightComponent c = comp(true);
            if (c != null) c.altitude = altitude;
            return this;
        }

        @Override
        public SpatialEntityFacade setHeight(float height) {
            if (handle.world() == null) return this;
            requireFinite("Spatial height", height);
            SpatialHeightComponent c = comp(true);
            if (c != null) c.height = Math.max(0f, height);
            return this;
        }

        @Override
        public SpatialEntityFacade setVolume(float altitude, float height) {
            if (handle.world() == null) return this;
            requireFinite("Spatial volume", altitude, height);
            SpatialHeightComponent c = comp(true);
            if (c != null) {
                c.altitude = altitude;
                c.height = Math.max(0f, height);
            }
            return this;
        }

        @Override
        public boolean participatesInRenderOrder() {
            World world = handle.world();
            if (world == null) return false;
            SpatialRenderOrderSystem system = world.getSystem(SpatialRenderOrderSystem.class);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                    .getSafe(handle.entityId, null);
            if (system == null || index == null) return false;
            sceneLayers.bind(world);
            return system.participatesInRenderOrder(
                    handle.entityId,
                    sceneLayers.isActorSpatialLayerEnabled(index.layerIndex));
        }

        private SpatialHeightComponent comp(boolean create) {
            World world = handle.world();
            if (world == null) return null;
            ComponentMapper<SpatialHeightComponent> mapper = world.getMapper(SpatialHeightComponent.class);
            return create
                    ? (mapper.has(handle.entityId)
                    ? mapper.get(handle.entityId) : mapper.create(handle.entityId))
                    : mapper.getSafe(handle.entityId, null);
        }
    }

    static final class SpriteFacadeImpl implements SpriteFacade {
        private final PixscapeEngine engine;
        private final EntityHandle handle;

        SpriteFacadeImpl(PixscapeEngine engine, EntityHandle handle) {
            this.engine = engine;
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return spriteCapabilityWorld(handle) != null;
        }

        @Override
        public int assetId() {
            AssetRefComponent c = src(false);
            return c != null ? c.assetId : -1;
        }

        @Override
        public SpriteFacade setAssetId(int assetId) {
            AssetRefComponent src = src(false);
            return src != null ? assignAsset(assetId, src.atlasTag) : this;
        }

        @Override
        public SpriteFacade setAsset(int assetId, String atlasTag) {
            String normalizedTag = isBlank(atlasTag) ? "main" : atlasTag;
            return assignAsset(assetId, normalizedTag);
        }

        @Override
        public SpriteFacade setVisible(boolean visible) {
            VisibilityComponent c = vis(true);
            if (c != null) c.visible = visible;
            return this;
        }

        @Override
        public SpriteFacade setTint(float r, float g, float b, float a) {
            TintComponent c = tint(true);
            if (c == null) return this;
            requireFinite("Sprite tint red", r);
            requireFinite("Sprite tint green", g);
            requireFinite("Sprite tint blue", b);
            requireFinite("Sprite tint alpha", a);
            c.rgba = Color.rgba8888(clamp01(r), clamp01(g), clamp01(b), clamp01(a));
            markColor();
            return this;
        }

        @Override
        public SpriteFacade setAlpha(float alpha) {
            TintComponent c = tint(true);
            if (c == null) return this;
            requireFinite("Sprite alpha", alpha);
            int nextA = (int) (clamp01(alpha) * 255f + 0.5f);
            int currentA = c.rgba & 0xFF;
            if (currentA != nextA) {
                c.rgba = (c.rgba & 0xFFFFFF00) | (nextA & 0xFF);
                markColor();
            }
            return this;
        }

        @Override
        public SpriteFacade setSize(float width, float height) {
            DimensionsComponent d = dim(true);
            if (d == null) return this;
            requireFinite("Sprite size", width, height);
            if (d.width != width || d.height != height) {
                d.width = width;
                d.height = height;
                markGeometry(GeometryDirty.SIZE);
            }
            return this;
        }

        @Override
        public boolean repeatsX() {
            RenderRepeatComponent repeat = comp(RenderRepeatComponent.class, false);
            return repeat != null && repeat.repeatX;
        }

        @Override
        public boolean repeatsY() {
            RenderRepeatComponent repeat = comp(RenderRepeatComponent.class, false);
            return repeat != null && repeat.repeatY;
        }

        @Override
        public SpriteFacade setRepeat(boolean repeatX, boolean repeatY) {
            World world = spriteCapabilityWorld(handle);
            if (world == null) return this;
            if (repeatX || repeatY) {
                QuadDeformComponent quad = world.getMapper(QuadDeformComponent.class)
                        .getSafe(handle.entityId, null);
                if (hasQuadDeformation(quad)) {
                    throw repeatQuadConflict();
                }
            }
            RenderRepeatComponent repeat = comp(
                    RenderRepeatComponent.class, repeatX || repeatY);
            if (repeat == null) return this;
            if (repeat.repeatX != repeatX || repeat.repeatY != repeatY) {
                repeat.repeatX = repeatX;
                repeat.repeatY = repeatY;
                markMaterial();
            }
            return this;
        }

        private SpriteFacade assignAsset(int assetId, String atlasTag) {
            World world = spriteCapabilityWorld(handle);
            if (world == null) return this;
            AssetRefComponent src = world.getMapper(AssetRefComponent.class).get(handle.entityId);
            String normalizedTag = isBlank(atlasTag) ? "main" : atlasTag;
            AtlasAssetBinding binding = requireSpriteBinding(
                    engine, assetId, normalizedTag);
            if (src.assetId == assetId && normalizedTag.equals(src.atlasTag)) return this;

            TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class)
                    .has(handle.entityId)
                    ? world.getMapper(TextureRegionComponent.class).get(handle.entityId)
                    : world.getMapper(TextureRegionComponent.class).create(handle.entityId);
            RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class)
                    .has(handle.entityId)
                    ? world.getMapper(RenderMaterialComponent.class).get(handle.entityId)
                    : world.getMapper(RenderMaterialComponent.class).create(handle.entityId);

            applySpriteBinding(binding, normalizedTag, tr, mat);
            src.assetId = assetId;
            src.atlasTag = normalizedTag;
            markMaterial();
            return this;
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private AssetRefComponent src(boolean create) {
            return comp(AssetRefComponent.class, create);
        }

        private VisibilityComponent vis(boolean create) {
            return comp(VisibilityComponent.class, create);
        }

        private TintComponent tint(boolean create) {
            return comp(TintComponent.class, create);
        }

        private DimensionsComponent dim(boolean create) {
            return comp(DimensionsComponent.class, create);
        }

        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = spriteCapabilityWorld(handle);
            if (world == null) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            if (create) {
                return mapper.has(handle.entityId)
                        ? mapper.get(handle.entityId) : mapper.create(handle.entityId);
            }
            return mapper.getSafe(handle.entityId, null);
        }

        private void markGeometry(int sub) {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.geometry(handle.entityId, sub);
        }

        private void markMaterial() {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.material(handle.entityId);
        }

        private void markColor() {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.color(handle.entityId);
        }

        private DirtyTrackerSystem dirty() {
            World w = spriteCapabilityWorld(handle);
            return w != null ? w.getSystem(DirtyTrackerSystem.class) : null;
        }
    }

    static final class QuadDeformFacadeImpl implements QuadDeformFacade {
        private final EntityHandle handle;

        QuadDeformFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return quadDeformCapabilityWorld(handle) != null;
        }

        @Override
        public boolean isDeformed() {
            return hasQuadDeformation(component());
        }

        @Override
        public float bottomLeftX() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.blX : 0f;
        }

        @Override
        public float bottomLeftY() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.blY : 0f;
        }

        @Override
        public float bottomRightX() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.brX : 0f;
        }

        @Override
        public float bottomRightY() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.brY : 0f;
        }

        @Override
        public float topRightX() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.trX : 0f;
        }

        @Override
        public float topRightY() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.trY : 0f;
        }

        @Override
        public float topLeftX() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.tlX : 0f;
        }

        @Override
        public float topLeftY() {
            QuadDeformComponent quad = component();
            return quad != null ? quad.tlY : 0f;
        }

        @Override
        public QuadDeformFacade setBottomLeft(float x, float y) {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            requireFinite("Quad deformation bottom-left", x, y);
            QuadDeformComponent quad = component(world);
            return apply(world, x, y,
                    quad != null ? quad.brX : 0f, quad != null ? quad.brY : 0f,
                    quad != null ? quad.trX : 0f, quad != null ? quad.trY : 0f,
                    quad != null ? quad.tlX : 0f, quad != null ? quad.tlY : 0f);
        }

        @Override
        public QuadDeformFacade setBottomRight(float x, float y) {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            requireFinite("Quad deformation bottom-right", x, y);
            QuadDeformComponent quad = component(world);
            return apply(world,
                    quad != null ? quad.blX : 0f, quad != null ? quad.blY : 0f,
                    x, y,
                    quad != null ? quad.trX : 0f, quad != null ? quad.trY : 0f,
                    quad != null ? quad.tlX : 0f, quad != null ? quad.tlY : 0f);
        }

        @Override
        public QuadDeformFacade setTopRight(float x, float y) {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            requireFinite("Quad deformation top-right", x, y);
            QuadDeformComponent quad = component(world);
            return apply(world,
                    quad != null ? quad.blX : 0f, quad != null ? quad.blY : 0f,
                    quad != null ? quad.brX : 0f, quad != null ? quad.brY : 0f,
                    x, y,
                    quad != null ? quad.tlX : 0f, quad != null ? quad.tlY : 0f);
        }

        @Override
        public QuadDeformFacade setTopLeft(float x, float y) {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            requireFinite("Quad deformation top-left", x, y);
            QuadDeformComponent quad = component(world);
            return apply(world,
                    quad != null ? quad.blX : 0f, quad != null ? quad.blY : 0f,
                    quad != null ? quad.brX : 0f, quad != null ? quad.brY : 0f,
                    quad != null ? quad.trX : 0f, quad != null ? quad.trY : 0f,
                    x, y);
        }

        @Override
        public QuadDeformFacade set(
                float blX, float blY,
                float brX, float brY,
                float trX, float trY,
                float tlX, float tlY) {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            requireFinite("Quad deformation BL", blX, blY);
            requireFinite("Quad deformation BR", brX, brY);
            requireFinite("Quad deformation TR", trX, trY);
            requireFinite("Quad deformation TL", tlX, tlY);
            return apply(world, blX, blY, brX, brY, trX, trY, tlX, tlY);
        }

        @Override
        public QuadDeformFacade reset() {
            World world = quadDeformCapabilityWorld(handle);
            if (world == null) return this;
            ComponentMapper<QuadDeformComponent> mapper =
                    world.getMapper(QuadDeformComponent.class);
            QuadDeformComponent quad = mapper.getSafe(handle.entityId, null);
            if (quad == null) return this;
            boolean changed = hasQuadDeformation(quad);
            mapper.remove(handle.entityId);
            if (changed) markQuadDirty(world);
            return this;
        }

        private QuadDeformFacade apply(
                World world,
                float blX, float blY,
                float brX, float brY,
                float trX, float trY,
                float tlX, float tlY) {
            ComponentMapper<QuadDeformComponent> mapper =
                    world.getMapper(QuadDeformComponent.class);
            QuadDeformComponent quad = mapper.getSafe(handle.entityId, null);
            boolean allZero = blX == 0f && blY == 0f
                    && brX == 0f && brY == 0f
                    && trX == 0f && trY == 0f
                    && tlX == 0f && tlY == 0f;
            boolean changed = quad == null
                    ? !allZero
                    : quad.blX != blX || quad.blY != blY
                    || quad.brX != brX || quad.brY != brY
                    || quad.trX != trX || quad.trY != trY
                    || quad.tlX != tlX || quad.tlY != tlY;

            if (!changed) {
                if (allZero && quad != null) mapper.remove(handle.entityId);
                return this;
            }
            if (allZero) {
                mapper.remove(handle.entityId);
                markQuadDirty(world);
                return this;
            }
            if (isRepeatActive(world)) {
                throw repeatQuadConflict();
            }
            if (quad == null) quad = mapper.create(handle.entityId);
            quad.blX = blX;
            quad.blY = blY;
            quad.brX = brX;
            quad.brY = brY;
            quad.trX = trX;
            quad.trY = trY;
            quad.tlX = tlX;
            quad.tlY = tlY;
            markQuadDirty(world);
            return this;
        }

        private QuadDeformComponent component() {
            World world = quadDeformCapabilityWorld(handle);
            return world != null ? component(world) : null;
        }

        private QuadDeformComponent component(World world) {
            return world.getMapper(QuadDeformComponent.class)
                    .getSafe(handle.entityId, null);
        }

        private boolean isRepeatActive(World world) {
            RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class)
                    .getSafe(handle.entityId, null);
            return repeat != null && (repeat.repeatX || repeat.repeatY);
        }

        private void markQuadDirty(World world) {
            DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
            if (dirty != null) dirty.geometry(handle.entityId, GeometryDirty.QUAD);
        }
    }

    static final class AuthoredGeometryFacadeImpl implements AuthoredGeometryFacade {
        private final EntityHandle handle;

        AuthoredGeometryFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return kind() != AuthoredGeometryKind.NONE;
        }

        @Override
        public AuthoredGeometryKind kind() {
            World world = handle.world();
            if (world == null) return AuthoredGeometryKind.NONE;
            int entityId = handle.entityId;
            if (world.getMapper(PolygonComponent.class).has(entityId)) {
                return AuthoredGeometryKind.POLYGON;
            }
            if (world.getMapper(PolylineComponent.class).has(entityId)) {
                return AuthoredGeometryKind.POLYLINE;
            }
            if (world.getMapper(DimensionsComponent.class).has(entityId)) {
                return AuthoredGeometryKind.RECTANGLE;
            }
            return AuthoredGeometryKind.NONE;
        }

        @Override
        public float width() {
            DimensionsComponent dimensions = dimensions();
            return dimensions != null ? dimensions.width : 0f;
        }

        @Override
        public float height() {
            DimensionsComponent dimensions = dimensions();
            return dimensions != null ? dimensions.height : 0f;
        }

        @Override
        public int vertexCount() {
            AuthoredGeometryKind kind = kind();
            if (kind == AuthoredGeometryKind.RECTANGLE) return 4;
            float[] vertices = vertices(kind);
            return vertices != null ? vertices.length / 2 : 0;
        }

        @Override
        public float localX(int vertexIndex) {
            return localCoordinate(vertexIndex, 0);
        }

        @Override
        public float localY(int vertexIndex) {
            return localCoordinate(vertexIndex, 1);
        }

        @Override
        public boolean closed() {
            AuthoredGeometryKind kind = kind();
            return kind == AuthoredGeometryKind.RECTANGLE
                    || kind == AuthoredGeometryKind.POLYGON;
        }

        private float localCoordinate(int vertexIndex, int axis) {
            AuthoredGeometryKind kind = kind();
            int count = vertexCount();
            if (vertexIndex < 0 || vertexIndex >= count) {
                throw new IndexOutOfBoundsException(
                        "Authored geometry vertex index " + vertexIndex
                                + " is outside [0, " + count + ").");
            }
            if (kind == AuthoredGeometryKind.RECTANGLE) {
                DimensionsComponent dimensions = dimensions();
                float width = dimensions != null ? dimensions.width : 0f;
                float height = dimensions != null ? dimensions.height : 0f;
                if (axis == 0) {
                    return vertexIndex == 1 || vertexIndex == 2 ? width : 0f;
                }
                return vertexIndex >= 2 ? height : 0f;
            }
            return vertices(kind)[vertexIndex * 2 + axis];
        }

        private DimensionsComponent dimensions() {
            World world = handle.world();
            return world != null
                    ? world.getMapper(DimensionsComponent.class)
                    .getSafe(handle.entityId, null)
                    : null;
        }

        private float[] vertices(AuthoredGeometryKind kind) {
            World world = handle.world();
            if (world == null) return null;
            if (kind == AuthoredGeometryKind.POLYGON) {
                PolygonComponent polygon = world.getMapper(PolygonComponent.class)
                        .getSafe(handle.entityId, null);
                return polygon != null ? polygon.vertices : null;
            }
            if (kind == AuthoredGeometryKind.POLYLINE) {
                PolylineComponent polyline = world.getMapper(PolylineComponent.class)
                        .getSafe(handle.entityId, null);
                return polyline != null ? polyline.vertices : null;
            }
            return null;
        }
    }

    private static IllegalStateException repeatQuadConflict() {
        return new IllegalStateException(
                "Quad deformation and sprite Repeat cannot be active together.");
    }

    static final class AnimationFacadeImpl implements AnimationFacade {
        private final PixscapeEngine engine;
        private final EntityHandle handle;

        AnimationFacadeImpl(PixscapeEngine engine, EntityHandle handle) {
            this.engine = engine;
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return anim() != null;
        }

        @Override
        public String clip() {
            AnimationComponent a = anim();
            return a != null && a.currentClip != null ? a.currentClip : "";
        }

        @Override
        public boolean hasClip(String clipName) {
            if (anim() == null) return false;
            AnimationDef def = activeDef();
            return def != null && def.clip(clipName) != null;
        }

        @Override
        public int frame() {
            AnimationComponent a = anim();
            return a != null ? a.frame : -1;
        }

        @Override
        public float stateTime() {
            AnimationComponent a = anim();
            return a != null ? a.stateTime : 0f;
        }

        @Override
        public AnimationFacade play() {
            AnimationComponent a = anim();
            if (a != null) a.playing = true;
            return this;
        }

        @Override
        public AnimationFacade pause() {
            AnimationComponent a = anim();
            if (a != null) a.playing = false;
            return this;
        }

        @Override
        public AnimationFacade stop() {
            AnimationComponent a = anim();
            if (a != null) {
                a.playing = false;
                a.stateTime = 0f;
                a.frame = -1;
                markMaterial();
            }
            return this;
        }

        @Override
        public AnimationFacade restart() {
            AnimationComponent a = anim();
            if (a != null) {
                a.stateTime = 0f;
                a.frame = -1;
                a.playing = true;
                markMaterial();
            }
            return this;
        }

        @Override
        public AnimationFacade setAnimation(int assetId) {
            if (anim() == null) return this;
            return applyAnimation(requireDefinition(assetId), null, false);
        }

        @Override
        public AnimationFacade setAnimation(String animationName) {
            if (anim() == null) return this;
            return applyAnimation(requireDefinition(animationName), null, false);
        }

        @Override
        public AnimationFacade play(String clipName) {
            AnimationComponent a = anim();
            if (a == null) return this;
            requireClip(activeDef(), clipName);
            selectClip(a, clipName);
            a.playing = true;
            return this;
        }

        @Override
        public AnimationFacade play(int animationAssetId, String clipName) {
            if (anim() == null) return this;
            return applyAnimation(requireDefinition(animationAssetId), clipName, true);
        }

        @Override
        public AnimationFacade play(String animationName, String clipName) {
            if (anim() == null) return this;
            return applyAnimation(requireDefinition(animationName), clipName, true);
        }

        @Override
        public AnimationFacade setClip(String clipName) {
            AnimationComponent a = anim();
            if (a == null) return this;
            requireClip(activeDef(), clipName);
            selectClip(a, clipName);
            return this;
        }

        @Override
        public AnimationFacade setLoop(boolean loop) {
            AnimationComponent a = anim();
            if (a != null) a.loop = loop;
            return this;
        }

        @Override
        public AnimationFacade setFps(float fps) {
            AnimationComponent a = anim();
            if (a == null) return this;
            if (fps < 0f || Float.isNaN(fps) || Float.isInfinite(fps)) {
                throw new IllegalArgumentException(
                        "Animation fps must be finite and >= 0, got " + fps + ".");
            }
            a.fps = fps;
            return this;
        }

        @Override
        public AnimationFacade setStateTime(float stateTime) {
            AnimationComponent a = anim();
            if (a != null) {
                requireFinite("Animation state time", stateTime);
                a.stateTime = Math.max(0f, stateTime);
                a.frame = -1;
                markMaterial();
            }
            return this;
        }

        @Override
        public boolean isPlaying() {
            AnimationComponent a = anim();
            return a != null && a.playing;
        }

        @Override
        public boolean isLooping() {
            AnimationComponent a = anim();
            return a != null && a.loop;
        }

        @Override
        public float fps() {
            AnimationComponent a = anim();
            return a != null ? a.fps : 0f;
        }

        @Override
        public boolean isFinished() {
            AnimationComponent a = anim();
            if (a == null || a.loop || a.fps <= 0f) return false;
            AnimationDef def = activeDef();
            AnimationClipDef clip = def != null ? def.clip(a.currentClip) : null;
            if (clip == null) return false;
            int start = Math.max(0, clip.start());
            int end = Math.max(0, clip.end());
            int count = Math.abs(end - start) + 1;
            return count > 0 && a.stateTime >= count / a.fps;
        }

        private AnimationComponent anim() {
            World world = animationCapabilityWorld(handle);
            if (world == null) return null;
            return world.getMapper(AnimationComponent.class).get(handle.entityId);
        }

        private AnimationDef activeDef() {
            World world = animationCapabilityWorld(handle);
            if (world == null) return null;
            AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).get(handle.entityId);
            return engine.getAnimationRegistry().getByAssetId(assetRef.assetId);
        }

        private AnimationDef requireDefinition(int assetId) {
            AnimationDef def = engine.getAnimationRegistry().getByAssetId(assetId);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown Animation asset id: " + assetId + ".");
            }
            return def;
        }

        private AnimationDef requireDefinition(String animationName) {
            AnimationDef def = engine.getAnimationRegistry().getByName(animationName);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown or blank Animation asset name: '" + animationName + "'.");
            }
            return def;
        }

        private AnimationFacade applyAnimation(
                AnimationDef def, String requestedClip, boolean startPlaying) {
            World world = animationCapabilityWorld(handle);
            if (world == null) return this;

            AnimationComponent animation = world.getMapper(AnimationComponent.class)
                    .get(handle.entityId);
            if (!animation.animationAssetIds.contains(def.assetId())) {
                throw new IllegalArgumentException(
                        "Animation entity " + handle.entityId + " does not own Animation asset #"
                                + def.assetId() + " ('" + def.name() + "').");
            }

            String clipName = requestedClip != null ? requestedClip : def.currentClip();
            requireClip(def, clipName);

            AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class)
                    .get(handle.entityId);
            requireSpriteBinding(engine, def.assetId(), assetRef.atlasTag);

            assetRef.assetId = def.assetId();
            animation.currentClip = clipName;
            animation.fps = def.fps();
            animation.stateTime = 0f;
            animation.frame = -1;
            if (startPlaying) animation.playing = true;
            markMaterial();
            return this;
        }

        private static void requireClip(AnimationDef def, String clipName) {
            if (def == null || def.clip(clipName) == null) {
                throw new IllegalArgumentException(
                        "Unknown or blank animation clip: '" + clipName + "'.");
            }
        }

        private void selectClip(AnimationComponent animation, String clipName) {
            animation.currentClip = clipName;
            animation.frame = -1;
            animation.stateTime = 0f;
            markMaterial();
        }

        private void markMaterial() {
            World world = animationCapabilityWorld(handle);
            DirtyTrackerSystem d = world != null ? world.getSystem(DirtyTrackerSystem.class) : null;
            if (d != null) d.material(handle.entityId);
        }
    }

    static final class ParticleFacadeImpl implements ParticleFacade {
        private final EntityHandle handle;

        ParticleFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return emitter(false) != null;
        }

        @Override
        public ParticleFacade setEffect(String effectPath, String atlasTag) {
            World world = handle.world();
            if (world == null) return this;
            if (isBlank(effectPath)) {
                throw new IllegalArgumentException("Particle effect path must not be blank.");
            }
            if (isBlank(atlasTag)) {
                throw new IllegalArgumentException("Particle atlas tag must not be blank.");
            }
            String normalizedEffectPath = ParticleEffectPath.normalize(effectPath);
            requirePreparedParticle(world, normalizedEffectPath, atlasTag);
            ParticleEmitterComponent c = emitter(true);
            if (c != null) {
                c.effectPath = normalizedEffectPath;
                c.atlasTag = atlasTag;
            }
            return this;
        }

        @Override
        public ParticleFacade setLooping(boolean looping) {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) c.looping = looping;
            return this;
        }

        @Override
        public ParticleFacade setAutoStart(boolean autoStart) {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) c.autoStart = autoStart;
            return this;
        }

        @Override
        public ParticleFacade play() {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) {
                c.playRequested = true;
                c.paused = false;
            }
            return this;
        }

        @Override
        public ParticleFacade pause() {
            ParticleEmitterComponent c = emitter(false);
            if (c != null) c.paused = true;
            return this;
        }

        @Override
        public ParticleFacade resume() {
            ParticleEmitterComponent c = emitter(false);
            if (c != null) c.paused = false;
            return this;
        }

        @Override
        public ParticleFacade restart() {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) {
                c.restartRequested = true;
                c.paused = false;
            }
            return this;
        }

        @Override
        public ParticleFacade stop() {
            ParticleEmitterComponent c = emitter(false);
            if (c != null) {
                c.paused = true;
                c.playRequested = false;
                c.restartRequested = false;
            }
            return this;
        }

        @Override
        public boolean isPaused() {
            ParticleEmitterComponent c = emitter(false);
            return c != null && c.paused;
        }

        @Override
        public boolean isLooping() {
            ParticleEmitterComponent c = emitter(false);
            return c != null && c.looping;
        }

        private ParticleEmitterComponent emitter(boolean create) {
            World world = handle.world();
            if (world == null) return null;
            if (create) {
                ComponentMapper<TransformComponent> transforms = world.getMapper(TransformComponent.class);
                if (!transforms.has(handle.entityId)) transforms.create(handle.entityId);
            }
            ComponentMapper<ParticleEmitterComponent> mapper = world.getMapper(ParticleEmitterComponent.class);
            return create
                    ? (mapper.has(handle.entityId)
                    ? mapper.get(handle.entityId) : mapper.create(handle.entityId))
                    : mapper.getSafe(handle.entityId, null);
        }
    }

    static final class ShaderFacadeImpl implements ShaderFacade {
        private final EntityHandle handle;

        ShaderFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean exists() {
            return renderCapabilityWorld() != null;
        }

        @Override
        public String shader() {
            RenderMaterialComponent m = mat();
            if (m == null) return null;
            return ShaderRegistry.getName(m.shaderIdx);
        }

        @Override
        public ShaderFacade use(String shaderName) {
            RenderMaterialComponent m = mat();
            if (m == null) return this;
            if (isBlank(shaderName)) {
                throw new IllegalArgumentException("Shader name must not be blank.");
            }
            int shaderIdx = ShaderRegistry.indexOf(shaderName);
            if (shaderIdx < 0) throw new IllegalArgumentException("Unknown shader: " + shaderName);
            if (m.shaderIdx != shaderIdx) {
                m.shaderIdx = shaderIdx;
                markMaterial();
            }
            return this;
        }

        @Override
        public ShaderFacade clear() {
            RenderMaterialComponent m = mat();
            if (m != null && m.shaderIdx != 0) {
                m.shaderIdx = 0;
                markMaterial();
            }
            return clearFloats();
        }

        @Override
        public ShaderFacade setFloat(String uniform, float value) {
            if (renderCapabilityWorld() == null) return this;
            requireUniformName(uniform);

            ShaderParamsComponent params = params(true);
            if (params != null) {
                for (int i = 0; i < params.floats.size; i++) {
                    ShaderFloatParam param = params.floats.get(i);
                    if (param != null && uniform.equals(param.name)) {
                        param.value = value;
                        markMaterial();
                        return this;
                    }
                }

                params.floats.add(new ShaderFloatParam(uniform, value));
                markMaterial();
            }

            return this;
        }

        @Override
        public float getFloat(String uniform, float defaultValue) {
            ShaderParamsComponent params = params(false);
            if (params == null || uniform == null || isBlank(uniform)) return defaultValue;

            for (int i = 0; i < params.floats.size; i++) {
                ShaderFloatParam param = params.floats.get(i);
                if (param != null && uniform.equals(param.name)) {
                    return param.value;
                }
            }

            return defaultValue;
        }

        @Override
        public boolean hasFloat(String uniform) {
            ShaderParamsComponent params = params(false);
            if (params == null || uniform == null || isBlank(uniform)) return false;

            for (int i = 0; i < params.floats.size; i++) {
                ShaderFloatParam param = params.floats.get(i);
                if (param != null && uniform.equals(param.name)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public ShaderFacade removeFloat(String uniform) {
            if (renderCapabilityWorld() == null) return this;
            requireUniformName(uniform);
            ShaderParamsComponent params = params(false);
            if (params == null) return this;

            for (int i = params.floats.size - 1; i >= 0; i--) {
                ShaderFloatParam param = params.floats.get(i);
                if (param != null && uniform.equals(param.name)) {
                    params.floats.removeIndex(i);
                    markMaterial();
                    break;
                }
            }

            return this;
        }

        @Override
        public ShaderFacade clearFloats() {
            ShaderParamsComponent params = params(false);
            if (params != null && params.floats.size > 0) {
                params.floats.clear();
                markMaterial();
            }
            return this;
        }

        private RenderMaterialComponent mat() {
            World world = renderCapabilityWorld();
            return world != null
                    ? world.getMapper(RenderMaterialComponent.class)
                    .get(handle.entityId) : null;
        }

        private ShaderParamsComponent params(boolean create) {
            World world = renderCapabilityWorld();
            if (world == null) return null;
            ComponentMapper<ShaderParamsComponent> mapper =
                    world.getMapper(ShaderParamsComponent.class);
            return create
                    ? (mapper.has(handle.entityId)
                    ? mapper.get(handle.entityId) : mapper.create(handle.entityId))
                    : mapper.getSafe(handle.entityId, null);
        }

        private World renderCapabilityWorld() {
            World world = handle.world();
            if (world == null) return null;
            return world.getMapper(RenderMaterialComponent.class).has(handle.entityId)
                    ? world : null;
        }

        private static void requireUniformName(String uniform) {
            if (isBlank(uniform)) {
                throw new IllegalArgumentException("Shader uniform name must not be blank.");
            }
        }

        private void markMaterial() {
            World world = renderCapabilityWorld();
            DirtyTrackerSystem d = world != null ? world.getSystem(DirtyTrackerSystem.class) : null;
            if (d != null) d.material(handle.entityId);
        }
    }

    static final class LightFacadeImpl implements LightFacade {
        private final EntityHandle handle;

        LightFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean hasPoint() {
            return has(PointLightComponent.class);
        }

        @Override
        public boolean hasCone() {
            return has(ConeLightComponent.class);
        }

        private boolean has(Class<? extends Component> type) {
            World world = handle.world();
            return world != null && world.getMapper(type).has(handle.entityId);
        }
    }

    static final class AssetsApiImpl implements AssetsAPI {
        private final PixscapeEngine engine;

        AssetsApiImpl(PixscapeEngine engine) {
            this.engine = engine;
        }

        @Override
        public AssetRegionRef region(String name) {
            return new AssetRegionRefImpl(requireByName(name));
        }

        @Override
        public AssetRegionRef region(int assetId) {
            return new AssetRegionRefImpl(requireById(assetId));
        }

        @Override
        public boolean contains(String name) {
            try {
                return resolveByName(name) != null;
            } catch (IllegalStateException ex) {
                return false;
            }
        }

        @Override
        public boolean contains(int assetId) {
            return resolveById(assetId) != null;
        }

        AtlasAssetBinding resolveById(int assetId) {
            if (assetId <= 0) return null;
            AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
            if (atlasService == null) return null;
            return atlasService.resolveBinding(assetId, currentAtlasTag(engine));
        }

        AtlasAssetBinding resolveByName(String name) {
            String normalized = normalizeLookupName(name);
            if (normalized == null) return null;
            AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
            if (atlasService == null) return null;
            String atlasTag = currentAtlasTag(engine);
            TextureAtlas atlas = atlasService.getAtlas(atlasTag);
            if (atlas == null) return null;

            Array<TextureAtlas.AtlasRegion> regions = atlas.getRegions();
            for (int i = 0; i < regions.size; i++) {
                TextureAtlas.AtlasRegion region = regions.get(i);
                int assetId = assetIdFromRegionName(region.name);
                if (assetId <= 0) continue;
                String regionName = normalizedName(region.name);
                if (matchesLookupName(regionName, normalized)) {
                    return atlasService.resolveBinding(assetId, atlasTag);
                }
            }
            return null;
        }

        AtlasAssetBinding requireById(int assetId) {
            AtlasAssetBinding binding = resolveById(assetId);
            if (binding == null) {
                throw new IllegalArgumentException(
                        "Asset #" + assetId + " is not available in current scene atlas. Add it to Runtime Availability before export."
                );
            }
            return binding;
        }

        AtlasAssetBinding requireByName(String name) {
            AtlasAssetBinding binding = resolveByName(name);
            if (binding == null) {
                throw new IllegalArgumentException(
                        "Asset '" + name + "' is not available in current scene atlas. Add it to Runtime Availability before export."
                );
            }
            return binding;
        }
    }

    static final class AssetRegionRefImpl implements AssetRegionRef {
        private final int assetId;
        private final String name;
        private final TextureRegion region;
        private final float width;
        private final float height;

        AssetRegionRefImpl(AtlasAssetBinding binding) {
            this.assetId = binding.assetId();
            this.name = normalizedName(binding.regionGroup());
            this.region = new TextureRegion(binding.firstRegion());
            this.width = binding.metadata().pixelWidth();
            this.height = binding.metadata().pixelHeight();
        }

        @Override
        public int assetId() {
            return assetId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public TextureRegion region() {
            return region;
        }

        @Override
        public float width() {
            return width;
        }

        @Override
        public float height() {
            return height;
        }
    }

    static final class SpritesApiImpl implements SpritesAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;
        private final AssetsApiImpl assets;

        SpritesApiImpl(PixscapeEngine engine, EntitiesAPI entities, AssetsApiImpl assets) {
            this.engine = engine;
            this.entities = entities;
            this.assets = assets;
        }

        @Override
        public SpriteRef spawn(int assetId, float x, float y) {
            return spawn(assets.requireById(assetId), x, y);
        }

        @Override
        public SpriteRef spawn(String name, float x, float y) {
            return spawn(assets.requireByName(name), x, y);
        }

        SpriteRef spawn(AtlasAssetBinding binding, float x, float y) {
            int entityId = createSpriteEntity(
                    engine,
                    binding,
                    currentAtlasTag(engine),
                    x,
                    y);
            return new SpriteRefImpl(entities.ofEntityId(entityId));
        }
    }

    static final class SpriteRefImpl implements SpriteRef {
        private final EntityRef entity;

        SpriteRefImpl(EntityRef entity) {
            this.entity = entity;
        }

        @Override
        public int entityId() {
            return entity.entityId();
        }

        @Override
        public EntityRef entity() {
            return entity;
        }

        @Override
        public TransformFacade transform() {
            return entity.transform();
        }

        @Override
        public SpriteFacade sprite() {
            return entity.sprite();
        }

        @Override
        public ShaderFacade shader() {
            return entity.shader();
        }

        @Override
        public SpriteRef position(float x, float y) {
            transform().setPosition(x, y);
            return this;
        }

        @Override
        public SpriteRef scale(float scale) {
            transform().setScale(scale);
            return this;
        }

        @Override
        public SpriteRef scale(float sx, float sy) {
            transform().setScale(sx, sy);
            return this;
        }

        @Override
        public SpriteRef rotationRad(float radians) {
            transform().setRotationRad(radians);
            return this;
        }

        @Override
        public SpriteRef tint(float r, float g, float b, float a) {
            sprite().setTint(r, g, b, a);
            return this;
        }

        @Override
        public SpriteRef alpha(float alpha) {
            sprite().setAlpha(alpha);
            return this;
        }

        @Override
        public SpriteRef shader(String shaderName) {
            shader().use(shaderName);
            return this;
        }

        @Override
        public void remove() {
            entity.remove();
        }
    }

    static final class AnimationsApiImpl implements AnimationsAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;
        private final AssetsApiImpl assets;
        private final SpritesApiImpl sprites;

        AnimationsApiImpl(
                PixscapeEngine engine,
                EntitiesAPI entities,
                AssetsApiImpl assets,
                SpritesApiImpl sprites) {
            this.engine = engine;
            this.entities = entities;
            this.assets = assets;
            this.sprites = sprites;
        }

        @Override
        public AnimationDefinition definition(int assetId) {
            AnimationDef def = engine.getAnimationRegistry().getByAssetId(assetId);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown Animation asset id: " + assetId + ".");
            }
            return def;
        }

        @Override
        public AnimationDefinition definition(String name) {
            AnimationDef def = engine.getAnimationRegistry().getByName(name);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown or blank Animation asset name: '" + name + "'.");
            }
            return def;
        }

        @Override
        public AnimationRef spawn(int assetId, float x, float y) {
            AnimationDef def = engine.getAnimationRegistry().getByAssetId(assetId);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown Animation asset id: " + assetId + ".");
            }
            AtlasAssetBinding binding = assets.requireById(def.assetId());
            SpriteRef sprite = sprites.spawn(binding, x, y);
            configureAnimationFromDef(engine, sprite.entityId(), def);
            return new AnimationRefImpl(sprite.entity());
        }

        @Override
        public AnimationRef spawn(String name, float x, float y) {
            AnimationDef def = engine.getAnimationRegistry().getByName(name);
            if (def == null) {
                throw new IllegalArgumentException(
                        "Unknown or blank Animation asset name: '" + name + "'.");
            }
            AtlasAssetBinding binding = assets.resolveById(def.assetId());
            if (binding == null) {
                throw new IllegalArgumentException(
                        "Animation '" + name + "' is not available in current scene atlas. Add it to Runtime Availability before export."
                );
            }
            SpriteRef sprite = sprites.spawn(binding, x, y);
            configureAnimationFromDef(engine, sprite.entityId(), def);
            return new AnimationRefImpl(sprite.entity());
        }

        @Override
        public AnimationFacade get(EntityRef entity) {
            if (entity == null) throw new IllegalArgumentException("entity must not be null");
            return entity.animation();
        }
    }

    static final class AnimationRefImpl implements AnimationRef {
        private final EntityRef entity;

        AnimationRefImpl(EntityRef entity) {
            this.entity = entity;
        }

        @Override
        public int entityId() {
            return entity.entityId();
        }

        @Override
        public EntityRef entity() {
            return entity;
        }

        @Override
        public TransformFacade transform() {
            return entity.transform();
        }

        @Override
        public SpriteFacade sprite() {
            return entity.sprite();
        }

        @Override
        public AnimationFacade animation() {
            return entity.animation();
        }

        @Override
        public ShaderFacade shader() {
            return entity.shader();
        }

        @Override
        public AnimationRef play() {
            animation().play();
            return this;
        }

        @Override
        public AnimationRef play(String clip) {
            animation().play(clip);
            return this;
        }

        @Override
        public AnimationRef loop(boolean loop) {
            animation().setLoop(loop);
            return this;
        }

        @Override
        public AnimationRef fps(float fps) {
            animation().setFps(fps);
            return this;
        }

        @Override
        public AnimationRef scale(float scale) {
            transform().setScale(scale);
            return this;
        }

        @Override
        public AnimationRef rotationRad(float radians) {
            transform().setRotationRad(radians);
            return this;
        }

        @Override
        public void remove() {
            entity.remove();
        }
    }

    static final class ParticlesApiImpl implements ParticlesAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;

        ParticlesApiImpl(PixscapeEngine engine, EntitiesAPI entities) {
            this.engine = engine;
            this.entities = entities;
        }

        @Override
        public ParticleRef spawn(String effectPathOrName, float x, float y) {
            int entityId = createParticleEntity(engine, effectPathOrName, x, y, true);
            return new ParticleRefImpl(entities.ofEntityId(entityId));
        }

        @Override
        public ParticleRef oneshot(String effectPathOrName, float x, float y) {
            int entityId = createParticleEntity(engine, effectPathOrName, x, y, false);
            EntityRef ref = entities.ofEntityId(entityId);
            ref.particles().restart();
            return new ParticleRefImpl(ref);
        }
    }

    static final class ParticleRefImpl implements ParticleRef {
        private final EntityRef entity;

        ParticleRefImpl(EntityRef entity) {
            this.entity = entity;
        }

        @Override
        public int entityId() {
            return entity.entityId();
        }

        @Override
        public EntityRef entity() {
            return entity;
        }

        @Override
        public TransformFacade transform() {
            return entity.transform();
        }

        @Override
        public ParticleFacade particles() {
            return entity.particles();
        }

        @Override
        public ParticleRef play() {
            particles().play();
            return this;
        }

        @Override
        public ParticleRef pause() {
            particles().pause();
            return this;
        }

        @Override
        public ParticleRef restart() {
            particles().restart();
            return this;
        }

        @Override
        public ParticleRef stop() {
            particles().stop();
            return this;
        }

        @Override
        public ParticleRef loop(boolean loop) {
            particles().setLooping(loop);
            return this;
        }

        @Override
        public ParticleRef scale(float scale) {
            transform().setScale(scale);
            return this;
        }

        @Override
        public void remove() {
            entity.remove();
        }
    }

    static final class TiledApiImpl implements TiledAPI {
        private final PixscapeEngine engine;
        private final ECSAPI ecs;
        private final EntitiesAPI entities;
        private final EntityReferenceTracker entityReferences;
        private final TiledAnimationsAPI animations;

        TiledApiImpl(PixscapeEngine engine, ECSAPI ecs, EntitiesAPI entities,
                     EntityReferenceTracker entityReferences) {
            this.engine = engine;
            this.ecs = ecs;
            this.entities = entities;
            this.entityReferences = entityReferences;
            this.animations = new TiledAnimationsApiImpl(engine);
        }

        @Override
        public TiledMapRef ofEntityId(int entityId) {
            return new TiledMapRefImpl(engine, ecs, entityReferences.capture(entityId));
        }

        @Override
        public TiledMapRef ofStableId(int stableId) {
            return ofEntityId(entities.entityIdOf(stableId));
        }

        @Override
        public TiledMapRef requireEntityId(int entityId) {
            TiledMapRef ref = ofEntityId(entityId);
            if (!ref.exists())
                throw new IllegalStateException("Tiled Map entity does not exist for entityId=" + entityId);
            return ref;
        }

        @Override
        public TiledMapRef requireStableId(int stableId) {
            TiledMapRef ref = ofStableId(stableId);
            if (!ref.exists())
                throw new IllegalStateException("Tiled Map entity does not exist for stableId=" + stableId);
            return ref;
        }

        @Override
        public TiledAnimationsAPI animations() {
            return animations;
        }
    }

    static final class TiledMapRefImpl implements TiledMapRef {
        private final PixscapeEngine engine;
        private final ECSAPI ecs;
        private final EntityHandle handle;
        private final TiledMapFacade map;
        private final TileEditFacade tiles;
        private final TiledSpatialFacade spatial;
        private final TileAnimationControlFacade tileAnimations;

        TiledMapRefImpl(PixscapeEngine engine, ECSAPI ecs, EntityHandle handle) {
            this.engine = engine;
            this.ecs = ecs;
            this.handle = handle;
            this.map = new TiledMapFacadeImpl(engine, handle);
            this.tiles = new TileEditFacadeImpl(engine, handle);
            this.spatial = new TiledSpatialFacadeImpl(handle);
            this.tileAnimations = new TileAnimationControlFacadeImpl(engine, handle);
        }

        @Override
        public int entityId() {
            return handle.entityId;
        }

        @Override
        public int stableId() {
            return handle.exists()
                    ? engine.getIdentityRegistry().getStableId(handle.entityId)
                    : -1;
        }

        @Override
        public boolean exists() {
            World world = handle.world();
            return world != null
                    && world.getMapper(TiledLayerComponent.class).has(handle.entityId)
                    && world.getMapper(TiledLayerComponent.class)
                    .get(handle.entityId).data != null;
        }

        @Override
        public TiledMapFacade map() {
            return map;
        }

        @Override
        public TileEditFacade tiles() {
            return tiles;
        }

        @Override
        public TiledSpatialFacade spatial() {
            return spatial;
        }

        @Override
        public TileAnimationControlFacade tileAnimations() {
            return tileAnimations;
        }
    }

    static final class TiledMapFacadeImpl implements TiledMapFacade {
        private final PixscapeEngine engine;
        private final EntityHandle handle;

        TiledMapFacadeImpl(PixscapeEngine engine, EntityHandle handle) {
            this.engine = engine;
            this.handle = handle;
        }

        @Override
        public int width() {
            TiledMapLayerData d = data();
            return d != null ? d.mapWidth : 0;
        }

        @Override
        public int height() {
            TiledMapLayerData d = data();
            return d != null ? d.mapHeight : 0;
        }

        @Override
        public int tileWidth() {
            TiledMapLayerData d = data();
            return d != null ? d.tileWidth : 0;
        }

        @Override
        public int tileHeight() {
            TiledMapLayerData d = data();
            return d != null ? d.tileHeight : 0;
        }

        @Override
        public int chunkSize() {
            TiledMapLayerData d = data();
            return d != null ? d.chunkSize : 0;
        }

        @Override
        public int chunksX() {
            TiledMapLayerData d = data();
            return d != null ? d.getChunksX() : 0;
        }

        @Override
        public int chunksY() {
            TiledMapLayerData d = data();
            return d != null ? d.getChunksY() : 0;
        }

        @Override
        public boolean isInside(int x, int y) {
            TiledMapLayerData d = data();
            return d != null && d.isInside(x, y);
        }

        @Override
        public String atlasTag() {
            TiledLayerComponent c = comp();
            return c != null ? c.atlasTag : "";
        }

        @Override
        public TiledMapFacade setAtlasTag(String atlasTag) {
            TiledLayerComponent c = comp();
            if (c == null) return this;
            String normalized = isBlank(atlasTag) ? "main" : atlasTag;
            if (!normalized.equals(c.atlasTag)) {
                c.atlasTag = normalized;
                if (c.data != null) {
                    c.data.markAllChunksContentDirty();
                }
            }
            return this;
        }

        @Override
        public TiledProjection projection() {
            TiledMapLayerData d = data();
            return d != null ? d.projection : null;
        }

        @Override
        public TiledMapFacade setVisible(boolean visible) {
            TiledMapLayerData d = data();
            if (d != null) d.visible = visible;
            return this;
        }

        @Override
        public TiledMapFacade setCollisionEnabled(boolean enabled) {
            TiledMapLayerData d = data();
            if (d == null) return this;
            SceneMetaRuntime scene = engine.getActiveSceneMeta();
            if (enabled && scene != null && !scene.physicsEnabled) {
                throw new IllegalStateException(
                        "Cannot enable Tiled map collisions while scene physics is disabled.");
            }
            if (d.collisionEnabled != enabled) {
                d.collisionEnabled = enabled;
                World world = handle.world();
                DirtyTrackerSystem dirty = world != null
                        ? world.getSystem(DirtyTrackerSystem.class) : null;
                if (dirty != null) dirty.physics(handle.entityId, PhysicsDirtyBits.ALL);
            }
            return this;
        }

        @Override
        public TiledMapFacade setOrigin(float x, float y) {
            TiledMapLayerData d = data();
            if (d == null) return this;
            requireFinite("Tiled map origin", x, y);
            TiledLayerComponent c = comp();
            if (d.originX != x || d.originY != y) {
                d.originX = x;
                d.originY = y;
                for (IntMap.Values<TileChunk> it = d.getChunks(); it.hasNext(); ) d.updateChunkBounds(it.next());
                d.markAllChunksContentDirty();
            }
            if (c != null) {
                c.originX = x;
                c.originY = y;
            }
            return this;
        }

        @Override
        public int worldToTileX(float worldX) {
            TiledMapLayerData d = data();
            return d != null ? d.worldToTileX(worldX) : 0;
        }

        @Override
        public int worldToTileY(float worldY) {
            TiledMapLayerData d = data();
            return d != null ? d.worldToTileY(worldY) : 0;
        }

        @Override
        public int worldToTileX(float worldX, float worldY) {
            TiledMapLayerData d = data();
            return d != null ? d.worldToTileX(worldX, worldY) : 0;
        }

        @Override
        public int worldToTileY(float worldX, float worldY) {
            TiledMapLayerData d = data();
            return d != null ? d.worldToTileY(worldX, worldY) : 0;
        }

        @Override
        public float tileToWorldX(int gx) {
            TiledMapLayerData d = data();
            return d != null ? d.tileToWorldX(gx) : 0f;
        }

        @Override
        public float tileToWorldY(int gy) {
            TiledMapLayerData d = data();
            return d != null ? d.tileToWorldY(gy) : 0f;
        }

        @Override
        public float tileToWorldX(int gx, int gy) {
            TiledMapLayerData d = data();
            return d != null ? d.tileToWorldX(gx, gy) : 0f;
        }

        @Override
        public float tileToWorldY(int gx, int gy) {
            TiledMapLayerData d = data();
            return d != null ? d.tileToWorldY(gx, gy) : 0f;
        }

        @Override
        public TiledMapFacade resize(int width, int height) {
            TiledMapLayerData d = data();
            if (d != null) {
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException(
                            "Tiled map dimensions must be > 0, got "
                                    + width + " x " + height + ".");
                }
                d.rebuildWithNewSize(width, height);
                TiledLayerComponent c = comp();
                if (c != null) {
                    c.mapWidthCells = d.mapWidth;
                    c.mapHeightCells = d.mapHeight;
                }
                TileEditFacadeImpl.syncAllChunkAnimations(engine, d);
            }
            return this;
        }

        private TiledLayerComponent comp() {
            World world = handle.world();
            if (world == null) return null;
            return world.getMapper(TiledLayerComponent.class).getSafe(handle.entityId, null);
        }

        private TiledMapLayerData data() {
            TiledLayerComponent c = comp();
            return c != null ? c.data : null;
        }
    }

    static final class TiledSpatialFacadeImpl implements TiledSpatialFacade {
        private final EntityHandle handle;

        TiledSpatialFacadeImpl(EntityHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean enabled() {
            TiledLayerComponent c = comp();
            TiledMapLayerData d = c != null ? c.data : null;
            return (c != null && c.spatialEnabled) || (d != null && d.spatialEnabled);
        }

        @Override
        public TiledSpatialFacade setEnabled(boolean enabled) {
            TiledLayerComponent c = comp();
            if (c != null) c.spatialEnabled = enabled;
            TiledMapLayerData d = c != null ? c.data : null;
            if (d != null) d.spatialEnabled = enabled;
            return this;
        }

        @Override
        public float defaultAltitude() {
            TiledLayerComponent c = comp();
            TiledMapLayerData d = c != null ? c.data : null;
            return d != null ? d.defaultTileAltitude : (c != null ? c.defaultTileAltitude : 0f);
        }

        @Override
        public float defaultHeight() {
            TiledLayerComponent c = comp();
            TiledMapLayerData d = c != null ? c.data : null;
            return d != null ? d.defaultTileHeight : (c != null ? c.defaultTileHeight : 0f);
        }

        @Override
        public TiledSpatialFacade setDefaultVolume(float altitude, float height) {
            TiledLayerComponent c = comp();
            if (c != null && c.data != null) {
                requireFinite("Tiled default Spatial volume", altitude, height);
                float sanitizedHeight = Math.max(0f, height);
                c.defaultTileAltitude = altitude;
                c.defaultTileHeight = sanitizedHeight;
                c.data.defaultTileAltitude = altitude;
                c.data.defaultTileHeight = sanitizedHeight;
                c.data.markAllChunksContentDirty();
            }
            return this;
        }

        @Override
        public boolean hasTileOverride(int x, int y) {
            TiledMapLayerData d = data();
            return d != null && d.hasTileSpatialOverride(x, y);
        }

        @Override
        public float tileAltitude(int x, int y) {
            TiledMapLayerData d = data();
            return d != null ? d.getTileAltitude(x, y) : 0f;
        }

        @Override
        public float tileHeight(int x, int y) {
            TiledMapLayerData d = data();
            return d != null ? d.getTileHeight(x, y) : 0f;
        }

        @Override
        public TiledSpatialFacade setTileVolume(int x, int y, float altitude, float height) {
            TiledMapLayerData d = data();
            if (d != null && d.isInside(x, y)) {
                requireFinite("Tiled cell Spatial volume", altitude, height);
                d.setTileSpatialOverride(x, y, altitude, Math.max(0f, height), 0);
            }
            return this;
        }

        @Override
        public TiledSpatialFacade clearTileOverride(int x, int y) {
            TiledMapLayerData d = data();
            if (d != null) d.clearTileSpatialOverride(x, y);
            return this;
        }

        private TiledLayerComponent comp() {
            World world = handle.world();
            if (world == null) return null;
            return world.getMapper(TiledLayerComponent.class).getSafe(handle.entityId, null);
        }

        private TiledMapLayerData data() {
            TiledLayerComponent c = comp();
            return c != null ? c.data : null;
        }
    }

    static final class TileEditFacadeImpl implements TileEditFacade {
        private final PixscapeEngine engine;
        private final EntityHandle handle;

        TileEditFacadeImpl(PixscapeEngine engine, EntityHandle handle) {
            this.engine = engine;
            this.handle = handle;
        }

        @Override
        public int get(int x, int y) {
            TiledMapLayerData d = data();
            return d != null ? d.getTile(x, y) : 0;
        }

        @Override
        public byte getFlags(int x, int y) {
            TiledMapLayerData d = data();
            return d != null ? d.getTileTransformFlags(x, y) : TileTransformFlags.NONE;
        }

        @Override
        public TileEditFacade set(int x, int y, int assetId) {
            return set(x, y, assetId, TileTransformFlags.NONE);
        }

        @Override
        public TileEditFacade set(int x, int y, int assetId, byte flags) {
            mutateCell(x, y, assetId, flags);
            return this;
        }

        @Override
        public TileEditFacade set(int x, int y, String animationName) {
            return setAnimated(x, y, animationName);
        }

        @Override
        public TileEditFacade setAnimated(int x, int y, String animationName) {
            int animationId = engine.getAnimatedTileRegistry().idByName(animationName);
            mutateCell(x, y, animationId, TileTransformFlags.NONE);
            return this;
        }

        @Override
        public TileEditFacade clear(int x, int y) {
            mutateCell(x, y, 0, TileTransformFlags.NONE);
            return this;
        }

        @Override
        public TileEditFacade fillRect(int x, int y, int width, int height, int assetId) {
            return fillRect(x, y, width, height, assetId, TileTransformFlags.NONE);
        }

        @Override
        public TileEditFacade fillRect(int x, int y, int width, int height, int assetId, byte flags) {
            int maxY = y + Math.max(0, height);
            int maxX = x + Math.max(0, width);
            for (int gy = y; gy < maxY; gy++) for (int gx = x; gx < maxX; gx++) mutateCell(gx, gy, assetId, flags);
            return this;
        }

        @Override
        public TileEditFacade clearRect(int x, int y, int width, int height) {
            return fillRect(x, y, width, height, 0, TileTransformFlags.NONE);
        }

        @Override
        public TileEditFacade hLine(int x, int y, int length, int assetId) {
            return hLine(x, y, length, assetId, TileTransformFlags.NONE);
        }

        @Override
        public TileEditFacade hLine(int x, int y, int length, int assetId, byte flags) {
            int step = length >= 0 ? 1 : -1;
            for (int i = 0; i != length; i += step) mutateCell(x + i, y, assetId, flags);
            return this;
        }

        @Override
        public TileEditFacade vLine(int x, int y, int length, int assetId) {
            return vLine(x, y, length, assetId, TileTransformFlags.NONE);
        }

        @Override
        public TileEditFacade vLine(int x, int y, int length, int assetId, byte flags) {
            int step = length >= 0 ? 1 : -1;
            for (int i = 0; i != length; i += step) mutateCell(x, y + i, assetId, flags);
            return this;
        }

        @Override
        public TileEditFacade markAllDirty() {
            TiledMapLayerData d = data();
            if (d != null) d.markAllChunksContentDirty();
            return this;
        }

        private void mutateCell(int gx, int gy, int assetId, byte flags) {
            TiledMapLayerData d = data();
            if (d == null || !d.isInside(gx, gy)) return;
            d.setTile(gx, gy, assetId, flags);
            syncCell(engine, d, gx, gy);
        }

        static void syncAllChunkAnimations(PixscapeEngine engine, TiledMapLayerData d) {
            if (d == null) return;
            for (IntMap.Values<TileChunk> it = d.getChunks(); it.hasNext(); ) {
                TileChunk chunk = it.next();
                TileAnimationStateSupport.syncChunk(chunk, engine.getAnimatedTileRegistry());
            }
            d.markAllChunksContentDirty();
        }

        static void syncCell(PixscapeEngine engine, TiledMapLayerData d, int gx, int gy) {
            int cx = gx / d.chunkSize;
            int cy = gy / d.chunkSize;
            TileChunk chunk = d.getChunk(cx, cy);
            if (chunk == null) return;
            int lx = gx - cx * d.chunkSize;
            int ly = gy - cy * d.chunkSize;
            TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, engine.getAnimatedTileRegistry());
        }

        private TiledMapLayerData data() {
            World w = handle.world();
            if (w == null) return null;
            ComponentMapper<TiledLayerComponent> mapper = w.getMapper(TiledLayerComponent.class);
            TiledLayerComponent c = mapper.getSafe(handle.entityId, null);
            return c != null ? c.data : null;
        }
    }

    static final class TiledAnimationsApiImpl implements TiledAnimationsAPI {
        private final PixscapeEngine engine;
        private final TileAnimationDefViewImpl reusableView = new TileAnimationDefViewImpl();

        TiledAnimationsApiImpl(PixscapeEngine engine) {
            this.engine = engine;
        }

        @Override
        public boolean contains(int animatedTileAssetId) {
            return engine.getAnimatedTileRegistry().contains(animatedTileAssetId);
        }

        @Override
        public boolean contains(String name) {
            return engine.getAnimatedTileRegistry().containsName(name);
        }

        @Override
        public int animationId(String name) {
            return engine.getAnimatedTileRegistry().idByName(name);
        }

        @Override
        public TileAnimationDefView get(int animatedTileAssetId) {
            TileAnimationDef def = engine.getAnimatedTileRegistry().get(animatedTileAssetId);
            if (def == null) return null;
            reusableView.bind(def);
            return reusableView;
        }

        @Override
        public TileAnimationDefView get(String name) {
            TileAnimationDef def = engine.getAnimatedTileRegistry().getByName(name);
            if (def == null) return null;
            reusableView.bind(def);
            return reusableView;
        }

        @Override
        public TiledAnimationsAPI put(int animatedTileAssetId, int[] frameAssetIds, int[] frameDurationsMs) {
            engine.getAnimatedTileRegistry().put(animatedTileAssetId, frameAssetIds, frameDurationsMs);
            return this;
        }

        @Override
        public TiledAnimationsAPI remove(int animatedTileAssetId) {
            engine.getAnimatedTileRegistry().remove(animatedTileAssetId);
            return this;
        }

        @Override
        public void clear() {
            engine.getAnimatedTileRegistry().clear();
        }
    }

    static final class TileAnimationDefViewImpl implements TileAnimationDefView {
        private TileAnimationDef def;

        void bind(TileAnimationDef def) {
            this.def = def;
        }

        @Override
        public int id() {
            return def != null ? def.id() : 0;
        }

        @Override
        public int frameCount() {
            return def != null ? def.frameCount() : 0;
        }

        @Override
        public int frameAssetId(int index) {
            return def != null ? def.frameAssetId(index) : 0;
        }

        @Override
        public int frameDurationMs(int index) {
            return def != null ? def.frameDurationMs(index) : 0;
        }
    }

    static final class TileAnimationControlFacadeImpl implements TileAnimationControlFacade {
        private final PixscapeEngine engine;
        private final EntityHandle handle;
        private TileChunk cellChunk;
        private int cellLocalIndex;
        private int cellAssetId;

        TileAnimationControlFacadeImpl(PixscapeEngine engine, EntityHandle handle) {
            this.engine = engine;
            this.handle = handle;
        }

        @Override
        public boolean isAnimated(int x, int y) {
            return resolveCell(x, y)
                    && TileAnimationResolver.isAnimated(cellAssetId, engine.getAnimatedTileRegistry());
        }

        @Override
        public boolean isPlaying(int x, int y) {
            return resolveCell(x, y)
                    && cellChunk.getAnimPlaybackState(cellLocalIndex) == TileAnimationPlayback.PLAYING;
        }

        @Override
        public boolean isPaused(int x, int y) {
            return resolveCell(x, y)
                    && cellChunk.getAnimPlaybackState(cellLocalIndex) == TileAnimationPlayback.PAUSED;
        }

        @Override
        public boolean isFinished(int x, int y) {
            return resolveAnimatedCell(x, y) && cellChunk.isAnimFinished(cellLocalIndex);
        }

        @Override
        public int currentFrame(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return 0;
            int count = TileAnimationResolver.frameCount(cellAssetId, engine.getAnimatedTileRegistry());
            return TileAnimationResolver.clampFrameIndex(cellChunk.getAnimFrameIndex(cellLocalIndex), count);
        }

        @Override
        public int elapsedMs(int x, int y) {
            return resolveAnimatedCell(x, y) ? cellChunk.getAnimFrameElapsedMs(cellLocalIndex) : 0;
        }

        @Override
        public TileAnimationControlFacade play(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            cellChunk.setAnimationPlaybackMode(cellLocalIndex, TileAnimationPlayback.MODE_LOOPING, false);
            cellChunk.setAnimationPlaybackState(cellLocalIndex, TileAnimationPlayback.PLAYING);
            return this;
        }

        @Override
        public TileAnimationControlFacade playOnce(int x, int y) {
            return playOnce(x, y, true);
        }

        @Override
        public TileAnimationControlFacade playOnce(int x, int y, boolean holdLastFrame) {
            if (!resolveAnimatedCell(x, y)) return this;
            int before = TileAnimationResolver.resolveVisualAssetId(
                    cellAssetId,
                    cellChunk.getAnimFrameIndex(cellLocalIndex),
                    engine.getAnimatedTileRegistry()
            );
            cellChunk.setAnimationState(
                    cellLocalIndex,
                    TileAnimationPlayback.PLAYING,
                    TileAnimationPlayback.MODE_PLAY_ONCE,
                    false,
                    holdLastFrame,
                    0,
                    0
            );
            int after = TileAnimationResolver.resolveVisualAssetId(cellAssetId, 0, engine.getAnimatedTileRegistry());
            if (before != after) cellChunk.markLocalDirty(cellLocalIndex);
            return this;
        }

        @Override
        public TileAnimationControlFacade pause(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            if (cellChunk.isAnimFinished(cellLocalIndex)) return this;
            cellChunk.setAnimationPlaybackState(cellLocalIndex, TileAnimationPlayback.PAUSED);
            return this;
        }

        @Override
        public TileAnimationControlFacade stop(int x, int y) {
            if (!resolveCell(x, y)) return this;
            int before = TileAnimationResolver.resolveVisualAssetId(
                    cellAssetId,
                    cellChunk.getAnimFrameIndex(cellLocalIndex),
                    engine.getAnimatedTileRegistry()
            );
            cellChunk.clearAnimationState(cellLocalIndex);
            int after = TileAnimationResolver.resolveVisualAssetId(
                    cellAssetId,
                    cellChunk.getAnimFrameIndex(cellLocalIndex),
                    engine.getAnimatedTileRegistry()
            );
            if (before != after) cellChunk.markLocalDirty(cellLocalIndex);
            return this;
        }

        @Override
        public TileAnimationControlFacade restart(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            int before = TileAnimationResolver.resolveVisualAssetId(
                    cellAssetId,
                    cellChunk.getAnimFrameIndex(cellLocalIndex),
                    engine.getAnimatedTileRegistry()
            );
            cellChunk.setAnimationState(
                    cellLocalIndex,
                    TileAnimationPlayback.PLAYING,
                    cellChunk.getAnimPlaybackMode(cellLocalIndex),
                    false,
                    cellChunk.isAnimHoldLastFrame(cellLocalIndex),
                    0,
                    0
            );
            int after = TileAnimationResolver.resolveVisualAssetId(cellAssetId, 0, engine.getAnimatedTileRegistry());
            if (before != after) cellChunk.markLocalDirty(cellLocalIndex);
            return this;
        }

        @Override
        public TileAnimationControlFacade setFrame(int x, int y, int frameIndex) {
            if (!resolveAnimatedCell(x, y)) return this;
            int count = TileAnimationResolver.frameCount(cellAssetId, engine.getAnimatedTileRegistry());
            int safe = TileAnimationResolver.clampFrameIndex(frameIndex, count);
            int before = TileAnimationResolver.resolveVisualAssetId(
                    cellAssetId,
                    cellChunk.getAnimFrameIndex(cellLocalIndex),
                    engine.getAnimatedTileRegistry()
            );
            cellChunk.setAnimationFrameIndex(cellLocalIndex, safe);
            int after = TileAnimationResolver.resolveVisualAssetId(cellAssetId, safe, engine.getAnimatedTileRegistry());
            if (before != after) cellChunk.markLocalDirty(cellLocalIndex);
            return this;
        }

        @Override
        public TileAnimationControlFacade setElapsedMs(int x, int y, int elapsedMs) {
            if (!resolveAnimatedCell(x, y)) return this;
            cellChunk.setAnimationFrameElapsedMs(cellLocalIndex, Math.max(0, elapsedMs));
            return this;
        }

        private boolean resolveAnimatedCell(int x, int y) {
            return resolveCell(x, y)
                    && TileAnimationResolver.isAnimated(cellAssetId, engine.getAnimatedTileRegistry());
        }

        private boolean resolveCell(int x, int y) {
            TiledMapLayerData d = data();
            if (d == null || !d.isInside(x, y)) return false;
            int cx = x / d.chunkSize;
            int cy = y / d.chunkSize;
            TileChunk chunk = d.getChunk(cx, cy);
            if (chunk == null) return false;
            int lx = x - cx * d.chunkSize;
            int ly = y - cy * d.chunkSize;
            cellChunk = chunk;
            cellLocalIndex = chunk.localIndexFor(lx, ly);
            cellAssetId = chunk.assetIds[cellLocalIndex];
            return true;
        }

        private TiledMapLayerData data() {
            World w = handle.world();
            if (w == null) return null;
            TiledLayerComponent c = w.getMapper(TiledLayerComponent.class)
                    .getSafe(handle.entityId, null);
            return c != null ? c.data : null;
        }

    }

    static final class GameObjectsApiImpl implements GameObjectsAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;

        GameObjectsApiImpl(PixscapeEngine engine, EntitiesAPI entities) {
            this.engine = engine;
            this.entities = entities;
        }

        @Override
        public GameObjectInstance spawn(String name, float x, float y) {
            return engine.spawnGameObject(name, x, y);
        }

        @Override
        public EntityRef root(String name, float x, float y) {
            return spawn(name, x, y).root();
        }

        @Override
        public EntityRef requireRoot(String name, float x, float y) {
            EntityRef root = spawn(name, x, y).root();
            if (!root.exists()) throw new IllegalStateException("GameObject spawn created no root: " + name);
            return root;
        }
    }
}
