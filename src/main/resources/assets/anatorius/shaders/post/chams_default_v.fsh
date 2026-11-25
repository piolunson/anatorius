#version 330

uniform sampler2D InSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform ChamsConfig {
    float GlowIntensity;
    float FillTint;
    float FillAlpha;
    int GlowThickness;
    int LineWidth;
    // Theme: 0=Default, 1=Gradient, 2=HueShift, 3=BlurGlow/ShorelineBloom, 4=OutlineOnly, 5=Sky, 6=Custom.
    int Theme;
    // Fill colour / gradient start / Sky tint, and the gradient's second fill colour.
    float ColorAR; float ColorAG; float ColorAB;
    float ColorBR; float ColorBG; float ColorBB;
    // Outline/glow band colour.
    float OutlineColorR; float OutlineColorG; float OutlineColorB;
    // Overall effect transparency (colour-picker alpha) — multiplies every output alpha (fill AND glow).
    float GlobalAlpha;
    // RenderMode: 0=Fill only, 1=Outline only, 2=Both. Orthogonal to Theme.
    int RenderMode;
    // FlatFill: 1 = ignore the captured silhouette RGB and use ColorA as a flat base (the first-person hand).
    int FlatFill;
    // GlowInside: 1 = ShorelineBloom inner-rim glow.
    int GlowInside;
    // Animation clock in seconds × the theme's speed (0 for still themes).
    float Clock;
    // Sky parallax: yaw/pitch as a screen offset.
    float ViewX;
    float ViewY;
    // Sky zoom, or the Gradient band count.
    float PatternScale;
    // Gradient theme's second outline colour.
    float Outline2R; float Outline2G; float Outline2B;
    // HueShift: start hue + spread for the fill and the outline.
    float FillOffset;
    float FillStrength;
    float OutlineOffset;
    float OutlineStrength;
    // Dots: 0 none, 1 dots, 2 lines; spacing in px; opacity.
    int DotsMode;
    int DotsRadius;
    float DotsAlpha;
    // Pixel step of the glow blur (1 = every pixel; 2+ = cheaper).
    int GlowStep;
};

in vec2 texCoord;
out vec4 fragColor;

#define TAU 6.28318530718

// Names a Custom shader (Anatorius/Shaders/paint.glsl) can use.
#define TIME Clock
#define RES ScreenSize
#define UV texCoord
#define FILL vec3(ColorAR, ColorAG, ColorAB)
#define EDGE vec3(OutlineColorR, OutlineColorG, OutlineColorB)

// The block between these markers is swapped for the user's file when the Custom theme is active.
//#paint-begin
vec4 paint(vec4 base, bool edge) {
    return base;
}
//#paint-end

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1.0e-10)), d / (q.x + 1.0e-10), q.x);
}

// Sky: flowing caustic light in screen space that slides with the camera. Returns the light level (~0.25..1).
float skyLight() {
    vec2 uv = (gl_FragCoord.xy / ScreenSize + vec2(ViewX, ViewY)) * PatternScale;
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 iter = p;
    float acc = 1.0;
    float t = Clock * 0.5 + 23.0;
    for (int n = 0; n < 5; n++) {
        float tn = t * (1.0 - (3.5 / float(n + 1)));
        iter = p + vec2(cos(tn - iter.x) + sin(tn + iter.y), sin(tn - iter.y) + cos(tn + iter.x));
        float sx = max(abs(sin(iter.x + tn)), 0.001);
        float cy = max(abs(cos(iter.y + tn)), 0.001);
        acc += 1.0 / max(length(vec2(p.x * 0.005 / sx, p.y * 0.005 / cy)), 0.0001);
    }
    acc = 1.17 - pow(max(acc / 5.0, 0.0), 1.4);
    float pw = acc * acc;
    pw *= pw;
    pw *= pw;
    vec3 col = clamp(vec3(abs(pw)) + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    return dot(col, vec3(0.299, 0.587, 0.114));
}

// Gradient: soft bands down the screen, GradientStep bands tall, moving with GradientSpeed.
float gradientMix() {
    return 0.5 + 0.5 * sin(texCoord.y * PatternScale * TAU - Clock * 2.0);
}

// HueShift: a rainbow running diagonally across the screen; keeps the base colour's brightness.
vec3 hueShift(vec3 base, float offset, float strength) {
    float hue = fract(offset + Clock * 0.1 + (texCoord.y - texCoord.x) * strength);
    float value = max(rgb2hsv(base).z, 0.75);
    return hsv2rgb(vec3(hue, 0.85, value));
}

// Fill colour + alpha multiplier for the inside of the silhouette.
vec4 fillTheme(vec3 base, vec3 seedA, vec3 seedB) {
    if (Theme == 1) return vec4(mix(seedA, seedB, gradientMix()), 1.0);
    if (Theme == 2) return vec4(hueShift(seedA, FillOffset, FillStrength), 1.0);
    if (Theme == 5) {
        float light = skyLight();
        return vec4(clamp(seedA * light * 1.4, 0.0, 1.0), clamp(light * 1.25, 0.0, 1.0));
    }
    return vec4(base, 1.0);
}

// Colour for the outline ring / glow band.
vec3 edgeTheme(vec3 seed) {
    if (Theme == 1) return mix(seed, vec3(Outline2R, Outline2G, Outline2B), gradientMix());
    if (Theme == 2) return hueShift(seed, OutlineOffset, OutlineStrength);
    if (Theme == 5) return clamp(seed * mix(0.75, 1.35, skyLight()), 0.0, 1.0);
    return seed;
}

// Dots / Lines pattern inside the fill: true on a pattern pixel.
bool onPattern() {
    if (DotsMode == 0) return false;
    float r = float(max(DotsRadius, 2));
    vec2 px = gl_FragCoord.xy;
    if (DotsMode == 1) return length(mod(px, r) - r * 0.5) < 1.0;
    return mod(px.x + px.y, r) < 1.2;
}

// Last step for every drawn pixel: the Custom theme hands it to the user's paint().
vec4 finish(vec4 c, bool edge) {
    if (Theme == 6) return clamp(paint(c, edge), 0.0, 1.0);
    return c;
}

// Weighted share of the neighbourhood inside the silhouette (~1 deep inside, ~0.5 at the edge). The H-pass already
// blurred the silhouette horizontally into InSampler.g, so the vertical half here costs O(r) samples, not O(r²).
float coverage(vec2 texel) {
    int r = max(GlowThickness, 1);
    int stepPx = max(GlowStep, 1);
    float invSpan = 1.0 / float(r + 1);
    float acc = 0.0;
    float wSum = 0.0;
    for (int y = -r; y <= r; y += stepPx) {
        float t = 1.0 - float(y) * invSpan * float(y) * invSpan;
        float w = t * t;
        acc  += w * texture(InSampler, texCoord + texel * vec2(0.0, float(y))).g;
        wSum += w;
    }
    return acc / max(wSum, 0.0001);
}

void main() {
    vec2 texel = 1.0 / ScreenSize;
    vec4 center = texture(OrigSampler, texCoord);

    // Fill is drawn unless the user asked for outline-only (RenderMode 1) or the OutlineOnly theme (Theme 4).
    bool drawFill    = (RenderMode != 1) && (Theme != 4);
    // Outline + glow is drawn unless the user asked for fill-only (RenderMode 0).
    bool drawOutline = (RenderMode != 0);

    vec3 fillSeedA   = vec3(ColorAR, ColorAG, ColorAB);
    vec3 fillSeedB   = vec3(ColorBR, ColorBG, ColorBB);
    vec3 outlineSeed = vec3(OutlineColorR, OutlineColorG, OutlineColorB);

    // Inside the silhouette: fill it (or leave it transparent). The outline/glow band lives OUTSIDE the shape.
    if (center.a > 0.0) {
        vec3 baseFill = (FlatFill == 1) ? fillSeedA : center.rgb;
        if (drawFill) {
            vec4 shade = fillTheme(baseFill, fillSeedA, fillSeedB);
            vec3 rgb = shade.rgb * FillTint;
            float a = center.a * FillAlpha * GlobalAlpha * shade.a;
            if (onPattern()) a = DotsAlpha * GlobalAlpha;
            if (GlowInside == 1) {
                // Rim is strong near the inside edge, fading toward the centre.
                float rim = clamp((1.0 - coverage(texel)) * GlowIntensity, 0.0, 1.0);
                rgb = mix(rgb, shade.rgb, rim);
                a = clamp(max(a, rim * GlobalAlpha), 0.0, 1.0);
            }
            fragColor = finish(vec4(rgb, a), false);
        } else if (GlowInside == 1) {
            // Outline-only interior with GlowInside: mirror the outside glow inward.
            float inner = 1.0 - coverage(texel);
            float glow = clamp(pow(GlowIntensity * inner, 0.72) * 1.35, 0.0, 1.0);
            fragColor = finish(vec4(edgeTheme(outlineSeed), glow * GlobalAlpha), true);
        } else {
            fragColor = vec4(0.0);
        }
        return;
    }

    // Outside the silhouette: the dilated outline ring + glow (unless fill-only was requested).
    if (!drawOutline) {
        fragColor = vec4(0.0);
        return;
    }

    if (LineWidth > 0) {
        float maxA = 0.0;
        for (int y = -LineWidth; y <= LineWidth; y++) {
            maxA = max(maxA, texture(InSampler, texCoord + texel * vec2(0.0, float(y))).r);
        }
        if (maxA > 0.0) {
            fragColor = finish(vec4(edgeTheme(outlineSeed), maxA * GlobalAlpha), true);
            return;
        }
    }

    if (GlowThickness > 0) {
        float glow = clamp(pow(GlowIntensity * coverage(texel), 0.72) * 1.35, 0.0, 1.0);
        if (glow > 0.0) {
            fragColor = finish(vec4(edgeTheme(outlineSeed), glow * GlobalAlpha), true);
            return;
        }
    }

    fragColor = vec4(0.0);
}
