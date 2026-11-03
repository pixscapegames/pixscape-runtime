vec2 localUV = pixscapeLocalUV(uv);
vec2 centered = localUV - 0.5;
float phase = time * pixscapeEntityFloat(PIXSCAPE_PARAM_u_speed);
float frequency = pixscapeEntityFloat(PIXSCAPE_PARAM_u_frequency);
vec2 offset = vec2(sin(centered.y * frequency + phase), cos(centered.x * frequency * 0.7 + phase * 1.2)) * pixscapeEntityFloat(PIXSCAPE_PARAM_u_amplitude);
return pixscapeSample(clamp(localUV + offset, 0.0, 1.0));
