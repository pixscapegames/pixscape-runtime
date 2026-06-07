<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

<h1>Pixscape Runtime</h1>

[![Maven Central](https://img.shields.io/maven-central/v/games.pixscape/pixscape-runtime.svg)](https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime)
[![Changelog](https://img.shields.io/badge/changelog-0.1.5-orange.svg)](CHANGELOG.md)<br>
[![Platforms](https://img.shields.io/badge/platforms-Desktop%20%7C%20Android%20%7C%20HTML5-green.svg)](#)<br>
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**High-performance 2D runtime for Pixscape Studio, built on LibGDX and Artemis-ODB.**

➡️ **Download Pixscape Studio:** https://github.com/pixscapegames/pixscape-studio-releases  
🌐 **Website:** https://pixscape.games/  
📘 **Documentation:** https://pixscape.games/docs  
📝 **Changelog:** [CHANGELOG.md](CHANGELOG.md)

![Pixscape Studio](docs/images/pixscape-studio.png)

## What is Pixscape Runtime?

Pixscape Runtime is the open-source runtime core powering **Pixscape Studio**.

It is a performance-oriented 2D game runtime built on top of **LibGDX** and **Artemis-ODB ECS**, designed for lightweight workflows, fast iteration, deterministic rendering, and multiplatform deployment.

Pixscape Runtime provides the low-level engine layer used by Pixscape Studio exports, including rendering, tiled maps, physics integration, animation playback, particles, shaders, lights, prefabs, runtime asset availability, and 2.5D spatial ordering.

Pixscape Studio is the recommended way to build games with Pixscape.  
This runtime repository is also available separately for developers who want direct engine-level access, advanced customization, or full integration inside existing LibGDX projects.

## Highlights

- **LibGDX-based runtime**
- **Artemis-ODB ECS architecture**
- **SOA-oriented rendering pipeline**
- **Fast sprite and tiled rendering**
- **Async atlas workflows**
- **Runtime asset availability system**
- **Prefab loading support**
- **Animation and particle runtime support**
- **Shader and light pipeline**
- **Box2D physics integration**
- **2.5D spatial ordering**
- **Desktop, Android and HTML5/WebGL2 deployment**

## 2.5D Spatial System

Pixscape Runtime `0.1.5` introduces the first version of the new **2.5D Spatial System**.

This system allows actors, tiled structures, walls, columns, and elevated environment elements to render in a coherent front/behind order without relying on expensive real-time raytracing.

The spatial system is designed around deterministic ordering:

- actors can move naturally in front of or behind environment structures
- spatial blocks define authorable occlusion zones
- altitude-aware layers support multi-level environments
- actor/environment ordering can use physics footprints
- tiled structures can participate in spatial sorting
- the final draw order is computed deterministically before rendering

This provides a foundation for richer 2.5D scenes while keeping the runtime lightweight and fast.

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
- 2.5D spatial ordering

### Assets

- Async atlas workflows
- Runtime asset availability declarations
- Runtime sprite access
- Runtime animation definitions
- Runtime particle definitions
- Runtime prefab loading
- Scene export compatibility with Pixscape Studio

### Animation

- Frame-based animation playback
- Animation clips
- Runtime animation metadata
- Horizontal flip metadata
- Dynamic animation spawning

### Physics

- Box2D integration
- Runtime physics synchronization
- Physics-based actor footprint support for spatial ordering
- Mouse/touch drag helper system

### Platforms

Pixscape Runtime targets:

- Desktop
- Android
- HTML5 / WebGL2

## Installation

Pixscape Runtime is available from Maven Central.

### Gradle

```gradle
dependencies {
    implementation "games.pixscape:pixscape-runtime:0.1.5"
}
```

### Maven

```xml
<dependency>
    <groupId>games.pixscape</groupId>
    <artifactId>pixscape-runtime</artifactId>
    <version>0.1.5</version>
</dependency>
```

## Documentation

Full documentation is available on the official website:

📘 https://pixscape.games/docs

## Download Pixscape Studio

Pixscape Studio builds are available here:

➡️ https://github.com/pixscapegames/pixscape-studio-releases

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## License

Pixscape Runtime is released under the **Apache License 2.0**.

See [LICENSE](LICENSE) for details.
