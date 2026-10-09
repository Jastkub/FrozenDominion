package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.jastkub.frozenfortress.entity.LastWatchSentinelEntity;
import com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * LUK OSTATNIEJ STRAZY - THE BOW OF THE LAST WATCH (legendary, 07.10.2026): the last watchman's bow, remade from the
 * Lamplighter's lantern lens and the Turnkey's chain. It makes its own arrows and never wears out.
 *
 * <pre>
 *   hold use, let go early      a light-arrow (a Kingsrime arrow's hit). On a MARKED foe: the Turnkey's chains come up
 *                               round EVERY marked foe and hold them two seconds - the marks are spent on it
 *   hold use to full (1 s)      PROMIEN LATARNI: a beam thirty blocks long through everything on its line, 44 damage
 *                               (+50% on the chained), marking each for five seconds. With CZUWANIE full: three beams
 *   sneak + use                 OSTATNIA WARTA: a leap five blocks back; where you stood a frozen watchman rises and
 *                               shoots for ten seconds (marked foes first; his bolts mark). 15 s
 *   use in the air              DESZCZ: five arrows fanned down ahead; on a marked foe each tops the mark up. 4 s
 *   CZUWANIE (Vigil)            each shot that strikes adds one (the bar under the bow), a shot that strikes nothing
 *                               breaks it; at five the bow burns gold and the next full draw splits
 * </pre>
 *
 * Its body animations (PlayerAnimator, through FFNetwork.playerAnim) are only pictures: without the library it works
 * the same. Its numbers are LastWatchCombat's.
 */
public class LastWatchBowItem extends BowItem {

    /** Ticks to a full draw (the pull property's 1.0). */
    public static final int FULL_DRAW = 20;
    public static final int WATCH_COOLDOWN = 300;
    public static final int RAIN_COOLDOWN = 80;
    /** The sigil under the archer at full draw: renewed this often, lives this long (fx_last_watch_sigil's clip). */
    public static final int SIGIL_EVERY = 30;
    public static final int SIGIL_LIFE = 32;
    /** The partial arrow: its base damage (as the Kingsrime bow's: 5 + 2 + 2) and full speed. */
    public static final double ARROW_DAMAGE = 9.0D;
    public static final float ARROW_SPEED = 3.6F;
    public static final double RAIN_DAMAGE = 5.0D;
    public static final float RAIN_SPEED = 2.8F;
    /** The backstep: speed back and up (about five blocks). */
    public static final double BACKSTEP = 0.8D;
    public static final double BACKSTEP_UP = 0.4D;

    static final String TAG_WATCH = "LWWatchReady";
    static final String TAG_RAIN = "LWRainReady";

    /** Server: the tick each archer let a draw go (so the stop that follows a release does not also fade him out). */
    private static final Map<UUID, Long> RELEASED = new HashMap<>();

    public LastWatchBowItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    // ================================================================================================ the inputs
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        if (player.isShiftKeyDown() && ready(stack, TAG_WATCH, now)) {
            lastWatch(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        boolean airborne = !player.onGround() && !player.isInWater() && !player.onClimbable() && !player.isPassenger()
                && !player.getAbilities().flying;
        if (airborne && ready(stack, TAG_RAIN, now)) {
            rain(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide && player.isShiftKeyDown()) {
            // (sneaking with the watchman not back yet: an ordinary draw - and how long he still needs)
            player.displayClientMessage(Component.translatable("item.frozen_dominion.last_watch_bow.watch_wait",
                    secondsLeft(stack, TAG_WATCH, now)).withStyle(ChatFormatting.DARK_AQUA), true);
        }
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), FFSounds.LAST_WATCH_DRAW.get(),
                    SoundSource.PLAYERS, 0.9F, 1.0F);
            FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_draw"), 4);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Each tick of the draw: at full, the tell - a bright note, the stance, the sigil under him (gold if it will
     *  split), and the lantern's light gathering at the bow. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int used = getUseDuration(stack) - remaining;
        boolean split = LastWatchCombat.vigil(stack) >= LastWatchCombat.VIGIL_MAX;
        if (level.isClientSide) {
            if (used >= FULL_DRAW && used % 2 == 0) {
                Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8D)).add(0.0D, -0.2D, 0.0D);
                level.addParticle(FFParticles.FROST_SWIRL.get(), at.x, at.y, at.z, 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (used == FULL_DRAW) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), FFSounds.LAST_WATCH_READY.get(),
                    SoundSource.PLAYERS, 1.1F, split ? 1.25F : 1.0F);
            FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_full"), 3);
        }
        if (used >= FULL_DRAW && (used - FULL_DRAW) % SIGIL_EVERY == 0) {
            AttackFxEntity.spawn(level, split ? "last_watch_sigil_vigil" : "last_watch_sigil",
                    player.position().add(0.0D, 0.02D, 0.0D), player.getYRot(), 1.0F, SIGIL_LIFE, player).follow(player);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int used = getUseDuration(stack) - timeLeft;
        if (used >= FULL_DRAW) {
            // the beam's recoil - on both sides, as a trident's riptide is, so the archer's own client feels it now
            Vec3 look = player.getLookAngle();
            if (!player.isShiftKeyDown() && !player.getAbilities().flying) {
                player.push(-look.x * 0.22D, 0.04D, -look.z * 0.22D);
            }
            if (level instanceof ServerLevel sl) {
                RELEASED.put(player.getUUID(), level.getGameTime());
                LastWatchCombat.fireBeam(sl, player, stack);
            }
        } else {
            float power = getPowerForTime(used);
            if (power < 0.1F) {
                return;
            }
            if (!level.isClientSide) {
                RELEASED.put(player.getUUID(), level.getGameTime());
                LastWatchArrowEntity arrow = new LastWatchArrowEntity(level, player, LastWatchArrowEntity.NORMAL);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * ARROW_SPEED, 1.0F);
                arrow.setBaseDamage(ARROW_DAMAGE + enchant(stack));
                int punch = stack.getEnchantmentLevel(Enchantments.PUNCH_ARROWS);
                if (punch > 0) {
                    arrow.setKnockback(punch);
                }
                if (stack.getEnchantmentLevel(Enchantments.FLAMING_ARROWS) > 0) {
                    arrow.setSecondsOnFire(100);
                }
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                level.addFreshEntity(arrow);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), FFSounds.LAST_WATCH_SHOOT.get(),
                        SoundSource.PLAYERS, 1.0F, 0.9F + power * 0.25F);
                FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_loose"), 2);
            }
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    /** The draw stopped without a shot (the slot changed, the bow was let go too soon): the stance fades out. */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof Player p && !p.level().isClientSide) {
            Long at = RELEASED.remove(p.getUUID());
            if (at == null || at != p.level().getGameTime()) {
                FFNetwork.playerAnim(p, null, 6);
            }
        }
    }

    private static double enchant(ItemStack stack) {
        int power = stack.getEnchantmentLevel(Enchantments.POWER_ARROWS);
        return power > 0 ? power * 0.5D + 0.5D : 0.0D;
    }

    // ================================================================================================ the skills
    /** OSTATNIA WARTA: the backstep (both sides - the archer's own client moves him) and, server side, the watchman
     *  where he stood. */
    static void lastWatch(Level level, Player player, ItemStack stack) {
        Vec3 from = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize();
        player.setDeltaMovement(-flat.x * BACKSTEP, BACKSTEP_UP, -flat.z * BACKSTEP);
        player.hasImpulse = true;
        player.resetFallDistance();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        stack.getOrCreateTag().putLong(TAG_WATCH, level.getGameTime() + WATCH_COOLDOWN);
        for (LastWatchSentinelEntity old : sl.getEntitiesOfClass(LastWatchSentinelEntity.class,
                player.getBoundingBox().inflate(96.0D), s -> s.isOwnedBy(player))) {
            old.shatter();
        }
        LastWatchSentinelEntity watchman = new LastWatchSentinelEntity(sl, player, from, player.getYRot());
        sl.addFreshEntity(watchman);
        AttackFxEntity.spawn(sl, "last_watch_dash", from, player.getYRot(), 1.0F, 14, player);
        sl.playSound(null, from.x, from.y, from.z, FFSounds.LAST_WATCH_BACKSTEP.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_backstep"), 2);
    }

    /** DESZCZ: in the air, five arrows fanned ahead and down - and the archer hangs a moment on the shot. */
    static void rain(Level level, Player player, ItemStack stack) {
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x * 0.6D, Math.max(dm.y, 0.22D), dm.z * 0.6D);
        player.resetFallDistance();
        if (level.isClientSide) {
            return;
        }
        stack.getOrCreateTag().putLong(TAG_RAIN, level.getGameTime() + RAIN_COOLDOWN);
        float pitch = Mth.clamp(player.getXRot() + 30.0F, 10.0F, 85.0F);
        double bonus = enchant(stack);
        for (int i = -2; i <= 2; i++) {
            LastWatchArrowEntity a = new LastWatchArrowEntity(level, player, LastWatchArrowEntity.RAIN);
            a.shootFromRotation(player, pitch, player.getYRot() + i * 11.0F, 0.0F, RAIN_SPEED, 1.0F);
            a.setBaseDamage(RAIN_DAMAGE + bonus);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(a);
        }
        AttackFxEntity.spawn(level, "last_watch_fan", player.position(), player.getYRot(), 1.0F, 8, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), FFSounds.LAST_WATCH_RAIN.get(),
                SoundSource.PLAYERS, 1.1F, 1.0F);
        FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_rain"), 1);
    }

    static boolean ready(ItemStack stack, String tag, long now) {
        return !stack.hasTag() || stack.getTag().getLong(tag) <= now;
    }

    static int secondsLeft(ItemStack stack, String tag, long now) {
        return stack.hasTag() ? (int) Math.max(0L, (stack.getTag().getLong(tag) - now + 19) / 20) : 0;
    }

    /** The watchman's cooldown coming round: a note for the archer alone. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (holder instanceof ServerPlayer sp && stack.hasTag() && stack.getTag().getLong(TAG_WATCH) == level.getGameTime()) {
            sp.playNotifySound(FFSounds.LAST_WATCH_VIGIL.get(), SoundSource.PLAYERS, 0.5F, 0.6F);
        }
    }

    // ================================================================================================ the item
    /** The pull item property (the 3D frames), off the bow's own full draw. */
    public static float pull(ItemStack stack, @Nullable LivingEntity e) {
        return e == null || e.getUseItem() != stack ? 0.0F
                : (stack.getUseDuration() - e.getUseItemRemainingTicks()) / (float) FULL_DRAW;
    }

    /** The frozen_dominion:vigil item property: 1 while Czuwanie is full (the lens burns gold). */
    public static float vigilFull(ItemStack stack) {
        return LastWatchCombat.vigil(stack) >= LastWatchCombat.VIGIL_MAX ? 1.0F : 0.0F;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return LastWatchCombat.vigil(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * LastWatchCombat.vigil(stack) / LastWatchCombat.VIGIL_MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return LastWatchCombat.vigil(stack) >= LastWatchCombat.VIGIL_MAX ? 0xFFD36B : 0x7FD8FF;
    }

    /** Czuwanie changing mid-draw must not stop the draw, nor bob the bow in the hand. */
    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** It never wears out - and still takes Power, Punch and Flame at the table. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return s -> true;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 30;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String k = "item.frozen_dominion.last_watch_bow.";
        tooltip.add(Component.translatable(k + "desc_beam").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "desc_chains").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "desc_watch").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "desc_rain").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(k + "desc_vigil").withStyle(ChatFormatting.GRAY));
        int v = LastWatchCombat.vigil(stack);
        tooltip.add(Component.translatable(k + "vigil", v, LastWatchCombat.VIGIL_MAX)
                .withStyle(v >= LastWatchCombat.VIGIL_MAX ? ChatFormatting.GOLD : ChatFormatting.BLUE));
        if (level != null) {
            long now = level.getGameTime();
            for (String[] s : new String[][]{{TAG_WATCH, "watch"}, {TAG_RAIN, "rain"}}) {
                int left = secondsLeft(stack, s[0], now);
                tooltip.add(left > 0 ? Component.translatable(k + s[1] + "_wait", left).withStyle(ChatFormatting.DARK_GRAY)
                        : Component.translatable(k + s[1] + "_ready").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
