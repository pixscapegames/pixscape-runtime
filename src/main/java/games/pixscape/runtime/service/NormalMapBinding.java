package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;

/** Immutable asset-owned normal UV mapping, independent of color packing and aliasing. */
public final class NormalMapBinding {
    public final int textureHandle;
    public final float u0, ux, uy, v0, vx, vy;
    public final float colorWidth,colorHeight;

    /** Diagnostic snapshot deserialization only; does not create or register a GPU resource. */
    private NormalMapBinding() {
        textureHandle=0;u0=ux=uy=v0=vx=vy=colorWidth=colorHeight=0;
    }

    NormalMapBinding(AtlasRegion color, AtlasRegion normal) {
        textureHandle = TextureRegistry.handleOf(normal.getTexture());
        float cw = color.rotate ? color.getRegionHeight() : color.getRegionWidth();
        float ch = color.rotate ? color.getRegionWidth() : color.getRegionHeight();
        colorWidth=cw;colorHeight=ch;
        float nw = normal.rotate ? normal.getRegionHeight() : normal.getRegionWidth();
        float nh = normal.rotate ? normal.getRegionWidth() : normal.getRegionHeight();
        float x = (color.offsetX - normal.offsetX) / nw;
        float y = (color.offsetY - normal.offsetY) / nh;
        float sx = cw / nw, sy = ch / nh;
        float du = normal.getU2() - normal.getU(), dv = normal.getV2() - normal.getV();
        if (normal.rotate) {
            u0 = normal.getU() + y * du; ux = 0; uy = sy * du;
            v0 = normal.getV() + x * dv; vx = sx * dv; vy = 0;
        } else {
            u0 = normal.getU() + x * du; ux = sx * du; uy = 0;
            v0 = normal.getV2() - y * dv; vx = 0; vy = -sy * dv;
        }
    }
}
