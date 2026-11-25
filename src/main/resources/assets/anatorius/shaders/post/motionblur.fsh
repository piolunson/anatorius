#version 330

uniform sampler2D InSampler;      // the frame just rendered
uniform sampler2D InDepthSampler; // main depth: vanilla clears it before drawing the hand -> depth < 1 = hand

layout(std140) uniform MotionBlurConfig {
    vec2 Velocity;  // screen-space distance (UV) the view swept during the virtual shutter, this frame only
    float Radial;   // zoom blur from moving forward
    float Samples;  // taps along the blur line
};

in vec2 texCoord;
out vec4 fragColor;

bool isHand(vec2 uv) {
    return texture(InDepthSampler, uv).r < 1.0;
}

void main() {
    vec3 here = texture(InSampler, texCoord).rgb;
    // the hand moves with the camera: keep it sharp
    if (isHand(texCoord)) {
        fragColor = vec4(here, 1.0);
        return;
    }

    vec2 dir = Velocity + (texCoord - 0.5) * Radial;
    int n = int(Samples);
    if (n < 2 || dot(dir, dir) < 1e-10) {
        fragColor = vec4(here, 1.0);
        return;
    }

    // centred line (-0.5 .. +0.5): the image is smeared around where it is, never shifted
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= n) break;
        float t = float(i) / float(n - 1) - 0.5;
        vec2 uv = clamp(texCoord + dir * t, vec2(0.0005), vec2(0.9995));
        // tent weight: the centre dominates, so edges read as motion, not as a double image
        float w = 1.0 - abs(t);
        if (isHand(uv)) w = 0.0; // don't drag the hand's colours into the world around it
        acc += texture(InSampler, uv).rgb * w;
        wsum += w;
    }
    fragColor = vec4(wsum > 0.0 ? acc / wsum : here, 1.0);
}
