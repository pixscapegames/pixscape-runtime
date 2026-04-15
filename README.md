<p align="center">
  <img src="pixscape_logo.png" alt="Pixscape logo" width="80">
</p>

<h1 align="center">Pixscape Runtime</h1>
<p align="center"><strong>Performance-oriented 2D runtime for Pixscape</strong></p>
<br>

Pixscape Runtime is the core runtime library used by Pixscape.

It provides the runtime-side foundation for 2D scenes, ECS-based gameplay, rendering, asset loading, tiled content, shader management, physics integration, and a new high-level public API built on top of the runtime engine.

## Highlights

- `PixscapeEngine` remains the main runtime entry point
- `engine.api()` exposes a new high-level public API for common runtime tasks
- direct Artemis-ODB `World` access remains first-class through the engine and `api().ecs()`
- stable ids are first-class in the public API
- tiled maps support orthogonal and isometric projections
- animated tiles are supported through:
  - global animated tile definitions
  - per-cell playback control
- dirty-based runtime updates minimize unnecessary work
- SOA-based runtime render state is designed for cache-friendly execution
- integrated culling avoids processing and submitting off-screen content
- shader control is available through a float-only high-level API in v1

## Design Philosophy

Pixscape Runtime does **not** try to hide ECS from the developer.

Instead, it offers two complementary layers:

- a high-level runtime API for common operations
- a direct ECS escape hatch for advanced gameplay and engine-level access

This makes it usable both as:

- a practical scene runtime for Pixscape projects
- a high-level runtime API for common scene/entity/tiled operations
- a flexible ECS playground for custom game logic
- a performance-oriented runtime designed to reduce unnecessary work during rendering and world updates

## Main Entry Point

The main entry point is `PixscapeEngine`.

Typical responsibilities:

- load a Pixscape runtime project
- load a scene
- update and render the active world
- expose the high-level runtime API
- expose direct ECS/world/services access

## Getting Started

Minimal example:

```java
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.api.EntityRef;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.engine.PixscapeEngine;

public final class MyGame extends ApplicationAdapter {

    private OrthographicCamera camera;
    private PixscapeEngine engine;
    private PixscapeAPI api;

    @Override
    public void create() {
        camera = new OrthographicCamera();

        engine = new PixscapeEngine()
                .setWorldCamera(camera)
                .setConfigurationCustomizer(builder -> {
                    // Register custom Artemis systems before world bootstrap if needed.
                    // builder.with(new MyGameplaySystem());
                });

        FileHandle projectRoot = Gdx.files.absolute("/path/to/your/project");

        engine.loadProject(projectRoot);
        engine.loadScene("MainScene");

        api = engine.api();

        EntityRef player = api.entities().requireTag("player");
        player.transform().setPosition(120f, 64f);
        player.sprite().setAlpha(0.9f);
        player.shader().setFloat("u_time", 0f);
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

## High-Level Runtime API

`engine.api()` exposes the high-level public runtime API:

- `entities()`
- `tiled()`
- `ecs()`

Example:

```java
PixscapeAPI api = engine.api();

EntityRef player = api.entities().requireTag("player");

player.transform().moveBy(4f, 0f);
player.animation().play("run");
player.particles().restart();
player.shader().setFloat("u_time", 1.5f);
```

### Identity Model

The public API is **stableId-first**.

Use `stableId` when you need a persistent, public-facing entity reference.
Use `entityId` when you need direct ECS/runtime access.

Example:

```java
long stableId = api.entities().ensureStableId(player.entityId());

EntityRef samePlayer = api.entities().requireStableId(stableId);
int entityId = api.entities().entityIdOf(stableId);
```

### Deferred Destruction

Entity destruction is scheduled through ECS deletion.

```java
api.entities().destroyStableId(stableId);
```

Deletion is fully applied after the next normal world processing step.
The API does **not** force an immediate `world.process()` call.

## Expert ECS Access

Pixscape Runtime still treats direct ECS access as a first-class workflow.

You can continue using:

- `engine.getWorld()`
- `engine.mapper(Class<T>)`
- `engine.system(Class<T>)`

The same expert access is also available through:

- `api.ecs().world()`
- `api.ecs().mapper(Class<T>)`
- `api.ecs().system(Class<T>)`

Example:

```java
var world = api.ecs().world();
var transformMapper = api.ecs().mapper(TransformComponent.class);
var dirtyTracker = api.ecs().system(DirtyTrackerSystem.class);
```

This low-level path coexists with the high-level API. It does not replace it.

## Scene Lifecycle

Typical flow:

```java
engine.loadProject(projectRoot);
engine.loadScene("MainScene");

engine.update(deltaTime);
engine.render();
```

### Main lifecycle methods

- `loadProject(...)`
- `loadScene(...)`
- `update(float dt)`
- `render()`
- `resize(int w, int h)`
- `dispose()`

## Custom Systems

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

## Identity and Tag Lookups

Pixscape Runtime exposes engine-managed registries for identity and tags:

- `getIdentityRegistry()`
- `getTagRegistry()`

It also exposes convenience helpers directly on `PixscapeEngine`:

- `findEntityByStableId(long stableId)`
- `firstEntityByName(String name)`
- `findEntitiesByName(String name)`
- `firstEntityByTag(String tag)`
- `findEntitiesByTag(String tag)`

The high-level API adds stableId-first and ref-based access:

- `api.entities().requireStableId(...)`
- `api.entities().requireEntityId(...)`
- `api.entities().requireName(...)`
- `api.entities().requireTag(...)`

### Lookup conventions

- single-result lookups return an entity id or `-1`
- multi-result lookups return an `IntBag`
- high-level lookups return an `EntityRef` or fail clearly when required

## Entity Facades

`EntityRef` is the main high-level handle for runtime-side entity work.

Current v1 facades include:

- `transform()`
- `sprite()`
- `animation()`
- `particles()`
- `shader()`
- `light()`
- `ecs()`

### Transform

Example:

```java
EntityRef player = api.entities().requireTag("player");

player.transform()
      .setPosition(120f, 64f)
      .setScale(2f)
      .setRotationRad(0.5f);
```

### Sprite

Example:

```java
player.sprite()
      .setAssetId(42)
      .setVisible(true)
      .setTint(1f, 1f, 1f, 1f)
      .setSize(32f, 48f);
```

### Animation

Example:

```java
player.animation()
      .play("run")
      .setLoop(true)
      .setFps(12f);
```

### Particles

Example:

```java
player.particles()
      .setEffect("particles/dust.p", "main")
      .setLooping(true)
      .restart();
```

### Shader

`ShaderFacade` is intentionally limited in v1 to **float uniforms only**.

Example:

```java
player.shader()
      .use("water")
      .setFloat("u_time", time)
      .setFloat("u_strength", 0.5f);
```

Shader v1 notes:

- float uniforms only
- no generic typed uniform API
- no vector, matrix, or texture uniform helpers in the public API yet

### Lights

The v1 high-level light API currently exposes presence checks for runtime entity lights:

- point lights
- cone lights

Ambient/global lighting control is not part of this entity-level API in v1.

## Tiled Content

Pixscape Runtime includes tiled scene support with:

- orthogonal projection
- isometric projection
- chunked tiled storage
- tile transform flags
- animated tile definitions
- per-cell animation playback state

The high-level tiled API is exposed through:

- `api.tiled()`

### Accessing a Tiled Layer

```java
var ground = api.tiled().requireEntityId(layerEntityId);
```

Or, when available:

```java
var ground = api.tiled().requireStableId(layerStableId);
```

### Tiled Map Access

`TiledMapFacade` exposes map-level operations such as:

- size
- tile size
- chunk size
- chunk grid dimensions
- projection
- atlas tag
- origin
- visibility
- collision flag
- world/tile coordinate conversion
- resize

Example:

```java
ground.map()
      .setAtlasTag("main")
      .setOrigin(0f, 0f)
      .setVisible(true)
      .setCollisionEnabled(true);
```

### Tiled Resize

```java
ground.map().resize(200, 120);
```

`resize(...)` is an **expensive** operation.

It rebuilds tiled chunk data and preserves only cells that are still inside the new bounds according to the current runtime behavior.
It does **not** change tile size, chunk size, or projection.

### Tile Editing

`TileEditFacade` exposes high-level tile operations:

- `get(...)`
- `getFlags(...)`
- `set(...)`
- `clear(...)`
- `fillRect(...)`
- `clearRect(...)`
- `hLine(...)`
- `vLine(...)`
- `markAllDirty()`

Example:

```java
ground.tiles()
      .set(10, 4, grassAssetId)
      .set(11, 4, grassAssetId)
      .set(12, 4, grassAssetId);

ground.tiles().fillRect(20, 8, 6, 2, stoneAssetId);
```

Logical tile mutations automatically keep per-cell tile animation state in sync.

### Tile Transform Flags

Tile transform flags are supported at runtime, including horizontal, vertical, and diagonal flips.

Example:

```java
ground.tiles().set(5, 5, grassAssetId, TileTransformFlags.FLIP_H);
```

## Tiled Animations

Pixscape Runtime supports animated tiles through two distinct concepts:

### 1. Global Animated Tile Definitions

Managed through:

- `api.tiled().animations()`

Example:

```java
api.tiled().animations().put(
    100,
    new int[]{101, 102, 103},
    new int[]{100, 100, 100}
);
```

This defines animated tile asset `100` using three visual frames.

### 2. Per-Cell Playback Control

Managed through:

- `TiledLayerRef.tileAnimations()`

Example:

```java
ground.tileAnimations().pause(10, 4);
ground.tileAnimations().play(10, 4);
ground.tileAnimations().setFrame(10, 4, 2);
ground.tileAnimations().restart(10, 4);
```

### Stop Semantics

```java
ground.tileAnimations().stop(10, 4);
```

Stopping clears the playback state for that cell.
It does **not** remove the logical tile asset from the map.

Dirty is raised only when the visible rendered frame actually changes.

### Non-Animated Cells

Calling tile animation playback methods on a non-animated cell is a no-op.

### Animated Tile Definition View Contract

`TiledAnimationsAPI.get(...)` returns an ephemeral read view.

Example:

```java
TileAnimationDefView view = api.tiled().animations().get(100);
```

Important:

- the returned view may be reused internally
- it is **not** a stable snapshot
- read it immediately and do not retain it long-term

## Shader Registry

The runtime also exposes:

- `getShaderRegistry()`

This gives access to shader registration and shader metadata used by the runtime.

## Physics

Pixscape Runtime integrates Box2D-related runtime services through:

- `getBox2dWorldService()`
- `getBox2dSyncSystem()`

These APIs are intended for gameplay code that needs direct access to the active physics world service and synchronization system.

## Asset and Rendering Services

Advanced runtime integrations can use:

- `getAtlasRuntimeService()`
- `getRenderState()`
- `getLayerState()`
- `getDrawList()`
- `getMetricsBatch()`
- `getRenderStats()`
- `getRenderStatsSink()`

These APIs are mainly useful for debugging, tooling, and advanced runtime extensions.

## Current MVP Scope

The current public runtime API is intentionally focused:

- single main camera
- no public multi-camera API
- no public post-FX chain API
- no public offscreen/FBO rendering API
- no public builder/creation API in v1

The goal is to keep the runtime API smaller, coherent, and practical.

## Requirements

JDK 21

## License

Licensed under the Apache License 2.0.

Commercial use is allowed under the terms of the license.
