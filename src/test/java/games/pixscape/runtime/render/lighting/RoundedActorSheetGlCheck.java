package games.pixscape.runtime.render.lighting;

/** Same real frames, moving light, wall, floor and two instances as the existing skin fixture. */
public final class RoundedActorSheetGlCheck {
    public static final class ExactFrame45 {
        public static void main(String[] args){
            System.setProperty("pixscape.test.exactFrame45","true");
            RoundedActorSheetGlCheck.main(args);
        }
    }
    public static final class Wall {
        public static void main(String[] args){
            System.setProperty("pixscape.test.roundedSheetWall","true");
            ExactFrame45.main(args);
        }
    }
    public static final class ActorExchange {
        public static void main(String[] args){
            System.setProperty("pixscape.test.roundedSheetActorExchange","true");
            ExactFrame45.main(args);
        }
    }
    public static void main(String[] args){
        System.setProperty("pixscape.test.realActorSkin","true");
        System.setProperty("pixscape.test.roundedActorSheet","true");
        SingleSkinGlCheck.main(args);
    }
}
