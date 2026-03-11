package games.pixscape.runtime.helper;

import games.pixscape.runtime.component.OrientedBoundsComponent;

/** Tests AABB et utilitaires culling/picking. Zéro objet. */
public final class OrientedBoundsHelper {
    private OrientedBoundsHelper(){}

    /** Calcule x1..y4 (8 floats) à partir d’un OBB (coin 1..4 en sens horaire). */
    public static void toCorners(OrientedBoundsComponent b, float[] out8) {
        float cx=b.cx, cy=b.cy, ux=b.ux, uy=b.uy, vx=b.vx, vy=b.vy, hx=b.hx, hy=b.hy;

        float ux_hx = ux*hx, uy_hx = uy*hx;
        float vx_hy = vx*hy, vy_hy = vy*hy;

        // P1 = C - U*hx - V*hy
        out8[0] = cx - ux_hx - vx_hy;
        out8[1] = cy - uy_hx - vy_hy;
        // P2 = C + U*hx - V*hy
        out8[2] = cx + ux_hx - vx_hy;
        out8[3] = cy + uy_hx - vy_hy;
        // P3 = C + U*hx + V*hy
        out8[4] = cx + ux_hx + vx_hy;
        out8[5] = cy + uy_hx + vy_hy;
        // P4 = C - U*hx + V*hy
        out8[6] = cx - ux_hx + vx_hy;
        out8[7] = cy - uy_hx + vy_hy;
    }

    /** Axis-aligned si U≈(1,0) et V≈(0,1). */
    public static boolean isAxisAligned(OrientedBoundsComponent b, float eps) {
        return Math.abs(b.ux - 1f) <= eps && Math.abs(b.uy) <= eps
                && Math.abs(b.vx) <= eps && Math.abs(b.vy - 1f) <= eps;
    }

    public static boolean contains(OrientedBoundsComponent b, float x, float y, float tolerance) {
        // vecteur point -> centre
        float dx = x - b.cx;
        float dy = y - b.cy;

        // projections sur les axes locaux U/V
        float pu = dx * b.ux + dy * b.uy; // coordonnée locale X
        float pv = dx * b.vx + dy * b.vy; // coordonnée locale Y

        float hx = b.hx + tolerance;
        float hy = b.hy + tolerance;

        return Math.abs(pu) <= hx && Math.abs(pv) <= hy;
    }

    /** Version sans tolérance. */
    public static boolean contains(OrientedBoundsComponent b, float x, float y) {
        return contains(b, x, y, 0f);
    }

    /**
     * Test de point dans un OBB défini par ses 4 coins (x0,y0,...,x3,y3) en sens horaire.
     * Les coins doivent venir de {@link #toCorners(OrientedBoundsComponent, float[])} puis
     * éventuellement être translatés (parallax, etc.).
     */
    public static boolean contains(float[] corners8, float x, float y, float tolerance) {
        float x0 = corners8[0], y0 = corners8[1];
        float x1 = corners8[2], y1 = corners8[3];
        float x3 = corners8[6], y3 = corners8[7];

        float vxX = x1 - x0;
        float vxY = y1 - y0;
        float vyX = x3 - x0;
        float vyY = y3 - y0;

        float lenUx = (float) Math.sqrt(vxX * vxX + vxY * vxY);
        float lenVy = (float) Math.sqrt(vyX * vyX + vyY * vyY);
        if (lenUx <= 1e-6f || lenVy <= 1e-6f) {
            return false;
        }

        float dx = x - x0;
        float dy = y - y0;

        float det = vxX * vyY - vxY * vyX;
        if (Math.abs(det) <= 1e-8f) {
            return false;
        }

        float invDet = 1f / det;
        float u = ( dy * (-vyX) + dx *  vyY) * invDet;
        float v = ( dy *  vxX  + dx * (-vxY)) * invDet;

        float tolU = tolerance / lenUx;
        float tolV = tolerance / lenVy;

        return u >= -tolU && u <= 1f + tolU
                && v >= -tolV && v <= 1f + tolV;
    }

    public static boolean contains(float[] corners8, float x, float y) {
        return contains(corners8, x, y, 0f);
    }
}
