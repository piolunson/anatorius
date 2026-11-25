#version 330

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform int RenderMode;
uniform float FillOpacity;

uniform float BloomRadius;
uniform float BloomStrength;

out vec4 fragColor;

float quad(float x) {
    return x * x;
}

void main() {
    vec4 current = texture(DiffuseSampler, texCoord);

    if (current.a != 0.0) {
        if (RenderMode == 1) discard;
        fragColor = vec4(current.rgb, current.a * FillOpacity);
        return;
    }

    if (RenderMode == 0) discard;

    float radius = max(1.0, BloomRadius);

    vec3 sum = vec3(0.0);
    float wsum = 0.0;

    for (float x = -radius; x <= radius; x += 1.0) {
        for (float y = -radius; y <= radius; y += 1.0) {
            float dist = length(vec2(x, y));
            float w = exp(-(dist * dist) / (radius * radius));
            vec4 s = texture(DiffuseSampler, texCoord + vec2(x, y) * oneTexel);
            sum += s.rgb * s.a * w;
            wsum += w;
        }
    }

    vec3 glow = (wsum > 0.0 ? sum / wsum : vec3(0.0));
    float alpha = clamp(length(glow) * BloomStrength, 0.0, 1.0);

    fragColor = vec4(glow, quad(alpha));
}
