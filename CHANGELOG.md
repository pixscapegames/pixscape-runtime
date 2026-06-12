# Changelog

## [0.1.6]

### Added

* Added Runtime Profiler for lightweight frame, update, render, and system timing diagnostics.
* Added one-shot tiled animation playback API for triggering tiled animations from runtime code.
* Added new SOA-based spatial system foundation for deterministic 2.5D actor/block ordering.
* Added spatial rule support for authored spatial blocks and actor/tile occlusion behavior.
* Added default spatial tiled ordering for spatial-enabled isometric tiled layers.
* Added `pixscape.tiled.spatialSortVerify=true` compact verification logging for spatial tiled ordering.

### Changed

* Spatial-enabled isometric tiled layers now use spatial tiled ordering by default.
* Spatial tiled ordering now preserves ISO depth while grouping exclusive block anchors inside the same ISO diagonal.
* Shared spatial block anchors are now treated as junction anchors and no longer disable spatial tiled ordering.
* Improved spatial ordering stability by keeping tiled anchors compatible with the actor/block interval planner.
* Added `pixscape.tiled.spatialSort=false` debug opt-out to restore default ISO tiled ordering when needed.

### Fixed

* Fixed spatial tiled anchor interleaving that could make actors render on the wrong side of isometric walls.
* Fixed false spatial ordering conflicts caused by shared corner/junction tiles expanding block anchor intervals.


## [0.1.5]

### Added
- Introduced the new 2.5D Spatial System for isometric and orthographic worlds.
- Added authorable `SpatialBlock` volumes for deterministic front/behind occlusion.
- Added support for altitude-aware spatial layers and multi-level environments.
- Added automatic actor-to-environment spatial sorting based on physical footprints.
- Added seamless front/behind rendering transitions for walls, columns, and architectural structures.
- Added runtime support for authored spatial occluders and linked tile groups.

### Improved
- Added Android-compatible support to `PhysicsDragMouseSystem`, allowing the same drag-based physics interactions to work consistently across Desktop and Android platforms.

### Fixed
- Fixed runtime preview culling for isometric tiled maps so chunks entering the top of the viewport are rendered before visible gaps can appear.
- Expanded isometric tiled culling bounds to account for tall tile sprites that extend beyond their chunk's logical bounds.


## [0.1.4]

### Added
- Added the high-level `PixscapeAPI` runtime entry point with dedicated accessors for assets, sprites, animations, particles, tiled maps, prefabs, entities, and expert ECS usage.
- Added runtime asset lookup through `AssetsAPI`, including atlas region resolution by asset name or asset id.
- Added sprite spawning through `SpritesAPI`, with fluent `SpriteRef` controls for transform, tint, alpha, shader, and removal.
- Added animation spawning through `AnimationsAPI`, with `AnimationRef` controls for playback, clips, looping, FPS, transform, shader, and removal.
- Added runtime animation definition loading, animation clip data, and `AnimationRegistry` support for exported animation assets.
- Added particle spawning through `ParticlesAPI`, including regular and one-shot particle effects.
- Added tiled animation registry APIs for lookup, registration, replacement, removal, and clearing of animated tile definitions.
- Added tiled layer editing helpers for setting static or animated tiles by asset id or animation name.
- Added public Javadoc coverage for the new runtime API surface.

### Changed
- Extended `PixscapeEngine` to expose and back the new runtime API facade.
- Updated runtime project loading to read exported animation metadata alongside scene and atlas data.
- Improved tiled map runtime editing so tile changes keep animated-cell playback state in sync.
- Extended GWT module configuration for the new API, animation, and tiled runtime support.

### Fixed
- Avoided ECS lockups during tiled map edits by extending tiled APIs to mutate runtime tile data through dedicated facades.
- Preserved tile animation definitions and lookup behavior when loading runtime projects.
- Expanded API, animation loading, and tile animation tests for the 0.1.4 runtime surface.

## [0.1.3]

### Breaking changes
- Moved `ShaderRegistry` from `games.pixscape.runtime.render` to `games.pixscape.runtime.service`
- Moved `TextureRegistry` from `games.pixscape.runtime.render` to `games.pixscape.runtime.service`
- Renamed `ShaderMode.SPRITE` to `ShaderMode.TEXTURE_2D`

### Added
- Added `PlatformTarget` runtime configuration for platform-specific rendering setup
- Added platform-aware shader loading support:
    - `AUTO`
    - `DESKTOP_GL30`
    - `ANDROID_ES3`
    - `HTML_WEBGL2`
- Added `PixscapeEngine#setPlatformTarget(...)` to let applications choose the runtime platform target before loading a project
- Added platform-specific shader loading:
    - Desktop GL30 uses `assets/shaders/330`
    - Android ES3 uses `assets/shaders/300es`
    - HTML WebGL2 uses `assets/shaders/300es`
- Added experimental runtime prefab fragment support based on Artemis `SaveFileFormat`.
- Added `RuntimePrefabFragmentSpawner` for ECS-based prefab instantiation without Studio dependencies.
- Added `PixscapeEngine.spawnPrefabFragment(...)` for spawning ECS prefab fragments at runtime.
- Added `PixscapeEngine.spawnPrefab(...)` convenience method to load and spawn prefab fragments from the runtime project.
- Added runtime asset resolution for spawned prefab entities using `AtlasRuntimeService.resolveCached(...)`.
- Added `preRenderCustomizer` / `postRenderCustomizer` hooks in `WorldConfigFactory`
- Added full HTML WebGL2 runtime support for Pixscape projects.
- Added GWT-safe shader uniform parameter storage using typed `Array<ShaderFloatParam>` instead of `ObjectFloatMap`.
- Added typed array factory support for shader float params to avoid GWT array cast issues.
- Added WebGL2-safe shader uniform application using cached uniform locations and explicit shader binding before uniform updates.
- Added runtime tiled map support for HTML WebGL2 preview/export.
- Added `PixscapeEngine#setLogLevel(...)` runtime API for configuring LibGDX log verbosity (`LOG_NONE`, `LOG_ERROR`, `LOG_INFO`, `LOG_DEBUG`).

### Improved
- Renamed `ShaderMode.SPRITE` to `ShaderMode.TEXTURE_2D` for clearer shader mode semantics
- Kept shader lookup simple at runtime: shaders are still resolved by name or index after platform-specific initialization
- Improved shader mode naming around texture binding strategy:
    - `TEXTURE_2D`
    - `MULTI_TEXTURE`
    - `TEXTURE_ARRAY`
- Cleaned up `ShaderRegistry` and removed unused/internal legacy code paths
- Kept legacy shader directory compatibility for `TEXTURE_2D`, which still maps to `sprite`
- Improved runtime architecture by separating Studio prefab editing pipeline from runtime instantiation pipeline.
- Prefab spawning no longer relies on manual component copying and instead uses Artemis serialization-based instantiation.
- Asset resolution for spawned entities now follows the same path as scene loading (`rebindAtlas` / runtime atlas resolution).
- Improved robustness of atlas loading by supporting multi-page atlas outputs generated by the Studio
- Improved shader uniform handling for WebGL2 by avoiding libGDX map implementations that are fragile under GWT.
- Improved render submit performance by caching shader uniform locations per shader.
- Improved tiled runtime initialization by rebuilding dense tiled data from sparse serialized layer data instead of serializing runtime-only chunk state.
- Improved runtime logging configurability by exposing public log-level control instead of relying only on the default internal INFO configuration.

### Fixed
- Updated runtime tests after shader service package moves and shader mode renaming
- Fixed physics joint serialization/remapping test to validate serialized entity reference consistency without depending on Box2D joint construction details
- Fixed outdated physics test calls that still used old joint creation signatures

### Notes
- Prefab runtime support is currently experimental and intended for internal validation.
- Prefab fragments (`.pixfragment.json`) are generated by the Studio and consumed by the runtime; they are not yet a stable public format.
- Runtime prefab spawning requires assets to be present in the exported runtime project (atlases, animations, etc.).
- HTML WebGL2 is now considered functional for runtime preview/export.
- Runtime-only tiled structures such as chunk bounds and dirty state remain transient and are rebuilt after scene load.


## [0.1.2]

### Added
- Box2D joints: Friction, Motor, Weld, Pulley, Gear
- Isometric tiled map support
- Tiled tile transformation support: flip horizontal, flip vertical, diagonal flip, and 90° rotations
- Tiled animations support

### Improved
- Tiled rendering no longer forces a full rebuild when chunks become visible again
- Render pipeline now bounds the ECS draw-list scan and appends visible tiled slots directly
- Atlas rebind now safely invalidates tiled chunks before rebuild
- Camera panning over tiled maps is smoother
- Packed and unpacked tiled rendering now produce consistent transformed quads and sorting in isometric mode
- Culling calculations were reduced
- Runtime hot paths were optimized to eliminate avoidable allocations and minimize garbage collection

### Fixed
- Restored VFX / particle rendering after draw-list extraction optimizations
- Preserved tiled transformations when tiled maps are rebuilt or resized
- Fixed tiled animation atlas dependencies during repack and cleanup
- Fixed tiled animation preview and placement ghost rendering

## [0.1.1] - 2026-03-22

### Added
- Parallax support for light layers

### Fixed
- Fixed light layers being ignored by the parallax pipeline
