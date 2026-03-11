package games.pixscape.runtime.render.fx;

import java.util.List;

/**
 * Chaîne de PostFX : liste ordonnée de passes à appliquer à une texture
 * (typiquement, la texture de rendu d'une caméra).
 */
public final class PostFxChain {

    public final int id;
    public final List<PostFxPass> passes;

    public PostFxChain(int id, List<PostFxPass> passes) {
        this.id = id;
        this.passes = passes;
    }

    public boolean isEmpty() {
        return passes == null || passes.isEmpty();
    }
}
