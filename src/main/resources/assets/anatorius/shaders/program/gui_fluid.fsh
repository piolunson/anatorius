#version 150

in vec2 texCoord;
uniform sampler2D u_Texture;
uniform float u_Time;
uniform vec4 u_Color;
uniform vec2 u_Resolution;
uniform vec2 InSize;

out vec4 fragColor;

// Perlin-like noise function for fog effect
float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    
    float a = noise(i);
    float b = noise(i + vec2(1.0, 0.0));
    float c = noise(i + vec2(0.0, 1.0));
    float d = noise(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for(int i = 0; i < 4; i++) {
        value += amplitude * smoothNoise(p * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    
    return value;
}

void main() {
    vec2 uv = texCoord;
    
    // Animated fog movement
    vec2 p = uv * 3.0;
    p.x += u_Time * 0.1;
    p.y += u_Time * 0.05;
    
    // Create flowing fog pattern
    float fog1 = fbm(p);
    float fog2 = fbm(p + vec2(u_Time * 0.15, u_Time * 0.08));
    
    // Combine fog layers with wave distortion
    vec2 distortion = vec2(
        sin(uv.y * 5.0 + u_Time * 0.3) * 0.02,
        cos(uv.x * 5.0 + u_Time * 0.2) * 0.02
    );
    
    float fog = mix(fog1, fog2, 0.5 + 0.5 * sin(u_Time * 0.2));
    fog += fbm(p + distortion * 2.0) * 0.3;
    
    // Smooth fog intensity
    fog = smoothstep(0.3, 0.7, fog);
    
    // Apply user color to fog with subtle gradient
    vec3 fogColor = u_Color.rgb * (0.8 + 0.2 * fog);
    
    // Mix with background texture
    vec4 texColor = texture(u_Texture, texCoord);
    
    // Apply alpha properly: use both fog intensity and user alpha
    float finalAlpha = u_Color.a * fog;
    fragColor = mix(texColor, vec4(fogColor, 1.0), finalAlpha);
}
