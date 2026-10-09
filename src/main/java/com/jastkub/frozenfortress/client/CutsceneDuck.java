package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE SCENES' WORDS OVER EVERYTHING: while any scene plays (a boss's entrance or death, the king's scenes, the prison's - see
 * ClientEvents.cutsceneActive), every sound is turned down to {@link #DOWN} of itself - music, the fight, the cold's
 * wind, the gate - but the spoken lines (the VOICE source: the bosses' and the king's words) and the sounds the scene
 * makes itself (BossScenes' own: its cuts and its title, and whatever sounds from where the boss stands - the clank of
 * the Overseer's hammer in his entrance is the film's, not the world's). Down over three quarters of a second, back up
 * over one when it ends.
 * (SoundEngineMixin does the turning; this says by how much.)
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class CutsceneDuck {

    public static final float DOWN = 0.25F;
    private static final float FALL = 0.05F, RISE = 0.0375F;
    /** How loud everything else is now: 1 - as it is, DOWN - under a scene. */
    private static float level = 1.0F;

    private CutsceneDuck() {
    }

    /** What a sound's volume is multiplied by now. */
    public static float factor(SoundInstance s) {
        if (level >= 1.0F || s == null) {
            return 1.0F;
        }
        SoundSource src = s.getSource();
        if (src == SoundSource.VOICE || src == SoundSource.MASTER || BossScenes.own(s)) {
            return 1.0F;
        }
        return level;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float want = mc.level != null && ClientEvents.cutsceneActive() ? DOWN : 1.0F;
        if (level == want) {
            return;
        }
        level = want < level ? Math.max(want, level - FALL) : Math.min(want, level + RISE);
        // every playing sound's volume worked out afresh (one category's update recomputes them all)
        mc.getSoundManager().updateSourceVolume(SoundSource.AMBIENT, mc.options.getSoundSourceVolume(SoundSource.AMBIENT));
    }
}
