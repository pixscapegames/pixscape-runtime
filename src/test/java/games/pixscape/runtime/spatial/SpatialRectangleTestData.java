package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;

/**
 * Test data for serialized settings and normalized zones within rendered images.
 */
public final class SpatialRectangleTestData {
    public static ActorSpatialRectangles rectangles(float height) {
        ActorSpatialRectangles r = new ActorSpatialRectangles();
        r.staticRectangle = new ActorSpatialRectangleData();
        r.staticRectangle.height = height;
        r.staticRectangle.width = height;
        r.staticRectangle.x = -height * .5f;
        return r;
    }

    public static void setHeight(SpatialRepresentationComponent spatial, float height) {
        spatial.rectangles = rectangles(height);
    }

    public static void setImageHeight(SpatialRepresentationComponent spatial, float height) {
        setImageZone(spatial, height, 1024, 1024, 512, 0);
    }

    public static void setImageZone(SpatialRepresentationComponent spatial, float size,
                                   float imageWidth, float imageHeight, float anchorX, float anchorY) {
        spatial.rectangles = new ActorSpatialRectangles();
        ActorSpatialRectangleData rectangle = new ActorSpatialRectangleData();
        rectangle.x = (anchorX - size * .5f) / imageWidth;
        rectangle.y = anchorY / imageHeight;
        rectangle.width = size / imageWidth;
        rectangle.height = size / imageHeight;
        spatial.rectangles.staticRectangle = rectangle;
    }

    public static void imageQuad(games.pixscape.runtime.render.DynamicEntityRenderState state,
                                 int slot, float left, float bottom, float width, float height) {
        state.x1[slot] = state.x2[slot] = left;
        state.x3[slot] = state.x4[slot] = left + width;
        state.y1[slot] = state.y4[slot] = bottom;
        state.y2[slot] = state.y3[slot] = bottom + height;
        state.u1[slot] = state.v1[slot] = 0;
        state.u2[slot] = state.v2[slot] = 1;
    }
}
