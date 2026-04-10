# Changelog

## [0.1.2]

### Added
- Box2D joints: Friction, Motor, Weld, Pulley, Gear
- Isometric tiled map support
- Tiled tile transformations support: flip horizontal, flip vertical, diagonal flip and 90° rotations

### Improved
- Tiled rendering: removed the forced full rebuild when chunks become visible again
- Render pipeline: bounded the ECS draw-list scan and appended visible tiled slots directly
- Tiled rendering: atlas rebind now safely invalidates tiled chunks before rebuild
- Tiled rendering: camera panning over tiled maps is smoother
- Tiled rendering and tiled fallback now use the same transformed tile quad generation
- Packed and unpacked tiled rendering now produce consistent sorting and visual results in isometric mode
- Culling: reduction of calculations

### Fixed
- VFX / particle rendering was restored after draw-list extraction optimizations
- Tiled transformations are now preserved when tiled maps are rebuilt or resized

## [0.1.1] - 2026-03-22

### Added
- Added parallax support for light layers.

### Fixed
- Fixed light layers being ignored by the parallax pipeline.