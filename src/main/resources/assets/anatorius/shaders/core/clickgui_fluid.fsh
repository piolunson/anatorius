#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
// vanilla globals (bound for every GUI draw): ScreenSize in framebuffer pixels, GameTime = (day tick + partial) / 24000
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

in vec4 vertexColor; // the user's colour + alpha
out vec4 fragColor;

// sin-free hash: stable at any coordinate (no float-precision banding as time grows)
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8); // rotate each octave: no grid-aligned streaks
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = rot * p * 2.02 + vec2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

void main() {
    float t = GameTime * 1200.0; // seconds
    // screen-space: every panel shows its part of ONE continuous fog field (no per-panel stretching)
    vec2 p = gl_FragCoord.xy / min(ScreenSize.x, ScreenSize.y) * 2.2;

    // domain warping: the fog flows and curls instead of just sliding
    vec2 q = vec2(fbm(p + vec2(0.0, t * 0.06)), fbm(p + vec2(5.2, 1.3) - t * 0.05));
    vec2 r = vec2(fbm(p + 3.0 * q + vec2(1.7, 9.2) + t * 0.04), fbm(p + 3.0 * q + vec2(8.3, 2.8) - t * 0.035));
    float fog = smoothstep(0.25, 0.85, fbm(p + 2.5 * r));

    vec3 col = vertexColor.rgb * (0.6 + 0.6 * fog);
    float alpha = vertexColor.a * (0.22 + 0.78 * fog); // never fully see-through: panels stay readable
    fragColor = vec4(col, alpha) * ColorModulator;
}
