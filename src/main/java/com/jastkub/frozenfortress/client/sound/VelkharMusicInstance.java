package com.jastkub.frozenfortress.client.sound;

import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One movement of Velkhar's theme. The handler swaps instances between
 * phases; each instance quietly kills itself when its moment has passed.
 */
public class VelkharMusicInstance extends AbstractTickableSoundInstance {

    private final VelkharEntity boss;
    private final int forPhase;
    private final boolean outro;

    public VelkharMusicInstance(SoundEvent sound, VelkharEntity boss, int forPhase, boolean outro) {
        super(sound, SoundSource.RECORDS, RandomSource.create());
        this.boss = boss;
        this.forPhase = forPhase;
        this.outro = outro;
        this.looping = !outro;
        this.delay = 0;
        this.volume = 1.0F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    public int getForPhase() {
        return forPhase;
    }

    public boolean isOutro() {
        return outro;
    }

    @Override
    public void tick() {
        if (outro) {
            // The outro is allowed to finish even as the body fades.
            return;
        }
        if (boss.isRemoved() || boss.isDeadOrDying() || boss.isDormant()
                || boss.getPhase() != forPhase || boss.distanceToSqr(
                        net.minecraft.client.Minecraft.getInstance().player) > 100.0D * 100.0D) {
            stop();
        }
    }
}
