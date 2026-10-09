package com.jastkub.frozenfortress.util;

/**
 * GeckoLib 4.9 hands a model's colour around as one ARGB int where 4.4 had four floats. These convert between the
 * two the way 4.4's vertex writer did it (each channel times 255, truncated, one byte), so a tint or a fade comes
 * out on the same byte it always did.
 */
public final class FFColor {

    private FFColor() {
    }

    /** Four 0..1 channels as one ARGB int. */
    public static int argb(float red, float green, float blue, float alpha) {
        return channel(alpha) << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    public static float red(int colour) {
        return (colour >> 16 & 0xFF) / 255.0F;
    }

    public static float green(int colour) {
        return (colour >> 8 & 0xFF) / 255.0F;
    }

    public static float blue(int colour) {
        return (colour & 0xFF) / 255.0F;
    }

    public static float alpha(int colour) {
        return (colour >>> 24) / 255.0F;
    }

    /** The colour with its alpha replaced. */
    public static int withAlpha(int colour, float alpha) {
        return (colour & 0x00FFFFFF) | channel(alpha) << 24;
    }

    /** The colour with its alpha multiplied by k (what `alpha *= k` was). */
    public static int scaleAlpha(int colour, float k) {
        return (colour & 0x00FFFFFF) | ((int) ((colour >>> 24) * k) & 0xFF) << 24;
    }

    private static int channel(float v) {
        return (int) (v * 255.0F) & 0xFF;
    }
}
