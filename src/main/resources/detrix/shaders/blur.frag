#version 150

in vec2 vPos;

uniform vec2 uResolution;
uniform sampler2D uTexture;
uniform vec2 uTextureResolution;
uniform vec2 uDirection;
uniform float uRadius;

out vec4 fragColor;

void main() {
    vec2 uv = vec2(vPos.x / uResolution.x, 1.0 - vPos.y / uResolution.y);
    vec2 texel = uDirection / uTextureResolution;

    vec4 sum = texture(uTexture, uv) * 0.1964825501511404;
    sum += texture(uTexture, uv + texel * (1.411764705882353 * uRadius)) * 0.2969069646728344;
    sum += texture(uTexture, uv - texel * (1.411764705882353 * uRadius)) * 0.2969069646728344;
    sum += texture(uTexture, uv + texel * (3.2941176470588234 * uRadius)) * 0.09447039785044732;
    sum += texture(uTexture, uv - texel * (3.2941176470588234 * uRadius)) * 0.09447039785044732;
    sum += texture(uTexture, uv + texel * (5.176470588235294 * uRadius)) * 0.010381362401148057;
    sum += texture(uTexture, uv - texel * (5.176470588235294 * uRadius)) * 0.010381362401148057;

    fragColor = sum;
}
