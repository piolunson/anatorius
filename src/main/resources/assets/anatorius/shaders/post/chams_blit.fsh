#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Identity copy — moves the composed chams result out of an internal target and into the (external)
// entity_outline framebuffer. Kept as a separate pass so the compose pass never reads and writes the same
// physical target in one go (that read/write hazard renders nothing on real drivers; vanilla's entity_outline
// chain uses the same swap-then-blit structure).
void main() {
    fragColor = texture(InSampler, texCoord);
}
