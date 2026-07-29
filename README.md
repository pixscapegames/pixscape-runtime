<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

<h1>Pixscape Runtime</h1>

[![Maven Central](https://img.shields.io/maven-central/v/games.pixscape/pixscape-runtime.svg)](https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime)
[![Changelog](https://img.shields.io/badge/changelog-0.1.9-orange.svg)](CHANGELOG.md)<br>
[![Platforms](https://img.shields.io/badge/platforms-Desktop%20%7C%20Android%20%7C%20HTML5-green.svg)](#)<br>
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**High-performance 2D runtime for Pixscape Studio Free, built on LibGDX and Artemis-ODB.**

➡️ **Download Pixscape Studio Free:** https://github.com/pixscapegames/pixscape-studio-releases  
🌐 **Website:** https://pixscape.games/  
📘 **Documentation:** https://pixscape.games/docs  
📝 **Changelog:** [CHANGELOG.md](CHANGELOG.md)

![Pixscape Studio Free](docs/images/pixscape-studio.png)

## What is Pixscape Runtime?

Pixscape Runtime is the open-source runtime core powering **Pixscape Studio Free** and exported Pixscape projects.

It is a performance-oriented 2D game runtime built on top of **LibGDX** and **Artemis-ODB ECS**, designed for lightweight workflows, fast iteration, deterministic rendering, and multiplatform deployment.

Pixscape Runtime provides the low-level engine layer used by Pixscape Studio exports, including rendering, tiled maps, physics integration, animation playback, particles, shaders, lights, prefabs, runtime asset availability, tileset profiles, repeated renderables, and deterministic 2.5D spatial ordering.

Pixscape Studio Free is the recommended way to build games with Pixscape.  
This runtime repository is also available separately for developers who want direct engine-level access, advanced customization, or full integration inside existing LibGDX projects.

## Highlights

- **LibGDX-based runtime**
- **Artemis-ODB ECS architecture**
- **SOA-oriented rendering pipeline**
- **Fast sprite and tiled rendering**
- **Async atlas workflows**
- **Runtime asset availability system**
- **Tileset-profile-aware tiled placement**
- **Efficient repeated renderables**
- **Prefab loading support**
- **Animation and particle runtime support**
- **Shader and light pipeline**
- **Box2D physics integration**
- **Spatial V3 deterministic 2.5D ordering**
- **Desktop, Android and HTML5/WebGL2 deployment**

## Spatial V3 — Deterministic 2.5D Ordering

Pixscape Runtime includes Spatial V3, a deterministic ordering system for actors and tiled environment structures in orthographic and isometric scenes.

Spatial V3 supports:

- connected wall structures with automatic merge and split handling
- deterministic corner and junction rules
- exposed-face compilation for tiled structures
- canonical static tile ranks for Spatial-enabled tiled layers
- source-aware Spatial layer runtime caches
- actor ordering based on circular physics footprints
- altitude-aware structures and multi-level environments
- stable actor, wall and tiled ordering without frame-to-frame flicker

## Runtime Features

### Rendering

- Sprite rendering
- Tiled map rendering
- Orthographic and isometric tiled support
- Texture array batching
- Multi-texture batching
- Layered rendering
- Runtime culling
- Deterministic render ordering
- Spatial V3 2.5D ordering
- Tileset-profile-aware tiled placement
- Native-size tile rendering with anchors and offsets
- Tiled transform flag support
- Efficient repeated renderables
- Render diagnostics for texture binds, flushes, projection uploads and region-cache resolution

### Assets

- Async atlas workflows
- Runtime asset availability declarations
- Runtime sprite access
- Runtime animation definitions
- Runtime particle definitions
- Runtime prefab loading
- Scene export compatibility with Pixscape Studio Free
- Tileset profile manifest loading
- Tiled animation metadata loading

### Animation

- Frame-based animation playback
- Animation clips
- Runtime animation metadata
- Horizontal flip metadata
- Dynamic animation spawning

### Physics

- Box2D integration
- Runtime physics synchronization
- Physics-based actor footprint support for Spatial ordering
- Mouse/touch drag helper system

### Platforms

Pixscape Runtime targets:

- Desktop
- Android
- HTML5 / WebGL2

Pixscape Runtime is built with JDK 21 tooling and published as Java 8-compatible bytecode for maximum LibGDX ecosystem portability. Pixscape Studio Free requires Java 21.

iOS/RoboVM is not currently listed as an officially tested target.

## Installation

Pixscape Runtime is available from Maven Central.

### Gradle

```gradle
dependencies {
    implementation "games.pixscape:pixscape-runtime:0.1.8"
}
```

### Maven

```xml
<dependency>
    <groupId>games.pixscape</groupId>
    <artifactId>pixscape-runtime</artifactId>
    <version>0.1.8</version>
</dependency>
```

## Documentation

Full documentation is available on the official website:

📘 https://pixscape.games/docs

## Download Pixscape Studio Free

Pixscape Studio Free builds are available here:

➡️ https://github.com/pixscapegames/pixscape-studio-releases

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## License

Pixscape Runtime is released under the **Apache License 2.0**.

See [LICENSE](LICENSE) for details.
