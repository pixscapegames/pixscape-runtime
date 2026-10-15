package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.NumberUtils;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/** One ordered E traversal shared by Runtime and Studio, independent of effect interleaving. */
public final class AmbientContributionRenderer implements Disposable {
    private static final byte STANDARD = 1, REPLAY = 2;
    public interface MaterialReplay {
        /** Reuses the original submitter, including textures, time, parameters and repeat expansion. */
        void replayAmbientMaterials(int first, int end);
    }
    private final AmbientContributionBatch batch = new AmbientContributionBatch();
    public void prepare() { batch.prepare(); }
    public void beginFrame(FrameRenderQueue queue) {
        if (queue.size != 0) java.util.Arrays.fill(queue.ambientMaterial, 0, queue.size, (byte) 0);
    }
    /** Called only for entries admitted by the actual original material/texture path. */
    public static void admit(FrameRenderQueue queue, int index, ShaderProgram actual, ShaderProgram standard) {
        queue.ambientMaterial[index] = actual == standard && queue.repeatFlags[index] == 0 ? STANDARD : REPLAY;
    }
    public static int materialPass(int pass, FrameRenderQueue queue, int index) {
        if (pass == LightCompositionPass.AMBIENT)
            return queue.ignoreAmbientLighting[index] ? LightCompositionPass.CONTRIBUTION : LightCompositionPass.MASK;
        if (pass == LightCompositionPass.ORIGINAL && queue.lighting.diagnostic == 0 && queue.ignoreAmbientLighting[index]
                && queue.ambientMaterial[index] == REPLAY) return LightCompositionPass.ORIGINAL_EXEMPT;
        return pass;
    }
    public static float originalColor(FrameRenderQueue queue, int index, int pass) {
        float color = queue.colorPacked[index];
        if (pass == LightCompositionPass.ORIGINAL && queue.lighting.diagnostic == 0 && queue.ignoreAmbientLighting[index]
                && queue.ambientMaterial[index] == STANDARD)
            return NumberUtils.intToFloatColor(NumberUtils.floatToIntColor(color) & 0xff000000);
        return color;
    }
    public void render(FrameRenderQueue queue, OrthographicCamera camera, WorldLightComposition composition,
                       RenderStats stats, MaterialReplay replay) {
        boolean needed = false;
        for (int i = 0; i < queue.size; i++) {
            if (!queue.ignoreAmbientLighting[i] || queue.ambientMaterial[i] == 0) continue;
            float minX = Math.min(Math.min(queue.x1[i], queue.x2[i]), Math.min(queue.x3[i], queue.x4[i]));
            float maxX = Math.max(Math.max(queue.x1[i], queue.x2[i]), Math.max(queue.x3[i], queue.x4[i]));
            float minY = Math.min(Math.min(queue.y1[i], queue.y2[i]), Math.min(queue.y3[i], queue.y4[i]));
            float maxY = Math.max(Math.max(queue.y1[i], queue.y2[i]), Math.max(queue.y3[i], queue.y4[i]));
            // A custom vertex shader may move an otherwise off-camera quad into view.
            if (queue.ambientMaterial[i] == REPLAY || camera.frustum.boundsInFrustum(
                    (minX + maxX) * .5f, (minY + maxY) * .5f, 0, (maxX - minX) * .5f, (maxY - minY) * .5f, 0)) {
                needed = true; break;
            }
        }
        if (!needed) return;
        composition.beginAmbientContribution();
        batch.begin(camera);
        for (int i = 0; i < queue.size; i++) {
            if (queue.ambientMaterial[i] == 0 || queue.light[i] != 0) continue;
            if (queue.ambientMaterial[i] == REPLAY) {
                batch.flush(stats);
                int end = i + 1;
                while (end < queue.size && queue.ambientMaterial[end] == REPLAY) end++;
                replay.replayAmbientMaterials(i, end);
                batch.begin(camera);
                i = end - 1;
            } else {
                if (!queue.ignoreAmbientLighting[i] && (queue.blend[i] == BlendMode.ADDITIVE.id
                        || queue.blend[i] == BlendMode.ADDITIVE_ALPHA.id)) continue;
                batch.draw(queue, i, queue.ignoreAmbientLighting[i], stats);
            }
        }
        batch.flush(stats);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }
    public void abort() { batch.abort(); }
    @Override public void dispose() { batch.dispose(); }
}
