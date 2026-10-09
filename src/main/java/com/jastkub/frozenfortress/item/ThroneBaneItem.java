package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.jastkub.frozenfortress.entity.ThroneBaneWaveEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ZMORA TRONU - THRONE'S BANE (07.10.2026): the legendary greatsword-hammer of the throne, forged in the Frost Anvil
 * from the Ice Aurochs' horn, the Forge Overseer's hammer head, a crown shard and four Kingsrime ingots. The heaviest
 * blade of the court: 22 a swing at 1.3 a second (the Kingsrime sword: 18 at 1.6), and its skills on top.
 *
 * <p>INPUTS (ThroneBaneSkills does the moves):
 * <ul>
 * <li>LMB - its swings. Each one that lands feeds GNIEW TURA (the Aurochs' Wrath): a stack, ten at most, shown on the
 *     item's bar, draining one every half second once six have passed without a hit. At ten the blade BLAZES for eight
 *     seconds: every swing throws a crescent of ice (ThroneBaneWaveEntity), every skill is empowered, and one
 *     Tronobojca is free.</li>
 * <li>RMB, tapped (let go within a third of a second) - SZARZA.</li>
 * <li>Sneak + RMB - MLOT NADZORCY.</li>
 * <li>RMB in the air - UPADEK KORONY.</li>
 * <li>RMB held two seconds and let go - TRONOBOJCA: a sigil draws itself under the feet as it winds, the blade
 *     blazes and chimes when it is wound; let go, a crescent six blocks wide goes out twelve, through armour.</li>
 * </ul>
 * Each skill has its own cooldown, kept on the stack (the vanilla cooldown would lock every one at once); one on its
 * cooldown answers with a dull clunk and how long it still needs, on the action bar.
 */
public class ThroneBaneItem extends SwordItem {

    static final String STACKS = "frozen_dominion:tb_stacks", LAST = "frozen_dominion:tb_last",
            CHARGED = "frozen_dominion:tb_charged", FREE = "frozen_dominion:tb_free", DECAY = "frozen_dominion:tb_decay";
    static final String CD_KEY_CHARGE = "frozen_dominion:tb_cd_charge", CD_KEY_HAMMER = "frozen_dominion:tb_cd_hammer",
            CD_KEY_PLUNGE = "frozen_dominion:tb_cd_plunge", CD_KEY_SLAYER = "frozen_dominion:tb_cd_slayer";

    public static final int MAX_STACKS = 10;
    /** Ticks without a hit before the stacks start draining, and then one every DECAY_EVERY. */
    public static final int DECAY_AFTER = 120, DECAY_EVERY = 10;
    public static final int CHARGED_T = 160;
    /** Held this long or less (client) - a tap: Szarza. The server, which hears of the release a tick or two later,
     *  allows a little more. Winding starts at WIND_START, it is wound at WOUND. */
    public static final int TAP_CLIENT = 6, TAP_SERVER = 9, WIND_START = 10, WOUND = 40;
    /** A crescent at most this often (a swing at 1.3 a second is 15 ticks). */
    static final int CRESCENT_GAP = 12;

    /** The game time this client last saw (for the bar, which is drawn without a level). */
    static long clientTime;
    /** Server: the sigil of each winding wielder (discarded when he lets go). */
    private static final Map<UUID, AttackFxEntity> SIGILS = new HashMap<>();
    private static final Map<UUID, Long> LAST_CRESCENT = new HashMap<>();
    private static final Map<UUID, Boolean> CRESCENT_FLIP = new HashMap<>();

    public ThroneBaneItem() {
        super(KingsrimeItems.KINGSRIME_TIER, 15, -2.7F, new Properties().rarity(Rarity.EPIC).fireResistant());
    }

    // ================================================================================================ state on the stack
    public static int stacks(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(STACKS) : 0;
    }

    public static boolean isCharged(ItemStack stack, @Nullable Level level) {
        long now = level != null ? level.getGameTime() : clientTime;
        return stack.hasTag() && stack.getTag().getLong(CHARGED) > now;
    }

    static long cooldownLeft(ItemStack stack, String key, long now) {
        return stack.hasTag() ? Math.max(0L, stack.getTag().getLong(key) - now) : 0L;
    }

    static void setCooldown(ItemStack stack, String key, long readyAt) {
        stack.getOrCreateTag().putLong(key, readyAt);
    }

    /** A blow landed (a swing, or a skill's hit - once per use): a stack, and at ten, GNIEW TURA wakes. */
    static void feed(Player p) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof ThroneBaneItem) || p.level().isClientSide) {
            return;
        }
        Level level = p.level();
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(LAST, now);
        if (isCharged(stack, level)) {
            return;
        }
        int n = Math.min(MAX_STACKS, tag.getInt(STACKS) + 1);
        if (n >= MAX_STACKS) {
            tag.putInt(STACKS, 0);
            tag.putLong(CHARGED, now + CHARGED_T);
            tag.putBoolean(FREE, true);
            awaken(p);
        } else {
            tag.putInt(STACKS, n);
            level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), FFSounds.THRONE_BANE_STACK.get(), SoundSource.PLAYERS,
                    0.6F, 0.8F + 0.07F * n);
        }
    }

    /** For the wielder's crescents (ThroneBaneWaveEntity): their first hit feeds him too. */
    public static void feedFrom(Player p) {
        feed(p);
    }

    /** GNIEW TURA wakes: horns of ice break the floor round him, the Aurochs bellows, the wrath burns round him. */
    private static void awaken(Player p) {
        ServerLevel level = (ServerLevel) p.level();
        level.playSound(null, p.getX(), p.getY() + 1.0D, p.getZ(), FFSounds.THRONE_BANE_AWAKEN.get(),
                SoundSource.PLAYERS, 2.0F, 1.0F);
        AttackFxEntity.spawn(level, "throne_bane_awaken", p.position(), p.getYRot(), 1.0F, ThroneBaneSkills.AWAKEN_LIFE, p);
        AttackFxEntity.spawn(level, "throne_bane_aura", p.position(), 0.0F, 1.0F, CHARGED_T, p).follow(p);
        p.displayClientMessage(Component.translatable("item.frozen_dominion.throne_bane.awaken")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    // ================================================================================================ the swings
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 80, 0), attacker);
        // the weight of it: the struck stop dead for a moment
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 3,
                false, false, false));
        if (attacker instanceof Player p && !p.level().isClientSide) {
            feed(p);
            crescent(p, stack);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        FFSwing.swing(entity, FFSwing.great(), true);
        if (entity instanceof Player p && !p.level().isClientSide && !p.isUsingItem()) {
            crescent(p, stack);
        }
        return super.onEntitySwing(stack, entity);
    }

    /** In the charged form, a swing throws a crescent of ice ahead (once a swing: a hit and its swing are one). */
    private static void crescent(Player p, ItemStack stack) {
        Level level = p.level();
        if (!isCharged(stack, level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_CRESCENT.get(p.getUUID());
        if (last != null && now - last < CRESCENT_GAP && now >= last) {
            return;
        }
        LAST_CRESCENT.put(p.getUUID(), now);
        boolean flip = !CRESCENT_FLIP.getOrDefault(p.getUUID(), false);
        CRESCENT_FLIP.put(p.getUUID(), flip);
        Vec3 dir = ThroneBaneSkills.flatLook(p);
        Vec3 at = p.position().add(dir.scale(0.9D)).add(0.0D, 0.55D, 0.0D);
        level.addFreshEntity(ThroneBaneWaveEntity.crescent(level, p, at, p.getYRot(), flip));
        level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), FFSounds.THRONE_BANE_CRESCENT.get(), SoundSource.PLAYERS,
                1.0F, 0.95F + level.random.nextFloat() * 0.1F);
    }

    // ================================================================================================ the right button
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || ThroneBaneSkills.busy(player)) {
            return InteractionResultHolder.pass(stack);
        }
        long now = level.getGameTime();
        boolean airborne = !player.onGround() && !player.isInWater() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.isPassenger() && !player.onClimbable();
        if (airborne) {
            if (denied(player, stack, CD_KEY_PLUNGE, now, "plunge")) {
                return InteractionResultHolder.fail(stack);
            }
            ThroneBaneSkills.startPlunge(player, stack);
            return InteractionResultHolder.consume(stack);      // (consume: no arm swing - the body has its own)
        }
        if (player.isShiftKeyDown()) {
            if (denied(player, stack, CD_KEY_HAMMER, now, "hammer")) {
                return InteractionResultHolder.fail(stack);
            }
            ThroneBaneSkills.startLeap(player, stack);
            return InteractionResultHolder.consume(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;                       // (without PlayerAnimator: the blade raised back, as a spear's)
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player p)) {
            return;
        }
        int held = getUseDuration(stack) - remaining;
        long now = level.getGameTime();
        if (level.isClientSide) {
            if (held == WOUND && p.isLocalPlayer() && slayerReady(stack, level)) {
                ThroneBaneSkills.clientShake(0.25F, 5);
            }
            return;
        }
        if (held == WIND_START) {
            if (!slayerReady(stack, level)) {
                denied(p, stack, CD_KEY_SLAYER, now, "slayer");
                return;
            }
            AttackFxEntity sigil = AttackFxEntity.spawn(level, "throne_bane_sigil", p.position(), p.getYRot(), 1.0F,
                    600, p).follow(p);
            AttackFxEntity old = SIGILS.put(p.getUUID(), sigil);
            if (old != null) {
                old.discard();
            }
            ThroneBaneSkills.anim(p, "throne_bane_windup", 4);
            ThroneBaneSkills.sound(p, FFSounds.THRONE_BANE_WINDUP.get(), 1.2F, 1.0F);
        } else if (held == WOUND && SIGILS.containsKey(p.getUUID())) {
            ThroneBaneSkills.sound(p, FFSounds.THRONE_BANE_READY.get(), 1.4F, 1.0F);
        }
    }

    /** Tronobojca may be let go: off its cooldown, or the charged form's free one unspent. */
    static boolean slayerReady(ItemStack stack, Level level) {
        return cooldownLeft(stack, CD_KEY_SLAYER, level.getGameTime()) <= 0L
                || (isCharged(stack, level) && stack.getTag().getBoolean(FREE));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player p)) {
            return;
        }
        int held = getUseDuration(stack) - timeLeft;
        long now = level.getGameTime();
        if (held <= (level.isClientSide ? TAP_CLIENT : TAP_SERVER)) {
            if (!denied(p, stack, CD_KEY_CHARGE, now, "charge")) {
                dropSigil(p, false);
                ThroneBaneSkills.startCharge(p, stack);
            }
            return;
        }
        if (held < WOUND || !slayerReady(stack, level)) {
            return;                                 // not wound: onStopUsing puts the sigil out and lets the body go
        }
        if (level.isClientSide) {
            if (p.isLocalPlayer()) {
                ThroneBaneSkills.clientShake(0.7F, 10);
            }
            return;
        }
        boolean emp = isCharged(stack, level);
        CompoundTag tag = stack.getOrCreateTag();
        if (emp && tag.getBoolean(FREE)) {
            tag.putBoolean(FREE, false);            // Gniew Tura's gift: this one costs nothing
        } else {
            setCooldown(stack, CD_KEY_SLAYER, now + ThroneBaneSkills.CD_SLAYER);
        }
        dropSigil(p, false);
        ThroneBaneSkills.anim(p, "throne_bane_release", 1);
        ThroneBaneSkills.sound(p, FFSounds.THRONE_BANE_SLASH.get(), 2.0F, emp ? 0.9F : 1.0F);
        Vec3 dir = ThroneBaneSkills.flatLook(p);
        level.addFreshEntity(ThroneBaneWaveEntity.slash(level, p, p.position().add(dir.scale(1.0D)), p.getYRot(),
                emp ? ThroneBaneSkills.SLAYER_DMG_EMP : ThroneBaneSkills.SLAYER_DMG, emp));
        stack.hurtAndBreak(3, p, q -> q.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof Player p && !p.level().isClientSide) {
            dropSigil(p, true);
        }
    }

    /** Put out the wind-up's sigil; `fade`: and let the body go back to its own (a wind-up given up). */
    private static void dropSigil(Player p, boolean fade) {
        if (p.level().isClientSide) {
            return;
        }
        AttackFxEntity sigil = SIGILS.remove(p.getUUID());
        if (sigil != null) {
            sigil.discard();
            if (fade) {
                ThroneBaneSkills.anim(p, null, 5);
            }
        }
    }

    /** On its cooldown: a dull clunk, and how long it still needs, to him alone. True when it is. */
    private static boolean denied(Player p, ItemStack stack, String key, long now, String skill) {
        long left = cooldownLeft(stack, key, now);
        if (left <= 0L) {
            return false;
        }
        if (!p.level().isClientSide) {
            p.playNotifySound(FFSounds.THRONE_BANE_DENIED.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            p.displayClientMessage(Component.translatable("item.frozen_dominion.throne_bane.cooldown",
                    Component.translatable("item.frozen_dominion.throne_bane.skill." + skill),
                    String.format(java.util.Locale.ROOT, "%.1f", left / 20.0F)).withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    // ================================================================================================ every tick
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) {
            clientTime = level.getGameTime();
            return;
        }
        if (!stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        long now = level.getGameTime();
        long charged = tag.getLong(CHARGED);
        if (charged > 0L && now >= charged) {
            tag.remove(CHARGED);
            tag.remove(FREE);
        }
        int n = tag.getInt(STACKS);
        if (n > 0 && now - tag.getLong(LAST) > DECAY_AFTER && now - tag.getLong(DECAY) >= DECAY_EVERY) {
            tag.putInt(STACKS, n - 1);
            tag.putLong(DECAY, now);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();      // (its state changes on every hit)
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    // ================================================================================================ the bar: Gniew Tura
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stacks(stack) > 0 || isCharged(stack, null) || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (isCharged(stack, null)) {
            long left = stack.getTag().getLong(CHARGED) - clientTime;
            return Mth.clamp(Math.round(13.0F * left / CHARGED_T), 0, 13);
        }
        int n = stacks(stack);
        return n > 0 ? Math.round(13.0F * n / MAX_STACKS) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (isCharged(stack, null)) {
            float f = 0.5F + 0.5F * Mth.sin(clientTime * 0.6F);
            return Mth.color(0.62F + 0.38F * f, 0.94F + 0.06F * f, 1.0F);   // white-hot, pulsing
        }
        int n = stacks(stack);
        if (n > 0) {
            float f = n / (float) MAX_STACKS;
            return Mth.color(0.18F + 0.32F * f, 0.30F + 0.58F * f, 0.66F + 0.34F * f);   // royal blue toward ice
        }
        return super.getBarColor(stack);
    }

    // ================================================================================================ the tooltip
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "item.frozen_dominion.throne_bane.";
        if (isCharged(stack, level)) {
            tooltip.add(Component.translatable(k + "state_charged").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        } else {
            tooltip.add(Component.translatable(k + "state_stacks", stacks(stack), MAX_STACKS)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable(k + "passive1").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(k + "passive2").withStyle(ChatFormatting.BLUE));
        String[] skills = {"charge", "hammer", "plunge", "slayer"};
        String[] keys = {CD_KEY_CHARGE, CD_KEY_HAMMER, CD_KEY_PLUNGE, CD_KEY_SLAYER};
        for (int i = 0; i < skills.length; i++) {
            long left = level != null ? cooldownLeft(stack, keys[i], level.getGameTime()) : 0L;
            Component line = Component.translatable(k + "use." + skills[i]).withStyle(ChatFormatting.GRAY);
            if (left > 0L) {
                line = Component.translatable(k + "use_cooldown", line,
                        String.format(java.util.Locale.ROOT, "%.1f", left / 20.0F)).withStyle(ChatFormatting.DARK_GRAY);
            }
            tooltip.add(line);
        }
        tooltip.add(Component.translatable(k + "combo").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
