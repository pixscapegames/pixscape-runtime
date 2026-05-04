#version 330 core

#include "pixscape_common.glsl"

in vec2  v_uv;
in vec4  v_color;
flat in int v_texIndex;

uniform sampler2D u_textures[16];
uniform vec3 u_ambientMul;
out vec4 fragColor;

void main() {
  vec4 texel;
  switch (v_texIndex) {
    case 0:  texel = texture(u_textures[0],  v_uv); break;
    case 1:  texel = texture(u_textures[1],  v_uv); break;
    case 2:  texel = texture(u_textures[2],  v_uv); break;
    case 3:  texel = texture(u_textures[3],  v_uv); break;
    case 4:  texel = texture(u_textures[4],  v_uv); break;
    case 5:  texel = texture(u_textures[5],  v_uv); break;
    case 6:  texel = texture(u_textures[6],  v_uv); break;
    case 7:  texel = texture(u_textures[7],  v_uv); break;
    case 8:  texel = texture(u_textures[8],  v_uv); break;
    case 9:  texel = texture(u_textures[9],  v_uv); break;
    case 10: texel = texture(u_textures[10], v_uv); break;
    case 11: texel = texture(u_textures[11], v_uv); break;
    case 12: texel = texture(u_textures[12], v_uv); break;
    case 13: texel = texture(u_textures[13], v_uv); break;
    case 14: texel = texture(u_textures[14], v_uv); break;
    default: texel = texture(u_textures[15], v_uv); break;
  }
  fragColor = pixscapeApplyMaterial(texel, v_color, u_ambientMul);
}
