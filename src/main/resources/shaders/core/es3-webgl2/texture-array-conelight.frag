#version 300 es
precision highp float;

in vec2 v_uv;
in vec4 v_color;

uniform float u_falloff;
uniform float u_dirX;
uniform float u_dirY;
uniform float u_coneCos;
uniform float u_softness;

out vec4 fragColor;

void main() {
    vec2 p = v_uv * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) discard;

    vec2 dir = normalize(vec2(u_dirX, u_dirY));
    vec2 v = (d > 0.0001) ? (p / d) : dir;

    float c = dot(v, dir);

    float edge0 = u_coneCos;
    float edge1 = clamp(u_coneCos + u_softness, -1.0, 1.0);
    float cone = smoothstep(edge0, edge1, c);

    float atten = pow(1.0 - clamp(d, 0.0, 1.0), max(u_falloff, 0.0001));
    float a = atten * cone;

    fragColor = vec4(v_color.rgb * a, v_color.a * a);
}