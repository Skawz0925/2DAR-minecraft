#version 150

in vec2 vPos;

uniform vec2 uResolution;
uniform sampler2D uTexture;
uniform vec4 uRect;
uniform float uRadius;
uniform vec4 uColor;

out vec4 fragColor;

float signedDistanceField(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 uv = vec2(vPos.x / uResolution.x, 1.0 - vPos.y / uResolution.y);
    vec3 blurredColor = texture(uTexture, uv).rgb;
    
    vec3 mixedColor = mix(blurredColor, uColor.rgb, uColor.a);

    vec2 local = vPos - uRect.xy;
    vec2 size = uRect.zw;
    vec2 rectHalf = size * 0.5;
    
    float r = min(uRadius, min(size.x, size.y) * 0.5);
    vec4 radius = vec4(r, r, r, r);
    
    float dist = signedDistanceField(local - rectHalf, rectHalf - 1.0, radius);
    float rr = 1.0 - smoothstep(0.0, 1.0, dist);

    fragColor = vec4(mixedColor, rr);
}
