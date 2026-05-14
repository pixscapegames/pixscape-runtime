#version 330 core
in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

uniform float u_falloff;

void main() {
    // UV -> [-1..1] (centered on 0,0)
    vec2 p = v_uv * 2.0 - 1.0;

    float d = length(p);
    float x = clamp(d, 0.0, 1.0);
    float atten = pow(1.0 - x, max(u_falloff, 0.0001));
    if (d > 1.0) discard;

    fragColor = vec4(v_color.rgb * atten, v_color.a * atten);
}
