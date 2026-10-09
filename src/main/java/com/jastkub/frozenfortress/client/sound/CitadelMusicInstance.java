package com.jastkub.frozenfortress.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The citadel's theme ("Dark Cave" by SunixMuz, CC BY 4.0 - see
 * sounds/music/CREDITS.txt), playing while the player is inside its walls.
 * It comes in over three seconds and goes out over three when asked to, so
 * walking out of the gate does not cut it off mid-bar. On the Music slider,
 * because it is the place's music, not a record.
 */
public class CitadelMusicInstance extends AbstractTickableSoundInstance {

    private static final float FADE = 1.0F / 60.0F;
    private boolean fading;

    public CitadelMusicInstance(SoundEvent sound) {
        super(sound, SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    /** Asks it to go: it fades, then stops itself. */
    public void fadeOut() {
        fading = true;
    }

    /** Takes back a fade begun a moment ago - the player stepped back in. */
    public void stay() {
        fading = false;
    }

    public boolean isFading() {
        return fading;
    }

    @Override
    public void tick() {
        if (fading) {
            volume -= FADE;
            if (volume <= 0.0F) {
                volume = 0.0F;
                stop();
            }
        } else if (volume < 1.0F) {
            volume = Math.min(1.0F, volume + FADE);
        }
    }
}
