#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
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

void main() {
    float t = GameTime * 1200.0; // seconds
    vec2 p = gl_FragCoord.xy / min(ScreenSize.x, ScreenSize.y) * 6.0;

    float v = sin(p.x + t)
            + sin(p.y + t * 0.8)
            + sin(p.x + p.y + t * 0.6)
            + sin(length(p - vec2(3.0, 2.0)) * 1.5 + t * 1.2);
    v *= 0.5;

    // smooth rainbow (phase-shifted cosines), tinted by the user's colour like before
    vec3 rainbow = 0.5 + 0.5 * cos(v * 3.14159 + vec3(0.0, 2.094, 4.188));
    fragColor = vec4(rainbow * vertexColor.rgb, vertexColor.a) * ColorModulator;
}
