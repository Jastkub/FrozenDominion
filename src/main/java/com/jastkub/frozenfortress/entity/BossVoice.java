package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * THE LINE SAID ONCE IN THE FIGHT: his own take, made over for each of them
 * (tools/gen_voices.py MORE: entity/[path]_vo_fight), heard at full on the voice channel by everyone near the fight -
 * as Velkhar's lines are - and its words in the chat under the boss's name. Once in a keeper's life: a fresh one,
 * put back after a lost fight, says it again.
 */
public final class BossVoice {

    /** Said already (the keeper's own persisted data - no field to save). */
    private static final String SAID = "ffSaidFightLine";

    private BossVoice() {
    }

    /** Its fight line, if it has not said it yet: true if it says it now. Server. */
    public static boolean fightLine(LivingEntity boss, String path) {
        if (!(boss.level() instanceof ServerLevel s) || boss.getPersistentData().getBoolean(SAID)) {
            return false;
        }
        boss.getPersistentData().putBoolean(SAID, true);
        SoundEvent line = SoundEvent.createVariableRangeEvent(FrozenFortress.id("entity_" + path + "_vo_fight"));
        Component said = Component.literal("")
                .append(Component.translatable(boss.getType().getDescriptionId())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("cutscene.frozen_dominion." + path + ".say_fight")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(boss) < 64.0D * 64.0D)) {
            p.playNotifySound(line, SoundSource.VOICE, 1.0F, 1.0F);
            p.sendSystemMessage(said);
        }
        return true;
    }
}