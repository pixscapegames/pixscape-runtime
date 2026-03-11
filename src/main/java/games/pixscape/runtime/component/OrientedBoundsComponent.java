package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class OrientedBoundsComponent extends PooledComponent {
    public float cx, cy;       // centre monde du quad
    public float ux, uy;       // axe X local (unitaire) dans le monde
    public float vx, vy;       // axe Y local (unitaire) dans le monde
    public float hx, hy;       // demi-extent en local (w/2, h/2 après scale)

    @Override
    protected void reset(){
        cx = cy = 0;
        ux = 1; uy = 0;
        vx = 0; vy = 1;
        hx = hy = 0;
    }
}
