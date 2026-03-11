package games.pixscape.runtime.render.fx;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry runtime : id -> PostFxChain.
 * Le Studio pourra remplir ça à partir des assets FX Graph,
 * ici on reste minimal.
 */
public final class PostFxRegistry {

    private final Map<Integer, PostFxChain> chains = new HashMap<>();

    public void registerChain(PostFxChain chain) {
        chains.put(chain.id, chain);
    }

    public PostFxChain getChain(int id) {
        return chains.get(id);
    }
}
