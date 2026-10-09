#version 150

// The roar's bent air (RoarWarpFx): each pixel shows the frame drawn so far (Sampler0), displaced.
//   Color.r  its strength (the roar's power, fading as a ring goes out)
//   Color.g  0: a ring's shell - bent most where it is seen edge on, like a lens's rim
//            1: the stream out of the maw - churned by a noise that flows outward
//   Color.a  the stream's fade along its length (1 on the shells)
//   UV0      the shell's longitude/latitude, or the stream's turn (0-1) and its distance out (blocks)

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float WarpTime;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPos;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float facing = abs(dot(n, v));
    float rim = pow(1.0 - facing, 1.6);
    float power = vertexColor.r * vertexColor.a;

    vec2 dir = n.xy;
    float dl = length(dir);
    dir = dl > 1.0e-4 ? dir / dl : vec2(0.0);
    float amt;
    if (vertexColor.g < 0.5) {
        // A RING: a lens going out - its rim bends the room most, and it shivers as it goes
        float wob = 0.78 + 0.22 * sin(texCoord0.x * 37.7 + WarpTime * 0.9) * sin(texCoord0.y * 22.0 - WarpTime * 0.7);
        amt = power * (0.22 * facing + rim) * wob;
    } else {
        // THE STREAM: air thrown out of the maw, churning - a noise flowing outward along it
        float a = texCoord0.x * 6.2831853;
        vec2 q = vec2(cos(a), sin(a)) * 1.7;
        float f1 = noise(q + vec2(texCoord0.y * 0.9 - WarpTime * 0.55, 0.0)) * 2.0 - 1.0;
        float f2 = noise(q * 1.9 + vec2(3.1, texCoord0.y * 1.6 - WarpTime * 0.95)) * 2.0 - 1.0;
        dir = normalize(vec2(f1, f2) + dir * 0.35 + vec2(1.0e-4));
        amt = power * (0.65 + 0.35 * rim) * (0.6 + 0.4 * abs(f1 + f2));
    }
    // a fraction of the screen, less far off; round on a wide screen
    float dist = length(viewPos);
    vec2 off = dir * amt * 0.045 * clamp(12.0 / (dist + 4.0), 0.25, 1.4);
    off.x *= ScreenSize.y / ScreenSize.x;

    vec3 col;
    col.r = texture(Sampler0, clamp(uv + off * 1.08, 0.001, 0.999)).r;
    col.g = texture(Sampler0, clamp(uv + off, 0.001, 0.999)).g;
    col.b = texture(Sampler0, clamp(uv + off * 0.92, 0.001, 0.999)).b;
    // and a breath of frost on the crest
    col += vec3(0.05, 0.09, 0.13) * rim * power * 0.6;
    fragColor = vec4(col, 1.0);
}
