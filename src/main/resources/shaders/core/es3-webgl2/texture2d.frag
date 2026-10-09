#version 300 es
precision mediump float;

in vec2 v_uv;
in vec4 v_color;

uniform sampler2D u_texture;

uniform float u_worldCoverage;
uniform float u_cutoutThreshold;
out vec4 fragColor;

void main() {
    vec4 texel = texture(u_texture, v_uv);
    if (u_cutoutThreshold >= 0.0 && texel.a < u_cutoutThreshold) discard;
    fragColor = texel * v_color;
    if (u_worldCoverage > 0.5) fragColor.a = 1.0;
}