vec2 centered = pixscapeLocalUV(uv) * 2.0 - 1.0;
float wave = 0.5 + 0.5 * sin(time * pixscapeEntityFloat(PIXSCAPE_PARAM_u_speed));
float radius = max(pixscapeEntityFloat(PIXSCAPE_PARAM_u_radius), 0.0001);
float glow = (1.0 - smoothstep(0.0, radius, length(centered))) * pixscapeEntityFloat(PIXSCAPE_PARAM_u_intensity) * wave;
return vec4(base.rgb * (1.0 + pixscapeEntityFloat(PIXSCAPE_PARAM_u_boost)) + glow * vec3(1.0, 0.9, 0.6), base.a);
