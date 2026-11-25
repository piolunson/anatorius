#version 330

in vec3 Position;

// Full-screen quad, already in clip space.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
