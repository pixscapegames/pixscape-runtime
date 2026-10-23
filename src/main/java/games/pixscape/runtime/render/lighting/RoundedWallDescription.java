package games.pixscape.runtime.render.lighting;

/** Explicit negative-X/negative-Y quarter wall, in map-cell units. No image detection.
 * Height, base and thickness come from the two explicitly tile-linked Spatial walls. */
public final class RoundedWallDescription {
    public float centerGX = 1;
    public float centerGY = 1;
    public float radiusCells = 1;

    public void validate() {
        if (!finite(centerGX) || !finite(centerGY) || !finite(radiusCells) || radiusCells <= 0)
            throw new IllegalArgumentException("Rounded wall requires a finite center and positive cell radius");
    }
    private static boolean finite(float f){return !Float.isNaN(f)&&!Float.isInfinite(f);}
    public RoundedWallDescription copy(){RoundedWallDescription d=new RoundedWallDescription();d.centerGX=centerGX;d.centerGY=centerGY;d.radiusCells=radiusCells;return d;}
    public boolean sameAs(RoundedWallDescription d){return d!=null&&centerGX==d.centerGX&&centerGY==d.centerGY&&radiusCells==d.radiusCells;}
}
