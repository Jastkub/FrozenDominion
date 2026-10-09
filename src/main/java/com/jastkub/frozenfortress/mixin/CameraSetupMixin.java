package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.client.CutsceneCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * THE FILM'S CAMERA WHERE THE FILM PUT IT: on 1.21.1 NeoForge fires ComputeCameraAngles inside Camera.setup, before setup places the camera at the
 * player's eyes - so a scene moving the camera from that event (BossScenes, VelkharScenes) was moved straight back.
 * On 1.20.1 Forge fired it after setup. The place the scene asked for this frame (CutsceneCamera.place) is put back
 * here, once setup is done.
 */
@Mixin(Camera.class)
public abstract class CameraSetupMixin {

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("HEAD"))
    private void frozenDominion$noPlaceYet(CallbackInfo ci) {
        CutsceneCamera.take();
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void frozenDominion$filmPlace(CallbackInfo ci) {
        Vec3 at = CutsceneCamera.take();
        if (at != null) {
            setPosition(at);
            detached = true;
        }
    }
}
