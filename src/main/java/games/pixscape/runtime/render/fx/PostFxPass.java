package games.pixscape.runtime.render.fx;

import games.pixscape.runtime.render.RenderContext;

/**
 * Une passe de postFX qui prend une texture en entrée et renvoie un handle
 * de texture en sortie (ibid. Godot viewport+shader, Unity URP pass).
 */
public interface PostFxPass {

    /**
     * @param ctx           contexte rendu
     * @param inputTexture  handle GL de la texture source
     * @return handle de la texture de sortie (peut être la même que l'entrée pour un effet in-place)
     */
    int apply(RenderContext ctx, int inputTexture);
}
