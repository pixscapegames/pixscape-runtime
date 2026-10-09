#version 300 es
precision highp float;

in vec2 v_uv;
in vec4 v_color;

#include "pixscape_entity_params.glsl"

out vec4 fragColor;

void main() {
    vec2 p = v_uv * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) discard;

    vec2 dir = normalize(vec2(pixscapeEntityFloat(3), pixscapeEntityFloat(4)));
    vec2 v = (d > 0.0001) ? (p / d) : dir;

    float c = dot(v, dir);

    float edge0 = pixscapeEntityFloat(5);
    float edge1 = clamp(edge0 + pixscapeEntityFloat(6), -1.0, 1.0);
    float cone = smoothstep(edge0, edge1, c);

    float atten = pow(1.0 - clamp(d, 0.0, 1.0), max(pixscapeEntityFloat(7), 0.0001));
    float a = atten * cone;

    fragColor = vec4(v_color.rgb * (a * pixscapeEntityFloat(8)), v_color.a * a);
}
