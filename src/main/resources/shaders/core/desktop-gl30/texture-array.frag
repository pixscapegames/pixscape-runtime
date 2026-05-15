#version 330 core

#include "pixscape_common.glsl"

in vec2  v_uv;
in vec4  v_color;
flat in int v_layer;

uniform sampler2DArray u_array;
uniform float u_cutoutThreshold;
uniform vec3  u_ambientMul;

out vec4 fragColor;

void main() {
    vec4 tex = texture(u_array, vec3(v_uv, v_layer));
    if (u_cutoutThreshold >= 0.0 && tex.a < u_cutoutThreshold) discard;
    fragColor = pixscapeApplyMaterial(tex, v_color, u_ambientMul);
}