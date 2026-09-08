package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Authored immediate-parent relation for a Game Object member.
 *
 * <p>{@link #parentStableId} is a persistent Pixscape entity stable ID. It is never an Artemis
 * entity ID or a Studio history ID. The relation names only the immediate parent. Top-level root,
 * child adjacency, effective Layer, and traversal order are derived state and are not serialized
 * here.</p>
 */
public final class GameObjectMemberComponent extends PooledComponent {
    public int parentStableId = -1;

    @Override
    protected void reset() {
        parentStableId = -1;
    }
}
