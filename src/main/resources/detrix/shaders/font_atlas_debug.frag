#version 150

in vec2 vUv;

uniform sampler2D uTexture;
uniform vec4 uUvRect;

out vec4 fragColor;

void main() {
    vec2 uv = mix(uUvRect.xy, uUvRect.zw, vUv);
    float v = texture(uTexture, uv).r;
    if (v > 0.0) {
        fragColor = vec4(v, v, v, 1.0);
    } else {
        fragColor = vec4(1.0, 0.0, 0.0, 0.2);
    }
}
