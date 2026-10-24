package games.pixscape.runtime.render.lighting;

/** Explicit, bounded external-run entry point, excluded from normal unit-test discovery. */
public final class SilhouetteGeneralizationGlCheck {
    public static void main(String[] args){
        System.out.println("GL_CHECK_JAVA "+System.getProperty("java.home")+" "+System.getProperty("java.version"));
        TwoDepthShadowFixtureGlCheck.verify();
        GrazingLightGlCheck.verify();
        System.out.println("SILHOUETTE_GL_CHECK_PASS");
    }
}
