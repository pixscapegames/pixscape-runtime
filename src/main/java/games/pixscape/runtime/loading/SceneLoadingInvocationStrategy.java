package games.pixscape.runtime.loading;

import com.artemis.InvocationStrategy;

/** Artemis entity-state barrier used during loading without invoking gameplay systems. */
public final class SceneLoadingInvocationStrategy extends InvocationStrategy {
    public void synchronizeEntitySubscriptions() {
        updateEntityStates();
    }
}
