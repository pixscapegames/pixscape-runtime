#version 330 core

in vec4 v_color;
in vec2 v_texCoords;
flat in int v_layer;

uniform sampler2DArray u_array;

out vec4 fragColor;

void main() {
    fragColor = v_color * texture(u_array, vec3(v_texCoords, v_layer));
}

