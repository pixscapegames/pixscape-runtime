package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.helper.ColorHelper;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;

/**
 * Sync dense dynamic ECS render state only on "dirty" entities via DirtyTrackerSystem.
 * <p>
 * - Union of lists (geometry/material/color/order/layer) without duplicates.
 * - Partial update according to coarse mask.
 * - Aucun consume/remove: DirtyFlushSystem flush en fin de frame.
 */
public final class RenderSpriteSyncSystem extends BaseSystem implements ProfiledSystem {

    private final DynamicEntityRenderState state;

    private DirtyTrackerSystem dirty;

    private ComponentMapper<OrientedBoundsComponent> mBounds;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<TintComponent> mTint;
    private ComponentMapper<RenderRepeatComponent> mRepeat;

    private ComponentMapper<PointLightComponent> mPointLight;
    private ComponentMapper<ConeLightComponent> mConeLight;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<ShaderParamsComponent> mShaderParams;
    private ComponentMapper<AnimationComponent> mAnimation;

    private EntitySubscription spriteSub;
    private EntitySubscription physicsBodySub;
    private EntitySubscription physicsShapesSub;

    // Work list (union)
    private final IntArray work = new IntArray(false, 256);

    private final float[] tmpCorners = new float[8];
    private final float[] tmpColor = new float[4];
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderSpriteSyncSystem(DynamicEntityRenderState state) {
        this.state = state;
    }

    @Override
    protected void initialize() {
        InternalTextures.initIfNeeded();

        // subscription = all "sprite renderable" entities
        spriteSub = world.getAspectSubscriptionManager().get(
                Aspect.all(
                        OrientedBoundsComponent.class,
                        RenderMaterialComponent.class,
                        EntityIndexComponent.class,
                        VisibilityComponent.class
                ).one(
                        TextureRegionComponent.class,
                        PointLightComponent.class,
                        ConeLightComponent.class
                )
        );

        spriteSub.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                markRenderRecordsDirty(entities);
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    state.releaseSlotForEntity(data[i]);
                }
            }
        });

        physicsBodySub = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsBodyComponent.class)
        );
        physicsShapesSub = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsShapesComponent.class)
        );
        EntitySubscription.SubscriptionListener physicsCompositionListener =
                new EntitySubscription.SubscriptionListener() {
                    @Override
                    public void inserted(IntBag entities) {
                        markRenderRecordsDirty(entities);
                    }

                    @Override
                    public void removed(IntBag entities) {
                        markRenderRecordsDirty(entities);
                    }
                };
        physicsBodySub.addSubscriptionListener(physicsCompositionListener);
        physicsShapesSub.addSubscriptionListener(physicsCompositionListener);
    }

    private void markRenderRecordsDirty(IntBag entities) {
        DirtyTrackerSystem tracker = dirty;
        if (tracker == null) {
            tracker = world.getSystem(DirtyTrackerSystem.class);
        }
        if (tracker == null || entities == null) return;

        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int e = data[i];
            tracker.geometry(e, GeometryDirty.ALL);
            tracker.material(e);
            tracker.color(e);
            tracker.order(e);
            tracker.layer(e);
        }
    }

    @Override
    protected void begin() {
        if (dirty == null) return;

        // Union of dirty lists
        work.clear();

        addList(dirty.geometryEntities());
        addList(dirty.materialEntities());
        addList(dirty.colorEntities());
        addList(dirty.orderEntities());
        addList(dirty.layerEntities());
    }

    private void addList(IntArray list) {
        if (list == null || list.size == 0) return;
        for (int i = 0, n = list.size; i < n; i++) {
            work.add(list.get(i));
        }
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_SPRITE_SYNC);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_SPRITE_SYNC, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        if (dirty == null) return;
        if (work.size == 0) return;

        for (int i = 0, n = work.size; i < n; i++) {
            int e = work.get(i);
            if (!world.getEntityManager().isActive(e)) continue;

            PointLightComponent pointLight = mPointLight.getSafe(e, null);
            ConeLightComponent coneLight = mConeLight.getSafe(e, null);
            boolean isLight = (pointLight != null) || (coneLight != null);

            OrientedBoundsComponent b = mBounds.getSafe(e, null);
            RenderMaterialComponent mat = mMat.getSafe(e, null);
            EntityIndexComponent entityIndex = mEntityIndex.getSafe(e, null);

            // TextureRegion + Transform only for non-light sprites
            TextureRegionComponent tr = isLight ? null : mTR.getSafe(e, null);
            TransformComponent t = isLight ? null : mTransform.getSafe(e, null);

            if (b == null || mat == null || entityIndex == null || (!isLight && tr == null)) {
                state.releaseSlotForEntity(e);
                continue;
            }

            int mask = dirty.coarseBits(e);

            // Validity texture
            // - sprites : tr.valid + mat.textureHandle != 0
            // - lights  : rely on internal handle
            boolean valid = isLight
                    ? (InternalTextures.whiteHandle() != 0)
                    : (tr.valid && mat.getTextureHandle() != 0);

            if (!valid) {
                state.releaseSlotForEntity(e);
                continue;
            }

            int renderSlot = state.acquireSlotForEntity(e);
            if (renderSlot == DynamicEntityRenderState.NO_SLOT) {
                continue;
            }

            // Kind + enabled
            state.kind[renderSlot] = RenderKind.SPRITE;
            if (isLight) {
                boolean en = (pointLight != null) ? pointLight.enabled : coneLight.enabled;
                state.enabled[renderSlot] = en;
                state.visible[renderSlot] = en;
            } else {
                state.enabled[renderSlot] = true;
                state.visible[renderSlot] = true;
            }

            byte repeatFlags = RenderRepeatFlags.NONE;
            if (!isLight && !mAnimation.has(e)) {
                RenderRepeatComponent repeat = mRepeat.getSafe(e, null);
                if (repeat != null) {
                    if (repeat.repeatX) repeatFlags |= RenderRepeatFlags.REPEAT_X;
                    if (repeat.repeatY) repeatFlags |= RenderRepeatFlags.REPEAT_Y;
                }
            }
            state.repeatFlags[renderSlot] = RenderRepeatFlags.sanitize(repeatFlags);

            // --- GEOMETRY: corners ---
            if ((mask & DirtyBits.GEOMETRY) != 0) {
                OrientedBoundsHelper.toCorners(b, tmpCorners);

                float blx = tmpCorners[0];
                float bly = tmpCorners[1];
                float brx = tmpCorners[2];
                float bry = tmpCorners[3];
                float trx = tmpCorners[4];
                float tryValue = tmpCorners[5];
                float tlx = tmpCorners[6];
                float tly = tmpCorners[7];

                state.x1[renderSlot] = blx;
                state.y1[renderSlot] = bly;
                state.x2[renderSlot] = tlx;
                state.y2[renderSlot] = tly;
                state.x3[renderSlot] = trx;
                state.y3[renderSlot] = tryValue;
                state.x4[renderSlot] = brx;
                state.y4[renderSlot] = bry;
            }

            // --- MATERIAL ---
            if (isLight) {
                state.shader[renderSlot] = mat.getShaderIdx();
                state.blend[renderSlot] = mat.getBlendModeId();
            } else if ((mask & DirtyBits.MATERIAL) != 0) {
                state.shader[renderSlot] = mat.getShaderIdx();
                state.blend[renderSlot] = mat.getBlendModeId();
                state.textureHandle[renderSlot] = mat.getTextureHandle();
            }

            // --- LIGHT FORCING: texture + UVs ---
            if (isLight) {
                state.textureHandle[renderSlot] = InternalTextures.whiteHandle();
                state.u1[renderSlot] = 0f;
                state.v1[renderSlot] = 0f;
                state.u2[renderSlot] = 1f;
                state.v2[renderSlot] = 1f;
            } else {
                // UVs must also be refreshed on GEOMETRY dirty because negative scale
                // changes the visual flip even if the material itself did not change.
                if ((mask & (DirtyBits.MATERIAL | DirtyBits.GEOMETRY)) != 0) {
                    float u1 = tr.u1;
                    float v1 = tr.v1;
                    float u2 = tr.u2;
                    float v2 = tr.v2;

                    boolean flipX = t != null && t.scaleX < 0f;
                    boolean flipY = t != null && t.scaleY < 0f;

                    if (flipX) {
                        float tmp = u1;
                        u1 = u2;
                        u2 = tmp;
                    }
                    if (flipY) {
                        float tmp = v1;
                        v1 = v2;
                        v2 = tmp;
                    }

                    state.u1[renderSlot] = u1;
                    state.v1[renderSlot] = v1;
                    state.u2[renderSlot] = u2;
                    state.v2[renderSlot] = v2;
                }
            }

            // --- COLOR ---
            if ((mask & DirtyBits.COLOR) != 0) {
                if (isLight) {
                    float r;
                    float g;
                    float blue;
                    float alpha;

                    if (pointLight != null) {
                        float intensity = pointLight.intensity;
                        r = pointLight.r * intensity;
                        g = pointLight.g * intensity;
                        blue = pointLight.b * intensity;
                        alpha = intensity;
                    } else {
                        float intensity = coneLight.intensity;
                        r = coneLight.r * intensity;
                        g = coneLight.g * intensity;
                        blue = coneLight.b * intensity;
                        alpha = intensity;
                    }

                    state.colorPacked[renderSlot] = Color.toFloatBits(r, g, blue, alpha);
                    state.a[renderSlot] = alpha;
                } else {
                    TintComponent tint = mTint.getSafe(e, null);
                    if (tint != null) {
                        ColorHelper.unpackRGBA8888(tint.rgba, tmpColor);
                        state.colorPacked[renderSlot] = Color.toFloatBits(tmpColor[0], tmpColor[1], tmpColor[2], tmpColor[3]);
                        state.a[renderSlot] = tmpColor[3];
                    } else {
                        state.colorPacked[renderSlot] = Color.WHITE.toFloatBits();
                        state.a[renderSlot] = 1f;
                    }
                }
            }

            // --- ORDER / LAYER / MATERIAL => sortKey ---
            if ((mask & (DirtyBits.MATERIAL | DirtyBits.ORDER | DirtyBits.LAYER)) != 0) {
                boolean orderDirty = (mask & (DirtyBits.ORDER | DirtyBits.LAYER)) != 0;
                boolean matDirty = isLight || (mask & DirtyBits.MATERIAL) != 0;

                int layerIndex = entityIndex.getLayerIndex();
                int z = entityIndex.getZIndex();

                state.layerIndex[renderSlot] = layerIndex;
                state.z[renderSlot] = z;
                state.runtimeOrder[renderSlot] = e; // stable-ish (tie = e & 2047)

                if (orderDirty || matDirty) {
                    state.sortKey[renderSlot] = SortKey64.packForBlend(
                            state.shader[renderSlot],
                            state.blend[renderSlot],
                            state.textureHandle[renderSlot],
                            layerIndex,
                            z,
                            state.runtimeOrder[renderSlot]
                    );
                }
            }
        }
    }

    /** Publishes persistent sprite records without culling, sorting, or submission. */
    public void prepareRuntimeAvailability() {
        begin();
        processSystemInternal();
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
