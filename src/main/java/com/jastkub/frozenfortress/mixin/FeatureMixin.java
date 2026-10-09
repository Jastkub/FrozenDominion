package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.worldgen.OurGround;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A FEATURE THAT BREAKS ON OUR WALLS DOES NOT TAKE ITS CHUNK WITH IT. WorldGenRegionMixin keeps the blocks of the world's features out of our pieces - and some features
 * count on what they set: Apotheosis sets a spawner and reads its block entity back at once; kept out, there is none,
 * and the NullPointerException failed the whole chunk's decoration, so the chunk never loaded.
 *
 * <p>So the outermost feature is placed here, inside a guard: if it throws after a block of it was kept out of ours,
 * it is left as far as it got and the chunk goes on. Anything else a feature throws is thrown on as before.
 */
@Mixin(Feature.class)
public abstract class FeatureMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;"
            + "Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
            + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void frozenDominion$guard(FeatureConfiguration config, WorldGenLevel level, ChunkGenerator generator,
                                      RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (OurGround.inFeature() || !(level instanceof WorldGenRegion)) {
            return;                                           // (the guarded call itself, or nested features)
        }
        Feature self = (Feature) (Object) this;
        OurGround.feature(true);
        try {
            cir.setReturnValue(self.place(config, level, generator, random, pos));
        } catch (RuntimeException e) {
            if (!OurGround.keptOutAny()) {
                throw e;
            }
            com.mojang.logging.LogUtils.getLogger().debug("Feature {} at {} broke against the citadel's walls: {}",
                    BuiltInRegistries.FEATURE.getKey(self), pos, e.toString());
            cir.setReturnValue(false);
        } finally {
            OurGround.feature(false);
        }
    }
}
