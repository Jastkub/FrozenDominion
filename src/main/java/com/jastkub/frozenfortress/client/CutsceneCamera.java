package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * NO FACE FROM THE INSIDE UNDER A FILM: a boss's film moves the camera away from the player's eyes
 * (BossScenes.camera), but a first-person view still has the player's own first-person body drawn - Epic Fight's whole
 * model - round wherever the camera now is. So for as long as a film has the camera, the view is put in third person
 * (the film sets the camera's place itself, whatever the view), and given back as it was when the film ends.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class CutsceneCamera {

    /** The view the player had before a film took it (null: none taken). */
    @Nullable
    private static CameraType before;

    /** Where a scene put the camera this frame (null: nowhere) - for CameraSetupMixin to put it back after setup. */
    @Nullable
    private static net.minecraft.world.phys.Vec3 place;

    private CutsceneCamera() {
    }

    /** A scene's camera place for this frame (asked from ComputeCameraAngles, inside Camera.setup). */
    public static void place(net.minecraft.world.phys.Vec3 at) {
        place = at;
    }

    /** The place asked for this frame, and cleared. */
    @Nullable
    public static net.minecraft.world.phys.Vec3 take() {
        net.minecraft.world.phys.Vec3 at = place;
        place = null;
        return at;
    }

    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean film = mc.level != null && (BossScenes.films() || VelkharScenes.active());
        if (film && before == null && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {  // (front too: setup turns a mirrored view round after the scene aims it)
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!film && before != null) {
            mc.options.setCameraType(before);
            before = null;
        }
    }
}
