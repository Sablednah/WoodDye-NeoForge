package com.sablednah.wooddye.core;

/**
 * The average tone of a wood texture, expressed in OKLab: how light it is, how strongly coloured,
 * and which colour. Dye chains are sorted on these, so the order is measured rather than hand-written
 * and a wood nobody has seen before still lands in a sensible place.
 *
 * <p>The average is always taken in <b>linear light</b> before conversion: averaging sRGB bytes
 * directly darkens any texture with contrast in it, which is most bark.
 *
 * @param lightness perceptual lightness, 0 (black) to 1 (white)
 * @param chroma    colourfulness, 0 for a pure grey; plank textures run to about 0.12
 * @param hue       hue angle in degrees, 0&ndash;360; meaningless when {@link #neutral()}
 */
public record Tone(double lightness, double chroma, double hue) {

    /**
     * Below this chroma a wood reads as grey and its hue angle is noise &mdash; pale oak planks and
     * birch bark both sit well under it, and a hue sort that trusted them would place them at random.
     */
    public static final double NEUTRAL_CHROMA = 0.03;

    /** Whether this tone is too grey for its {@link #hue} to mean anything. */
    public boolean neutral() {
        return chroma < NEUTRAL_CHROMA;
    }

    /** The tone of a single sRGB colour, {@code 0xRRGGBB}. */
    public static Tone ofRgb(int rgb) {
        return ofLinear(
                toLinear((rgb >> 16 & 0xFF) / 255.0),
                toLinear((rgb >> 8 & 0xFF) / 255.0),
                toLinear((rgb & 0xFF) / 255.0));
    }

    /** Parses {@code #rrggbb} (the leading {@code #} is optional), or returns {@code null}. */
    public static Tone parseHex(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6) {
            return null;
        }
        try {
            return ofRgb(Integer.parseInt(digits, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The tone of a whole texture: the alpha-weighted mean of its pixels, taken in linear light.
     * Returns {@code null} for a fully transparent image.
     *
     * @param argb non-premultiplied sRGB pixels, {@code 0xAARRGGBB}
     */
    public static Tone average(int[] argb) {
        double r = 0, g = 0, b = 0, weight = 0;
        for (int pixel : argb) {
            double alpha = (pixel >>> 24) / 255.0;
            if (alpha == 0) {
                continue;
            }
            r += alpha * toLinear((pixel >> 16 & 0xFF) / 255.0);
            g += alpha * toLinear((pixel >> 8 & 0xFF) / 255.0);
            b += alpha * toLinear((pixel & 0xFF) / 255.0);
            weight += alpha;
        }
        return weight == 0 ? null : ofLinear(r / weight, g / weight, b / weight);
    }

    private static double toLinear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /** Linear sRGB to OKLab (Bj&ouml;rn Ottosson's matrices), then to lightness/chroma/hue. */
    private static Tone ofLinear(double r, double g, double b) {
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        double hue = Math.toDegrees(Math.atan2(bb, a));
        return new Tone(lightness, Math.hypot(a, bb), hue < 0 ? hue + 360 : hue);
    }
}
