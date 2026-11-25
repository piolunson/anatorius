#version 330

// Packed by SkyShaderRenderer (no custom uniform block needed):
//   ColorModulator   sky colour (alpha = opacity)
//   ModelOffset      xy = camera parallax offset, z = animation time in seconds
//   TextureMat[0]    x = style (0 Waves, 1 Galaxy), y/z = framebuffer size in px
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

out vec4 fragColor;

#define TAU 6.28318530718

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

// Flowing caustic light, like sunlight through water.
vec4 waves(vec2 uv, float time) {
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 it = p;
    float acc = 1.0;
    float t = time * 0.5 + 23.0;
    for (int n = 0; n < 5; n++) {
        float tn = t * (1.0 - (3.5 / float(n + 1)));
        it = p + vec2(cos(tn - it.x) + sin(tn + it.y), sin(tn - it.y) + cos(tn + it.x));
        float sx = max(abs(sin(it.x + tn)), 0.001);
        float cy = max(abs(cos(it.y + tn)), 0.001);
        acc += 1.0 / max(length(vec2(p.x * 0.005 / sx, p.y * 0.005 / cy)), 0.0001);
    }
    acc = 1.17 - pow(max(acc / 5.0, 0.0), 1.4);
    float pw = acc * acc;
    pw *= pw;
    pw *= pw;
    vec3 col = clamp(vec3(abs(pw)) + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    float light = dot(col, vec3(0.299, 0.587, 0.114));
    return vec4(light * ColorModulator.rgb * 1.4, light * ColorModulator.a);
}

// Drifting nebula clouds with twinkling stars.
vec4 galaxy(vec2 uv, float aspect, float time) {
    vec2 p = vec2(uv.x * aspect, uv.y) * 2.5;
    float n = fbm(p + vec2(time * 0.02, time * 0.015));
    float cloud = fbm(p * 1.7 + n * 1.6 - vec2(time * 0.015, 0.0));
    vec3 col = ColorModulator.rgb * pow(cloud, 2.0) * 1.9 + ColorModulator.rgb * 0.06;

    vec2 g = vec2(uv.x * aspect, uv.y) * 70.0;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash(id);
    vec2 pos = (vec2(hash(id + 1.3), hash(id + 2.7)) - 0.5) * 0.6;
    float star = step(0.94, h) * (1.0 - smoothstep(0.0, 0.09, length(f - pos)));
    star *= 0.55 + 0.45 * sin(time * 2.5 + h * 60.0);
    col += vec3(star);
    return vec4(col, ColorModulator.a);
}

void main() {
    vec2 size = max(TextureMat[0].yz, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / size + ModelOffset.xy;
    float time = ModelOffset.z;
    if (TextureMat[0].x > 0.5) {
        fragColor = galaxy(uv, size.x / size.y, time);
    } else {
        fragColor = waves(uv, time);
    }
}
