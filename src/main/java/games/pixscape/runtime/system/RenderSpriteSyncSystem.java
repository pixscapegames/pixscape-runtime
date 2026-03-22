package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import com.badlogic.gdx.graphics.Color;
import games.pixscape.runtime.helper.ColorHelper;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.render.*;

/**
 * Sync RenderStateSOA only on "dirty" entities via DirtyTrackerSystem.
 *
 * - Union of lists (geometry/material/color/order/layer) without duplicates.
 * - Partial update according to coarse mask.
 * - Aucun consume/remove: DirtyFlushSystem flush en fin de frame.
 */
public final class RenderSpriteSyncSystem extends BaseSystem {

    private final RenderStateSOA state;

    private DirtyTrackerSystem dirty;

    private ComponentMapper<OrientedBoundsComponent>   mBounds;
    private ComponentMapper<TextureRegionComponent>    mTR;
    private ComponentMapper<RenderMaterialComponent>   mMat;
    private ComponentMapper<EntityIndexComponent>      mEntityIndex;
    private ComponentMapper<TintComponent>             mTint;

    private ComponentMapper<PointLightComponent>       mPointLight;
    private ComponentMapper<ConeLightComponent>        mConeLight;
    private ComponentMapper<TransformComponent>        mTransform;
    private ComponentMapper<ShaderParamsComponent>     mShaderParams;

    private EntitySubscription spriteSub;

    // Work list (union)
    private final IntArray work = new IntArray(false, 256);


    private final float[] tmpCorners = new float[8];
    private final float[] tmpColor   = new float[4];

    public RenderSpriteSyncSystem(RenderStateSOA state) {
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
            @Override public void inserted(IntBag entities) {
                DirtyTrackerSystem tracker = dirty;
                if (tracker == null) {
                    tracker = world.getSystem(DirtyTrackerSystem.class);
                }
                if (tracker == null) return;

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

            @Override public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    state.disable(data[i]);
                }
            }
        });

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
            int e = list.get(i);
            work.add(e);
        }
    }

    @Override
    protected void processSystem() {
        if (dirty == null) return;
        if (work.size == 0) return;

        for (int i = 0, n = work.size; i < n; i++) {
            int e = work.get(i);
            if (!world.getEntityManager().isActive(e)) continue;

            PointLightComponent pointLight = mPointLight.getSafe(e, null);
            ConeLightComponent  coneLight  = mConeLight.getSafe(e, null);
            boolean isLight = (pointLight != null) || (coneLight != null);

            // Set minimal commun
            OrientedBoundsComponent  b          = mBounds.getSafe(e, null);
            RenderMaterialComponent  mat        = mMat.getSafe(e, null);
            EntityIndexComponent     entityIndex= mEntityIndex.getSafe(e, null);

            // TextureRegion only for non-light sprites
            TextureRegionComponent tr = isLight ? null : mTR.getSafe(e, null);

            if (b == null || mat == null || entityIndex == null || (!isLight && tr == null)) {
                state.disable(e);
                continue;
            }

            int mask = dirty.coarseBits(e);

            // Always "touch" when there is a ticket
            state.touch(e);
            state.entityId[e] = e;



            // Validity texture
            // - sprites : tr.valid + mat.textureHandle != 0
            // - lights: rely on internal handle (not mat.textureHandle)
            boolean valid = isLight
                    ? (InternalTextures.whiteHandle() != 0)
                    : (tr.valid && mat.getTextureHandle() != 0);

            if (!valid) {
                state.disable(e);
                continue;
            }

            // Kind + enabled
            state.kind[e] = RenderStateSOA.KIND_SPRITE;
            if (isLight) {
                boolean en = (pointLight != null) ? pointLight.enabled : coneLight.enabled;
                state.enabled[e] = en;
                state.visible[e] = en;
            } else {
                state.enabled[e] = true;
                state.visible[e] = true;
            }

            // --- GEOMETRY: corners ---
            if ((mask & DirtyBits.GEOMETRY) != 0) {
                OrientedBoundsHelper.toCorners(b, tmpCorners);

                float blx = tmpCorners[0], bly = tmpCorners[1];
                float brx = tmpCorners[2], bry = tmpCorners[3];
                float trx = tmpCorners[4], try_ = tmpCorners[5];
                float tlx = tmpCorners[6], tly = tmpCorners[7];

                state.x1[e] = blx; state.y1[e] = bly;
                state.x2[e] = tlx; state.y2[e] = tly;
                state.x3[e] = trx; state.y3[e] = try_;
                state.x4[e] = brx; state.y4[e] = bry;
            }

            // --- MATERIAL ---
            if (isLight) {
                state.shader[e] = mat.getShaderIdx();
                state.blend[e]  = mat.getBlendModeId();
            } else if ((mask & DirtyBits.MATERIAL) != 0) {
                state.shader[e] = mat.getShaderIdx();
                state.blend[e]  = mat.getBlendModeId();
                if (!isLight) {
                    state.textureHandle[e] = mat.getTextureHandle();
                }
            }

            // --- LIGHT FORCING: texture + UVs (structural, not conditioned by MATERIAL dirty) ---
            if (isLight) {
                state.textureHandle[e] = InternalTextures.whiteHandle();
                state.u1[e] = 0f; state.v1[e] = 0f;
                state.u2[e] = 1f; state.v2[e] = 1f;
            } else {
                // --- UVs (TextureRegion) ---
                // In practice: if anim changes UV, yor must mark MATERIAL (or a dedicated UV bit).
                if ((mask & DirtyBits.MATERIAL) != 0) {
                    state.u1[e] = tr.u1; state.v1[e] = tr.v1;
                    state.u2[e] = tr.u2; state.v2[e] = tr.v2;
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
                    state.colorPacked[e] = Color.toFloatBits(r, g, blue, alpha);
                    state.a[e] = alpha;
                } else {
                    TintComponent tint = mTint.getSafe(e, null);
                    if (tint != null) {
                        ColorHelper.unpackRGBA8888(tint.rgba, tmpColor);
                        state.colorPacked[e] = Color.toFloatBits(tmpColor[0], tmpColor[1], tmpColor[2], tmpColor[3]);
                        state.a[e] = tmpColor[3];
                    } else {
                        state.colorPacked[e] = Color.WHITE.toFloatBits();
                        state.a[e] = 1f;
                    }
                }
            }

            // --- ORDER / LAYER / MATERIAL => sortKey ---
            if ((mask & (DirtyBits.MATERIAL | DirtyBits.ORDER | DirtyBits.LAYER)) != 0) {

                boolean orderDirty = (mask & (DirtyBits.ORDER | DirtyBits.LAYER)) != 0;
                boolean matDirty   = isLight || (mask & DirtyBits.MATERIAL) != 0;


                ;


                int layerIndex      = entityIndex.getLayerIndex();
                int z               = entityIndex.getZIndex();
                state.layerIndex[e] = layerIndex;
                state.z[e]          = z;


                state.runtimeOrder[e] = e; // stable-ish (tie = e & 2047)

                if (orderDirty || matDirty) {
                    state.sortKey[e] = SortKey64.packForBlend(
                            state.shader[e],
                            state.blend[e],
                            state.textureHandle[e],
                            layerIndex,
                            z,
                            state.runtimeOrder[e]
                    );
                }
            }

        }
    }
}
