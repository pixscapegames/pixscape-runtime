package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.RenderExtension;
import games.pixscape.runtime.render.fx.PostFxPass;

public interface RuntimeAPI {

    // --- ECS ---

    /**
     * Registers an Artemis system in the runtime world configuration.
     */
    void registerSystem(BaseSystem system);

    /**
     * Registers metadata for an ECS component that should be exposed in the editor UI.
     */
    void registerEditableComponent(Class<?> componentType, EditableComponentMeta meta);


    // --- Injection / transmuters ---

    /**
     * Registers a tag injector applied to every entity carrying {@code tag} during scene loading.
     */
    void registerTagInjector(String tag, TagInjector injector);


    // --- Rendu ---

    /**
     * Registers a rendering extension with lifecycle hooks around camera rendering.
     */
    void registerRenderExtension(RenderExtension extension);

    /**
     * Registers a reusable post-processing pass for FX graphs.
     *
     * @param id unique pass identifier in the project scope
     */
    void registerPostFxPass(String id, PostFxPass pass);
}
