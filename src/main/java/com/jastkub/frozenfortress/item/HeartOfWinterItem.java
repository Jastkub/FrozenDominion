package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Heart of the Silent Winter - the still-beating core of a dead season.
 * Consuming it grants a permanent blessing: +2 hearts and immunity to
 * freezing cold. The blessing survives death.
 */
public class HeartOfWinterItem extends Item {

    public static final String BLESSING_TAG = "frozen_dominion:winter_blessing";
    public static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("6c8f2e1a-9b4d-4c7e-8a2f-3d5b1e9c0a7d");

    public HeartOfWinterItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    /**
     * THE MONSTROSITY WOKEN: used on the top of a statue of Sovereign Ice - two blocks one on the
     * other and an arm either side of the upper one, as an iron golem is built - the Heart goes into it, the ice breaks
     * and a Monstrosity climbs out of the floor: tamed, its waker's.
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        Level level = ctx.getLevel();
        net.minecraft.core.BlockPos top = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        net.minecraft.world.level.block.Block ice = com.jastkub.frozenfortress.registry.FFBlocks.SOVEREIGN_ICE.get();
        // the statue wears its
        // skull for a head, as an iron golem wears its pumpkin - the Heart goes into the skull (or the ice under it)
        net.minecraft.world.level.block.Block skullBlock = com.jastkub.frozenfortress.registry.FFBlocks.MONSTROSITY_SKULL.get();
        if (level.getBlockState(top).is(skullBlock)) {
            top = top.below();
        }
        net.minecraft.core.BlockPos skull = top.above();
        if (player == null || !level.getBlockState(top).is(ice) || !level.getBlockState(top.below()).is(ice)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        net.minecraft.core.Direction side = null;
        for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH}) {
            if (level.getBlockState(top.relative(d)).is(ice) && level.getBlockState(top.relative(d.getOpposite())).is(ice)) {
                side = d;
                break;
            }
        }
        if (side == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        // and it stands on a block of blackstone
        net.minecraft.core.BlockPos plinth = top.below(2);
        if (!level.getBlockState(skull).is(skullBlock)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.frozen_dominion.monstrosity_skull_missing"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!isBlackstone(level.getBlockState(plinth))) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.frozen_dominion.monstrosity_plinth"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            for (net.minecraft.core.BlockPos p : new net.minecraft.core.BlockPos[]{top, top.below(), top.relative(side),
                    top.relative(side.getOpposite()), plinth, skull}) {
                sl.destroyBlock(p, false);
            }
            com.jastkub.frozenfortress.entity.HollowGolemEntity golem =
                    com.jastkub.frozenfortress.registry.FFEntities.HOLLOW_GOLEM.get().create(sl);
            if (golem != null) {
                net.minecraft.core.BlockPos at = plinth;          // it climbs out where its plinth stood
                golem.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
                golem.tameTo(player);
                sl.addFreshEntity(golem);
            }
            sl.playSound(null, top, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.6F, 0.6F);
            sl.sendParticles(FFParticles.SOUL_FROST.get(), top.getX() + 0.5D, top.getY() + 0.5D, top.getZ() + 0.5D,
                    80, 1.0D, 1.0D, 1.0D, 0.08D);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.frozen_dominion.monstrosity_tamed"), true);
            if (!player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Blackstone, or any of the worked blackstones. */
    private static boolean isBlackstone(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.world.level.block.Blocks.BLACKSTONE)
                || s.is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE)
                || s.is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS)
                || s.is(net.minecraft.world.level.block.Blocks.CHISELED_POLISHED_BLACKSTONE)
                || s.is(net.minecraft.world.level.block.Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                || s.is(net.minecraft.world.level.block.Blocks.GILDED_BLACKSTONE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getPersistentData().getBoolean(BLESSING_TAG)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            player.getPersistentData().putBoolean(BLESSING_TAG, true);
            applyBlessing(player);
            player.heal(4.0F);
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.4F);
            ((ServerLevel) level).sendParticles(FFParticles.SOUL_FROST.get(),
                    player.getX(), player.getY(1.0D), player.getZ(), 40, 0.5D, 0.8D, 0.5D, 0.05D);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** (Re)applies the permanent max-health bonus; also called after respawn. */
    public static void applyBlessing(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(HEALTH_MODIFIER_UUID) == null) {
            health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_UUID,
                    "Winter blessing", 4.0D, AttributeModifier.Operation.ADDITION));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.heart_of_the_silent_winter.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.heart_of_the_silent_winter.lore"));
    }
}
