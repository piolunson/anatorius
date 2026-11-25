#version 150

// Forked from https://www.shadertoy.com/view/MsjSzz
uniform float u_Time;
uniform vec2 u_Resolution;
uniform sampler2D u_Texture;

in vec2 texCoord;
out vec4 fragColor;

const float WEIGHT = 20.0;

// Rasterize functions
float line(vec2 p, vec2 p0, vec2 p1, float w) {
    vec2 d = p1 - p0;
    float t = clamp(dot(d, p - p0) / dot(d, d), 0.0, 1.0);
    vec2 proj = p0 + d * t;
    float dist = length(p - proj);
    dist = 1.0 / dist * WEIGHT * w / u_Resolution.x;
    return min(dist * dist, 1.0);
}

vec3 hsv(float h, float s, float v) {
    vec4 t = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(vec3(h) + t.xyz) * 6.0 - vec3(t.w));
    return v * mix(vec3(t.x), clamp(p - vec3(t.x), 0.0, 1.0), s);
}

void main() {
    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
    uv = uv * 2.0 - 1.0;
    uv.x *= u_Resolution.x / u_Resolution.y;

    float line_width = 0.4;
    float time = u_Time * 0.31415 + sin(length(uv) + u_Time * 0.2) / length(uv) * 0.1;
    
    vec3 c = vec3(0.0);
    
    for (float i = 8.0; i < 24.0; i += 2.0) {
        float f = line(
            uv,
            vec2(cos(time * i) / 2.0, sin(time * i) / 2.0),
            vec2(sin(time * i) / 2.0, -cos(time * i) / 2.0),
            0.5
        );
        c += hsv(i / 24.0, 1.0, 1.0) * f;
    }
    
    fragColor = vec4(c, 1.0);
}
