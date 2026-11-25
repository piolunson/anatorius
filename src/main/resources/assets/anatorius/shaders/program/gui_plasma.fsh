#version 150

in vec2 texCoord;
uniform sampler2D u_Texture;
uniform float u_Time;
uniform vec4 u_Color;
uniform vec2 u_Resolution;
uniform vec2 InSize;

out vec4 fragColor;

void main() {
    vec2 p = texCoord * 10.0;
    float time = u_Time * 1.5;
    
    float v1 = sin(p.x + time);
    float v2 = sin(p.y + time);
    float v3 = sin(p.x + p.y + time);
    float v4 = sin(length(p) + time);
    
    float v = (v1 + v2 + v3 + v4) * 0.5;
    
    vec3 col = vec3(sin(v), sin(v + 2.0), sin(v + 4.0)) * 0.5 + 0.5;
    vec4 plasmaColor = vec4(col, 1.0) * vec4(u_Color.rgb, 1.0);
    
    vec4 texColor = texture(u_Texture, texCoord);
    fragColor = mix(texColor, plasmaColor, u_Color.a);
}
