package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * THE STOMP'S OWN KEY. From the saddle
 * the Monstrosity stomps the way a horse leaps: the key is held to charge the bar and let go to strike
 * (HollowGolemEntity, PlayerRideableJumping). That was the vanilla Jump key, so nothing of ours showed in Controls.
 *
 * <p>Now it is a key of its own under "Frozen Dominion", Space by default. It is live only in the saddle (its own
 * conflict context), so it does not show red against Jump - and while riding, it is what charges the stomp: the
 * movement input's jump is set from it right after the game reads the keys (MovementInputUpdateEvent), so whoever
 * moves it to another key gets Space back as a plain jump that does nothing on the colossus's back.
 */
public final class ClientRiderKeys {

    /** Live only on a Monstrosity's back. Conflicts with nothing but itself. */
    public static final IKeyConflictContext IN_SADDLE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof HollowGolemEntity;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping STOMP = new KeyMapping("key.frozen_dominion.monstrosity_stomp", IN_SADDLE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.categories.frozen_dominion");

    private ClientRiderKeys() {
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(STOMP);
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
    public static final class Input {
        /** On its back, the stomp key is the jump (the horse's bar charges while it is held). */
        @SubscribeEvent
        public static void onInput(MovementInputUpdateEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || event.getEntity() != mc.player
                    || !(mc.player.getVehicle() instanceof HollowGolemEntity golem)
                    || golem.getControllingPassenger() != mc.player || ClientEvents.cutsceneActive()) {
                return;
            }
            event.getInput().jumping = STOMP.isDown();
        }
    }
}
