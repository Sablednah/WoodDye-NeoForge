package com.sablednah.wooddye.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Which way a dye chain runs. Either way it is one line of woods that a lightening dye steps back
 * along and a darkening dye steps forward along; this only decides how the line is sorted.
 *
 * <p>Implements {@link TranslatableEnum} for the config screen, as {@link LogOrder} does.
 */
public enum DyeOrder implements TranslatableEnum {
    /** Lightest wood first, darkest last. */
    SHADE,
    /** Around the colour wheel. Greyish woods have no real hue, so they lead, lightest first. */
    RAINBOW;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("wooddye.configuration.dyeOrder." + name());
    }

    /**
     * Sort {@code items} into this order. Ties, which are common between near-identical woods, fall
     * back to {@code id} so the chain is the same on every server and every restart.
     */
    public <T> List<T> sort(List<T> items, Function<T, Tone> tone, Function<T, String> id) {
        Comparator<T> byShade = Comparator
                .comparingDouble((T item) -> -tone.apply(item).lightness())
                .thenComparing(id);
        List<T> sorted = new ArrayList<>(items);
        if (this == SHADE) {
            sorted.sort(byShade);
            return sorted;
        }

        List<T> neutral = new ArrayList<>();
        List<T> coloured = new ArrayList<>();
        for (T item : items) {
            (tone.apply(item).neutral() ? neutral : coloured).add(item);
        }
        neutral.sort(byShade);
        coloured.sort(Comparator.comparingDouble((T item) -> tone.apply(item).hue()).thenComparing(id));

        sorted.clear();
        sorted.addAll(neutral);
        // Hue is a circle and a chain is a line, so the circle has to be cut somewhere. Cutting at
        // the widest empty stretch keeps neighbouring colours together: with vanilla's woods that is
        // the gap between warped's teal and crimson's purple, so the chain runs crimson -> warped.
        int start = widestGap(coloured, tone);
        for (int i = 0; i < coloured.size(); i++) {
            sorted.add(coloured.get((start + i) % coloured.size()));
        }
        return sorted;
    }

    /** Index of the item that follows the widest hue gap, in a list already sorted by hue. */
    private static <T> int widestGap(List<T> byHue, Function<T, Tone> tone) {
        int start = 0;
        double widest = -1;
        for (int i = 0; i < byHue.size(); i++) {
            double previous = tone.apply(byHue.get((i + byHue.size() - 1) % byHue.size())).hue();
            double gap = (tone.apply(byHue.get(i)).hue() - previous + 360) % 360;
            if (gap > widest) {
                widest = gap;
                start = i;
            }
        }
        return start;
    }
}
