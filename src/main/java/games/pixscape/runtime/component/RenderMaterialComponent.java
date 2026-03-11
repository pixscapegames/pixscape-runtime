package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.render.BlendMode;

/**
 * Définit les propriétés de rendu d’une entité (shader, blend, etc.)
 */
public final class RenderMaterialComponent extends PooledComponent {

    // IMPORTANT: ids stables (compat scènes existantes)
    // 0..3 doivent rester compatibles avec l’ancien système.
    // 0 = OPAQUE
    // 1 = ALPHA
    // 2 = ADDITIVE_ALPHA (ancien "ADDITIVE" libGDX: SRC_ALPHA, ONE)
    // 3 = MULTIPLY_ALPHA (ancien "MULTIPLY": DST_COLOR, ONE_MINUS_SRC_ALPHA)

    // Indices shader (0..(1<<SHADER_BITS)-1)
    public int shaderIdx = 0;

    /** BlendMode.id (stable, sérialisable) */
    public int blendModeId = BlendMode.ALPHA.id;

    // runtime only
    public transient int textureHandle = 0;
    public transient String debugAtlasTag;

    @Override protected void reset() {
        shaderIdx = 0;
        blendModeId = BlendMode.ALPHA.id;
        textureHandle = 0;
        debugAtlasTag = null;
    }

    public int getShaderIdx() { return shaderIdx; }
    public int getBlendModeId() { return blendModeId; }
    public int getTextureHandle() { return textureHandle; }

    public void setBlendMode(BlendMode mode) {
        this.blendModeId = mode.id;
    }

    public BlendMode getBlendMode() {
        return BlendMode.fromId(blendModeId);
    }
}
