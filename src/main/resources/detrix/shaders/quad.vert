#version 150

in vec2 aUnitPos;
in vec2 aUnitUv;

uniform vec4 uRect;
uniform vec2 uResolution;

out vec2 vPos;
out vec2 vUv;

void main() {
    vec2 pos = uRect.xy + aUnitPos * uRect.zw;
    vPos = pos;
    vUv = aUnitUv;
    vec2 ndc = vec2(pos.x / uResolution.x * 2.0 - 1.0, 1.0 - pos.y / uResolution.y * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
}

