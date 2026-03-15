# Public Gameplay/Runtime API MVP Audit (develop snapshot)

## Scope
Audit focus:
1. direct access to Artemis `World`
2. helper access for entity lookup by name/tags
3. helper access for mappers, main camera, and runtime services
4. extension points for custom systems
5. role/usefulness of `RuntimeAPI`, `TagInjector`, `RuntimeModule`
6. HyperLap2D references to remove
7. multi-camera/FBO/post-FX items to hide/remove for mono-camera, no-FBO MVP

---

## 1) Current state inventory

### Runtime entry point and world access
- `PixscapeEngine` is the effective runtime entry point and already exposes direct world access via `getWorld()`. It also exposes the main camera and several render internals (`getCamera`, `getRenderState`, `getLayerState`, `getDrawList`, etc.).
- `PixscapeEngine` includes a `setConfigurationCustomizer(Consumer<WorldConfigurationBuilder>)` hook used to customize world configuration before `World` creation.

### ECS/system bootstrap
- `WorldConfigFactory.buildWorld(...)` builds a fixed baseline pipeline (`DirtyTrackerSystem`, `Box2dSyncSystem`, render systems, submit system, `DirtyFlushSystem`) and then executes an optional `customizer.accept(builder)`.
- This is currently the only real extension path for adding custom Artemis systems.

### Runtime API interfaces
- `RuntimeAPI`, `RuntimeModule`, `TagInjector` exist as public API contracts.
- There is no implementation/wiring path that bridges these interfaces into `PixscapeEngine`/`WorldConfigFactory` scene loading and bootstrap flow.
- Result: these interfaces are currently conceptual/stub-level, not operational.

### Entity lookup helpers by name/tag
- `IdentityRegistry` and `TagRegistry` provide solid helper APIs for lookups (`findByStableId`, `firstByName`, `first(tag)`, `get(tag)`, `addTag/removeTag`, etc.).
- However, neither registry is instantiated nor exposed by engine/runtime bootstrap.
- Result: helper APIs exist in codebase but are not available from the runtime public surface by default.

### Runtime services
- `AtlasRuntimeService` and `Box2dWorldService` are effectively integrated and exposed from `PixscapeEngine` (`getAtlasRuntimeService`, `getBox2dWorldService`, `getBox2dSyncSystem`).
- `PhysicsService`, `ZOrderRuntimeService`, `IdentityRegistry`, `TagRegistry` exist but are not managed/exposed by `PixscapeEngine`.

### Camera/post-FX/multi-camera state
- Current render execution path in `RenderSubmitSystem` is mono-camera (single `OrthographicCamera cam`) without any FBO/post-FX processing stage.
- Nonetheless, API and data surface still include forward-looking multi-camera/FBO/post-FX artifacts:
  - `RenderExtension` lifecycle with `beforeAllCameras/beforeCamera/afterCamera/afterAllCameras`
  - `RuntimeAPI.registerRenderExtension(...)`
  - `RuntimeAPI.registerPostFxPass(...)`
  - `render.fx` package (`PostFxPass`, `PostFxChain`, `PostFxRegistry`)
  - ECS components `CameraFxComponent`, `LayerPostFXComponent`, and `CameraSettingsComponent.useOffscreen`
  - `LayerStateBuildSystem` still derives `postFxChainId` from `LayerPostFXComponent`

### HyperLap2D references
- HyperLap2D appears in JavaDoc/comments in `RuntimeModule` and `TagInjector`.

---

## 2) Problems / inconsistencies

1. **Two parallel public API stories with only one actually working**
   - Working today: `PixscapeEngine` + raw Artemis access.
   - Non-working/aspirational: `RuntimeAPI` + `RuntimeModule` + `TagInjector` registration model.

2. **Extension point mismatch**
   - Real extension mechanism is `setConfigurationCustomizer(...)` (builder-level).
   - Public API interface advertises module-style registration (`registerSystem`, injectors, render extensions, post-FX passes) but no runtime integration implements it.

3. **Lookup/service helper gap**
   - Name/tag lookup registries and several helper services exist but are not lifecycle-managed or discoverable through engine API.

4. **MVP mismatch: mono-camera runtime vs exposed multi-camera/post-FX concepts**
   - Public-facing API still suggests multi-camera render hooks and post-FX pass registration.
   - Runtime core path is currently single-camera draw submission with no visible FBO/post-FX pipeline.

5. **Dead/weakly wired API surface increases maintenance cost**
   - Interfaces/components that compile but are not wired lead to false expectations for integrators.

6. **Branding/legacy leakage**
   - HyperLap2D references should be removed to avoid ecosystem confusion.

---

## 3) Recommended public API surface for MVP

For a **public gameplay/runtime API MVP**, prefer a single clear facade centered on `PixscapeEngine` (or a thin wrapper over it) and explicitly support mono-camera/no-FBO behavior.

### A. Keep and formalize
- Direct ECS access:
  - `World world()` / `getWorld()`
  - `OrthographicCamera mainCamera()` / `getCamera()`
- Minimal runtime services:
  - atlas service accessor
  - physics world/sync accessor
- Extension point for custom systems:
  - keep builder customizer (`Consumer<WorldConfigurationBuilder>`) as the MVP extension hook

### B. Add MVP helper accessors (high value)
- Mapper helper:
  - `ComponentMapper<T> mapper(Class<T>)`
- Entity lookup helpers:
  - `OptionalInt findEntityByStableId(long)`
  - `IntArray findEntitiesByName(String)` / `OptionalInt firstEntityByName(String)`
  - `IntArray findEntitiesByTag(String)` / `OptionalInt firstEntityByTag(String)`
- Managed helper services:
  - expose lazily-created `IdentityRegistry` and `TagRegistry`
  - optionally expose `PhysicsService` and `ZOrderRuntimeService`

### C. Document as intentionally out-of-scope for MVP
- Multi-camera orchestration
- Offscreen/FBO rendering control
- Post-FX graph/pass registration
- Camera/layer post-FX component-driven runtime behavior

---

## 4) Deprecations/removals (MVP cleanup)

### Remove or deprecate immediately
1. `RuntimeAPI.registerRenderExtension(...)`
2. `RuntimeAPI.registerPostFxPass(...)`
3. `RenderExtension` (or make internal/experimental)
4. `render.fx` package (`PostFxPass`, `PostFxChain`, `PostFxRegistry`) if no immediate runtime usage
5. `CameraFxComponent` (if not used by any active runtime path)
6. `LayerPostFXComponent` and `layerState.postFxChainId` write path for MVP
7. `CameraSettingsComponent.useOffscreen` for no-FBO MVP
8. HyperLap2D wording in docs/comments (`RuntimeModule`, `TagInjector`)

### Keep but mark clearly
- `RuntimeAPI`, `RuntimeModule`, `TagInjector`:
  - either fully wire them now, or mark `@Deprecated` + "not active in MVP runtime bootstrap" and route users to engine customizer.

---

## 5) Migration notes

### For runtime integrators
- If you currently rely on hypothetical module APIs (`RuntimeModule.register(...)`), migrate to:
  - `engine.setConfigurationCustomizer(builder -> builder.with(new MySystem(...)))`
- For post-FX/render hooks, remove usage in MVP and keep custom rendering external to runtime submit loop.

### For gameplay code
- Use `engine.getWorld()` as canonical ECS entry.
- Add stable helper access (`IdentityRegistry`, `TagRegistry`) via engine-managed services instead of ad-hoc aspect scans.

### For code maintainers
- Decide one of two paths quickly:
  1. **Wire `RuntimeAPI` for real**, or
  2. **Trim it from MVP public docs/surface** and keep only builder customizer + engine accessors.
- Avoid shipping both stories simultaneously without clear status labels.

---

## 6) Concrete file/class list impacted

### High-priority (public API alignment)
- `src/main/java/games/pixscape/runtime/engine/PixscapeEngine.java`
- `src/main/java/games/pixscape/runtime/loading/WorldConfigFactory.java`
- `src/main/java/games/pixscape/runtime/api/RuntimeAPI.java`
- `src/main/java/games/pixscape/runtime/api/RuntimeModule.java`
- `src/main/java/games/pixscape/runtime/api/TagInjector.java`

### Helper services exposure
- `src/main/java/games/pixscape/runtime/service/IdentityRegistry.java`
- `src/main/java/games/pixscape/runtime/service/TagRegistry.java`
- `src/main/java/games/pixscape/runtime/service/PhysicsService.java`
- `src/main/java/games/pixscape/runtime/service/ZOrderRuntimeService.java`

### Mono-camera/no-FBO cleanup candidates
- `src/main/java/games/pixscape/runtime/render/RenderExtension.java`
- `src/main/java/games/pixscape/runtime/render/fx/PostFxPass.java`
- `src/main/java/games/pixscape/runtime/render/fx/PostFxChain.java`
- `src/main/java/games/pixscape/runtime/render/fx/PostFxRegistry.java`
- `src/main/java/games/pixscape/runtime/component/CameraFxComponent.java`
- `src/main/java/games/pixscape/runtime/component/LayerPostFXComponent.java`
- `src/main/java/games/pixscape/runtime/component/CameraSettingsComponent.java`
- `src/main/java/games/pixscape/runtime/system/LayerStateBuildSystem.java`

---

## Bottom line
Current develop is close to an MVP runtime core operationally, but its public API story is inconsistent: the real extension/access path is engine+customizer, while interface-level module/post-FX hooks are mostly unwired. The shortest route to a solid public MVP is to formalize world/camera/service/lookup access on `PixscapeEngine`, and hide/deprecate non-operational multicam/post-FX surfaces until fully implemented.
