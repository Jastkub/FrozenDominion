package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * NO FACE FROM THE INSIDE UNDER A FILM: a boss's film moves the camera away from the player's eyes
 * (BossScenes.camera), but a first-person view still has the player's own first-person body drawn - Epic Fight's whole
 * model - round wherever the camera now is. So for as long as a film has the camera, the view is put in third person
 * (the film sets the camera's place itself, whatever the view), and given back as it was when the film ends.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class CutsceneCamera {

    /** The view the player had before a film took it (null: none taken). */
    @Nullable
    private static CameraType before;

    private CutsceneCamera() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean film = mc.level != null && (BossScenes.films() || VelkharScenes.active());
        if (film && before == null && mc.options.getCameraType().isFirstPerson()) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!film && before != null) {
            mc.options.setCameraType(before);
            before = null;
        }
    }
}
