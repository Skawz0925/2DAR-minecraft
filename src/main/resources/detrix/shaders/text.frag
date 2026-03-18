#version 150

in vec2 vUv;

uniform sampler2D uTexture;
uniform vec4 uUvRect;
uniform vec4 uColor;

out vec4 fragColor;

void main() {
    vec2 uv = mix(uUvRect.xy, uUvRect.zw, vUv);
    vec4 texColor = texture(uTexture, uv);
    fragColor = vec4(uColor.rgb, uColor.a * texColor.r);
}
