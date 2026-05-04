#version 300 es
precision highp float;

in vec2 v_uv;
in vec4 v_color;

uniform float u_falloff;

out vec4 fragColor;

void main() {
    vec2 p = v_uv * 2.0 - 1.0;

    float d = length(p);
    float x = clamp(d, 0.0, 1.0);

    float atten = pow(1.0 - x, max(u_falloff, 0.0001));

    if (d > 1.0) discard;

    fragColor = vec4(v_color.rgb * atten, v_color.a * atten);
}