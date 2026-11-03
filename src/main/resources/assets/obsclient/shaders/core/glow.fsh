#version 150

uniform float Time;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Zamanla değişen gökkuşağı rengi
    vec3 rainbow = 0.5 + 0.5 * cos(Time + vec3(0, 2, 4));
    fragColor = vec4(rainbow, 1.0) * vertexColor;
}