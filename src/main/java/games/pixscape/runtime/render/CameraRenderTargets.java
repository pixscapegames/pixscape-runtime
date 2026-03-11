package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/**
 * Gestionnaire des FBO par caméra.
 *
 * - Aucune allocation pendant le rendu normal.
 * - (Ré)alloue les FBO uniquement au resize ou quand une caméra passe en offscreen.
 * - Expose des TextureRegion "déjà flipées" pour la passe post-FX.
 */
public final class CameraRenderTargets {

    private final CameraStateSOA cameraState;
    private final FrameBuffer[]  fbos;
    private final TextureRegion[] colorRegions;

    private final Pixmap.Format colorFormat;
    private final boolean useDepth;

    private int fbWidth  = -1;
    private int fbHeight = -1;

    public CameraRenderTargets(CameraStateSOA cameraState,
                               Pixmap.Format colorFormat,
                               boolean useDepth) {
        this.cameraState = cameraState;
        this.colorFormat = colorFormat;
        this.useDepth    = useDepth;

        this.fbos         = new FrameBuffer[cameraState.capacity()];
        this.colorRegions = new TextureRegion[cameraState.capacity()];
    }

    /** À appeler quand la fenêtre / viewport change. */
    public void resizeAll(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (width == fbWidth && height == fbHeight) return; // rien à faire

        fbWidth  = width;
        fbHeight = height;

        // On détruit les FBO existants (taille obsolète)
        for (int i = 0; i < fbos.length; i++) {
            disposeFbo(i);
        }
    }

    /**
     * À appeler à chaque frame AVANT le rendu (ex: depuis WorldCanvas.act).
     * Crée les FBO nécessaires pour les caméras actives en offscreen.
     */
    public void ensureTargetsForActiveCameras() {
        if (fbWidth <= 0 || fbHeight <= 0) return;

        for (int cam = 0; cam <= cameraState.maxIndex; cam++) {
            if (!cameraState.enabled[cam]) continue;

            boolean wantsOffscreen =
                    cameraState.useOffscreen[cam]
                            || cameraState.postFxChainId[cam] != 0;

            if (!wantsOffscreen) {
                // Cette caméra ne veut pas d'offscreen : on libère si besoin
                disposeFbo(cam);
                continue;
            }

            // Si FBO déjà créé → ok
            if (fbos[cam] != null) continue;

            // Sinon, on alloue une fois
            createFbo(cam);
        }
    }

    private void createFbo(int cam) {
        if (fbWidth <= 0 || fbHeight <= 0) return;

        FrameBuffer fbo = new FrameBuffer(colorFormat, fbWidth, fbHeight, useDepth);
        fbos[cam] = fbo;

        // Handles GL pour debug / stats
        cameraState.fboHandle[cam]          = fbo.getFramebufferHandle();
        cameraState.colorTextureHandle[cam] = fbo.getColorBufferTexture().getTextureObjectHandle();
        cameraState.depthHandle[cam]        = 0; // (optionnel si depth texture un jour)

        // Region pour le blit plein écran (flip Y car FBO inversé en LibGDX)
        TextureRegion region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        colorRegions[cam] = region;
    }

    private void disposeFbo(int cam) {
        FrameBuffer f = fbos[cam];
        if (f != null) {
            f.dispose();
            fbos[cam] = null;
        }
        colorRegions[cam] = null;

        if (cameraState.fboHandle != null) {
            cameraState.fboHandle[cam]          = 0;
            cameraState.colorTextureHandle[cam] = 0;
            cameraState.depthHandle[cam]        = 0;
        }
    }

    /** TextureRegion de couleur associée à la caméra, ou null si pas de FBO. */
    public TextureRegion getColorRegion(int camIndex) {
        if (camIndex < 0 || camIndex >= colorRegions.length) return null;
        return colorRegions[camIndex];
    }

    public void dispose() {
        for (int i = 0; i < fbos.length; i++) {
            disposeFbo(i);
        }
    }

    public int getWidth()  { return fbWidth; }
    public int getHeight() { return fbHeight; }
}
