package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sovereign's Lament - the greatsword of Velkhar, reforged for mortal hands.
 * Hits inflict Frostbite. Hold use to gather Winter's Sorrow, release to
 * unleash a ring of ice spikes and a freezing shockwave.
 */
public class SovereignsLamentItem extends SwordItem {

    @Override
    public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        FFSwing.swing(entity, FFSwing.great(), true);
        return super.onEntitySwing(stack, entity);
    }

    private static final int CHARGE_TICKS = 15;
    private static final int COOLDOWN_TICKS = 160;

    public SovereignsLamentItem() {
        super(FFTiers.EVERFROST, new net.minecraft.world.item.Item.Properties()
                .rarity(Rarity.EPIC).fireResistant()
                .attributes(SwordItem.createAttributes(FFTiers.EVERFROST, 3, -2.9F)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 100, 0), attacker);
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    target.getX(), target.getY(0.6D), target.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.08D);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int held = getUseDuration(stack, entity) - timeLeft;
        if (held < CHARGE_TICKS || level.isClientSide) {
            return;
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.hurtAndBreak(4, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(player.getUsedItemHand()));

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 origin = player.position();
        serverLevel.playSound(null, player.blockPosition(), FFSounds.SHOCKWAVE.get(), SoundSource.PLAYERS, 1.4F, 0.9F);
        serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(), origin.x, origin.y + 0.2D, origin.z, 1, 0, 0, 0, 0);

        // Ring of erupting ice spikes: two waves at radius 2.5 and 4.5.
        for (int ring = 0; ring < 2; ring++) {
            double radius = 2.5D + ring * 2.0D;
            int count = 8 + ring * 4;
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2.0D / count) * i;
                double x = origin.x + Math.cos(angle) * radius;
                double z = origin.z + Math.sin(angle) * radius;
                IceSpikeEntity spike = new IceSpikeEntity(serverLevel, player, x, origin.y, z,
                        5.0F + ring, 4 + ring * 3);
                serverLevel.addFreshEntity(spike);
            }
        }

        // Direct freezing burst around the player.
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0D), e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
            target.hurt(serverLevel.damageSources().playerAttack(player), 8.0F);
            target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 140, 1), player);
            Vec3 push = target.position().subtract(origin).normalize().scale(0.8D).add(0.0D, 0.4D, 0.0D);
            target.push(push.x, push.y, push.z);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.sovereigns_lament.desc1"));
        tooltip.add(Component.translatable("item.frozen_dominion.sovereigns_lament.desc2"));
        tooltip.add(Component.translatable("item.frozen_dominion.sovereigns_lament.lore"));
    }
}
