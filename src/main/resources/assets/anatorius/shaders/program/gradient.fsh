#version 330

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform int RenderMode;
uniform float FillOpacity;

uniform vec4 Color1;
uniform vec4 Color2;
uniform float Time;

out vec4 fragColor;

float quad(float x) {
    return x * x;
}

vec3 hueGradient(float t) {
    return mix(Color1.rgb, Color2.rgb, clamp(t, 0.0, 1.0));
}

void main() {
    vec4 current = texture(DiffuseSampler, texCoord);

    float t = (sin(Time + (gl_FragCoord.x + gl_FragCoord.y) * 0.01) + 1.0) * 0.5;
    vec3 grad = hueGradient(t);

    if (current.a != 0.0) {
        if (RenderMode == 1) discard;
        fragColor = vec4(grad, current.a * FillOpacity);
        return;
    }

    if (RenderMode == 0) discard;

    const int Width = 3;
    float divider = 5.0;
    float maxSample = 3.0;

    float alpha = 0.0;

    for (int x = -Width; x < Width; x++) {
        for (int y = -Width; y < Width; y++) {
            vec4 s = texture(DiffuseSampler, texCoord + vec2(x, y) * oneTexel);
            if (s.a != 0.0) {
                alpha += max(0.0, (maxSample - distance(vec2(x, y), vec2(0.0))) / divider);
            }
        }
    }

    fragColor = vec4(grad, quad(alpha));
}
