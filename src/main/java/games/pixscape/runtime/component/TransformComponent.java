package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class TransformComponent extends PooledComponent {

    // --- State (monde) ---
    public float x = 0f, y = 0f;
    public float originX = 0f, originY = 0f;
    /** Rotation en radians (convention interne). */
    public float rotationRad = 0f;
    public float scaleX = 1f, scaleY = 1f;

    // --- Caches dérivés (MAJ dans Helper.refreshCaches) ---
    public float cos = 1f, sin = 0f;        // cos/sin(rotation)
    public float absCos = 1f, absSin = 0f;  // pour AABB envelope rapide
    public float invScaleX = 1f, invScaleY = 1f;

    public static void translate(TransformComponent t, float dx, float dy) { t.x += dx; t.y += dy; }
    public static void scaleBy(TransformComponent t, float dsx, float dsy){ t.scaleX += dsx; t.scaleY += dsy; }

    /** Artemis pool reset. */
    @Override protected void reset() {
        x = y = originX = originY = 0f;
        rotationRad = 0f;
        scaleX = scaleY = 1f;
        cos = 1f; sin = 0f; absCos = 1f; absSin = 0f;
        invScaleX = invScaleY = 1f;
    }
}
