#version 450
layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 oColor;
layout(set = 0, binding = 0) uniform sampler2D uColor;
layout(set = 0, binding = 1) uniform sampler2D uDepth;

layout(push_constant) uniform Params {
    vec4 shift;   // xy = uv shift at near plane, z = near weight scale, w = debug
} pc;

void main() {
    float d = texture(uDepth, vUv).r;
    if (pc.shift.w > 0.5) {
        oColor = vec4(d, d, d, 1.0);
        return;
    }
    float amount = 1.0 - d;
    vec2 uv = clamp(vUv + pc.shift.xy * amount, vec2(0.0), vec2(1.0));
    oColor = vec4(texture(uColor, uv).rgb, 1.0);
}
