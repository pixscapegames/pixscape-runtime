package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/** Desktop file/readback diagnostics are not part of WebGL rendering. */
final class ShadowWitnessCapture {
    void capture(LightingFrame.Light light, FrameBuffer d1, FrameBuffer d2,
                 FrameBuffer receiver, int resolution, int lightIndex) { }
}
