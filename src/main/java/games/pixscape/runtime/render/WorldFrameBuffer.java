package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;

/**
 * World targets retain one managed registration and fresh attachments after context loss.
 * LibGDX 1.14.2 build() appends both attachments and a managed registration on every rebuild.
 */
public final class WorldFrameBuffer extends FrameBuffer {
    public WorldFrameBuffer(GLFrameBuffer.FrameBufferBuilder builder) {
        super(builder);
    }

    public WorldFrameBuffer(Pixmap.Format format, int width, int height, boolean depth) {
        super(format, width, height, depth);
    }

    @Override
    protected void build() {
        boolean rebuilding = framebufferHandle != 0;
        // Old GL names belong to the lost context. Do not delete possibly reused names.
        textureAttachments.clear();
        colorBufferHandles.clear();
        super.build();
        if (rebuilding) {
            Array<GLFrameBuffer> managed = buffers.get(Gdx.app);
            // super.build appended this object; preserve its original position in the
            // list that invalidateAllFrameBuffers is currently traversing.
            if (managed != null && managed.size > 0 && managed.peek() == this) managed.pop();
        }
    }

    @Override
    protected Texture createTexture(FrameBufferTextureAttachmentSpec spec) {
        Texture texture = super.createTexture(spec);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }
}
