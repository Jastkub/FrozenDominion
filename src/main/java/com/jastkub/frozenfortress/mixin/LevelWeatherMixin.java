package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.client.StormSky;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The king's storm as the client level's weather (StormSky): never lighter than the world's own, never on the server.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void frozenDominion$stormRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide) {
            float o = StormSky.overcast(partialTick);
            if (o > cir.getReturnValueF()) {
                cir.setReturnValue(o);
            }
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void frozenDominion$stormThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide) {
            float o = StormSky.overcast(partialTick) * StormSky.THUNDER;
            if (o > cir.getReturnValueF()) {
                cir.setReturnValue(o);
            }
        }
    }
}
