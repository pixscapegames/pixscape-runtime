#version 330 core

in vec2 v_uv;
in vec4 v_color;

uniform sampler2D u_texture;

uniform float u_worldCoverage;
out vec4 fragColor;

void main() {
    vec4 texel = texture(u_texture, v_uv);
    fragColor = texel * v_color;
    if (u_worldCoverage > 0.5) fragColor.a = 1.0;
}