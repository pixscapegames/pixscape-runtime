package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import games.pixscape.runtime.render.BlendMode;

/**
 * Stores shader and blend properties used to render an entity.
 */
public final class RenderMaterialComponent extends PooledComponent {

    // Blend mode ids are serialized and must stay stable across runtime versions.
    public int shaderIdx = 0;

    /**
     * Serialized {@link BlendMode} id.
     */
    public int blendModeId = BlendMode.ALPHA.id;

    /**
     * Runtime texture handle resolved from atlas data.
     */
    public transient int textureHandle = 0;
    public transient String debugAtlasTag;

    @Override
    protected void reset() {
        shaderIdx = 0;
        blendModeId = BlendMode.ALPHA.id;
        textureHandle = 0;
        debugAtlasTag = null;
    }

    public int getShaderIdx() {
        return shaderIdx;
    }

    public int getBlendModeId() {
        return blendModeId;
    }

    public int getTextureHandle() {
        return textureHandle;
    }

    public void setBlendMode(BlendMode mode) {
        this.blendModeId = mode.id;
    }

    public BlendMode getBlendMode() {
        return BlendMode.fromId(blendModeId);
    }
}
