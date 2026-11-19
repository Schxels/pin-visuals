#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionConfig {
    float Strength;
};

in vec2 texCoord;

out vec4 fragColor;

// Pin Visuals motion blur: the new frame mixed with the last (already blurred) one.
void main(){
    vec4 now = texture(InSampler, texCoord);
    vec4 before = texture(PrevSampler, texCoord);
    fragColor = vec4(mix(now.rgb, before.rgb, Strength), 1.0);
}
