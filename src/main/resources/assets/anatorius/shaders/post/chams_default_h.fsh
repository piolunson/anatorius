#version 330

uniform sampler2D InSampler;

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform DilateConfig {
    int LineWidth;
    int GlowThickness;
    // Pixel step of the glow blur (1 = every pixel; 2+ = cheaper, FastLines / GlowQuality).
    int GlowStep;
};

in vec2 texCoord;
out vec4 fragColor;

// Horizontal half of the dilation (r = solid outline, max) and the glow blur (g = weighted coverage).
void main() {
    vec2 texel = 1.0 / ScreenSize;

    float maxA = 0.0;
    for (int x = -LineWidth; x <= LineWidth; x++) {
        maxA = max(maxA, texture(InSampler, texCoord + texel * vec2(float(x), 0.0)).a);
    }

    int stepPx = max(GlowStep, 1);
    float invSpan = 1.0 / float(GlowThickness + 1);
    float gAcc = 0.0;
    float gW   = 0.0;
    for (int x = -GlowThickness; x <= GlowThickness; x += stepPx) {
        float a = texture(InSampler, texCoord + texel * vec2(float(x), 0.0)).a;
        float t = 1.0 - float(x) * invSpan * float(x) * invSpan;
        float w = t * t;
        gAcc += w * a;
        gW   += w;
    }

    fragColor = vec4(maxA, gW > 0.0 ? gAcc / gW : 0.0, 0.0, 1.0);
}
