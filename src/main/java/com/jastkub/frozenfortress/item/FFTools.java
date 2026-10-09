package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity;
import com.jastkub.frozenfortress.entity.projectile.IceArrowEntity;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.entity.projectile.SovereignBeamEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The working tools of the winter court - what a player can actually carry
 * out of the fortress and fight with, as opposed to the trophies.
 */
public final class FFTools {

    /**
     * Rimebound Focus - the Rimeweavers' casting rod. Looses a volley of
     * frost bolts in a shallow fan; the ammunition is the rod itself.
     */
    public static class RimeboundFocus extends Item {

        public RimeboundFocus() {
            super(new Properties().stacksTo(1).durability(220).rarity(Rarity.RARE));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                Vec3 look = player.getLookAngle();
                for (int i = -1; i <= 1; i++) {
                    double spread = Math.toRadians(i * 7.0);
                    double cos = Math.cos(spread), sin = Math.sin(spread);
                    Vec3 aim = new Vec3(look.x * cos - look.z * sin, look.y,
                            look.x * sin + look.z * cos);
                    FrostBoltEntity bolt = new FrostBoltEntity(level, player, aim.x, aim.y, aim.z);
                    bolt.setPos(player.getX(), player.getEyeY() - 0.2D, player.getZ());
                    bolt.setDamage(7.0F);
                    level.addFreshEntity(bolt);
                }
                level.playSound(null, player.blockPosition(), FFSounds.FROST_BOLT_FIRE.get(),
                        SoundSource.PLAYERS, 1.0F, 1.1F);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            player.getCooldowns().addCooldown(this, 26);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.rimebound_focus.desc"));
            tooltip.add(Component.translatable("item.frozen_dominion.rimebound_focus.lore"));
        }
    }

    /** Everfrost Bow - looses the black-ice arrows the Stillbows carry. */
    public static class EverfrostBow extends BowItem {

        public EverfrostBow() {
            super(new Properties().stacksTo(1).durability(624).rarity(Rarity.RARE));
        }

        protected EverfrostBow(Properties properties) {
            super(properties);
        }

        /** What a bow forged on from this one adds to the arrow. */
        protected void prepareArrow(IceArrowEntity arrow, float power) {
        }

        protected float velocity() {
            return 3.2F;
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            if (!(entity instanceof Player player)) {
                return;
            }
            int used = getUseDuration(stack) - timeLeft;
            float power = getPowerForTime(used);
            if (power < 0.1F) {
                return;
            }
            if (!level.isClientSide) {
                IceArrowEntity arrow = new IceArrowEntity(level, player);
                arrow.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(),
                        0.0F, power * velocity(), 1.0F);
                arrow.setBaseDamage(arrow.getBaseDamage() + 2.0D);
                prepareArrow(arrow, power);
                if (power >= 1.0F) {
                    arrow.setCritArrow(true);
                }
                arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                level.addFreshEntity(arrow);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
                level.playSound(null, player.blockPosition(), FFSounds.STILLBOW_SHOOT.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F / (level.random.nextFloat() * 0.4F + 1.2F)
                                + power * 0.5F);
            }
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        }

        /** Needs no arrows in the quiver - it makes its own. */
        @Override
        public java.util.function.Predicate<ItemStack> getAllSupportedProjectiles() {
            return s -> true;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.everfrost_bow.desc1"));
            tooltip.add(Component.translatable("item.frozen_dominion.everfrost_bow.desc2"));
        }
    }

    /**
     * Winter's Horn - one long note that throws everything around the bearer
     * back and leaves it frozen where it lands.
     */
    public static class WintersHorn extends Item {

        public WintersHorn() {
            super(new Properties().stacksTo(1).durability(64).rarity(Rarity.RARE));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                ServerLevel serverLevel = (ServerLevel) level;
                Vec3 centre = player.position();
                serverLevel.playSound(null, player.blockPosition(), FFSounds.VELKHAR_ROAR.get(),
                        SoundSource.PLAYERS, 1.6F, 1.5F);
                serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(),
                        centre.x, centre.y + 0.2D, centre.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                serverLevel.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                        centre.x, centre.y + 1.0D, centre.z, 90, 3.5D, 1.2D, 3.5D, 0.25D);

                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(8.0D),
                        e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
                    victim.hurt(level.damageSources().playerAttack(player), 6.0F);
                    victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 140, 1), player);
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2), player);
                    Vec3 push = victim.position().subtract(centre).normalize().scale(1.5D);
                    victim.push(push.x, 0.55D, push.z);
                    victim.hurtMarked = true;
                }
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            player.getCooldowns().addCooldown(this, 220);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.winters_horn.desc"));
            tooltip.add(Component.translatable("item.frozen_dominion.winters_horn.lore"));
        }
    }

    /**
     * Sovereign's Signet - the court's blink. Steps the bearer through the
     * cold to wherever they are looking, and leaves spikes behind them.
     */
    public static class SovereignsSignet extends Item {

        private static final double RANGE = 14.0D;

        public SovereignsSignet() {
            super(new Properties().stacksTo(1).durability(120).rarity(Rarity.EPIC));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                ServerLevel serverLevel = (ServerLevel) level;
                Vec3 from = player.position();
                Vec3 look = player.getLookAngle();

                // walk forward until something solid stops us
                Vec3 target = from;
                for (double d = 1.0D; d <= RANGE; d += 0.5D) {
                    Vec3 probe = from.add(look.x * d, Math.max(0.0D, look.y * d), look.z * d);
                    if (!level.noCollision(player, player.getBoundingBox()
                            .move(probe.subtract(from)))) {
                        break;
                    }
                    target = probe;
                }
                if (target.distanceToSqr(from) < 1.0D) {
                    return InteractionResultHolder.fail(stack);
                }

                serverLevel.sendParticles(FFParticles.FROST_SWIRL.get(),
                        from.x, from.y + 1.0D, from.z, 30, 0.4D, 0.9D, 0.4D, 0.06D);
                player.teleportTo(target.x, target.y, target.z);
                player.resetFallDistance();
                serverLevel.playSound(null, player.blockPosition(), FFSounds.STILLBOW_BLINK.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                        target.x, target.y + 1.0D, target.z, 30, 0.4D, 0.9D, 0.4D, 0.06D);

                // a parting gift where the bearer used to be
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI * 2.0D * i / 4.0D;
                    serverLevel.addFreshEntity(new IceSpikeEntity(level, player,
                            from.x + Math.cos(angle) * 1.4D, from.y,
                            from.z + Math.sin(angle) * 1.4D, 6.0F, 3));
                }
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            player.getCooldowns().addCooldown(this, 90);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.sovereigns_signet.desc"));
            tooltip.add(Component.translatable("item.frozen_dominion.sovereigns_signet.lore"));
            com.jastkub.frozenfortress.integration.curios.CuriosHooks.appendSlotTooltip(tooltip, "ring");
        }
    }

    /**
     * Hollow Breath - what Velkhar was doing when he inhaled, bottled.
     *
     * <p>Opens the same beam the Sovereign turns on his hall, sweeping
     * wherever the wielder looks for three seconds. The cost is the wait:
     * three quarters of a minute of nothing, and the wielder is rooted in
     * place while it burns, so it has to be spent on purpose.
     */
    public static class HollowBreath extends Item {

        private static final int COOLDOWN = 900;

        public HollowBreath() {
            super(new Properties().stacksTo(1).durability(80).rarity(Rarity.EPIC).fireResistant());
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);

            if (!level.isClientSide) {
                SovereignBeamEntity beam = new SovereignBeamEntity(level, player);
                level.addFreshEntity(beam);

                // rooted for the whole channel: charge plus burn
                int channel = SovereignBeamEntity.CHARGE + SovereignBeamEntity.DURATION;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, channel, 3, false, false, true));
                level.playSound(null, player.blockPosition(), FFSounds.VELKHAR_ROAR.get(),
                        SoundSource.PLAYERS, 1.0F, 0.85F);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }

            player.getCooldowns().addCooldown(this, COOLDOWN);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level,
                                    List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.hollow_breath.desc1"));
            tooltip.add(Component.translatable("item.frozen_dominion.hollow_breath.desc2"));
            tooltip.add(Component.translatable("item.frozen_dominion.hollow_breath.lore"));
        }
    }

    private FFTools() {
    }
}
