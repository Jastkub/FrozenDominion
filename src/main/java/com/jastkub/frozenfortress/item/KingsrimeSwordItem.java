package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FFAllies;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordBladesEntity;
import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrescentEntity;
import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrownEntity;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * MIECZ KRÓLEWSKIEGO SZRONU - the Everfrost sword taken to the Frost Anvil, and its skills. Royal frost: fast and precise, where the Throne's Bane is the heavy one.
 *
 * <p>LEFT CLICK stays the sword's (Better Combat's swings, weapon_attributes/kingsrime_sword.json): 18 damage, 1.6 a
 * second, Frostbite on every hit, and every third hit a line of ice spikes (IceSpikeEntity) driven out ahead.
 *
 * <p>THE USE KEY, three skills, each its own cooldown (kept on the stack, so the client knows them for the HUD -
 * KingsrimeSwordHud - and the item never takes a vanilla cooldown, which would lock all three at once):
 * <ul>
 * <li>a TAP: SZRONOWE CIĘCIE - a crescent of frost (KingsrimeSwordCrescentEntity) flies nine blocks, cutting through
 *     everything in its way;</li>
 * <li>SNEAK + the key: KRÓLEWSKI KROK - three ticks of a blink-dash six blocks through them, untouchable while it lasts
 *     (KingsrimeSwordEvents), leaving a line of hanging ice blades (KingsrimeSwordBladesEntity) that burst half a
 *     second later;</li>
 * <li>HELD 1.2 s and let go: KORONACJA MROZU - the blade planted, a crown of ice blades rising round the wielder
 *     (KingsrimeSwordCrownEntity, the tell and the charge meter at once) and bursting outward: everyone within five
 *     and a half blocks cut and frozen where they stand for a second.</li>
 * </ul>
 * KRÓLEWSKI GNIEW: hits on the Frostbitten build up to five stacks (the item's bar), held six seconds, then draining;
 * at five the next Szronowe Cięcie flies as three, in a fan. And the Krok through a foe and the Cięcie within a second
 * of it makes the crescent twice the size.
 *
 * <p>Released before the crown is whole, it sinks back into the floor and costs nothing; a released tap with the
 * crown not ready is simply the crescent. With both on their cooldowns the key falls through to the off hand (a raised
 * shield). Never against players.
 */
public class KingsrimeSwordItem extends SwordItem {

    // ---------------------------------------------------------------------------------------------------- the numbers
    public static final int CRESCENT_CD = 100, STEP_CD = 180, CROWN_CD = 320;
    /** A release this soon after the press is a tap (the crescent); the crown is whole this long after it. */
    public static final int TAP = 6, CHARGE = 24, MAX_HOLD = 80;
    public static final float CRESCENT_DMG = 12.0F, CRESCENT_BIG_DMG = 16.0F, WRATH_DMG = 14.0F, WRATH_BIG_DMG = 18.0F;
    public static final float STEP_DMG = 14.0F, CROWN_DMG = 20.0F;
    /** The fan of the wrath's three crescents, degrees either side. */
    public static final float WRATH_FAN = 20.0F;
    public static final int WRATH_MAX = 5, WRATH_HOLD = 120, WRATH_DRAIN = 10;
    /** The Krok through a foe, then the Cięcie within this many ticks: the crescent doubles. */
    public static final int COMBO = 20;
    /** The step: its reach, its ticks, its untouchable ticks (from the cast), and how close it must pass to cut. */
    public static final double STEP_REACH = 6.0D, STEP_PASS = 1.15D;
    public static final int STEP_T = 3, STEP_SAFE = 7;

    // ------------------------------------------------------------------------------------------------ the stack's tag
    private static final String HITS = "frozen_dominion:hits";
    private static final String TAG = "frozen_dominion:kingsrime";
    public static final String CD_CRESCENT = "cres", CD_STEP = "step", CD_CROWN = "crown";
    private static final String WRATH = "wrath", WRATH_AT = "wrathAt", STEP_AT = "stepAt", STEP_HIT = "stepHit";

    /** Who is mid-step and untouchable until when (game time); the server's alone. */
    private static final Map<UUID, Long> SAFE_UNTIL = new WeakHashMap<>();
    /** What a hold of the use key is for: set as it starts, read as it ends (server). */
    private static final Map<UUID, Hold> HOLDS = new WeakHashMap<>();

    private static final class Hold {
        final boolean tap;
        final int chargeFrom;
        @Nullable
        KingsrimeSwordCrownEntity crown;

        Hold(boolean tap, int chargeFrom) {
            this.tap = tap;
            this.chargeFrom = chargeFrom;
        }
    }

    public KingsrimeSwordItem() {
        // 1 + the tier's 6 + 11 = 18 damage; 4 - 2.4 = 1.6 a second
        super(KingsrimeItems.KINGSRIME_TIER, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                .attributes(SwordItem.createAttributes(KingsrimeItems.KINGSRIME_TIER, 11, -2.4F)));
    }

    // ================================================================================================ the sword itself
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        FFSwing.swing(entity, FFSwing.blade(), true);
        return super.onEntitySwing(stack, entity);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean bitten = target.hasEffect(FFEffects.FROSTBITE);
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), attacker);
        if (bitten && attacker instanceof ServerPlayer p) {
            addWrath(stack, p);
        }
        int hits = com.jastkub.frozenfortress.util.FFItemData.read(stack).getInt(HITS) + 1;
        if (hits >= 3) {
            hits = 0;
            spikes(attacker);
        }
        final int h = hits;
        com.jastkub.frozenfortress.util.FFItemData.update(stack, t -> t.putInt(HITS, h));
        return super.hurtEnemy(stack, target, attacker);
    }

    /** The passive: a line of ice spikes driven out ahead, one a tick, each a little bigger. */
    private static void spikes(LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 dir = Vec3.directionFromRotation(0.0F, attacker.getYRot());
        for (int i = 1; i <= 5; i++) {
            Vec3 p = attacker.position().add(dir.scale(i * 1.0D));
            float scale = 0.72F + 0.07F * i;
            level.addFreshEntity(new IceSpikeEntity(level, attacker, p.x, p.y, p.z, 6.0F, i, scale));
        }
        level.playSound(null, attacker.blockPosition(), FFSounds.FROST_RELEASE.get(), SoundSource.PLAYERS, 0.9F, 1.2F);
    }

    // ===================================================================================================== the use key
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        long now = level.getGameTime();
        if (player.isShiftKeyDown()) {
            if (!ready(stack, CD_STEP, now)) {
                if (player instanceof ServerPlayer sp) {
                    notReady(sp, stack, CD_STEP, now);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (player instanceof ServerPlayer sp) {
                step(sp, stack);
            }
            return InteractionResultHolder.consume(stack);
        }
        // A SHIELD IN THE OTHER HAND : the plain use key raises it, at once,
        // as it would with any sword - a block a moment late is no block against the king's twin blades. The Royal
        // Step stays on sneak; the crescent and the coronation are for a free hand: sword and board, or the full art.
        if (player.getOffhandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) {
            return InteractionResultHolder.pass(stack);
        }
        boolean cres = ready(stack, CD_CRESCENT, now), crown = ready(stack, CD_CROWN, now);
        if (!cres && !crown) {
            if (player instanceof ServerPlayer sp) {
                notReady(sp, stack, CD_CRESCENT, now);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!crown) {
            // nothing to hold for: the crescent goes the moment the key does
            if (player instanceof ServerPlayer sp) {
                crescent(sp, stack);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (player instanceof ServerPlayer sp) {
            HOLDS.put(sp.getUUID(), new Hold(cres, cres ? TAP : 1));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(living instanceof ServerPlayer p)) {
            return;
        }
        Hold h = HOLDS.get(p.getUUID());
        if (h == null) {
            return;
        }
        int used = getUseDuration(stack, living) - remaining + 1;                // ticks held, this one counted
        if (used == h.chargeFrom) {
            h.crown = KingsrimeSwordCrownEntity.raise((ServerLevel) level, p, CHARGE - h.chargeFrom);
            FFNetwork.playerAnim(p, FrozenFortress.id("kingsrime_plant"), 3);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), FFSounds.KINGSRIME_CROWN_RISE.get(), SoundSource.PLAYERS,
                    1.0F, 1.0F);
        }
        if (used == CHARGE) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), FFSounds.KINGSRIME_CROWN_READY.get(), SoundSource.PLAYERS,
                    1.1F, 1.0F);
        }
        if (used >= MAX_HOLD) {
            p.releaseUsingItem();                                         // a crown is not held forever
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof ServerPlayer p)) {
            return;
        }
        Hold h = HOLDS.remove(p.getUUID());
        if (h == null) {
            return;
        }
        int used = getUseDuration(stack, living) - timeLeft;
        long now = level.getGameTime();
        if (h.tap && used < TAP) {
            sinkCrown(h, p, false);
            if (ready(stack, CD_CRESCENT, now)) {
                crescent(p, stack);
            }
        } else if (used >= CHARGE && h.crown != null && h.crown.isAlive() && ready(stack, CD_CROWN, now)) {
            coronation(p, stack, h.crown);
        } else {
            sinkCrown(h, p, true);
            if (!h.tap && used < TAP) {
                notReady(p, stack, CD_CRESCENT, now);                    // a tap meant for the crescent, still cooling
            }
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        // switched away mid-hold (releaseUsing has already taken the hold when it was a release)
        if (entity instanceof ServerPlayer p) {
            Hold h = HOLDS.remove(p.getUUID());
            if (h != null) {
                sinkCrown(h, p, true);
            }
        }
    }

    private static void sinkCrown(Hold h, ServerPlayer p, boolean sound) {
        if (h.crown != null && h.crown.isAlive()) {
            h.crown.sink();
            FFNetwork.playerAnim(p, null, 4);
            if (sound) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), FFSounds.KINGSRIME_CROWN_SINK.get(),
                        SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    // ===================================================================================================== the skills
    /** SZRONOWE CIĘCIE: one crescent, or the wrath's three; twice the size straight after a Krok through a foe. */
    static void crescent(ServerPlayer p, ItemStack stack) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        CompoundTag k = tag(stack);
        boolean wrath = k.getInt(WRATH) >= WRATH_MAX;
        boolean big = k.getBoolean(STEP_HIT) && now - k.getLong(STEP_AT) <= COMBO;
        float scale = big ? 2.0F : 1.0F;
        float dmg = wrath ? (big ? WRATH_BIG_DMG : WRATH_DMG) : (big ? CRESCENT_BIG_DMG : CRESCENT_DMG);
        float pitch = Mth.clamp(p.getXRot(), -35.0F, 35.0F);
        float[] fan = wrath ? new float[]{-WRATH_FAN, 0.0F, WRATH_FAN} : new float[]{0.0F};
        for (int i = 0; i < fan.length; i++) {
            float roll = wrath ? -12.0F + 9.0F * (i - 1) : -12.0F;
            KingsrimeSwordCrescentEntity.fire(level, p, p.getYRot() + fan[i], pitch, roll, scale, dmg, wrath);
        }
        k.putLong(CD_CRESCENT, now + CRESCENT_CD);
        if (wrath) {
            k.putInt(WRATH, 0);
        }
        if (big) {
            k.putBoolean(STEP_HIT, false);
        }
        save(stack, k);
        FFNetwork.playerAnim(p, FrozenFortress.id("kingsrime_crescent"), 1);
        level.playSound(null, p.getX(), p.getEyeY() - 0.3D, p.getZ(), FFSounds.KINGSRIME_CRESCENT.get(),
                SoundSource.PLAYERS, big ? 1.3F : 1.0F, big ? 0.82F : 0.96F + p.getRandom().nextFloat() * 0.08F);
        if (wrath) {
            level.playSound(null, p.getX(), p.getEyeY() - 0.3D, p.getZ(), FFSounds.KINGSRIME_WRATH.get(),
                    SoundSource.PLAYERS, 1.2F, 1.0F);
        }
    }

    /** KRÓLEWSKI KROK: the path worked out now, walked by the blades' entity over the next three ticks. */
    static void step(ServerPlayer p, ItemStack stack) {
        if (p.isPassenger() || p.isSleeping() || p.isSpectator()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        Vec3 dir = Vec3.directionFromRotation(0.0F, p.getYRot());
        List<Vec3> path = stepPath(p, dir);
        if (path.isEmpty()) {
            p.displayClientMessage(Component.translatable("item.frozen_dominion.kingsrime_sword.no_room")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 from = p.position(), to = path.get(path.size() - 1);
        // who it goes through: their middles within reach of the line it runs, at a height it passes
        List<LivingEntity> passed = new ArrayList<>();
        AABB sweep = new AABB(from, to).inflate(STEP_PASS + 1.0D, 1.0D, STEP_PASS + 1.0D).expandTowards(0.0D, 2.0D, 0.0D);
        for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> foe(p, e))) {
            Vec3 mid = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D);
            double d = segmentDistance(from.add(0.0D, 1.0D, 0.0D), to.add(0.0D, 1.0D, 0.0D), mid);
            if (d <= STEP_PASS + v.getBbWidth() * 0.5D + Math.max(0.0D, v.getBbHeight() - 2.0D) * 0.5D) {
                passed.add(v);
            }
        }
        long now = level.getGameTime();
        CompoundTag k = tag(stack);
        k.putLong(CD_STEP, now + STEP_CD);
        k.putLong(STEP_AT, now + STEP_T);
        k.putBoolean(STEP_HIT, !passed.isEmpty());
        save(stack, k);
        SAFE_UNTIL.put(p.getUUID(), now + STEP_SAFE);
        p.fallDistance = 0.0F;
        KingsrimeSwordBladesEntity.lay(level, p, from, path, passed);
        FFNetwork.playerAnim(p, FrozenFortress.id("kingsrime_step"), 1);
        level.playSound(null, from.x, from.y + 1.0D, from.z, FFSounds.KINGSRIME_STEP.get(), SoundSource.PLAYERS, 1.0F,
                0.95F + p.getRandom().nextFloat() * 0.1F);
    }

    /** KORONACJA MROZU: the crown, whole, bursts. */
    static void coronation(ServerPlayer p, ItemStack stack, KingsrimeSwordCrownEntity crown) {
        long now = p.level().getGameTime();
        CompoundTag k = tag(stack);
        k.putLong(CD_CROWN, now + CROWN_CD);
        save(stack, k);
        crown.burst();
        FFNetwork.playerAnim(p, FrozenFortress.id("kingsrime_coronation"), 2);
        p.level().playSound(null, p.getX(), p.getY() + 0.5D, p.getZ(), FFSounds.KINGSRIME_CORONATION.get(),
                SoundSource.PLAYERS, 1.5F, 1.0F);
    }

    /**
     * Where the step goes: a point every quarter block along `dir`, up to STEP_REACH - climbing a step or a block on
     * its way, stopping at a wall. The STEP_T points it is walked through, or none when there is no room for it.
     */
    static List<Vec3> stepPath(ServerPlayer p, Vec3 dir) {
        Level level = p.level();
        AABB box = p.getBoundingBox();
        double dy = 0.0D, reached = 0.0D;
        List<double[]> marks = new ArrayList<>();
        marks.add(new double[]{0.0D, 0.0D});
        for (double d = 0.25D; d <= STEP_REACH + 1.0E-6D; d += 0.25D) {
            double x = dir.x * d, z = dir.z * d;
            double ny = Double.NaN;
            if (level.noCollision(p, box.move(x, dy, z))) {
                ny = dy;
            } else {
                for (double up : new double[]{0.55D, 1.05D}) {
                    if (dy + up <= 1.3D && level.noCollision(p, box.move(x, dy + up, z))
                            && level.noCollision(p, box.move(dir.x * (d - 0.25D), dy + up, dir.z * (d - 0.25D)))) {
                        ny = dy + up;
                        break;
                    }
                }
            }
            if (Double.isNaN(ny)) {
                break;
            }
            dy = ny;
            reached = d;
            marks.add(new double[]{d, dy});
        }
        List<Vec3> out = new ArrayList<>();
        if (reached < 1.0D) {
            return out;
        }
        for (int i = 1; i <= STEP_T; i++) {
            double want = reached * i / STEP_T, y = 0.0D, at = 0.0D;
            for (double[] m : marks) {
                if (m[0] <= want + 1.0E-6D) {
                    at = m[0];
                    y = m[1];
                }
            }
            out.add(p.position().add(dir.x * at, y, dir.z * at));
        }
        return out;
    }

    // ============================================================================================ the wrath and combo
    private static void addWrath(ItemStack stack, ServerPlayer p) {
        CompoundTag k = tag(stack);
        int before = k.getInt(WRATH);
        int w = Math.min(WRATH_MAX, before + 1);
        k.putInt(WRATH, w);
        k.putLong(WRATH_AT, p.level().getGameTime());
        save(stack, k);
        if (w == WRATH_MAX && before < WRATH_MAX) {
            p.playNotifySound(FFSounds.KINGSRIME_WRATH_READY.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        }
    }

    public static int wrath(ItemStack stack) {
        CompoundTag k = peek(stack);
        return k == null ? 0 : k.getInt(WRATH);
    }

    /** The Krok has just gone through a foe: the next Cięcie is the big one (for the HUD). */
    public static boolean comboOpen(ItemStack stack, long now) {
        CompoundTag k = peek(stack);
        return k != null && k.getBoolean(STEP_HIT) && now - k.getLong(STEP_AT) <= COMBO && now >= k.getLong(STEP_AT) - STEP_T;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag k = peek(stack);
        if (k == null) {
            return;
        }
        long now = level.getGameTime();
        // the wrath: held six seconds from the last stack, then a stack every half second
        int w = k.getInt(WRATH);
        if (w > 0 && now - k.getLong(WRATH_AT) >= WRATH_HOLD) {
            k.putInt(WRATH, w - 1);
            k.putLong(WRATH_AT, now - WRATH_HOLD + WRATH_DRAIN);
            save(stack, k);
        }
        // a skill back: a tick of crystal in the wielder's own ears
        if (selected) {
            String[] cds = {CD_CRESCENT, CD_STEP, CD_CROWN};
            float[] pitch = {1.5F, 1.25F, 0.95F};
            for (int i = 0; i < cds.length; i++) {
                if (k.contains(cds[i]) && k.getLong(cds[i]) == now) {
                    p.playNotifySound(FFSounds.KINGSRIME_READY.get(), SoundSource.PLAYERS, 0.45F, pitch[i]);
                }
            }
        }
    }

    // ===================================================================================================== cooldowns
    /** A COPY of the sword's state (1.21.1: it lives in the stack's custom data); a change goes back by save(). */
    private static CompoundTag tag(ItemStack stack) {
        return com.jastkub.frozenfortress.util.FFItemData.element(stack, TAG);
    }

    private static void save(ItemStack stack, CompoundTag k) {
        com.jastkub.frozenfortress.util.FFItemData.update(stack, t -> t.put(TAG, k));
    }

    /** The sword's state if it has any (a copy), else null - the old getTagElement. */
    @javax.annotation.Nullable
    private static CompoundTag peek(ItemStack stack) {
        CompoundTag t = com.jastkub.frozenfortress.util.FFItemData.read(stack);
        return t.contains(TAG) ? t.getCompound(TAG) : null;
    }

    /** Game time the skill is ready at (0: ready). */
    public static long readyAt(ItemStack stack, String skill) {
        CompoundTag k = peek(stack);
        return k == null ? 0L : k.getLong(skill);
    }

    public static boolean ready(ItemStack stack, String skill, long now) {
        long at = readyAt(stack, skill);
        return at <= now || at - now > 20L * 60L;                         // (a stamp from another world's clock: ready)
    }

    public static int cooldownOf(String skill) {
        return switch (skill) {
            case CD_STEP -> STEP_CD;
            case CD_CROWN -> CROWN_CD;
            default -> CRESCENT_CD;
        };
    }

    private static void notReady(ServerPlayer p, ItemStack stack, String skill, long now) {
        double left = Math.max(0L, readyAt(stack, skill) - now) / 20.0D;
        p.displayClientMessage(Component.translatable("item.frozen_dominion.kingsrime_sword.cooldown",
                Component.translatable("item.frozen_dominion.kingsrime_sword.skill." + skillKey(skill)),
                String.format(java.util.Locale.ROOT, "%.1f", left)).withStyle(ChatFormatting.GRAY), true);
    }

    private static String skillKey(String skill) {
        return switch (skill) {
            case CD_STEP -> "step";
            case CD_CROWN -> "coronation";
            default -> "crescent";
        };
    }

    // ======================================================================================== who the skills cut, how
    /** A foe of `p`'s for the skills: alive, not a player, not his, not a bystander. */
    public static boolean foe(Player p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof Player || e instanceof ArmorStand || !e.isAttackable()) {
            return false;
        }
        if (e.isAlliedTo(p) || e.isPassengerOfSameVehicle(p) || e instanceof Npc) {
            return false;
        }
        if (e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) {
            return false;
        }
        if (e instanceof IronGolem g && g.isPlayerCreated()) {
            return false;
        }
        if (e instanceof HollowGolemEntity g && g.isTamed() && g.mySide(p)) {
            return false;
        }
        return !FFAllies.spares(p, e);
    }

    /** A skill's cut: it always lands (a skill is not lost to the swing's immunity), with Frostbite. */
    public static boolean cut(Player p, LivingEntity v, float dmg, int frostTicks, int frostAmp) {
        v.invulnerableTime = 0;
        boolean hurt = v.hurt(p.damageSources().playerAttack(p), dmg);
        if (v.isAlive()) {
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, frostTicks, frostAmp), p);
        }
        return hurt;
    }

    /** Too big to be rooted (a boss): slowed instead. */
    public static boolean unrootable(LivingEntity e) {
        return e.getMaxHealth() >= 150.0F || e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** In the untouchable part of a step. */
    public static boolean stepping(LivingEntity e) {
        Long until = e instanceof Player ? SAFE_UNTIL.get(e.getUUID()) : null;
        return until != null && e.level().getGameTime() < until;
    }

    static double segmentDistance(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-6D ? 0.0D : Mth.clamp(p.subtract(a).dot(ab) / len, 0.0D, 1.0D);
        return a.add(ab.scale(t)).distanceTo(p);
    }

    // ================================================================================================ bar and tooltip
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return wrath(stack) > 0 || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int w = wrath(stack);
        return w > 0 ? Math.round(13.0F * w / WRATH_MAX) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int w = wrath(stack);
        if (w <= 0) {
            return super.getBarColor(stack);
        }
        if (w < WRATH_MAX) {
            return 0x6FC8FF;
        }
        float k = 0.5F + 0.5F * Mth.sin(net.minecraft.Util.getMillis() / 120.0F);   // full: it pulses
        return Mth.color(0.55F + 0.45F * k, 0.85F + 0.15F * k, 1.0F);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();  // its tag changes all through a fight
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String k = "item.frozen_dominion.kingsrime_sword.";
        tooltip.add(Component.translatable(k + "desc1").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable(k + "desc2").withStyle(ChatFormatting.BLUE));
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable(k + "shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (String s : new String[]{"crescent", "step", "coronation", "wrath"}) {
            tooltip.add(Component.translatable(k + "skill." + s).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(k + "skill." + s + ".input").withStyle(ChatFormatting.WHITE)));
            tooltip.add(Component.literal("  ").append(Component.translatable(k + "skill." + s + ".desc"))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(k + "combo").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(k + "shield").withStyle(ChatFormatting.DARK_GRAY));
    }
}
