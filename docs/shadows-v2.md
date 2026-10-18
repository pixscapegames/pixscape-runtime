# Shadows V2

Actors cast onto decor. They receive light occluded by decor, but never shadows
from themselves or other actors. Ambient is independent of that visibility test;
available normal maps still affect direct lighting. Decor receives the combined
shadows of all casters. The renderer reuses two shared six-view atlases per
retained shadowed light, without per-actor exclusions or targets. Initial
geometry, render storage and GPU resources are prepared before scene READY;
viewport and shadow-setting changes prepare resources at the frame boundary.

See [actor calibration](actor-shadow-profile.md) for the foot directrix,
per-clip profiles and the single open alpha sheet used for compensation.

## Decor geometry and joins

Linked cells identify image anchors; they do not constrain physical wall
footprints. Vertical joins align height and faces without requiring an upper
wall to fit inside the lower linked-cell footprint.

When a tile belongs to several volumes, reception partitions the native image
quad using the nearest visible projected face and its affine depth. Selection
and coplanar caster submission are canonical in geometric coordinates, rather
than volume IDs or insertion order. This preserves the existing alpha and
caster geometry. Artwork outside its described support still uses the existing
coverage extrapolation. Irregular or curved tiles do not acquire a reconstructed
physical contour: their approximate supports can still produce incorrect
depths or corner coverage. This is not a general solution for nonrectilinear
tiles or multi-map ordering.

## Grazing light

One-sided simple decor faces use normalized geometric normals, independently
of normal-map normals. Actors and two-sided sheets keep their reception rules.
With normalized light direction L and geometric normal Ng:

```glsl
float cG = dot(Ng, L);
float w = smoothstep(0.052335956, 0.087155743, max(0.0, cG));
direct *= w;
```

This scales all direct light, including its 10% minimum: zero through 3 degrees,
a continuous ramp to 5 degrees, and the reference beyond 5 degrees. Ambient,
attenuation, alpha, shadow filtering, bias, resolutions and casters are unchanged.
At PCF 256, quantization of the existing nine binary visibility tests can still
produce a visible step inside the 3–5 degree ramp. The ramp smooths incidence,
not those visibility tests; perfect continuity is not claimed at that setting.

## Validation scope

Desktop GL30/GL330 was executed on NVIDIA GTX 1650, including native Studio and
Preview, hard/PCF shadows, actor decor-only occlusion, foot contacts and
compensation, grazing reception and shared-face permutations. The final corner
campaign compared 130 captures with reversed volume order at zero tolerance.
Measured corner-fix GPU times overlapped the reference series; no general FPS
gain is claimed.

Runtime production classes were compiled for Java 8 and both Studios' HTML
players passed GWT compilation. ES300 shader compilation through the desktop
NVIDIA compiler is separate evidence; it is not execution on a GLES device.
No real WebGL or Android GLES execution is claimed for these changes.
