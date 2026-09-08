<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

<h1>Pixscape Runtime</h1>

[![Maven Central](https://img.shields.io/maven-central/v/games.pixscape/pixscape-runtime.svg)](https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime)
[![Changelog](https://img.shields.io/badge/changelog-0.2.1-orange.svg)](CHANGELOG.md)<br>
[![Platforms](https://img.shields.io/badge/platforms-Desktop%20%7C%20Android%20%7C%20HTML5-green.svg)](#)<br>
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**High-performance ECS-first 2D runtime for Pixscape, built on LibGDX and Artemis-ODB.**

➡️ **Download Pixscape Studio Free:** https://pixscape.games/</br>
🌐 **Website:** https://pixscape.games/</br>
📘 **Documentation:** https://pixscape.games/docs</br>
📝 **Changelog:** [CHANGELOG.md](CHANGELOG.md)

![Pixscape Studio Free](assets/readme/pixscape-studio-free.png)

## What is Pixscape Runtime?

Pixscape Runtime is the open-source engine layer powering **Pixscape Studio Free** and exported Pixscape projects.

It is a performance-oriented 2D and 2.5D runtime built on **LibGDX** and **Artemis-ODB ECS**, designed for fast rendering, deterministic composition, editor-driven workflows and multiplatform deployment.

Pixscape Runtime provides scene management, ECS entities, rendering, Tiled Maps, Game Objects, physics, animation, particles, shaders, lights, asset availability, tileset profiles, repeated renderables and deterministic 2.5D Spatial ordering.

Pixscape Studio Free is the recommended visual authoring environment for Pixscape.

The Runtime can also be integrated directly into existing LibGDX projects for developers who want engine-level access while keeping LibGDX APIs and ecosystem libraries available alongside Pixscape.

## Highlights

* **LibGDX-based runtime**
* **Artemis-ODB ECS architecture**
* **SOA-oriented rendering pipeline**
* **Universal Layer composition**
* **First-class Tiled Map entities**
* **Multiple Tiled Maps per Scene and Layer**
* **Hierarchical Game Objects**
* **Game Object spawning and lifecycle API**
* **Game Object physics, joints and Spatial Actor support**
* **Fast sprite and tiled rendering**
* **Texture-array batching**
* **Async atlas workflows**
* **Runtime asset availability system**
* **Tileset-profile-aware tiled placement**
* **Animation and particle runtime support**
* **Shader and light pipeline**
* **Box2D physics integration**
* **Spatial V3 deterministic 2.5D ordering**
* **Typed entity custom properties**
* **Authored rectangle, polygon and polyline geometry**
* **Direct quad deformation**
* **Animated Tiled object entities**
* **Desktop, Android and HTML5/WebGL2 deployment**

## Runtime Features

### Rendering

* Sprite rendering
* First-class Tiled Map rendering
* Orthographic and isometric tiled support
* Multiple independently configured Tiled Maps
* Universal Layer and z-order composition
* Texture-array batching
* Multi-texture batching
* Runtime culling
* Deterministic render ordering
* Spatial V3 2.5D ordering
* Tileset-profile-aware tile placement
* Native-size tile rendering with anchors and offsets
* Tiled transform flag support
* Efficient repeated renderables
* Direct quad deformation
* Render diagnostics for texture binds, flushes, projection uploads and region-cache resolution

### Tiled Maps

Tiled Maps are first-class Runtime entities with their own identity and configuration.

Each Map can define its own:

* projection
* tile size
* map dimensions
* chunk size
* origin
* Spatial Depth configuration
* collision state

Multiple Tiled Maps can coexist inside the same Scene or Layer.

Each Map preserves its internal tile ordering while participating as a complete composition block in the normal Layer and z-order pipeline.

### Game Objects

Game Objects provide a hierarchical authoring and runtime model while remaining fully backed by ECS entities.

Runtime support includes:

* hierarchical parent/child relationships
* reusable Game Object assets
* Game Object spawning
* hierarchy-aware transforms
* atomic composition
* runtime lifecycle and removal
* physics bodies and shapes
* physics joints
* Spatial Actors
* Runtime Availability integration

Individual entities remain accessible through Pixscape's Runtime APIs.

### Spatial V3

Pixscape Runtime includes Spatial V3, a deterministic ordering system for actors and environment structures in orthographic and isometric scenes.

Spatial V3 supports:

* connected wall structures with automatic merge and split handling
* deterministic corner and junction rules
* exposed-face compilation for tiled structures
* canonical static tile ranks
* actor ordering based on authored physics footprints
* altitude-aware structures and multi-level environments
* stable actor, wall and tiled ordering without frame-to-frame flicker
* Spatial Actors inside ordinary Layers and Game Objects

### Assets

* Async atlas workflows
* Indexed runtime asset bindings
* Runtime asset availability declarations
* Runtime sprite access
* Runtime animation definitions
* Runtime particle definitions
* Runtime Game Object assets
* Scene export compatibility with Pixscape Studio Free
* Tileset profile manifest loading
* Tiled animation metadata loading
* Progressive scene resource loading

### Animation

* Frame-based animation playback
* Animation clips
* Runtime animation metadata
* Horizontal flip metadata
* Dynamic animation spawning
* Multiple authored animations per animation entity
* Atomic animation switching by asset ID or name
* Read-only animation definition queries
* Animated Tiled object playback

### Physics

* Box2D integration
* Persistent authored physics shapes
* Runtime physics synchronization
* Physics-based actor footprints for Spatial ordering
* Game Object physics hierarchy support
* Physics joints inside Game Objects
* Mouse/touch drag helper system
* High-level Physics API for runtime state, scale, parallax and Box2D body/world access

### Entity Data

Runtime entities can expose authored Studio data through supported APIs, including:

* typed custom properties
* tags
* rectangle geometry
* polygon geometry
* polyline geometry
* direct quad deformation
* render order
* animation state
* physics state

## Platforms

Pixscape Runtime targets:

* Desktop
* Android
* HTML5 / WebGL2

Pixscape Runtime is built with JDK 21 tooling and published as Java 8-compatible bytecode for broad LibGDX ecosystem compatibility.

Pixscape Studio Free requires Java 21.

iOS/RoboVM is not currently listed as an officially tested target.

## Installation

Pixscape Runtime is available from Maven Central.

### Gradle

```gradle
dependencies {
    implementation "games.pixscape:pixscape-runtime:0.2.1"
}
```

### Maven

```xml
<dependency>
    <groupId>games.pixscape</groupId>
    <artifactId>pixscape-runtime</artifactId>
    <version>0.2.1</version>
</dependency>
```

Maven Central:</br>
https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime</br>

## Documentation

Full documentation is available on the official website:</br>

📘 https://pixscape.games/docs</br>

Runtime Java APIs are divided into `HIGH_LEVEL`, `SUPPORTED_EXPERT`, and
`INTERNAL` support levels. See [RUNTIME_API_SUPPORT_POLICY.md](RUNTIME_API_SUPPORT_POLICY.md)
for compatibility, lifecycle, and ownership expectations.

## Download Pixscape Studio Free

Pixscape Studio Free builds are available here:</br>

➡️ https://pixscape.games/</br>

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## License

Pixscape Runtime is released under the **Apache License 2.0**.

See [LICENSE](LICENSE) for details.
