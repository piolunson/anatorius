#version 330 core

in vec2 texCoord;
in vec2 oneTexel;

uniform sampler2D u_Texture;
uniform int u_Width;
uniform bool u_FastLines;
uniform int u_ShapeMode;
uniform float u_GlowMultiplier;
uniform float u_Time;
uniform vec4 u_FillColor;
uniform vec4 u_FillColor2;
uniform vec4 u_OutlineColor;
uniform vec4 u_OutlineColor2;
uniform int u_Dots;
uniform int u_DotsRadius;
uniform float u_DotsAlpha;
uniform float u_Step;

out vec4 color;

bool decorator(vec4 current) {
    if (u_Dots == 0) return false;
    if (current.a == 0) return false;
    if (u_Dots == 1)
    return int(gl_FragCoord.x) - (u_DotsRadius * int(gl_FragCoord.x / u_DotsRadius)) == 0
        && int(gl_FragCoord.y) - (u_DotsRadius * int(gl_FragCoord.y / u_DotsRadius)) == 0;
    return int(gl_FragCoord.x) % u_DotsRadius == 0 || int(gl_FragCoord.y) % u_DotsRadius == 0;
}

vec4 gradient(vec4 rgb, vec4 rgb2, float step, float time) {
    vec2 frag = vec2(gl_FragCoord.xy);
    float distance = sqrt(frag.x * frag.x + frag.y * frag.y);

    distance = distance / step;

    distance = ((sin(distance + time) + 1.0) / 2.0);

    float distanceInv = 1. - distance;
    float r = rgb.r * distance + rgb2.r * distanceInv;
    float g = rgb.g * distance + rgb2.g * distanceInv;
    float b = rgb.b * distance + rgb2.b * distanceInv;
    float a = rgb.a * distance + rgb2.a * distanceInv;
    return vec4(r, g, b, a);
}

void main() {
    vec4 center = texture(u_Texture, texCoord);

    if (center.a != 0.0) {
        if (u_ShapeMode == 0) discard;
        
        vec4 fill = gradient(u_FillColor, u_FillColor2, u_Step * 300., u_Time);
        if (decorator(fill)) {
            center = vec4(fill.rgb, u_DotsAlpha);
        } else {
            center = fill;
        }
    }
    else {
        if (u_ShapeMode == 1 || u_Width == 0.0) discard;

        float dist = u_Width * u_Width + 1.0;

        if (u_FastLines && u_Width > 2) {
            bool present = false;

            for (int x = -u_Width; x <= u_Width; x += u_Width) {
                for (int y = -u_Width; y <= u_Width; y += u_Width) {
                    vec4 offset = texture(u_Texture, texCoord + oneTexel * vec2(x, y));
                    if (offset.a != 0.0) {
                        present = true;
                        break;
                    }
                }
            }

            if (!present) discard;
        }


        for (int x = -u_Width; x <= u_Width; x++) {
            for (int y = -u_Width; y <= u_Width; y++) {
                vec4 offset = texture(u_Texture, texCoord + oneTexel * vec2(x, y));

                if (offset.a != 0) {
                    dist = min(x * x + y * y - 1.0, dist);
                }
            }
        }

        float minDist = u_Width * u_Width;

        if (dist > minDist)
            center.a = 0.0;
        else {
            vec4 outline = gradient(u_OutlineColor, u_OutlineColor2, u_Step * 300., u_Time);
            center.rgb = outline.rgb;
            center.a = min((1.0 - (dist / minDist)) * u_GlowMultiplier, 1.0) * outline.a;
            if (dist <= 1.5 && u_GlowMultiplier > 0)
                center.a = 0.8;
        }
    }

    color = center;
}
