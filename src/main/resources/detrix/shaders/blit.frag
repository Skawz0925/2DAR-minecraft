#version 150

in vec2 vPos;

uniform vec2 uResolution;
uniform sampler2D uTexture;

out vec4 fragColor;

void main() {
    vec2 uv = vec2(vPos.x / uResolution.x, 1.0 - vPos.y / uResolution.y);
    fragColor = texture(uTexture, uv);
}
