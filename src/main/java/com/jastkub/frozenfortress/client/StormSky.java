package com.jastkub.frozenfortress.client;

import net.minecraft.util.Mth;

/**
 * THE KING'S STORM DARKENS THE SKY UNDER IT. StormcrownSpiral sets how overcast it is where the camera stands (0-1: the
 * storm's reach round the crown, gathering and clearing with it); LevelWeatherMixin hands it to the client level as
 * its rain (and half of it as thunder) wherever the world's own weather is lighter. So it is the game's own storm sky:
 * a grey sky darkened, grey clouds, no sun, moon or stars, the light dimmed, the fog greyed and snow falling over the
 * snowy plains - on this client only (the server's weather is untouched: no snow piles up, nothing else changes).
 *
 * <p>No client classes here: the mixin is common (Level), and reads this only where the level is the client's.
 */
public final class StormSky {

    /** At full, the thunder's share of the overcast (deeper grey; under 0.9, so the level never counts as thundering). */
    public static final float THUNDER = 0.5F;

    private static volatile float last, now;

    private StormSky() {
    }

    /** StormcrownSpiral, each client tick. */
    public static void set(float overcast) {
        last = now;
        now = overcast;
    }

    public static void clear() {
        last = now = 0.0F;
    }

    /** How overcast (0-1) this frame. */
    public static float overcast(float partialTick) {
        float a = last, b = now;
        return a <= 0.0F && b <= 0.0F ? 0.0F : Mth.lerp(partialTick, a, b);
    }
}
