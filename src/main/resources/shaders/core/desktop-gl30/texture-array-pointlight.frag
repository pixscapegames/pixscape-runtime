#version 330 core
in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

uniform float u_falloff; // 1..4 typiquement

void main() {
    // UV -> [-1..1] (centre en 0,0)
    vec2 p = v_uv * 2.0 - 1.0;

    float d = length(p);              // 0 au centre, ~1 au bord du cercle inscrit
    float x = clamp(d, 0.0, 1.0);     // normalisé

    // dégradé: 1 au centre -> 0 au bord
    float atten = pow(1.0 - x, max(u_falloff, 0.0001));

    // option: découpe en disque (sinon ça fait un "rond dans un carré" mais ça reste OK visuellement)
    // si tu veux VRAIMENT éviter le carré:
    if (d > 1.0) discard;

    fragColor = vec4(v_color.rgb * atten, v_color.a * atten);
}
