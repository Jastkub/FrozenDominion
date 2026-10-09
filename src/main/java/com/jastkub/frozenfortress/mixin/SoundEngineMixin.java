package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.client.CutsceneDuck;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE SCENES' WORDS OVER EVERYTHING: while a scene plays, every sound but the spoken lines and the scene's own is turned down
 * (CutsceneDuck). Two ways in: a sound's volume as it starts (play, which works it out from its number and its
 * source) and as it plays on (calculateVolume of the sound - every tick for a moving sound, and for every playing
 * sound when a volume changes, which CutsceneDuck makes happen as the scene opens and closes).
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    /** The sound play() is starting (render thread only), for the volume it works out without it. */
    @Unique
    private static SoundInstance frozenDominion$starting;

    @Inject(method = "play", at = @At("HEAD"))
    private void frozenDominion$startsAt(SoundInstance sound, CallbackInfo ci) {
        frozenDominion$starting = sound;
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void frozenDominion$started(SoundInstance sound, CallbackInfo ci) {
        frozenDominion$starting = null;
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void frozenDominion$duckStarting(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        SoundInstance s = frozenDominion$starting;
        if (s != null) {
            cir.setReturnValue(cir.getReturnValueF() * CutsceneDuck.factor(s));
        }
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"),
            cancellable = true)
    private void frozenDominion$duckPlaying(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * CutsceneDuck.factor(sound));
    }
}
