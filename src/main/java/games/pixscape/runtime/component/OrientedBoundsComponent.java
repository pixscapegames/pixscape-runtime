package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class OrientedBoundsComponent extends PooledComponent {
    public float cx, cy;       // quad center in world space
    public float ux, uy;       // local X axis (unit) in world
    public float vx, vy;       // local Y axis (unit) in world
    public float hx, hy;       // local half-extent (w/2, h/2 after scale)

    @Override
    protected void reset() {
        cx = cy = 0;
        ux = 1;
        uy = 0;
        vx = 0;
        vy = 1;
        hx = hy = 0;
    }
}
