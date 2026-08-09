# Changelog


## [0.1.9]

### Breaking changes

* Replaced the legacy fixture authoring schema with persistent `PhysicsShapeData` and `PhysicsGeometryData`.
* Physics scenes and prefab data using the previous schema must be recreated or re-exported.
* Moved spatial domain classes to `games.pixscape.runtime.spatial` and Artemis spatial components to `games.pixscape.runtime.component.spatial`.
* Removed `ParticleEmitterComponent.localSpace` and `ParticleFacade#setLocalSpace(boolean)`.
* Particle effects now always follow their owning entity at `TransformComponent.x/y`; transform origin is no longer applied to emitter positioning.
* Spatial actors now require an explicitly authored physics footprint; ordinary circle fixtures are no longer inferred automatically.
* Removed Tiled layer lookup by Studio display name from `TiledAPI`; Runtime tiled layers must now be accessed by exported layer index, entity ID or stable ID.
* Runtime no longer lazily acquires or prepares undeclared scene resources after READY. Particle effects, prefab fragments and other resources used only dynamically must be declared through Runtime Availability before scene loading completes.

### Added

* Added scene-wide persistent physics shape identities with monotonic allocation and strict load validation.
* Added an authored-to-compiled physics pipeline with polygon validation, decomposition and derived fixture caches.
* Added linked physics shapes whose geometry is derived from Spatial Block footprints.
* Added synchronization between compiled physics footprints and Spatial actor ordering.
* Added indexed atlas asset bindings grouped by Asset ID, with precomputed region metadata and animation frame groups.
* Added `EntityRef#renderOrder()` with high-level layer-index and z-index controls for runtime entities.
* Added runtime layer placement support for spawned particles, sprites, animations, prefabs and existing scene entities without requiring direct ECS component access.
* Added progressive scene loading through `SceneLoadHandle`, with phase, progress and runtime-ready state for custom loading screens.
* Added `PixscapeEngine#setAssetManager(...)` for applications that provide and manage a shared LibGDX `AssetManager`.
* Added exact resource discovery for the selected scene, including its scene data, atlas pages and declared particle effects.
* Added a high-level Physics API for runtime state, pixels-per-meter, parallax conversion and native Box2D world/body access.
* Added advanced render hooks for custom extraction, submission and integration around the Runtime render pipeline.
* Added animation state and query controls, plus repeatable sprite creation and mutation through the public Runtime API.
* Added Runtime Availability support for declared prefab fragments, including progressive file acquisition during scene loading.
* Added a formal Runtime API support policy defining `HIGH_LEVEL`, `SUPPORTED_EXPERT` and `INTERNAL` compatibility levels.

### Changed

* Scene READY now guarantees acquisition and required heavyweight preparation of known scene dependencies and declared Runtime Availability resources; normal gameplay no longer performs implicit scene-resource loading or particle preparation after READY.
* Particle spawning and effect replacement now require the requested effect to have been prepared before READY and reject unavailable resources before mutating ECS state.
* Reduced Spatial query and frame-preparation overhead by avoiding full scene-layer and inactive slot-range scans.
* Box2D bodies are now built from validated compiled fixtures instead of legacy fixture data.
* Scene loading and prefab spawning now rebuild derived physics state from persistent authored shapes.
* Runtime prefab spawning now allocates fresh physics shape identities and validates body and joint graphs before publication.
* Runtime asset, sprite, animation, prefab and tiled rendering consumers now resolve atlas assets through the shared indexed binding model.
* `AssetRegionRef#region()` now returns a defensive texture-region snapshot that can be modified without altering the indexed atlas binding.
* Particle creation through the Runtime API now guarantees both `TransformComponent` and `ParticleEmitterComponent` without adding rectangular render or interaction proxy components.
* Runtime layer APIs now consistently use exported numerical layer indices instead of Studio-only display names.
* Runtime scene-layer metadata now consistently distinguishes authored layer entities from rendered actors carrying layer-placement components.
* Runtime z-index mutations now enforce the render pipeline's supported range of `-32768` through `32767`.
* Scene loading now follows one availability, construction and runtime-readiness pipeline across synchronous and progressive entry points.
* Project bootstrap no longer instantiates application custom systems before the first scene World is constructed.
* `PhysicsMouseDragSystem` can now use `PhysicsAPI` directly for lifecycle, parallax and pixel-to-meter conversion.
* Sprite and animation facades now recognize authored scene actors without requiring a layer-only `LayerComponent`.
* Runtime-ready completion now waits for heavy scene state, including particles, physics, Tiled maps, Spatial data and render preparation.
* Runtime references now preserve entity incarnation identity and report stale access consistently after removal or world replacement.

### Improved

* Asset lookup by Asset ID now uses the atlas binding index instead of repeatedly scanning and regrouping atlas regions.
* Reduced repeated atlas resolution work during sprite and animation spawning, entity rebinds, prefab instantiation and tiled rendering.
* Animation systems now reuse indexed frame groups and precomputed region metadata.
* Atlas loading now builds and validates the asset index once for each published atlas, while unload operations clear the corresponding bindings.
* Centralized texture handle, UV and pixel-size resolution around shared atlas metadata.
* Scene and prefab state is now fully validated before it becomes visible through Runtime facades.
* Atlas publication now reuses pages already loaded by the configured `AssetManager` instead of loading duplicate textures.
* Released temporary texture-array CPU image backing after GPU upload, avoiding retained GWT Pixmap/canvas memory.

### Fixed

* Fixed stale or duplicated physics shape identities across scene loading and prefab instantiation.
* Prevented invalid body, fixture, polygon or joint data from being partially published.
* Fixed Spatial actor footprints becoming stale after physics or pixels-per-meter changes.
* Prevented callers from mutating the texture-region object owned by an indexed atlas binding through `AssetRegionRef`.
* Fixed Spatial actor ordering across multiple actor layers by using a single global depth domain.
* Prevented rendered actor metadata from being interpreted as authored layer metadata by Tiled, particle visibility and Spatial systems.
* Prevented invalid preserved z-index values from passing through runtime layer changes.
* Made particle effect replacement failure-atomic so a failed replacement preserves the previously published effect.
* Fixed particle extraction to preserve premultiplied-alpha blend semantics.
* Fixed Spatial participation and invalid typed Tiled references across removal, replacement and scene transitions.
* Fixed stale facade and render-order access after entity removal or world replacement.

### Tests

* Expanded regression coverage for physics persistence, compilation, prefabs, joints, Spatial integration and stable-frame behavior.
* Added regression coverage for atlas index construction, asset grouping, indexed API resolution, animations, prefabs and tiled rendering.
* Revalidated the indexed asset pipeline on Desktop and through forced GWT compilation.
* Added regression coverage for particle transform positioning, proxy-free particle creation and the removal of the legacy local-space API.
* Added regression coverage for runtime render-order mutation, z-index boundaries, index-only Tiled access and authored-layer isolation from rendered actors.
* Added grouped coverage for progressive loading, exact availability, shared `AssetManager` reuse and runtime-ready completion across particles, physics, Tiled, Spatial and rendering.
* Added grouped facade identity and failure-contract coverage, with forced GWT compilation and Java 8 compatibility validation.
* Added regression coverage for declared prefab availability, strict pre-READY particle preparation and deferred HTML resource delivery without post-READY gameplay loading.

## [0.1.8]

### Added

* Added runtime loading for exported `tileset-profiles.json` metadata.
* Added runtime tileset profile lookup by tile asset ID.
* Added profile-aware tiled sprite placement support for native-size tiles.
* Added support for tileset anchors, cell size, projection, offsets and native render size in tiled placement.
* Added profile-aware placement tests for tall isometric tiles, offsets and anchor behavior.
* Added efficient repeated renderables for backgrounds and parallax-style layers. Repeated images are expanded at render-submit time while preserving atlas and batch compatibility.
* Added render statistics for TextureArray batch diagnostics:
  * texture binds
  * texture array bind skips
  * shader binds
  * projection uploads
  * submitted quads
  * flushed quads
  * flushed vertices
  * capacity-triggered flushes
  * region resolve cache hits and misses
* Added the Spatial V3 wall and structure model.
* Added deterministic wall junction, merge, and split handling.
* Added compiled Spatial V3 structure geometry.
* Added canonical static tile ranks for Spatial-enabled tiled layers.
* Added source-aware Spatial layer runtime ownership to prevent scene-scoped caches from being reused with a different tiled map source.

### Changed

* Tiled runtime rendering now uses tileset profile placement when profile metadata is available.
* Tiled transform flags are still applied after base profile placement.
* Runtime project loading now carries tileset profile metadata into tiled rendering systems.
* Optimized TextureArrayMeshBatch region resolution with a small RegionResolveCache.
* Reduced redundant TextureArray state changes across batch flushes. 
* Improved TextureArray batch state tracking for projection uploads, shader uniforms and texture array binding.
* Spatial-enabled tiled layers now require canonical tile ranks; missing ranks fail explicitly instead of falling back to ordinary isometric ordering.
* Spatial layer runtime caches now use both the layer entity and tiled-map source identity when validating cached structures, projected faces and anchors.

### Fixed

* Fixed runtime placement support for tiles whose native image size differs from their logical tiled cell size.
* Fixed tall/isometric tiles requiring explicit placement metadata instead of relying on implicit tile-size assumptions.
* Fixed missing tileset profiles being reported as diagnostics instead of silently falling back to legacy placement.
* Fixed diagonal tiled transform mappings so all 8 Tiled flip/diagonal combinations match Tiled's expected rendering behavior.
* Avoided unnecessary TextureArray rebinds during repeated batch flushes when the active bundle has not changed.
* Fixed spatial ordering issues in complex tiled wall layouts.
* Fixed inconsistent rendering around wall corners, junctions, and enclosed tiled structures.
* Fixed small spatial ordering artifacts near tiled wall seams and corners by using the full circular actor footprint instead of only the actor center.
* Fixed missing actor/face relations when a circular footprint overlaps adjacent spatial slices at tiled junctions.
* Fixed Spatial ordering becoming inactive after scene changes when Artemis reused layer entity IDs and pooled component instances.
* Fixed stale projected-face anchors and missing actor/face relations after Spatial scene A → B → A activation sequences.
* Fixed Spatial tile synchronization silently using ordinary isometric ordering when a required canonical rank was missing.

### Improved
* Added deterministic ordering rules for tiled spatial junctions.
* Improved render extraction diagnostics for ECS slots, including emitted/skipped slot details and skip reasons.
* Improved dynamic actor ordering accuracy around complex 2.5D tiled structures.

### Tests

* Added runtime tileset profile manifest loading tests.
* Added regression coverage for sprite/body lifecycle rendering and spatial tiled ordering.
* Added regression tests for circle footprint spatial relations, including corners, seams, multi-slice coverage, large/small radii, deduplication, and flicker prevention.
* Added tiled profile placement helper tests.
* Added tiled render synchronization coverage for profile-aware placement and transform flags.
* Added regression coverage for canonical Spatial tile-rank enforcement, source-aware cache ownership and A → B → A scene activation.


## [0.1.7]

### Changed

* Runtime artifacts now target Java 8-compatible bytecode while continuing to build with JDK 21.


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
