#version 330 core

#include "pixscape_common.glsl"

in vec2  v_uv;
in vec4  v_color;
flat in int v_layer;

uniform sampler2DArray u_array;
uniform float u_outlineSize;
uniform float u_outlineIntensity;
uniform vec3  u_ambientMul;

out vec4 fragColor;

void main() {
    vec4 tex = texture(u_array, vec3(v_uv, v_layer));
    vec4 base = pixscapeApplyMaterial(tex, v_color, u_ambientMul);
    float centerA = base.a;

    float s = u_outlineSize;

    float alphaSum = 0.0;
    alphaSum += texture(u_array, vec3(v_uv + vec2( s,  0.0), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2(-s,  0.0), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2( 0.0,  s), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2( 0.0, -s), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2( s,  s), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2(-s,  s), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2( s, -s), v_layer)).a;
    alphaSum += texture(u_array, vec3(v_uv + vec2(-s, -s), v_layer)).a;

    float edge = step(0.01, alphaSum);
    float outlineMask = edge * (1.0 - centerA);

    vec3 outlineColor = v_color.rgb * u_ambientMul;

    vec3 color = mix(base.rgb, outlineColor, outlineMask * u_outlineIntensity);
    float alpha = max(centerA, outlineMask * u_outlineIntensity);

    fragColor = vec4(color, alpha);
}