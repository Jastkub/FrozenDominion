package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Warden's Lodestone - what the vault used to hold its guardians in place.
 *
 * <p>Replaces the healing vial, which was the wrong item for this mod twice
 * over: it handed out yet another cure for Frostbite in a set that already
 * had two, and "drink to undo the hazard" is the least interesting answer a
 * cold-themed place can give you.
 *
 * <p>This does not protect you from anything. It drags every hostile within
 * reach into one spot and pins them there - an answer to being surrounded
 * rather than an answer to being cold, and one that rewards carrying a
 * sweeping weapon to follow it up with.
 */
public class WardensLodestoneItem extends Item {

    private static final double PULL_RADIUS = 12.0D;
    private static final int COOLDOWN = 420;

    public WardensLodestoneItem() {
        super(new Properties().stacksTo(1).durability(64).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Vec3 anchor = player.position().add(player.getLookAngle().scale(3.0D));
            int caught = 0;

            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(PULL_RADIUS),
                    e -> e != player && e.isAlive() && !e.isAlliedTo(player)
                            && !(e instanceof Player))) {
                Vec3 toAnchor = anchor.subtract(victim.position());
                double distance = toAnchor.length();
                if (distance < 0.5D) {
                    continue;
                }
                // Heavier things resist it, so it gathers the swarm without
                // yanking a boss out of whatever it was doing.
                double resistance = victim.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) != null
                        ? victim.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)
                        : 0.0D;
                double strength = Math.max(0.0D, 1.0D - resistance) * 0.9D;
                if (strength <= 0.01D) {
                    continue;
                }
                Vec3 pull = toAnchor.normalize().scale(strength);
                victim.push(pull.x, pull.y * 0.35D + 0.12D, pull.z);
                victim.hurtMarked = true;
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 2), player);
                caught++;
            }

            level.playSound(null, player.blockPosition(), FFSounds.VAULT_WARDEN_SLAM.get(),
                    SoundSource.PLAYERS, 1.2F, 1.5F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(FFParticles.FROST_SWIRL.get(),
                        anchor.x, anchor.y + 1.0D, anchor.z, 50, 1.6D, 1.0D, 1.6D, 0.12D);
                serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(),
                        anchor.x, anchor.y + 0.2D, anchor.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (caught > 0) {
                stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.wardens_lodestone.desc1"));
        tooltip.add(Component.translatable("item.frozen_dominion.wardens_lodestone.desc2"));
        tooltip.add(Component.translatable("item.frozen_dominion.wardens_lodestone.lore"));
        com.jastkub.frozenfortress.integration.curios.CuriosHooks.appendSlotTooltip(tooltip, "belt");
    }
}
