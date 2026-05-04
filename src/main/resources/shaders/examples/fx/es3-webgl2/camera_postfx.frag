#version 300 es
precision mediump float;

in vec4 v_color;
in vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_intensity;
uniform float u_time;

out vec4 fragColor;

void main() {
    vec4 base = texture(u_texture, v_texCoords) * v_color;

    float pulse = 0.5 + 0.5 * sin(u_time * 2.0);
    vec3 tintColor = vec3(0.85, 0.9, 1.2);

    float k = clamp(u_intensity * pulse, 0.0, 1.0);
    vec3 finalColor = mix(base.rgb, base.rgb * tintColor, k);

    fragColor = vec4(finalColor, base.a);
}