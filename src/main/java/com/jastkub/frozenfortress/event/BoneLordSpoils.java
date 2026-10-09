package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.ChainedChestBlockEntity;
import com.jastkub.frozenfortress.entity.BoneLordEntity;
import com.jastkub.frozenfortress.registry.FFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * THE BONE LORD'S SPOILS: he dies going over backwards into the chasm, and what he dropped went
 * with him onto the spikes. So nothing of it falls: all of it - the party's trophies too (PartyScaling has made them by
 * now: this is the last word on his drops) - is put in a chest that comes up on his own pillar, where his heap lay, with
 * a crack of ice. What the chest cannot hold is left lying beside it.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class BoneLordSpoils {

    private BoneLordSpoils() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel s) || event.getDrops().isEmpty()) {
            return;
        }
        BlockPos centre;
        if (event.getEntity() instanceof BoneLordEntity lord && lord.home() != null) {
            centre = lord.home();
        } else if (event.getEntity() instanceof com.jastkub.frozenfortress.entity.HollowGolemEntity golem
                && !golem.isTamed()) {
            // THE ICE MONSTROSITY'S TOO: in the middle of her prison's floor - the hall her gate keeps (its Room)
            net.minecraft.world.phys.AABB hall = com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.hallOf(s,
                    golem, golem.blockPosition(), 6);
            if (hall == null) {
                return;
            }
            centre = BlockPos.containing(hall.getCenter().x, hall.minY, hall.getCenter().z);
        } else {
            return;
        }
        BlockPos at = spot(s, centre);
        if (at == null) {
            return;                                             // (nowhere free there: they fall where it fell)
        }
        Direction face = Direction.fromYRot(event.getEntity().getYRot()).getOpposite();
        // IN CHAINS: a chained coffer, its chains coming off as
        // somebody comes to it - and the chest it becomes holds what he dropped
        s.setBlock(at, FFBlocks.CHAINED_CHEST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                face.getAxis().isHorizontal() ? face : Direction.SOUTH), 3);
        if (!(s.getBlockEntity(at) instanceof ChainedChestBlockEntity coffer)) {
            return;
        }
        List<ItemStack> spoils = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            spoils.add(drop.getItem().copy());
        }
        coffer.releaseWith(spoils);
        event.setCanceled(true);
        s.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.6F, 0.5F);
        s.playSound(null, at, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.2F, 0.7F);
        s.sendParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5D, at.getY() + 0.6D, at.getZ() + 0.5D, 30, 0.6D, 0.4D,
                0.6D, 0.05D);
    }

    /** A cell free for a chest: at `home` (his heap's place, her floor's middle), else the nearest open one round it,
     *  a step up or down allowed (a dais in the middle of a floor). */
    @Nullable
    private static BlockPos spot(ServerLevel s, BlockPos home) {
        for (int r = 0; r <= 6; r++) {
            for (int dy : new int[]{0, 1, -1, 2}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos p = home.offset(dx, dy, dz);
                        BlockState here = s.getBlockState(p);
                        if ((here.isAir() || here.canBeReplaced()) && s.getBlockState(p.above()).isAir()
                                && s.getBlockState(p.below()).isFaceSturdy(s, p.below(), Direction.UP)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }
}
