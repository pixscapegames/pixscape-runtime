package games.pixscape.runtime.render;

/**
 * Low-level hooks on the rendering pipeline ({@code multi-camera} and related features).
 * By default everything is empty; each module can implement what it needs.
 */
public interface RenderExtension {

    default void beforeAllCameras(RenderContext ctx) {}
    default void beforeCamera(RenderContext ctx, int cameraIndex) {}
    default void afterCamera(RenderContext ctx, int cameraIndex) {}
    default void afterAllCameras(RenderContext ctx) {}
}
