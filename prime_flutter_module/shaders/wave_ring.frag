// PrimeGram: "Моя волна" glowing sphere - a real GLSL fragment shader
// (dart:ui.FragmentProgram). Filled, not a ring outline: colors flow and swirl freely
// throughout the whole circular area (the thing the user actually liked about the earlier
// Canvas version, PrimeWaveBackgroundView.java's blob painter), just sized/positioned to
// match the real Yandex Music "Моя волна" composition studied via a Figma reference. This
// is the whole reason the Music tab moved to Flutter: the native Android side has no
// GLSL/EGL pipeline, so a real flowing-color effect like this was never achievable there.

#include <flutter/runtime_effect.glsl>

uniform vec2 uSize;
uniform float uTime;
// 0 = idle (slow ambient drift), 1 = playing (faster, brighter) - the sphere visibly
// "breathes" differently depending on whether the wave is actually running.
uniform float uEnergy;

out vec4 fragColor;

vec3 palette(float t) {
    vec3 c1 = vec3(0.322, 0.149, 0.996); // #5226FE violet
    vec3 c2 = vec3(0.698, 0.129, 1.000); // #B221FF purple
    vec3 c3 = vec3(1.000, 0.106, 0.847); // hot magenta
    float tt = fract(t) * 3.0;
    if (tt < 1.0) return mix(c1, c2, tt);
    if (tt < 2.0) return mix(c2, c3, tt - 1.0);
    return mix(c3, c1, tt - 2.0);
}

// Cheap layered-sine pseudo-noise (no texture/hash sampler available in a Flutter runtime
// shader) - four offset traveling waves summed together read as organic turbulence once
// domain-warped below, close enough to a liquid-plasma look without a real noise texture.
float turbulence(vec2 p, float t) {
    float v = 0.0;
    v += sin(p.x * 3.1 + t * 0.9);
    v += sin(p.y * 2.7 - t * 0.7);
    v += sin((p.x + p.y) * 2.3 + t * 1.2);
    v += sin((p.x - p.y) * 2.9 - t * 0.5);
    v += sin(length(p) * 3.6 - t * 1.4);
    return v / 5.0;
}

void main() {
    vec2 fragCoord = FlutterFragCoord().xy;
    float scale = min(uSize.x, uSize.y);
    vec2 uv = (fragCoord - 0.5 * uSize) / scale;

    float radius = 0.46;
    float dist = length(uv);
    float speed = 0.5 + 0.5 * uEnergy;

    // Domain warp: sample the turbulence field at a position that is itself displaced by
    // an earlier turbulence sample, so the colors visibly swirl and fold into each other
    // instead of just rippling in place.
    vec2 warp = vec2(
        turbulence(uv * 1.4 + vec2(11.0, 3.0), uTime * speed),
        turbulence(uv * 1.4 - vec2(5.0, 8.0), uTime * speed)
    ) * 0.35;
    float n = turbulence((uv + warp) * 1.7, uTime * speed);

    vec3 col = palette(n * 0.5 + uTime * 0.015);

    // Reference's asymmetric bias - brighter/warmer toward the bottom of the sphere.
    float bottomBias = smoothstep(-radius, radius, uv.y);
    col *= mix(0.55, 1.25, bottomBias);

    // Soft circular mask - solid color inside, feathered fade to near-black outside, plus
    // a brighter rim right at the edge (the reference's glowing lip).
    float edge = smoothstep(radius + 0.10, radius - 0.16, dist);
    float rim = exp(-abs(dist - radius) * 14.0) * (0.4 + 0.6 * bottomBias);

    vec3 base = vec3(0.05, 0.05, 0.06);
    vec3 result = mix(base, col, edge) + col * rim * 0.5;

    fragColor = vec4(result, 1.0);
}
