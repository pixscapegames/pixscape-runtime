package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ObjectFloatMap;
import games.pixscape.runtime.component.CameraFxComponent;
import games.pixscape.runtime.component.CameraSettingsComponent;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.render.CameraRenderTargets;
import games.pixscape.runtime.render.CameraStateSOA;
import games.pixscape.runtime.render.ShaderRegistry;
import games.pixscape.runtime.render.fx.PostFxRegistry;

/**
 * Passe post-processing :
 * - Si la caméra rend en offscreen (FBO),
 *   on copie la texture couleur dans le backbuffer via SpriteBatch.
 * - Applique éventuellement un shader "global" par caméra (post-FX plein écran).
 */
public final class PostProcessDispatchSystem extends BaseSystem {

    private static final String TAG = "PostFX";
    private static final boolean DEBUG_PREVIEW = true;
    private static final float DEBUG_LOG_INTERVAL = 1.0f;

    private final CameraStateSOA      cameraState;
    private final CameraRenderTargets cameraTargets;
    private final PostFxRegistry      fxRegistry;
    private final boolean advancedRendering;

    private ComponentMapper<CameraFxComponent> mCameraFx;
    private ComponentMapper<ShaderParamsComponent>   mShaderParams;
    private ComponentMapper<CameraSettingsComponent> mCameraSettings;

    private EntitySubscription cameraSub;

    private final SpriteBatch        batch;
    private final OrthographicCamera screenCam;

    private float time = 0f;
    private float debugLogTimer = 0f;
    private boolean debugPreviewFrame = false;
    private final StringBuilder debugSb = new StringBuilder(256);

    public PostProcessDispatchSystem(CameraStateSOA cameraState,
                                     CameraRenderTargets cameraTargets,
                                     PostFxRegistry fxRegistry,
                                     boolean advancedRendering) {
        this.cameraState   = cameraState;
        this.cameraTargets = cameraTargets;
        this.fxRegistry    = fxRegistry;
        this.advancedRendering = advancedRendering;

        this.batch     = new SpriteBatch();
        this.screenCam = new OrthographicCamera();
    }

    @Override
    protected void initialize() {
        resizeIfNeeded();

        // Toutes les entités avec CameraSettingsComponent (meta optionnel)
        cameraSub = world.getAspectSubscriptionManager()
                .get(Aspect.all(CameraSettingsComponent.class));
    }

    private void resizeIfNeeded() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        if (w <= 0 || h <= 0) return;

        screenCam.setToOrtho(false, w, h);
        screenCam.update();
    }

    @Override
    protected void processSystem() {
        // 1) Mode avancé seulement
        if (!advancedRendering) {
            return;
        }

        if (cameraTargets == null) {
            return;
        }

        float dt = world.getDelta();
        time += dt;
        if (DEBUG_PREVIEW) {
            debugLogTimer += dt;
            if (debugLogTimer >= DEBUG_LOG_INTERVAL) {
                debugLogTimer = 0f;
                debugPreviewFrame = true;
            } else {
                debugPreviewFrame = false;
            }
        }

        // Resize écran si nécessaire
        if ((int) screenCam.viewportWidth  != Gdx.graphics.getWidth()
                || (int) screenCam.viewportHeight != Gdx.graphics.getHeight()) {
            resizeIfNeeded();
        }

        // On ne traite que cam0 pour l’instant
        if (cameraState.maxIndex < 0 || !cameraState.enabled[0]) {
            return;
        }

        boolean wantsOffscreen =
                cameraState.useOffscreen[0]
                        || cameraState.postFxChainId[0] != 0;

        if (!wantsOffscreen) {
            // Rien à faire : rendu direct dans le backbuffer par RenderSubmitSystem
            return;
        }

        TextureRegion src = cameraTargets.getColorRegion(0);
        if (src == null) {
            if (DEBUG_PREVIEW && debugPreviewFrame) {
                debugSb.setLength(0);
                debugSb.append("[PREVIEW] cam=0 wantsOffscreen=").append(wantsOffscreen)
                        .append(" chainId=").append(cameraState.postFxChainId[0])
                        .append(" fbo=").append(cameraState.fboHandle[0])
                        .append(" src=null");
                Gdx.app.log(TAG, debugSb.toString());
            }
            return;
        }

        // --------------------------------------------------------------------
        // Rendu post-FX dans le backbuffer
        // --------------------------------------------------------------------
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        batch.setProjectionMatrix(screenCam.combined);
        batch.setColor(1f, 1f, 1f, 1f);

        // --------------------------------------------------------------------
        // Choix du shader global depuis CameraMeta (aucun hardcodé ici)
        // --------------------------------------------------------------------
        int camEntity = -1;
        if (cameraState.entityId != null && cameraState.entityId.length > 0) {
            camEntity = cameraState.entityId[0]; // caméra index 0
        }

        ShaderProgram shader = null;
        String shaderName = null;

        if (camEntity >= 0 && mCameraFx != null && mCameraFx.has(camEntity)) {
            CameraFxComponent fx = mCameraFx.get(camEntity);

            if (fx != null) {
                shaderName = fx.postFxShaderName;

                // <none> ou vide => pas de shader global
                if (shaderName == null || shaderName.isEmpty() || "<none>".equals(shaderName)) {
                    shaderName = null;
                }
            }

            if (shaderName != null) {
                shader = ShaderRegistry.get(shaderName);

                if (shader != null && !shader.isCompiled()) {
                    shader = null; // on n'utilise pas un shader mal compilé
                }
            }
        }

        if (shader == null && shaderName != null) {
            Gdx.app.log(TAG,
                    "Shader '" + shaderName + "' null ou non compilé, fallback shader par défaut.");
        }

        // Sélection du shader (null => shader par défaut, pas de FX)
        batch.setShader(shader);

        batch.begin();

        // Uniforms seulement si shader valide (après begin() !)
        if (shader != null) {
            float intensity = 0.4f; // valeur par défaut

            if (mShaderParams != null && mShaderParams.has(camEntity)) {
                ShaderParamsComponent params = mShaderParams.get(camEntity);
                ObjectFloatMap<String> map = params.floats;
                for (ObjectFloatMap.Entry<String> e : map) {
                    shader.setUniformf(e.key, e.value);
                    if ("u_intensity".equals(e.key)) {
                        intensity = e.value;
                    }
                }
            }

            shader.setUniformf("u_intensity", intensity);
            shader.setUniformf("u_time", time);
        }

        // Draw plein écran depuis le FBO
        batch.draw(
                src,
                0f, 0f,
                screenCam.viewportWidth, screenCam.viewportHeight
        );
        batch.end();

        // Optionnel, mais safe si un jour on réutilise ce SpriteBatch
        batch.setShader(null);

        if (DEBUG_PREVIEW && debugPreviewFrame) {
            debugSb.setLength(0);
            debugSb.append("[PREVIEW] cam=0 wantsOffscreen=").append(wantsOffscreen)
                    .append(" chainId=").append(cameraState.postFxChainId[0])
                    .append(" fbo=").append(cameraState.fboHandle[0])
                    .append(" shader=").append(shaderName == null ? "<none>" : shaderName)
                    .append(" drew=true");
            Gdx.app.log(TAG, debugSb.toString());
        }
    }

    @Override
    protected void dispose() {
        batch.dispose();
    }
}
