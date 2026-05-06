package games.pixscape.runtime.api;
import com.badlogic.gdx.utils.IntMap;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.SaveFileFormat;
import com.badlogic.gdx.graphics.Color;
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
        return s == null || s.trim().isEmpty();
    }

    private final PixscapeEngine engine;
    private final ECSAPI ecs;
    private final EntitiesAPI entities;
    private final TiledAPI tiled;
    private final PrefabsAPI prefabs;

    public PixscapeApiImpl(PixscapeEngine engine) {
        this.engine = engine;
        this.ecs = new EcsApiImpl(engine);
        this.entities = new EntitiesApiImpl(engine, ecs);
        this.tiled = new TiledApiImpl(engine, ecs, entities);
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

    public PrefabsAPI prefabs() { return prefabs; }

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
        public EntityRef ofStableId(long stableId) {
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
        public EntityRef requireStableId(long stableId) {
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
        public int entityIdOf(long stableId) {
            return engine.findEntityByStableId(stableId);
        }

        @Override
        public int findEntityId(long stableId, int defaultValue) {
            int entityId = entityIdOf(stableId);
            return entityId >= 0 ? entityId : defaultValue;
        }

        @Override
        public long stableIdOf(int entityId) {
            return engine.getIdentityRegistry().getStableId(entityId);
        }

        @Override
        public long ensureStableId(int entityId) {
            return engine.getIdentityRegistry().ensureStableId(entityId);
        }

        @Override
        public boolean existsEntityId(int entityId) {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId);
        }

        @Override
        public boolean existsStableId(long stableId) {
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
        public void destroyStableId(long stableId) {
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

        @Override public int entityId() { return entityId; }
        @Override public long stableId() { return engine.getIdentityRegistry().getStableId(entityId); }
        @Override public boolean exists() {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId);
        }

        @Override public TransformFacade transform() { if (transform == null) transform = new TransformFacadeImpl(engine, entityId); return transform; }
        @Override public SpriteFacade sprite() { if (sprite == null) sprite = new SpriteFacadeImpl(engine, entityId); return sprite; }
        @Override public AnimationFacade animation() { if (animation == null) animation = new AnimationFacadeImpl(engine, entityId); return animation; }
        @Override public ParticleFacade particles() { if (particles == null) particles = new ParticleFacadeImpl(engine, entityId); return particles; }
        @Override public ShaderFacade shader() { if (shader == null) shader = new ShaderFacadeImpl(engine, entityId); return shader; }
        @Override public LightFacade light() { if (light == null) light = new LightFacadeImpl(engine, entityId); return light; }
        @Override public ECSAPI ecs() { return ecs; }
    }

    static final class TransformFacadeImpl implements TransformFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        TransformFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public float x() { TransformComponent t = t(false); return t != null ? t.x : 0f; }
        @Override public float y() { TransformComponent t = t(false); return t != null ? t.y : 0f; }
        @Override public float rotationRad() { TransformComponent t = t(false); return t != null ? t.rotationRad : 0f; }
        @Override public float scaleX() { TransformComponent t = t(false); return t != null ? t.scaleX : 1f; }
        @Override public float scaleY() { TransformComponent t = t(false); return t != null ? t.scaleY : 1f; }

        @Override public TransformFacade setPosition(float x, float y) {
            TransformComponent t = t(true); if (t == null) return this;
            if (t.x != x || t.y != y) { t.x = x; t.y = y; markGeometry(GeometryDirty.POSITION); }
            return this;
        }
        @Override public TransformFacade setX(float x) { TransformComponent t = t(true); if (t != null && t.x != x) { t.x = x; markGeometry(GeometryDirty.POSITION);} return this; }
        @Override public TransformFacade setY(float y) { TransformComponent t = t(true); if (t != null && t.y != y) { t.y = y; markGeometry(GeometryDirty.POSITION);} return this; }
        @Override public TransformFacade moveBy(float dx, float dy) { if (dx != 0f || dy != 0f) { TransformComponent t = t(true); if (t != null) { t.x += dx; t.y += dy; markGeometry(GeometryDirty.POSITION);} } return this; }
        @Override public TransformFacade setRotationRad(float radians) { TransformComponent t = t(true); if (t != null && t.rotationRad != radians) { t.rotationRad = radians; markGeometry(GeometryDirty.ROTATION);} return this; }
        @Override public TransformFacade rotateByRad(float radians) { if (radians != 0f) { TransformComponent t = t(true); if (t != null) { t.rotationRad += radians; markGeometry(GeometryDirty.ROTATION);} } return this; }
        @Override public TransformFacade setScale(float uniform) { return setScale(uniform, uniform); }
        @Override public TransformFacade setScale(float sx, float sy) { TransformComponent t = t(true); if (t != null && (t.scaleX != sx || t.scaleY != sy)) { t.scaleX = sx; t.scaleY = sy; markGeometry(GeometryDirty.SCALE);} return this; }
        @Override public TransformFacade setScaleX(float sx) { TransformComponent t = t(true); if (t != null && t.scaleX != sx) { t.scaleX = sx; markGeometry(GeometryDirty.SCALE);} return this; }
        @Override public TransformFacade setScaleY(float sy) { TransformComponent t = t(true); if (t != null && t.scaleY != sy) { t.scaleY = sy; markGeometry(GeometryDirty.SCALE);} return this; }
        @Override public TransformFacade setOrigin(float ox, float oy) { TransformComponent t = t(true); if (t != null && (t.originX != ox || t.originY != oy)) { t.originX = ox; t.originY = oy; markGeometry(GeometryDirty.ORIGIN);} return this; }

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

        SpriteFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public int assetId() { AssetRefComponent c = src(false); return c != null ? c.assetId : -1; }

        @Override public SpriteFacade setAssetId(int assetId) {
            AssetRefComponent src = src(true);
            if (src == null) return this;
            if (src.assetId != assetId) {
                src.assetId = assetId;
                resolveRegion(src);
                markMaterial();
            }
            return this;
        }

        @Override public SpriteFacade setAsset(int assetId, String atlasTag) {
            AssetRefComponent src = src(true);
            if (src == null) return this;
            boolean changed = false;
            if (src.assetId != assetId) { src.assetId = assetId; changed = true; }
            String normalizedTag = atlasTag == null || isBlank(atlasTag) ? "main" : atlasTag;
            if (!normalizedTag.equals(src.atlasTag)) { src.atlasTag = normalizedTag; changed = true; }
            if (changed) {
                resolveRegion(src);
                markMaterial();
            }
            return this;
        }

        @Override public SpriteFacade setVisible(boolean visible) {
            VisibilityComponent c = vis(true);
            if (c != null) c.visible = visible;
            return this;
        }

        @Override public SpriteFacade setTint(float r, float g, float b, float a) {
            TintComponent c = tint(true);
            if (c == null) return this;
            c.rgba = Color.rgba8888(clamp01(r), clamp01(g), clamp01(b), clamp01(a));
            markColor();
            return this;
        }

        @Override public SpriteFacade setAlpha(float alpha) {
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

        @Override public SpriteFacade setSize(float width, float height) {
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

        private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
        private AssetRefComponent src(boolean create) { return comp(AssetRefComponent.class, create); }
        private VisibilityComponent vis(boolean create) { return comp(VisibilityComponent.class, create); }
        private TintComponent tint(boolean create) { return comp(TintComponent.class, create); }
        private DimensionsComponent dim(boolean create) { return comp(DimensionsComponent.class, create); }

        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = engine.getWorld();
            if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            if (create) return mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
            return mapper.getSafe(entityId, null);
        }

        private void markGeometry(int sub) { DirtyTrackerSystem d = dirty(); if (d != null) d.geometry(entityId, sub); }
        private void markMaterial() { DirtyTrackerSystem d = dirty(); if (d != null) d.material(entityId); }
        private void markColor() { DirtyTrackerSystem d = dirty(); if (d != null) d.color(entityId); }
        private DirtyTrackerSystem dirty() { World w = engine.getWorld(); return w != null ? w.getSystem(DirtyTrackerSystem.class) : null; }
    }

    static final class AnimationFacadeImpl implements AnimationFacade {
        private final PixscapeEngine engine; private final int entityId;
        AnimationFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }
        @Override public boolean exists() { return anim(false) != null; }
        @Override public AnimationFacade play() { AnimationComponent a = anim(true); if (a != null) a.playing = true; return this; }
        @Override public AnimationFacade pause() { AnimationComponent a = anim(false); if (a != null) a.playing = false; return this; }
        @Override public AnimationFacade stop() { AnimationComponent a = anim(false); if (a != null) { a.playing = false; a.stateTime = 0f; a.frame = -1; markMaterial(); } return this; }
        @Override public AnimationFacade restart() { AnimationComponent a = anim(false); if (a != null) { a.stateTime = 0f; a.frame = -1; a.playing = true; markMaterial(); } return this; }
        @Override public AnimationFacade play(String clipName) { setClip(clipName); return play(); }
        @Override public AnimationFacade setClip(String clipName) { AnimationComponent a = anim(true); if (a != null) { a.currentClip = clipName != null ? clipName : ""; a.frame = -1; a.stateTime = 0f; markMaterial(); } return this; }
        @Override public AnimationFacade setLoop(boolean loop) { AnimationComponent a = anim(true); if (a != null) a.loop = loop; return this; }
        @Override public AnimationFacade setFps(float fps) { AnimationComponent a = anim(true); if (a != null) a.fps = fps; return this; }
        @Override public AnimationFacade setStateTime(float stateTime) { AnimationComponent a = anim(true); if (a != null) { a.stateTime = Math.max(0f, stateTime); a.frame = -1; markMaterial(); } return this; }
        @Override public boolean isPlaying() { AnimationComponent a = anim(false); return a != null && a.playing; }
        @Override public boolean isLooping() { AnimationComponent a = anim(false); return a != null && a.loop; }
        @Override public float fps() { AnimationComponent a = anim(false); return a != null ? a.fps : 0f; }

        private AnimationComponent anim(boolean create) { return comp(AnimationComponent.class, create); }
        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = engine.getWorld(); if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }
        private void markMaterial() { DirtyTrackerSystem d = engine.getWorld() != null ? engine.getWorld().getSystem(DirtyTrackerSystem.class) : null; if (d != null) d.material(entityId); }
    }

    static final class ParticleFacadeImpl implements ParticleFacade {
        private final PixscapeEngine engine; private final int entityId;
        ParticleFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }
        @Override public boolean exists() { return emitter(false) != null; }
        @Override public ParticleFacade setEffect(String effectPath, String atlasTag) { ParticleEmitterComponent c = emitter(true); if (c != null) { c.effectPath = effectPath != null ? effectPath : ""; c.atlasTag = atlasTag != null ? atlasTag : ""; } return this; }
        @Override public ParticleFacade setLocalSpace(boolean localSpace) { ParticleEmitterComponent c = emitter(true); if (c != null) c.localSpace = localSpace; return this; }
        @Override public ParticleFacade setLooping(boolean looping) { ParticleEmitterComponent c = emitter(true); if (c != null) c.looping = looping; return this; }
        @Override public ParticleFacade setAutoStart(boolean autoStart) { ParticleEmitterComponent c = emitter(true); if (c != null) c.autoStart = autoStart; return this; }
        @Override public ParticleFacade play() { ParticleEmitterComponent c = emitter(true); if (c != null) { c.playRequested = true; c.paused = false; } return this; }
        @Override public ParticleFacade pause() { ParticleEmitterComponent c = emitter(false); if (c != null) c.paused = true; return this; }
        @Override public ParticleFacade resume() { ParticleEmitterComponent c = emitter(false); if (c != null) c.paused = false; return this; }
        @Override public ParticleFacade restart() { ParticleEmitterComponent c = emitter(true); if (c != null) { c.restartRequested = true; c.paused = false; } return this; }
        @Override public ParticleFacade stop() { ParticleEmitterComponent c = emitter(false); if (c != null) { c.paused = true; c.playRequested = false; c.restartRequested = false; } return this; }
        @Override public boolean isPaused() { ParticleEmitterComponent c = emitter(false); return c != null && c.paused; }
        @Override public boolean isLooping() { ParticleEmitterComponent c = emitter(false); return c != null && c.looping; }

        private ParticleEmitterComponent emitter(boolean create) {
            World world = engine.getWorld(); if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<ParticleEmitterComponent> mapper = world.getMapper(ParticleEmitterComponent.class);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }
    }

    static final class ShaderFacadeImpl implements ShaderFacade {
        private final PixscapeEngine engine; private final int entityId;
        ShaderFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public String shader() {
            RenderMaterialComponent m = mat(false);
            if (m == null) return null;
            return ShaderRegistry.getName(m.shaderIdx);
        }

        @Override public ShaderFacade use(String shaderName) {
            int shaderIdx = ShaderRegistry.indexOf(shaderName);
            if (shaderIdx < 0) throw new IllegalArgumentException("Unknown shader: " + shaderName);
            RenderMaterialComponent m = mat(true);
            if (m != null && m.shaderIdx != shaderIdx) {
                m.shaderIdx = shaderIdx;
                markMaterial();
            }
            return this;
        }

        @Override public ShaderFacade clear() {
            RenderMaterialComponent m = mat(true);
            if (m != null && m.shaderIdx != 0) {
                m.shaderIdx = 0;
                markMaterial();
            }
            return clearFloats();
        }

        @Override public ShaderFacade setFloat(String uniform, float value) {
            if (uniform == null || isBlank(uniform)) return this;
            ShaderParamsComponent params = params(true);
            if (params != null) params.floats.put(uniform, value);
            markMaterial();
            return this;
        }

        @Override public float getFloat(String uniform, float defaultValue) {
            ShaderParamsComponent params = params(false);
            if (params == null || uniform == null || isBlank(uniform)) return defaultValue;
            return params.floats.get(uniform, defaultValue);
        }

        @Override public boolean hasFloat(String uniform) {
            ShaderParamsComponent params = params(false);
            return params != null && uniform != null && params.floats.containsKey(uniform);
        }

        @Override public ShaderFacade removeFloat(String uniform) {
            ShaderParamsComponent params = params(false);
            if (params != null && uniform != null && params.floats.containsKey(uniform)) {
                params.floats.remove(uniform, 0f);
                markMaterial();
            }
            return this;
        }

        @Override public ShaderFacade clearFloats() {
            ShaderParamsComponent params = params(false);
            if (params != null && params.floats.size > 0) {
                params.floats.clear();
                markMaterial();
            }
            return this;
        }

        private RenderMaterialComponent mat(boolean create) { return comp(RenderMaterialComponent.class, create); }
        private ShaderParamsComponent params(boolean create) { return comp(ShaderParamsComponent.class, create); }
        private <T extends Component> T comp(Class<T> type, boolean create) {
            World world = engine.getWorld(); if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<T> mapper = world.getMapper(type);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }
        private void markMaterial() { DirtyTrackerSystem d = engine.getWorld() != null ? engine.getWorld().getSystem(DirtyTrackerSystem.class) : null; if (d != null) d.material(entityId); }
    }

    static final class LightFacadeImpl implements LightFacade {
        private final PixscapeEngine engine; private final int entityId;
        LightFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }
        @Override public boolean hasPoint() { return has(PointLightComponent.class); }
        @Override public boolean hasCone() { return has(ConeLightComponent.class); }
        private boolean has(Class<? extends Component> type) {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId) && world.getMapper(type).has(entityId);
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

        @Override public TiledLayerRef ofEntityId(int entityId) { return new TiledLayerRefImpl(engine, ecs, entityId); }
        @Override public TiledLayerRef ofStableId(long stableId) { return ofEntityId(entities.entityIdOf(stableId)); }
        @Override public TiledLayerRef requireEntityId(int entityId) {
            TiledLayerRef ref = ofEntityId(entityId);
            if (!ref.exists()) throw new IllegalStateException("Tiled layer entity does not exist for entityId=" + entityId);
            return ref;
        }
        @Override public TiledLayerRef requireStableId(long stableId) {
            TiledLayerRef ref = ofStableId(stableId);
            if (!ref.exists()) throw new IllegalStateException("Tiled layer entity does not exist for stableId=" + stableId);
            return ref;
        }
        @Override public TiledAnimationsAPI animations() { return animations; }
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

        @Override public int entityId() { return entityId; }
        @Override public long stableId() { return engine.getIdentityRegistry().getStableId(entityId); }
        @Override public boolean exists() {
            World world = engine.getWorld();
            return world != null && entityId >= 0 && world.getEntityManager().isActive(entityId)
                    && world.getMapper(TiledLayerComponent.class).has(entityId)
                    && world.getMapper(TiledLayerComponent.class).get(entityId).data != null;
        }

        @Override public TiledMapFacade map() { return map; }
        @Override public TileEditFacade tiles() { return tiles; }
        @Override public TileAnimationControlFacade tileAnimations() { return tileAnimations; }
    }

    static final class TiledMapFacadeImpl implements TiledMapFacade {
        private final PixscapeEngine engine;
        private final int entityId;

        TiledMapFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public int width() { TiledMapLayerData d = data(); return d != null ? d.mapWidth : 0; }
        @Override public int height() { TiledMapLayerData d = data(); return d != null ? d.mapHeight : 0; }
        @Override public int tileWidth() { TiledMapLayerData d = data(); return d != null ? d.tileWidth : 0; }
        @Override public int tileHeight() { TiledMapLayerData d = data(); return d != null ? d.tileHeight : 0; }
        @Override public int chunkSize() { TiledMapLayerData d = data(); return d != null ? d.chunkSize : 0; }
        @Override public int chunksX() { TiledMapLayerData d = data(); return d != null ? d.getChunksX() : 0; }
        @Override public int chunksY() { TiledMapLayerData d = data(); return d != null ? d.getChunksY() : 0; }
        @Override public String atlasTag() { TiledLayerComponent c = comp(false); return c != null ? c.atlasTag : ""; }
        @Override public TiledMapFacade setAtlasTag(String atlasTag) {
            TiledLayerComponent c = comp(true);
            if (c == null) return this;
            String normalized = atlasTag == null || isBlank(atlasTag) ? "main" : atlasTag;
            if (!normalized.equals(c.atlasTag)) {
                c.atlasTag = normalized;
                if (c.data != null) {
                    c.data.markAllChunksContentDirty();
                }
            }
            return this;
        }
        @Override public Object projection() { TiledMapLayerData d = data(); return d != null ? d.projection : null; }
        @Override public TiledMapFacade setVisible(boolean visible) { TiledMapLayerData d = data(); if (d != null) d.visible = visible; return this; }
        @Override public TiledMapFacade setCollisionEnabled(boolean enabled) { TiledMapLayerData d = data(); if (d != null) d.collisionEnabled = enabled; return this; }
        @Override public TiledMapFacade setOrigin(float x, float y) {
            TiledMapLayerData d = data();
            TiledLayerComponent c = comp(false);
            if (d != null && (d.originX != x || d.originY != y)) {
                d.originX = x;
                d.originY = y;
                for (IntMap.Values<TileChunk> it = d.getChunks(); it.hasNext();) d.updateChunkBounds(it.next());
                d.markAllChunksContentDirty();
            }
            if (c != null) {
                c.originX = x;
                c.originY = y;
            }
            return this;
        }
        @Override public int worldToTileX(float worldX) { TiledMapLayerData d = data(); return d != null ? d.worldToTileX(worldX) : 0; }
        @Override public int worldToTileY(float worldY) { TiledMapLayerData d = data(); return d != null ? d.worldToTileY(worldY) : 0; }
        @Override public int worldToTileX(float worldX, float worldY) { TiledMapLayerData d = data(); return d != null ? d.worldToTileX(worldX, worldY) : 0; }
        @Override public int worldToTileY(float worldX, float worldY) { TiledMapLayerData d = data(); return d != null ? d.worldToTileY(worldX, worldY) : 0; }
        @Override public float tileToWorldX(int gx) { TiledMapLayerData d = data(); return d != null ? d.tileToWorldX(gx) : 0f; }
        @Override public float tileToWorldY(int gy) { TiledMapLayerData d = data(); return d != null ? d.tileToWorldY(gy) : 0f; }
        @Override public float tileToWorldX(int gx, int gy) { TiledMapLayerData d = data(); return d != null ? d.tileToWorldX(gx, gy) : 0f; }
        @Override public float tileToWorldY(int gx, int gy) { TiledMapLayerData d = data(); return d != null ? d.tileToWorldY(gx, gy) : 0f; }
        @Override public TiledMapFacade resize(int width, int height) {
            TiledMapLayerData d = data();
            if (d != null) {
                d.rebuildWithNewSize(width, height);
                TiledLayerComponent c = comp(false);
                if (c != null) { c.mapWidthCells = d.mapWidth; c.mapHeightCells = d.mapHeight; }
                TileEditFacadeImpl.syncAllChunkAnimations(engine, d);
            }
            return this;
        }

        private TiledLayerComponent comp(boolean create) {
            World world = engine.getWorld(); if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return null;
            ComponentMapper<TiledLayerComponent> mapper = world.getMapper(TiledLayerComponent.class);
            return create ? (mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId)) : mapper.getSafe(entityId, null);
        }
        private TiledMapLayerData data() { TiledLayerComponent c = comp(false); return c != null ? c.data : null; }
    }

    static final class TileEditFacadeImpl implements TileEditFacade {
        private final PixscapeEngine engine; private final int entityId;
        TileEditFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public int get(int x, int y) { TiledMapLayerData d = data(); return d != null ? d.getTile(x, y) : 0; }
        @Override public byte getFlags(int x, int y) { TiledMapLayerData d = data(); return d != null ? d.getTileTransformFlags(x, y) : TileTransformFlags.NONE; }

        @Override public TileEditFacade set(int x, int y, int assetId) { return set(x, y, assetId, TileTransformFlags.NONE); }
        @Override public TileEditFacade set(int x, int y, int assetId, byte flags) { mutateCell(x, y, assetId, flags); return this; }
        @Override public TileEditFacade clear(int x, int y) { mutateCell(x, y, 0, TileTransformFlags.NONE); return this; }

        @Override public TileEditFacade fillRect(int x, int y, int width, int height, int assetId) { return fillRect(x, y, width, height, assetId, TileTransformFlags.NONE); }
        @Override public TileEditFacade fillRect(int x, int y, int width, int height, int assetId, byte flags) {
            int maxY = y + Math.max(0, height);
            int maxX = x + Math.max(0, width);
            for (int gy = y; gy < maxY; gy++) for (int gx = x; gx < maxX; gx++) mutateCell(gx, gy, assetId, flags);
            return this;
        }
        @Override public TileEditFacade clearRect(int x, int y, int width, int height) { return fillRect(x, y, width, height, 0, TileTransformFlags.NONE); }
        @Override public TileEditFacade hLine(int x, int y, int length, int assetId) { return hLine(x, y, length, assetId, TileTransformFlags.NONE); }
        @Override public TileEditFacade hLine(int x, int y, int length, int assetId, byte flags) {
            int step = length >= 0 ? 1 : -1;
            for (int i = 0; i != length; i += step) mutateCell(x + i, y, assetId, flags);
            return this;
        }
        @Override public TileEditFacade vLine(int x, int y, int length, int assetId) { return vLine(x, y, length, assetId, TileTransformFlags.NONE); }
        @Override public TileEditFacade vLine(int x, int y, int length, int assetId, byte flags) {
            int step = length >= 0 ? 1 : -1;
            for (int i = 0; i != length; i += step) mutateCell(x, y + i, assetId, flags);
            return this;
        }

        @Override public TileEditFacade markAllDirty() { TiledMapLayerData d = data(); if (d != null) d.markAllChunksContentDirty(); return this; }

        private void mutateCell(int gx, int gy, int assetId, byte flags) {
            TiledMapLayerData d = data();
            if (d == null || !d.isInside(gx, gy)) return;
            d.setTile(gx, gy, assetId, flags);
            syncCell(engine, d, gx, gy);
        }

        static void syncAllChunkAnimations(PixscapeEngine engine, TiledMapLayerData d) {
            if (d == null) return;
            for (IntMap.Values<TileChunk> it = d.getChunks(); it.hasNext();) {
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

        TiledAnimationsApiImpl(PixscapeEngine engine) { this.engine = engine; }
        @Override public boolean contains(int animatedTileAssetId) { return engine.getAnimatedTileRegistry().contains(animatedTileAssetId); }
        @Override public TileAnimationDefView get(int animatedTileAssetId) {
            TileAnimationDef def = engine.getAnimatedTileRegistry().get(animatedTileAssetId);
            if (def == null) return null;
            reusableView.bind(def);
            return reusableView;
        }
        @Override public TiledAnimationsAPI put(int animatedTileAssetId, int[] frameAssetIds, int[] frameDurationsMs) {
            engine.getAnimatedTileRegistry().put(animatedTileAssetId, frameAssetIds, frameDurationsMs);
            return this;
        }
        @Override public TiledAnimationsAPI remove(int animatedTileAssetId) { engine.getAnimatedTileRegistry().remove(animatedTileAssetId); return this; }
        @Override public void clear() { engine.getAnimatedTileRegistry().clear(); }
    }

    static final class TileAnimationDefViewImpl implements TileAnimationDefView {
        private TileAnimationDef def;
        void bind(TileAnimationDef def) { this.def = def; }
        @Override public int id() { return def != null ? def.id() : 0; }
        @Override public int frameCount() { return def != null ? def.frameCount() : 0; }
        @Override public int frameAssetId(int index) { return def != null ? def.frameAssetId(index) : 0; }
        @Override public int frameDurationMs(int index) { return def != null ? def.frameDurationMs(index) : 0; }
    }

    static final class TileAnimationControlFacadeImpl implements TileAnimationControlFacade {
        private final PixscapeEngine engine; private final int entityId;
        private TileChunk cellChunk;
        private int cellLocalIndex;
        private int cellAssetId;

        TileAnimationControlFacadeImpl(PixscapeEngine engine, int entityId) { this.engine = engine; this.entityId = entityId; }

        @Override public boolean isAnimated(int x, int y) {
            return resolveCell(x, y)
                    && TileAnimationResolver.isAnimated(cellAssetId, engine.getAnimatedTileRegistry());
        }

        @Override public boolean isPlaying(int x, int y) {
            return resolveCell(x, y)
                    && cellChunk.getAnimPlaybackState(cellLocalIndex) == TileAnimationPlayback.PLAYING;
        }

        @Override public boolean isPaused(int x, int y) {
            return resolveCell(x, y)
                    && cellChunk.getAnimPlaybackState(cellLocalIndex) == TileAnimationPlayback.PAUSED;
        }

        @Override public TileAnimationControlFacade play(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            cellChunk.setAnimationPlaybackState(cellLocalIndex, TileAnimationPlayback.PLAYING);
            return this;
        }

        @Override public TileAnimationControlFacade pause(int x, int y) {
            if (!resolveAnimatedCell(x, y)) return this;
            cellChunk.setAnimationPlaybackState(cellLocalIndex, TileAnimationPlayback.PAUSED);
            return this;
        }

        @Override public TileAnimationControlFacade stop(int x, int y) {
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

        @Override public TileAnimationControlFacade restart(int x, int y) {
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

        @Override public TileAnimationControlFacade setFrame(int x, int y, int frameIndex) {
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

        @Override public TileAnimationControlFacade setElapsedMs(int x, int y, int elapsedMs) {
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
