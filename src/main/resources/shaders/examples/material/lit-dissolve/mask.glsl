vec2 localUV = pixscapeLocalUV(uv);
float pattern = 0.5 + 0.25 * sin(localUV.x * 43.0) + 0.25 * sin(localUV.y * 37.0);
float threshold = 0.5 + 0.45 * sin(time * pixscapeEntityFloat(PIXSCAPE_PARAM_dissolveSpeed));
return step(threshold, pattern);
