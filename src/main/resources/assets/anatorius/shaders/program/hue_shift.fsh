#version 330

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform int RenderMode;
uniform float FillOpacity;

uniform float Hue;

out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float quad(float x) {
    return x * x;
}

void main() {
    vec4 current = texture(DiffuseSampler, texCoord);

    if (current.a != 0.0) {
        if (RenderMode == 1) discard;
        vec3 hsv = rgb2hsv(current.rgb);
        hsv.x = fract(hsv.x + Hue);
        vec3 shifted = hsv2rgb(hsv);
        fragColor = vec4(shifted, current.a * FillOpacity);
        return;
    }

    if (RenderMode == 0) discard;

    const int Width = 3;
    float divider = 5.0;
    float maxSample = 3.0;

    float alpha = 0.0;
    vec3 sampleColor = vec3(1.0);

    for (int x = -Width; x < Width; x++) {
        for (int y = -Width; y < Width; y++) {
            vec4 s = texture(DiffuseSampler, texCoord + vec2(x, y) * oneTexel);
            if (s.a != 0.0) {
                sampleColor = s.rgb;
                alpha += max(0.0, (maxSample - distance(vec2(x, y), vec2(0.0))) / divider);
            }
        }
    }

    vec3 hsv = rgb2hsv(sampleColor);
    hsv.x = fract(hsv.x + Hue);
    vec3 shifted = hsv2rgb(hsv);

    fragColor = vec4(shifted, quad(alpha));
}
