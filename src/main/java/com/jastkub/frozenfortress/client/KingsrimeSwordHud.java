package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Kingsrime sword's three skills beside the hotbar while it is in hand (textures/gui/kingsrime_skills.png,
 * tools/gen_kingsrime_skills.py): Szronowe Cięcie, Królewski Krok, Koronacja Mrozu, each dark and filling back from
 * the top as its cooldown runs out - the item itself never takes a vanilla cooldown, which would lock all three. Over
 * the crescent, five pips of the Królewski Gniew (full: they burn); and its frame lit while the Krok's combo is open.
 * Registers itself (an event subscriber on the mod bus), nothing to wire.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KingsrimeSwordHud {

    private static final ResourceLocation ICONS = FrozenFortress.id("textures/gui/kingsrime_skills.png");
    private static final String[] SKILLS = {KingsrimeSwordItem.CD_CRESCENT, KingsrimeSwordItem.CD_STEP,
            KingsrimeSwordItem.CD_CROWN};

    private KingsrimeSwordHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "kingsrime_skills",
                (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
    }

    private static void draw(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || p.isSpectator()) {
            return;
        }
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof KingsrimeSwordItem)) {
            return;
        }
        long now = mc.level.getGameTime();
        // to the right of the hotbar, past the off hand's slot and the attack meter when they are there
        int x = width / 2 + 91 + 6;
        if (p.getMainArm() == HumanoidArm.LEFT && !p.getOffhandItem().isEmpty()) {
            x += 29;
        }
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            x += 22;
        }
        int y = height - 20;
        boolean combo = KingsrimeSwordItem.comboOpen(stack, now);
        int wrath = KingsrimeSwordItem.wrath(stack);
        float pulse = 0.5F + 0.5F * Mth.sin((float) (net.minecraft.Util.getMillis() / 110.0D));
        for (int i = 0; i < SKILLS.length; i++) {
            int ix = x + i * 19;
            long at = KingsrimeSwordItem.readyAt(stack, SKILLS[i]);
            int cd = KingsrimeSwordItem.cooldownOf(SKILLS[i]);
            float left = KingsrimeSwordItem.ready(stack, SKILLS[i], now) ? 0.0F : Mth.clamp((at - now) / (float) cd, 0.0F, 1.0F);
            int frame = 0xB0101820;
            if (i == 0 && combo) {
                frame = 0xFF000000 | Mth.color(0.75F + 0.25F * pulse, 0.95F, 1.0F);
            } else if (i == 0 && wrath >= KingsrimeSwordItem.WRATH_MAX) {
                frame = 0xFF000000 | Mth.color(0.35F + 0.4F * pulse, 0.6F + 0.3F * pulse, 1.0F);
            }
            g.fill(ix - 1, y - 1, ix + 17, y + 17, frame);
            g.fill(ix, y, ix + 16, y + 16, 0xC0080C14);
            g.blit(ICONS, ix, y, i * 16, 0, 16, 16, 64, 16);
            if (left > 0.0F) {
                int h = Mth.ceil(16 * left);
                g.fill(ix, y + 16 - h, ix + 16, y + 16, 0xB0000000);
            }
        }
        // the wrath's pips over the crescent
        for (int k = 0; k < KingsrimeSwordItem.WRATH_MAX; k++) {
            int px = x + 1 + k * 3, py = y - 5;
            int col = k < wrath
                    ? (wrath >= KingsrimeSwordItem.WRATH_MAX ? 0xFF000000 | Mth.color(0.7F + 0.3F * pulse, 0.92F, 1.0F) : 0xFF6FC8FF)
                    : 0x80303A48;
            g.fill(px, py, px + 2, py + 2, col);
        }
    }
}
