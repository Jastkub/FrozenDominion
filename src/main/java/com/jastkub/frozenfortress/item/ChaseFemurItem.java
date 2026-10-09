package com.jastkub.frozenfortress.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * GNAT POGONI - THE FEMUR OF THE CHASE (08.10.2026): the Bone Lord's own thighbone, shod in everfrost by Velkhar's
 * smith (the Lord's femur and two ingots, at his anvil - VelkharSmithEntity). What it was, it does: whatever it strikes
 * may go down - trip, and lie a second where it fell - and more surely when the one swinging it is running (the Lord
 * caught nobody standing still).
 */
public class ChaseFemurItem extends SwordItem {

    /** The chance a blow trips what it strikes: standing, and at a run. */
    static final float TRIP = 0.15F, TRIP_RUNNING = 0.38F;
    /** How long it lies there. */
    static final int DOWN_TICKS = 24;

    public ChaseFemurItem() {
        super(KingsrimeItems.KINGSRIME_TIER, 12, -2.9F, new Properties().rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean running = attacker.isSprinting();
        if (!attacker.level().isClientSide && attacker.getRandom().nextFloat() < (running ? TRIP_RUNNING : TRIP)) {
            // down it goes: rooted, and too winded to swing back for a moment
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOWN_TICKS, 9, false, true), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DOWN_TICKS, 1, false, true), attacker);
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            target.hurtMarked = true;
            if (target instanceof Player p) {
                p.setSprinting(false);
            }
            attacker.level().playSound(null, target.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS,
                    1.2F, 0.6F);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.frozen_dominion.chase_femur.desc1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.frozen_dominion.chase_femur.desc2").withStyle(ChatFormatting.DARK_AQUA));
    }
}
