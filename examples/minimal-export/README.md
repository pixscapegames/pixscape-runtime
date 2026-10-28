# Minimal Pixscape Runtime project

This is a small, hand-assembled example of the **current** export layout. It was
constructed from the Runtime/Studio format contract, not exported by Studio.
The original 16 × 16 PNG was generated specifically for this example. The
example files are distributed under the accompanying Apache-2.0 `LICENSE`.
No `tiled-iso-demo` artwork is included.

Pass this directory (the one containing `project.json`) as the Runtime project
directory. Select scene `Simple`. It contains one real Layer and one textured
content entity with a distinct stable ID. The scene stores `TransformComponent`
and `EntityIndexComponent` on the content entity; its visible flag, texture
region marker and render material are inherited from archetype 2. The atlas
region `square__a1` resolves `AssetRefComponent.assetId = 1`.

`animations.json`, `tiled-animations.json` and `tileset-profiles.json` are empty
because this scene has no animation or tiled map. No HUD, Game Object, particle,
audio or custom shader is needed. Runtime supplies its core shaders from its own
resources. Application code still supplies a camera/viewport and chooses the
scene; JSON alone does not create a runnable application.

From `pixscape-runtime/schemas`, run `npm ci` and
`npm run validate -- ../examples/minimal-export`. The Runtime test
`PublishedExportLoadTest` separately loads this scene with `SceneLoader` and
parses the atlas descriptor without an OpenGL context. No visual render is
claimed for this hand-assembled example.
