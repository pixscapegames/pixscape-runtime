package games.pixscape.runtime.render.lighting;

/** Ten bounded renders: exact archived stress pose, five selections, two pipelines. */
public final class Frame45GlCheck {
    public static void main(String[] args) {
        System.setProperty("pixscape.test.realActorSkin","true");
        System.setProperty("pixscape.test.exactFrame45","true");
        SingleSkinGlCheck.main(args);
    }
}
