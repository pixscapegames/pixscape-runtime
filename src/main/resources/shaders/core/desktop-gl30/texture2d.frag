#version 330 core

#include "pixscape_common.glsl"

in vec2 v_uv;
in vec4 v_color;

uniform sampler2D u_texture;
uniform vec3 u_ambientMul;

out vec4 fragColor;

void main() {
    vec4 texel = texture(u_texture, v_uv);
    fragColor = pixscapeApplyMaterial(texel, v_color, u_ambientMul);
}
