package com.sablednah.wooddye.core;

/**
 * How dyeing steps through log / stripped-log blocks. Logs have two textures — bark on the sides and
 * end-grain rings on the ends — whose colours progress in different orders, so a single order can
 * only ever look right on one of them.
 */
public enum LogOrder {
    /** Use the same light&rarr;dark order as planks (by inner-wood colour). Looks right on the ends. */
    SAME_AS_PLANKS,
    /** Use the bark colour order. Looks right on the bark sides. */
    BARK,
    /** Pick per click: bark order when clicking a side face, wood order when clicking an end. */
    INTELLIGENT
}
