#version 300 es
precision highp float;
precision mediump int;

in vec2 a_position;
in vec4 a_color;
in vec2 a_texCoord0;
in float a_layer;

uniform mat4 u_projTrans;

out vec4 v_color;
out vec2 v_texCoords;
flat out int v_layer;

void main() {
    v_color = a_color;
    v_color.a *= 255.0 / 254.0;
    v_texCoords = a_texCoord0;
    v_layer = int(a_layer + 0.5);
    gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
}

