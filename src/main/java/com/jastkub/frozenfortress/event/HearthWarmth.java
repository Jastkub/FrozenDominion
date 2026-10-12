package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * BY A FIRE THE COLD LETS GO: whoever stands
 * within REACH of a burning campfire is warmed - Warmth for a few seconds, kept up for as long as they stay, and Warmth
 * keeps Frostbite off and takes it away (WarmthEffect, CommonEvents.onEffectApplicable). The citadel's rooms have dead
 * hearths in them for it (tools/citadel/citadel6.py: _cold_hearths) - a flint and steel brings one back.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class HearthWarmth {

    /** How near a fire warms, along the ground and up or down. */
    private static final int REACH = 3, REACH_Y = 2;
    /** How long its warmth outlasts leaving it; renewed every CHECK ticks while near. */
    private static final int LINGER = 60, CHECK = 10;

    private HearthWarmth() {
    }

    @SubscribeEvent
    public static void warm(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % CHECK != 3
                || p.isSpectator() || !p.isAlive()) {
            return;
        }
        if (nearFire(p.level(), p.blockPosition())) {
            // no swirl of particles (the fire is the sign), the icon kept so it can be seen why the cold lets go
            p.addEffect(new MobEffectInstance(FFEffects.WARMTH.get(), LINGER, 0, true, false, true));
        }
    }

    private static boolean nearFire(Level level, BlockPos at) {
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-REACH, -REACH_Y, -REACH), at.offset(REACH, REACH_Y, REACH))) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
                return true;
            }
        }
        return false;
    }
}
