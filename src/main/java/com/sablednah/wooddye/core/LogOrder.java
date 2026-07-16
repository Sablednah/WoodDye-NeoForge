package com.sablednah.wooddye.core;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * How dyeing steps through log / stripped-log blocks. Logs have two textures — bark on the sides and
 * end-grain rings on the ends — whose colours progress in different orders, so a single order can
 * only ever look right on one of them.
 *
 * <p>Implements {@link TranslatableEnum} so the config screen offers readable names; without it that
 * dropdown falls back to the raw constant, e.g. {@code SAME_AS_PLANKS}.
 */
public enum LogOrder implements TranslatableEnum {
    /** Use the same light&rarr;dark order as planks (by inner-wood colour). Looks right on the ends. */
    SAME_AS_PLANKS,
    /** Use the bark colour order. Looks right on the bark sides. */
    BARK,
    /** Pick per click: bark order when clicking a side face, wood order when clicking an end. */
    INTELLIGENT;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("wooddye.configuration.logOrder." + name());
    }
}
