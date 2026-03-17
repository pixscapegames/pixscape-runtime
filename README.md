<p align="center">
  <img src="pixscape_logo.png" alt="Pixscape logo" width="80">
</p>

<h1 align="center">Pixscape Runtime</h1>
<p align="center"><strong>Performance-oriented 2D runtime for Pixscape</strong></p>
<br>
Pixscape Runtime is the core runtime library used by Pixscape.

It provides the runtime-side foundation for 2D scenes, ECS-based gameplay, rendering, asset loading, tiled content, shader management, and physics integration.

## Highlights

- `PixscapeEngine` is the main public entry point
- direct Artemis-ODB `World` access remains first-class
- scene loading and runtime bootstrap are handled by the engine
- helper APIs are available for identity/tag lookups, mappers, systems, shaders, and physics
- SOA-based runtime render state for cache-friendly data access
- integrated culling to avoid processing and submitting off-screen content
- dirty-based update flow to minimize unnecessary runtime work
- designed for performance-conscious 2D runtime execution

## Design Philosophy

Pixscape Runtime does **not** try to hide ECS from the developer.

Instead, it provides:

- a clear runtime entry point
- direct access to the Artemis world
- scene-oriented convenience
- a focused set of runtime services and helpers
- performance-oriented runtime internals built around SOA structures, culling, and dirty-driven updates

This makes it usable both as:

- a practical scene runtime for Pixscape projects
- a flexible ECS playground for custom game logic
- a runtime designed to reduce unnecessary work during rendering and world updates

## Main Entry Point

The main entry point is `PixscapeEngine`.

Typical responsibilities:

- load a Pixscape runtime project
- load a scene
- update and render the active world
- expose runtime services and ECS helpers

## Getting Started

Minimal example:

```java
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.engine.PixscapeEngine;

public final class MyGame extends ApplicationAdapter {

    private OrthographicCamera camera;
    private PixscapeEngine engine;

    @Override
    public void create() {
        camera = new OrthographicCamera();

        engine = new PixscapeEngine()
                .setWorldCamera(camera)
                .setConfigurationCustomizer(builder -> {
                    // Register custom Artemis systems before world bootstrap if needed.
                    // builder.with(new MyGameplaySystem());
                });

        // Replace with your Pixscape runtime project root
        FileHandle projectRoot = Gdx.files.absolute("/path/to/your/project");

        engine.loadProject(projectRoot);
        engine.loadScene("MainScene");
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        engine.update(dt);
        engine.render();
    }

    @Override
    public void resize(int width, int height) {
        engine.resize(width, height);
    }

    @Override
    public void dispose() {
        if (engine != null) {
            engine.dispose();
        }
    }
}
```

## Core API

PixscapeEngine exposes the essential runtime API:

- getWorld()
- getCamera()
- setWorldCamera(...)
- setConfigurationCustomizer(...)
- mapper(Class<T>)
- system(Class<T>)

Example:
```java
World world = engine.getWorld();

OrthographicCamera camera = engine.getCamera();

ComponentMapper<MyComponent> myComponent = engine.mapper(MyComponent.class);
MyGameplaySystem mySystem = engine.system(MyGameplaySystem.class);
```

### Scene Lifecycle

Typical flow:

```java
engine.loadProject(projectRoot);
engine.loadScene("MainScene");

engine.update(deltaTime);
engine.render();
```

### Main lifecycle methods:

- loadProject(...)

- loadScene(...)

- update(float dt)

- render()

- resize(int w, int h)

- dispose()

### Custom Systems

Custom Artemis systems can be injected before world creation through:
```java
setConfigurationCustomizer(Consumer<WorldConfigurationBuilder>)
```
Example:
```java
PixscapeEngine engine = new PixscapeEngine()
        .setConfigurationCustomizer(builder -> {
            builder.with(new MyGameplaySystem());
            builder.with(new MySpawnSystem());
        });
```

This hook is intended for pre-bootstrap configuration.

### Identity and Tag Lookups

Pixscape Runtime exposes engine-managed registries for identity and tags:
- getIdentityRegistry()
- getTagRegistry()

It also exposes convenience helpers directly on PixscapeEngine:
- findEntityByStableId(long stableId)
- firstEntityByName(String name)
- findEntitiesByName(String name)
- firstEntityByTag(String tag)
- findEntitiesByTag(String tag)

### Lookup conventions:

- single-result lookups return an entity id or -1

- multi-result lookups return an IntBag

Example:
```java
int player = engine.firstEntityByTag("player");
if (player != -1) {
    // player found
}
```

### Registry coherence

Identity and tag registries are runtime services managed by PixscapeEngine.

They are rebound and rebuilt as worlds and scenes change. For runtime mutations, prefer the registry APIs that keep component data and indexes coherent.

### In practice:

- scene loading restores persisted identity/tag components

- registry rebuild restores lookup state

- runtime edits to identity/tags should go through registry APIs when possible

### Shader Registry

The runtime exposes:

- getShaderRegistry()

This provides access to shader registration and default shader metadata, including default uniform definitions for custom shaders.

### Physics

Pixscape Runtime integrates Box2D-related runtime services through:

- getBox2dWorldService()

- getBox2dSyncSystem()

These APIs allow gameplay code to access the active physics world service and synchronization system when needed.

### Asset and Rendering Services

Advanced runtime integrations can use:

- getAtlasRuntimeService()
- getRenderState()
- getLayerState()
- getDrawList()
- getMetricsBatch()
- getRenderStats()
- getRenderStatsSink()

These are mainly useful for debugging, tooling, and advanced runtime extensions.

### Tiled Content

Pixscape Runtime includes tiled scene support and runtime-side tiled memory allocation.
When loading a scene, the engine handles the world rebuild and tiled runtime setup required by tiled layers internally.

## Current MVP Scope

The current public runtime API is intentionally focused:
- single main camera
- no public multi-camera API
- no public post-FX chain API
- no public offscreen/FBO rendering API

The goal is to keep the runtime API smaller, coherent, and practical.

## Requirements

JDK 21

## License
Licensed under the Apache License 2.0.

Commercial use is allowed under the terms of the license.