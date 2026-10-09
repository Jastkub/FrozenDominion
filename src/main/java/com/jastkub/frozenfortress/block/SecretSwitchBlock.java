package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * A secret's catch: a brick of the wall itself - stone or deepslate, as the
 * wall is - loose in its joints, with a rune on it worn almost smooth. It does
 * not glow and it is not carved. Press it and
 * the false masonry grinds aside. Now and then a thread of cold air leaks past
 * it, for whoever is really looking.
 */
public class SecretSwitchBlock extends CitadelLockBlock {

    /** Set in a wall of deepslate bricks: wears their face. */
    public static final BooleanProperty DEEP = BooleanProperty.create("deep");
    /** Which of the ten faint runes is cut in it. */
    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, 9);

    public SecretSwitchBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.STONE)
                .noLootTable());
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(DEEP, false).setValue(RUNE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DEEP, RUNE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN) || random.nextInt(40) != 0) {
            return;
        }
        level.addParticle(FFParticles.SOUL_FROST.get(), pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D, pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                0.0D, 0.01D, 0.0D);
    }
}
