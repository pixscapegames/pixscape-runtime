package games.pixscape.runtime.api;

/**
 * Controls an existing point or cone light. Retain the facade from {@link EntityRef#light()};
 * repeated access on that reference returns the same object. Call on the render thread before
 * World processing. Indexed getters and parameter setters allocate nothing after acquisition.
 * Stale references and absent lights return zero/false and ignore mutations. Cone-only setters
 * reject a live point light. Invalid values are rejected before mutation; equal writes do nothing.
 *
 * <p>Intensity, linear RGB, falloff and cone angles can vary each frame without geometry rebuilds
 * or resource preparation. The next normal World processing publishes the final values together.
 * Move and orient through {@link EntityRef#transform()}: positions are local inside Game Objects,
 * rotations are radians. Cone azimuth follows that transform and its ancestors (screen +X at zero).
 * No automatic following of a physics sibling is implied.</p>
 *
 * <p>Radius, height, movement and activation change shadow influence. At the existing frame
 * publication boundary, more relevant casters can grow CPU/GPU shadow replay capacity. Profile
 * the intended scene and motion range; these operations are not allocation free until capacity
 * is established. Rendering costs CPU/GPU work per active light and selected caster even at zero
 * intensity. Shadow resolution is preparation-only; setters never recreate shadow targets.</p>
 */
public interface LightFacade {
    boolean hasPoint();
    boolean hasCone();
    boolean enabled();
    /**
     * Occasional command, independent of Visible. Forward admission scans authored lights before
     * activation and rejects a second active light atomically, without fallback. Shadowed activation
     * requires sufficient prepared shadow-target capacity. Repeating the value skips admission.
     * Use intensity for per-frame fades.
     */
    LightFacade setEnabled(boolean enabled);
    float red();
    float green();
    float blue();
    /** Finite linear RGB in [0,1]. Alpha is not a light parameter. */
    LightFacade setColor(float r, float g, float b);
    float intensity();
    /** Finite nonnegative multiplier; zero does not disable admission or shadow rendering. */
    LightFacade setIntensity(float intensity);
    float radius();
    /**
     * Positive radius in common lighting pixels, at most Float.MAX_VALUE / 2. Resizes the authored
     * light proxy and adjusts its origin to preserve its center. Only size/origin geometry is dirtied;
     * multiple writes coalesce before World processing. The same Runtime Physics ownership guard
     * as TransformFacade applies when adjusting the proxy origin.
     */
    LightFacade setRadius(float radius);
    float falloff();
    /** Finite positive attenuation exponent (1 is linear). */
    LightFacade setFalloff(float falloff);
    float height();
    /** Finite absolute source Z in common lighting pixels, independent of Spatial altitude/origin. */
    LightFacade setHeight(float height);
    float coneAngleDeg();
    /** Full cone opening in degrees, in (0,360]. */
    LightFacade setConeAngleDeg(float degrees);
    float inclinationDeg();
    /** Elevation from horizontal XY in [-90,90] degrees; positive toward +Z. */
    LightFacade setInclinationDeg(float degrees);
    float softness();
    /** Cone edge softness in [0,1]. */
    LightFacade setSoftness(float softness);
    /** 0 unshadowed, 1 hard shadows, 2 3x3 PCF. */
    int shadowQuality();
    /**
     * Occasional change in [0,2]. Nonzero quality requires sufficient prepared target capacity,
     * otherwise throws before mutation. PCF increases GPU sampling; enabling shadows can grow
     * replay capacity at frame publication. No shader/target preparation occurs in this setter.
     */
    LightFacade setShadowQuality(int quality);
    /** Target request (256, 512 or 1024); changing it requires resource preparation. */
    int shadowResolution();
}
