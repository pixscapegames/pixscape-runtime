# Changelog

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