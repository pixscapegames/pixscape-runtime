package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.SaveFileFormat;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TagRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;

public final class PixscapeApiImpl implements PixscapeAPI {

    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private final PixscapeEngine engine;
    private final ECSAPI ecs;
    private final EntitiesAPI entities;
    private final TiledAPI tiled;
    private final PrefabsAPI prefabs;
    private final AssetsAPI assets;
    private final SpritesAPI sprites;
    private final AnimationsAPI animations;
    private final ParticlesAPI particles;

    public PixscapeApiImpl(PixscapeEngine engine) {
        this.engine = engine;
        this.ecs = new EcsApiImpl(engine);
        this.entities = new EntitiesApiImpl(engine, ecs);
        this.tiled = new TiledApiImpl(engine, ecs, entities);
        this.assets = new AssetsApiImpl(engine);
        this.sprites = new SpritesApiImpl(engine, entities, assets);
        this.animations = new AnimationsApiImpl(engine, entities, sprites);
        this.particles = new ParticlesApiImpl(engine, entities);
        this.prefabs = new PrefabsApiImpl(engine, entities);
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
    public PrefabsAPI prefabs() {
        return prefabs;
    }

    private static String currentAtlasTag(PixscapeEngine engine) {
        String tag = engine != null ? engine.getCurrentSceneAtlasTag() : null;
        return isBlank(tag) ? "main" : tag;
    }

    private static TextureAtlas.AtlasRegion firstRegion(AtlasRuntimeService atlasService, int assetId, String atlasTag) {
        if (atlasService == null) return null;
        com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> regions = atlasService.resolve(assetId, atlasTag);
        return regions != null && regions.size > 0 ? regions.first() : null;
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
                                          int assetId,
                                          String atlasTag,
                                          float x,
                                          float y,
                                          float width,
                                          float height) {
        World world = requireWorld(engine);
        int e = world.create();

        TransformComponent transform = world.edit(e).create(TransformComponent.class);
        transform.x = x;
        transform.y = y;

        DimensionsComponent dimensions = world.edit(e).create(DimensionsComponent.class);
        dimensions.width = width;
        dimensions.height = height;

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
        assetRef.assetId = assetId;
        assetRef.atlasTag = atlasTag;

        TextureRegionComponent textureRegion = world.edit(e).create(TextureRegionComponent.class);
        RenderMaterialComponent material = world.edit(e).create(RenderMaterialComponent.class);

        resolveSpriteRegion(engine, assetRef, textureRegion, material);
        markSpawnDirty(world, e);
        return e;
    }

    private static void configureDefaultAnimation(PixscapeEngine engine, int entityId, int assetId) {
        World world = requireWorld(engine);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).has(entityId)
                ? world.getMapper(AnimationComponent.class).get(entityId)
                : world.getMapper(AnimationComponent.class).create(entityId);

        // TODO 0.1.4 Studio integration:
        // When animation assets export clip metadata, load exported clips here instead of
        // creating a single default clip across every atlas frame.
        int frameCount = 1;
        AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
        String atlasTag = currentAtlasTag(engine);
        if (atlasService != null) {
            com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> regions = atlasService.resolve(assetId, atlasTag);
            if (regions != null && regions.size > 0) {
                frameCount = regions.size;
            }
        }

        animation.clips.put("default", new AnimationComponent.Clip(0, Math.max(0, frameCount - 1)));
        animation.currentClip = "default";
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
        emitter.effectPath = normalizeEffectPath(effectPathOrName);
        emitter.atlasTag = currentAtlasTag(engine);
        emitter.looping = looping;
        emitter.autoRemoveWhenComplete = !looping;
        emitter.autoStart = true;
        emitter.paused = false;
        emitter.playRequested = true;

        markSpawnDirty(world, e);
        return e;
    }

    private static String normalizeEffectPath(String effectPathOrName) {
        String value = effectPathOrName.trim().replace('\\', '/');
        return value.endsWith(".p") ? value : value + ".p";
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
        AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
        if (atlasService == null) {
            throw new IllegalArgumentException(
                    "Asset #" + assetRef.assetId + " is not available in current scene atlas. Add it to Runtime Availability before export."
            );
        }

        AtlasRuntimeService.CachedRegion cached = atlasService.resolveCached(assetRef.assetId, assetRef.atlasTag);
        if (cached == null) {
            throw new IllegalArgumentException(
                    "Asset #" + assetRef.assetId + " is not available in current scene atlas. Add it to Runtime Availability before export."
            );
        }

        textureRegion.u1 = cached.u1;
        textureRegion.v1 = cached.v1;
        textureRegion.u2 = cached.u2;
        textureRegion.v2 = cached.v2;
        textureRegion.pixW = cached.pixW;
        textureRegion.pixH = cached.pixH;
        textureRegion.valid = true;
        material.textureHandle = cached.textureHandle;
        material.debugAtlasTag = assetRef.atlasTag;
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

    static final class ResolvedAsset {
        final int assetId;
        final String name;
        final String atlasTag;
        final AtlasRuntimeService.CachedRegion cached;
        final TextureRegion region;

        ResolvedAsset(int assetId,
                      String name,
                      String atlasTag,
                      AtlasRuntimeService.CachedRegion cached,
                      TextureRegion region) {
            this.assetId = assetId;
            this.name = name;
            this.atlasTag = atlasTag;
            this.cached = cached;
            this.region = region;
        }
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

        EntitiesApiImpl(PixscapeEngine engine, ECSAPI ecs) {
            this.engine = engine;
            this.ecs = ecs;
        }

        @Override
        public EntityRef ofEntityId(int entityId) {
            return new EntityRefImpl(engine, ecs, entityId);
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
            destroyEntityId(ref.entityId());
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
        private final int entityId;
        private TransformFacade transform;
        private SpriteFacade sprite;
        private AnimationFacade animation;
        private ParticleFacade particles;
        private ShaderFacade shader;
        private LightFacade light;

        EntityRefImpl(PixscapeEngine engine, ECSAPI ecs, int entityId) {
            this.engine = engine;
            this.ecs = ecs;
            this.entityId = entityId;
        }

        @Override
        public int entityId() {
            return entityId;
        }

        @Override
        public long stableId() {
            return engine.getIdentityRegistry().getStableId(entityId);
        }

        @Override
        public boolean exists() {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId);
        }

        @Override
        public TransformFacade transform() {
            if (transform == null) transform = new TransformFacadeImpl(engine, entityId);
            return transform;
        }

        @Override
        public SpriteFacade sprite() {
            if (sprite == null) sprite = new SpriteFacadeImpl(engine, entityId);
            return sprite;
        }

        @Override
        public AnimationFacade animation() {
            if (animation == null) animation = new AnimationFacadeImpl(engine, entityId);
            return animation;
        }

        @Override
        public ParticleFacade particles() {
            if (particles == null) particles = new ParticleFacadeImpl(engine, entityId);
            return particles;
        }

        @Override
        public ShaderFacade shader() {
            if (shader == null) shader = new ShaderFacadeImpl(engine, entityId);
            return shader;
        }

        @Override
        public LightFacade light() {
            if (light == null) light = new LightFacadeImpl(engine, entityId);
            return light;
        }

        @Override
        public ECSAPI ecs() {
            return ecs;
        }

        @Override
        public void remove() {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return;
            world.delete(entityId);
        }
    }

    static final class TransformFacadeImpl implements TransformFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        TransformFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
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
            TransformComponent t = t(true);
            if (t != null && t.x != x) {
                t.x = x;
                markGeometry(GeometryDirty.POSITION);
            }
            return this;
        }

        @Override
        public TransformFacade setY(float y) {
            TransformComponent t = t(true);
            if (t != null && t.y != y) {
                t.y = y;
                markGeometry(GeometryDirty.POSITION);
            }
            return this;
        }

        @Override
        public TransformFacade moveBy(float dx, float dy) {
            if (dx != 0f || dy != 0f) {
                TransformComponent t = t(true);
                if (t != null) {
                    t.x += dx;
                    t.y += dy;
                    markGeometry(GeometryDirty.POSITION);
                }
            }
            return this;
        }

        @Override
        public TransformFacade setRotationRad(float radians) {
            TransformComponent t = t(true);
            if (t != null && t.rotationRad != radians) {
                t.rotationRad = radians;
                markGeometry(GeometryDirty.ROTATION);
            }
            return this;
        }

        @Override
        public TransformFacade rotateByRad(float radians) {
            if (radians != 0f) {
                TransformComponent t = t(true);
                if (t != null) {
                    t.rotationRad += radians;
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
            TransformComponent t = t(true);
            if (t != null && t.scaleX != sx) {
                t.scaleX = sx;
                markGeometry(GeometryDirty.SCALE);
            }
            return this;
        }

        @Override
        public TransformFacade setScaleY(float sy) {
            TransformComponent t = t(true);
            if (t != null && t.scaleY != sy) {
                t.scaleY = sy;
                markGeometry(GeometryDirty.SCALE);
            }
            return this;
        }

        @Override
        public TransformFacade setOrigin(float ox, float oy) {
            TransformComponent t = t(true);
            if (t != null && (t.originX != ox || t.originY != oy)) {
                t.originX = ox;
                t.originY = oy;
                markGeometry(GeometryDirty.ORIGIN);
            }
            return this;
        }

        private TransformComponent t(boolean create) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<TransformComponent> mapper = world.getMapper(TransformComponent.class);
            if (create) return mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
            return mapper.getSafe(entityId, null);
        }

        private void markGeometry(int subMask) {
            DirtyTrackerSystem dirty = engine.getWorld() != null ? engine.getWorld().getSystem(DirtyTrackerSystem.class) : null;
            if (dirty != null) dirty.geometry(entityId, subMask);
        }
    }

    static final class SpriteFacadeImpl implements SpriteFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        SpriteFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
        }

        @Override
        public int assetId() {
            AssetRefComponent c = src(false);
            return c != null ? c.assetId : -1;
        }

        @Override
        public SpriteFacade setAssetId(int assetId) {
            AssetRefComponent src = src(true);
            if (src == null) return this;
            if (src.assetId != assetId) {
                src.assetId = assetId;
                resolveRegion(src);
                markMaterial();
            }
            return this;
        }

        @Override
        public SpriteFacade setAsset(int assetId, String atlasTag) {
            AssetRefComponent src = src(true);
            if (src == null) return this;
            boolean changed = false;
            if (src.assetId != assetId) {
                src.assetId = assetId;
                changed = true;
            }
            String normalizedTag = isBlank(atlasTag) ? "main" : atlasTag;
            if (!normalizedTag.equals(src.atlasTag)) {
                src.atlasTag = normalizedTag;
                changed = true;
            }
            if (changed) {
                resolveRegion(src);
                markMaterial();
            }
            return this;
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
            c.rgba = Color.rgba8888(clamp01(r), clamp01(g), clamp01(b), clamp01(a));
            markColor();
            return this;
        }

        @Override
        public SpriteFacade setAlpha(float alpha) {
            TintComponent c = tint(true);
            if (c == null) return this;
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
            if (d.width != width || d.height != height) {
                d.width = width;
                d.height = height;
                markGeometry(GeometryDirty.SIZE);
            }
            return this;
        }

        private void resolveRegion(AssetRefComponent src) {
            World world = engine.getWorld();
            if (world == null) return;
            TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class).has(entityId)
                    ? world.getMapper(TextureRegionComponent.class).get(entityId)
                    : world.getMapper(TextureRegionComponent.class).create(entityId);
            RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class).has(entityId)
                    ? world.getMapper(RenderMaterialComponent.class).get(entityId)
                    : world.getMapper(RenderMaterialComponent.class).create(entityId);

            AtlasRuntimeService atlas = engine.getAtlasRuntimeService();
            if (atlas == null) {
                tr.valid = false;
                mat.textureHandle = 0;
                return;
            }
            AtlasRuntimeService.CachedRegion cached = atlas.resolveCached(src.assetId, src.atlasTag);
            if (cached == null) {
                tr.valid = false;
                mat.textureHandle = 0;
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
            mat.debugAtlasTag = src.atlasTag;
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
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            if (create) return mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
            return mapper.getSafe(entityId, null);
        }

        private void markGeometry(int sub) {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.geometry(entityId, sub);
        }

        private void markMaterial() {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.material(entityId);
        }

        private void markColor() {
            DirtyTrackerSystem d = dirty();
            if (d != null) d.color(entityId);
        }

        private DirtyTrackerSystem dirty() {
            World w = engine.getWorld();
            return w != null ? w.getSystem(DirtyTrackerSystem.class) : null;
        }
    }

    static final class AnimationFacadeImpl implements AnimationFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        AnimationFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
        }

        @Override
        public boolean exists() {
            return anim(false) != null;
        }

        @Override
        public AnimationFacade play() {
            AnimationComponent a = anim(true);
            if (a != null) a.playing = true;
            return this;
        }

        @Override
        public AnimationFacade pause() {
            AnimationComponent a = anim(false);
            if (a != null) a.playing = false;
            return this;
        }

        @Override
        public AnimationFacade stop() {
            AnimationComponent a = anim(false);
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
            AnimationComponent a = anim(false);
            if (a != null) {
                a.stateTime = 0f;
                a.frame = -1;
                a.playing = true;
                markMaterial();
            }
            return this;
        }

        @Override
        public AnimationFacade play(String clipName) {
            setClip(clipName);
            return play();
        }

        @Override
        public AnimationFacade setClip(String clipName) {
            AnimationComponent a = anim(true);
            if (a != null) {
                a.currentClip = clipName != null ? clipName : "";
                a.frame = -1;
                a.stateTime = 0f;
                markMaterial();
            }
            return this;
        }

        @Override
        public AnimationFacade setLoop(boolean loop) {
            AnimationComponent a = anim(true);
            if (a != null) a.loop = loop;
            return this;
        }

        @Override
        public AnimationFacade setFps(float fps) {
            AnimationComponent a = anim(true);
            if (a != null) a.fps = fps;
            return this;
        }

        @Override
        public AnimationFacade setStateTime(float stateTime) {
            AnimationComponent a = anim(true);
            if (a != null) {
                a.stateTime = Math.max(0f, stateTime);
                a.frame = -1;
                markMaterial();
            }
            return this;
        }

        @Override
        public boolean isPlaying() {
            AnimationComponent a = anim(false);
            return a != null && a.playing;
        }

        @Override
        public boolean isLooping() {
            AnimationComponent a = anim(false);
            return a != null && a.loop;
        }

        @Override
        public float fps() {
            AnimationComponent a = anim(false);
            return a != null ? a.fps : 0f;
        }

        private AnimationComponent anim(boolean create) {
            return comp(AnimationComponent.class, create);
        }

        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }

        private void markMaterial() {
            DirtyTrackerSystem d = engine.getWorld() != null ? engine.getWorld().getSystem(DirtyTrackerSystem.class) : null;
            if (d != null) d.material(entityId);
        }
    }

    static final class ParticleFacadeImpl implements ParticleFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        ParticleFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
        }

        @Override
        public boolean exists() {
            return emitter(false) != null;
        }

        @Override
        public ParticleFacade setEffect(String effectPath, String atlasTag) {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) {
                c.effectPath = effectPath != null ? effectPath : "";
                c.atlasTag = atlasTag != null ? atlasTag : "";
            }
            return this;
        }

        @Override
        public ParticleFacade setLocalSpace(boolean localSpace) {
            ParticleEmitterComponent c = emitter(true);
            if (c != null) c.localSpace = localSpace;
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
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<ParticleEmitterComponent> mapper = world.getMapper(ParticleEmitterComponent.class);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }
    }

    static final class ShaderFacadeImpl implements ShaderFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        ShaderFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
        }

        @Override
        public String shader() {
            RenderMaterialComponent m = mat(false);
            if (m == null) return null;
            return ShaderRegistry.getName(m.shaderIdx);
        }

        @Override
        public ShaderFacade use(String shaderName) {
            int shaderIdx = ShaderRegistry.indexOf(shaderName);
            if (shaderIdx < 0) throw new IllegalArgumentException("Unknown shader: " + shaderName);
            RenderMaterialComponent m = mat(true);
            if (m != null && m.shaderIdx != shaderIdx) {
                m.shaderIdx = shaderIdx;
                markMaterial();
            }
            return this;
        }

        @Override
        public ShaderFacade clear() {
            RenderMaterialComponent m = mat(true);
            if (m != null && m.shaderIdx != 0) {
                m.shaderIdx = 0;
                markMaterial();
            }
            return clearFloats();
        }

        @Override
        public ShaderFacade setFloat(String uniform, float value) {
            if (uniform == null || isBlank(uniform)) return this;

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
            ShaderParamsComponent params = params(false);
            if (params == null || uniform == null || isBlank(uniform)) return this;

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

        private RenderMaterialComponent mat(boolean create) {
            return comp(RenderMaterialComponent.class, create);
        }

        private ShaderParamsComponent params(boolean create) {
            return comp(ShaderParamsComponent.class, create);
        }

        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }

        private void markMaterial() {
            DirtyTrackerSystem d = engine.getWorld() != null ? engine.getWorld().getSystem(DirtyTrackerSystem.class) : null;
            if (d != null) d.material(entityId);
        }
    }

    static final class LightFacadeImpl implements LightFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        LightFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
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
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId) && world.getMapper(type).has(entityId);
        }
    }

    static final class AssetsApiImpl implements AssetsAPI {
        private final PixscapeEngine engine;

        AssetsApiImpl(PixscapeEngine engine) {
            this.engine = engine;
        }

        @Override
        public AssetRegionRef region(String name) {
            ResolvedAsset asset = resolveByName(name);
            if (asset == null) {
                throw new IllegalArgumentException(
                        "Asset '" + name + "' is not available in current scene atlas. Add it to Runtime Availability before export."
                );
            }
            return new AssetRegionRefImpl(asset);
        }

        @Override
        public AssetRegionRef region(int assetId) {
            ResolvedAsset asset = resolveById(assetId);
            if (asset == null) {
                throw new IllegalArgumentException(
                        "Asset #" + assetId + " is not available in current scene atlas. Add it to Runtime Availability before export."
                );
            }
            return new AssetRegionRefImpl(asset);
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
            try {
                return resolveById(assetId) != null;
            } catch (IllegalStateException ex) {
                return false;
            }
        }

        ResolvedAsset resolveById(int assetId) {
            if (assetId < 0) return null;
            AtlasRuntimeService atlasService = engine.getAtlasRuntimeService();
            if (atlasService == null) return null;
            String atlasTag = currentAtlasTag(engine);
            AtlasRuntimeService.CachedRegion cached = atlasService.resolveCached(assetId, atlasTag);
            if (cached == null) return null;
            TextureAtlas.AtlasRegion region = firstRegion(atlasService, assetId, atlasTag);
            if (region == null) {
                throw new IllegalStateException(
                        "Asset '#" + assetId + "' is resolved in the current scene atlas but no TextureRegion could be created."
                );
            }
            String name = region != null ? normalizedName(region.name) : cached.regionName;
            return new ResolvedAsset(assetId, name, atlasTag, cached, region);
        }

        ResolvedAsset resolveByName(String name) {
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
                if (assetId < 0) continue;
                String regionName = normalizedName(region.name);
                if (matchesLookupName(regionName, normalized)) {
                    AtlasRuntimeService.CachedRegion cached = atlasService.resolveCached(assetId, atlasTag);
                    if (cached == null) continue;
                    if (region == null) {
                        throw new IllegalStateException(
                                "Asset '" + name + "' is resolved in the current scene atlas but no TextureRegion could be created."
                        );
                    }
                    return new ResolvedAsset(assetId, regionName, atlasTag, cached, region);
                }
            }
            return null;
        }
    }

    static final class AssetRegionRefImpl implements AssetRegionRef {
        private final ResolvedAsset asset;

        AssetRegionRefImpl(ResolvedAsset asset) {
            this.asset = asset;
        }

        @Override
        public int assetId() {
            return asset.assetId;
        }

        @Override
        public String name() {
            return asset.name;
        }

        @Override
        public TextureRegion region() {
            return asset.region;
        }

        @Override
        public float width() {
            return asset.cached.pixW;
        }

        @Override
        public float height() {
            return asset.cached.pixH;
        }
    }

    static final class SpritesApiImpl implements SpritesAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;
        private final AssetsAPI assets;

        SpritesApiImpl(PixscapeEngine engine, EntitiesAPI entities, AssetsAPI assets) {
            this.engine = engine;
            this.entities = entities;
            this.assets = assets;
        }

        @Override
        public SpriteRef spawn(int assetId, float x, float y) {
            AssetRegionRef region = assets.region(assetId);
            int entityId = createSpriteEntity(engine, assetId, currentAtlasTag(engine), x, y, region.width(), region.height());
            return new SpriteRefImpl(entities.ofEntityId(entityId));
        }

        @Override
        public SpriteRef spawn(String name, float x, float y) {
            AssetRegionRef region = assets.region(name);
            int entityId = createSpriteEntity(engine, region.assetId(), currentAtlasTag(engine), x, y, region.width(), region.height());
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
        private final SpritesAPI sprites;

        AnimationsApiImpl(PixscapeEngine engine, EntitiesAPI entities, SpritesAPI sprites) {
            this.engine = engine;
            this.entities = entities;
            this.sprites = sprites;
        }

        @Override
        public AnimationRef spawn(int assetId, float x, float y) {
            SpriteRef sprite = sprites.spawn(assetId, x, y);
            configureDefaultAnimation(engine, sprite.entityId(), assetId);
            return new AnimationRefImpl(sprite.entity());
        }

        @Override
        public AnimationRef spawn(String name, float x, float y) {
            SpriteRef sprite = sprites.spawn(name, x, y);
            configureDefaultAnimation(engine, sprite.entityId(), sprite.sprite().assetId());
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
        private final TiledAnimationsAPI animations;

        TiledApiImpl(PixscapeEngine engine, ECSAPI ecs, EntitiesAPI entities) {
            this.engine = engine;
            this.ecs = ecs;
            this.entities = entities;
            this.animations = new TiledAnimationsApiImpl(engine);
        }

        @Override
        public TiledLayerRef ofEntityId(int entityId) {
            return new TiledLayerRefImpl(engine, ecs, entityId);
        }

        @Override
        public TiledLayerRef ofStableId(int stableId) {
            return ofEntityId(entities.entityIdOf(stableId));
        }

        @Override
        public TiledLayerRef requireEntityId(int entityId) {
            TiledLayerRef ref = ofEntityId(entityId);
            if (!ref.exists())
                throw new IllegalStateException("Tiled layer entity does not exist for entityId=" + entityId);
            return ref;
        }

        @Override
        public TiledLayerRef requireStableId(int stableId) {
            TiledLayerRef ref = ofStableId(stableId);
            if (!ref.exists())
                throw new IllegalStateException("Tiled layer entity does not exist for stableId=" + stableId);
            return ref;
        }

        @Override
        public TiledAnimationsAPI animations() {
            return animations;
        }
    }

    static final class TiledLayerRefImpl implements TiledLayerRef {
        private final PixscapeEngine engine;
        private final ECSAPI ecs;
        private final int entityId;
        private final TiledMapFacade map;
        private final TileEditFacade tiles;
        private final TileAnimationControlFacade tileAnimations;

        TiledLayerRefImpl(PixscapeEngine engine, ECSAPI ecs, int entityId) {
            this.engine = engine;
            this.ecs = ecs;
            this.entityId = entityId;
            this.map = new TiledMapFacadeImpl(engine, entityId);
            this.tiles = new TileEditFacadeImpl(engine, entityId);
            this.tileAnimations = new TileAnimationControlFacadeImpl(engine, entityId);
        }

        @Override
        public int entityId() {
            return entityId;
        }

        @Override
        public int stableId() {
            return engine.getIdentityRegistry().getStableId(entityId);
        }

        @Override
        public boolean exists() {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId)
                    && world.getMapper(TiledLayerComponent.class).has(entityId)
                    && world.getMapper(TiledLayerComponent.class).get(entityId).data != null;
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
        public TileAnimationControlFacade tileAnimations() {
            return tileAnimations;
        }
    }

    static final class TiledMapFacadeImpl implements TiledMapFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        TiledMapFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
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
        public String atlasTag() {
            TiledLayerComponent c = comp(false);
            return c != null ? c.atlasTag : "";
        }

        @Override
        public TiledMapFacade setAtlasTag(String atlasTag) {
            TiledLayerComponent c = comp(true);
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
        public Object projection() {
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
            if (d != null) d.collisionEnabled = enabled;
            return this;
        }

        @Override
        public TiledMapFacade setOrigin(float x, float y) {
            TiledMapLayerData d = data();
            TiledLayerComponent c = comp(false);
            if (d != null && (d.originX != x || d.originY != y)) {
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
                d.rebuildWithNewSize(width, height);
                TiledLayerComponent c = comp(false);
                if (c != null) {
                    c.mapWidthCells = d.mapWidth;
                    c.mapHeightCells = d.mapHeight;
                }
                TileEditFacadeImpl.syncAllChunkAnimations(engine, d);
            }
            return this;
        }

        private TiledLayerComponent comp(boolean create) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<TiledLayerComponent> mapper = world.getMapper(TiledLayerComponent.class);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }

        private TiledMapLayerData data() {
            TiledLayerComponent c = comp(false);
            return c != null ? c.data : null;
        }
    }

    static final class TileEditFacadeImpl implements TileEditFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        TileEditFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
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
            World w = engine.getWorld();
            if (w == null || entityId < 0 || !w.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<TiledLayerComponent> mapper = w.getMapper(TiledLayerComponent.class);
            TiledLayerComponent c = mapper.getSafe(entityId, null);
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
        public TileAnimationDefView get(int animatedTileAssetId) {
            TileAnimationDef def = engine.getAnimatedTileRegistry().get(animatedTileAssetId);
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
        private final int entityId;
        private TileChunk cellChunk;
        private int cellLocalIndex;
        private int cellAssetId;

        TileAnimationControlFacadeImpl(PixscapeEngine engine, int entityId) {
            this.engine = engine;
            this.entityId = entityId;
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
        public TileAnimationControlFacade play(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            cellChunk.setAnimationPlaybackState(cellLocalIndex, TileAnimationPlayback.PLAYING);
            return this;
        }

        @Override
        public TileAnimationControlFacade pause(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
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
            cellChunk.setAnimationState(cellLocalIndex, TileAnimationPlayback.PLAYING, 0, 0);
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
            World w = engine.getWorld();
            if (w == null || entityId < 0 || !w.getEntityManager().isActive(entityId)) return null;
            TiledLayerComponent c = w.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
            return c != null ? c.data : null;
        }

    }

    static final class PrefabsApiImpl implements PrefabsAPI {
        private final PixscapeEngine engine;
        private final EntitiesAPI entities;

        PrefabsApiImpl(PixscapeEngine engine, EntitiesAPI entities) {
            this.engine = engine;
            this.entities = entities;
        }

        @Override
        public SpawnResult spawn(String name, float x, float y) {
            return engine.spawnPrefab(name, x, y);
        }

        @Override
        public SpawnResult spawnFragment(SaveFileFormat fragment, float x, float y) {
            return engine.spawnPrefabFragment(fragment, x, y);
        }

        @Override
        public EntityRef first(String name, float x, float y) {
            SpawnResult result = spawn(name, x, y);
            if (result == null || result.createdEntityIds() == null || result.createdEntityIds().isEmpty()) {
                return entities.ofEntityId(-1);
            }
            return entities.ofEntityId(result.createdEntityIds().get(0));
        }

        @Override
        public EntityRef requireFirst(String name, float x, float y) {
            SpawnResult result = spawn(name, x, y);
            if (result == null || result.createdEntityIds() == null || result.createdEntityIds().isEmpty()) {
                throw new IllegalStateException("Prefab spawn created no entities: " + name);
            }
            return entities.ofEntityId(result.createdEntityIds().get(0));
        }
    }
}
