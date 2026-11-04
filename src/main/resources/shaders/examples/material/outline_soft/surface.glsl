vec2 localUV = pixscapeLocalUV(uv);
float s = pixscapeEntityFloat(PIXSCAPE_PARAM_u_outlineSize);
float alphaSum = pixscapeSample(localUV + vec2(s,0)).a + pixscapeSample(localUV + vec2(-s,0)).a
    + pixscapeSample(localUV + vec2(0,s)).a + pixscapeSample(localUV + vec2(0,-s)).a
    + pixscapeSample(localUV + vec2(s,s)).a + pixscapeSample(localUV + vec2(-s,s)).a
    + pixscapeSample(localUV + vec2(s,-s)).a + pixscapeSample(localUV + vec2(-s,-s)).a;
float outline = step(0.01, alphaSum) * (1.0 - base.a) * pixscapeEntityFloat(PIXSCAPE_PARAM_u_outlineIntensity);
return vec4(mix(base.rgb, vec3(1.0), outline), max(base.a, outline));
