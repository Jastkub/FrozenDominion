package com.jastkub.frozenfortress.client.anim;

import com.jastkub.frozenfortress.FrozenFortress;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * THE LEGENDARY WEAPONS' BODY ANIMATIONS (07.10.2026), through PlayerAnimator - the ONLY class that touches it, and
 * only ever reached through WeaponAnimClient after ModList says it is loaded, so without the library nothing here
 * is ever class-loaded. One layer on every player, above Better Combat's; an animation is a file in
 * assets/frozen_dominion/player_animation/, played by its id (frozen_dominion:<file name>).
 */
public final class WeaponAnims {

    static final ResourceLocation LAYER = FrozenFortress.id("weapon");

    private WeaponAnims() {
    }

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500, player -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    public static void play(AbstractClientPlayer player, ResourceLocation id, int fadeTicks) {
        IAnimation a = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (!(a instanceof ModifierLayer<?> raw)) {
            return;
        }
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        if (id == null) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(Math.max(1, fadeTicks), Ease.INOUTSINE), null);
            return;
        }
        dev.kosmx.playerAnim.api.IPlayable anim = PlayerAnimationRegistry.getAnimation(id);
        if (anim == null) {
            com.mojang.logging.LogUtils.getLogger().warn("no player animation {}", id);
            return;
        }
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(Math.max(1, fadeTicks), Ease.INOUTSINE),
                anim instanceof KeyframeAnimation k ? new KeyframeAnimationPlayer(k) : anim.playAnimation());
    }
}
