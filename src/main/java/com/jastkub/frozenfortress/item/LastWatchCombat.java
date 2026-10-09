package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.jastkub.frozenfortress.entity.LastWatchBeamEntity;
import com.jastkub.frozenfortress.entity.LastWatchChainsEntity;
import com.jastkub.frozenfortress.entity.LastWatchMarkEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * THE RULES OF THE LAST WATCH (Luk Ostatniej Strazy, 07.10.2026) - everything the bow does to what it strikes, in one
 * place, server side: the MARK of the lantern (5 s, a lantern-eye over the foe), the Turnkey's CHAINS that a plain
 * arrow on a marked foe brings up out of the ground round every marked foe (2 s, rooted), the BEAM of a full draw, and
 * CZUWANIE (Vigil), the count of hits in a row kept on the bow.
 *
 * <p>Marks and pins live on the foe (its persistent data: until when, and whose); their pictures are entities of
 * their own (LastWatchMarkEntity, LastWatchChainsEntity) that only show it.
 */
public final class LastWatchCombat {

    // ------------------------------------------------------------------------------------------------ the numbers
    /** Promien Latarni: damage to every foe on its line (Power adds 8% a level). The Kingsrime bow's full draw is
     *  about 33 (42 with its crit) on one foe; this is more, never misses, and goes through all of them. */
    public static final float BEAM_DAMAGE = 44.0F;
    /** Its reach, blocks (the model's fifteen two-block segments). */
    public static final double BEAM_RANGE = 30.0D;
    /** How far either side of its line it takes a foe (on top of the foe's own box). */
    public static final double BEAM_WIDTH = 0.45D;
    /** The split beam of a full Czuwanie: three, this many degrees apart; the middle one hits harder. */
    public static final float SPLIT_ANGLE = 9.0F;
    public static final float SPLIT_CENTRE = 1.25F;
    /** A pinned foe takes this much more from the beam. */
    public static final float PINNED_BONUS = 1.5F;
    public static final int MARK_TICKS = 100;
    public static final int PIN_TICKS = 40;
    /** Bosses and the like (this much health or more, or in forge:bosses) are only held half as long, and slowed
     *  instead of nailed down - a miniboss pinned for good is no fight. */
    public static final float HEAVY_HEALTH = 150.0F;
    public static final float CHAINS_DAMAGE = 8.0F;
    /** How far from the archer the chains find his marked foes. */
    public static final double PIN_REACH = 48.0D;
    public static final int VIGIL_MAX = 5;

    static final String TAG_MARK = FrozenFortress.MODID + ":lw_mark";
    static final String TAG_MARK_BY = FrozenFortress.MODID + ":lw_mark_by";
    static final String TAG_PIN = FrozenFortress.MODID + ":lw_pin";
    static final String TAG_VIGIL = "LWVigil";

    private LastWatchCombat() {
    }

    // ------------------------------------------------------------------------------------------------ who is a foe
    /** Can the bow's light hurt and mark `e`? Everything living but the archer, his own (his pets, his team), armour
     *  stands - and players he may not harm. */
    public static boolean isFoe(Player owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e instanceof ArmorStand || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p) {
            return !p.isCreative() && owner.canHarmPlayer(p);
        }
        if (e instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID())) {
            return false;
        }
        return !e.isAlliedTo(owner);
    }

    /** The watchman's choice: a marked foe, a monster, or whatever is after the archer. */
    public static boolean isHostile(Player owner, LivingEntity e) {
        if (!isFoe(owner, e)) {
            return false;
        }
        return isMarkedBy(e, owner) || e instanceof Enemy || (e instanceof Mob m && m.getTarget() == owner);
    }

    public static boolean isHeavy(LivingEntity e) {
        // (1.20.1's canChangeDimensions() - false for the bosses - is now that and canUsePortal: the vanilla bosses
        //  refuse portals there)
        return e.getMaxHealth() >= HEAVY_HEALTH || !e.canChangeDimensions(e.level(), e.level())
                || (e.isAlive() && !e.isSleeping() && !e.canUsePortal(true))
                || e.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES);
    }

    // ------------------------------------------------------------------------------------------------ the mark
    public static boolean isMarkedBy(LivingEntity e, Player owner) {
        CompoundTag d = e.getPersistentData();
        return d.getLong(TAG_MARK) > e.level().getGameTime() && d.hasUUID(TAG_MARK_BY)
                && d.getUUID(TAG_MARK_BY).equals(owner.getUUID());
    }

    /** Marks `e` for MARK_TICKS (or tops a mark back up), and shows it. */
    public static void mark(LivingEntity e, Player owner) {
        if (!(e.level() instanceof ServerLevel level) || !isFoe(owner, e)) {
            return;
        }
        boolean fresh = !isMarkedBy(e, owner);
        CompoundTag d = e.getPersistentData();
        d.putLong(TAG_MARK, level.getGameTime() + MARK_TICKS);
        d.putUUID(TAG_MARK_BY, owner.getUUID());
        LastWatchMarkEntity fx = LastWatchMarkEntity.on(e);
        if (fx != null) {
            fx.refresh(MARK_TICKS);
        } else {
            level.addFreshEntity(new LastWatchMarkEntity(level, e, MARK_TICKS));
        }
        level.playSound(null, e.getX(), e.getY(0.8D), e.getZ(), FFSounds.LAST_WATCH_MARK.get(), SoundSource.PLAYERS,
                fresh ? 1.0F : 0.7F, fresh ? 1.0F : 1.25F);
    }

    /** Tops up a mark that is already there (Deszcz's arrows); nothing if it is not. */
    public static void refreshMark(LivingEntity e, Player owner) {
        if (isMarkedBy(e, owner)) {
            mark(e, owner);
        }
    }

    static void clearMark(LivingEntity e) {
        e.getPersistentData().remove(TAG_MARK);
        e.getPersistentData().remove(TAG_MARK_BY);
        LastWatchMarkEntity fx = LastWatchMarkEntity.on(e);
        if (fx != null) {
            fx.consume();
        }
    }

    // ------------------------------------------------------------------------------------------------ the chains
    public static boolean isPinned(LivingEntity e) {
        return e.getPersistentData().getLong(TAG_PIN) > e.level().getGameTime();
    }

    /**
     * A plain arrow struck a marked foe: the Turnkey's chains come up out of the ground round EVERY foe the archer has
     * marked (within PIN_REACH of him, and the struck one), and the marks are spent on it.
     */
    public static void pinAllMarked(Player owner, LivingEntity struck) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        List<LivingEntity> marked = level.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(PIN_REACH), e -> isMarkedBy(e, owner));
        if (!marked.contains(struck) && isMarkedBy(struck, owner)) {
            marked.add(struck);
        }
        for (LivingEntity e : marked) {
            clearMark(e);
            pin(level, e, owner);
        }
        if (!marked.isEmpty() && owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("item.frozen_dominion.last_watch_bow.pinned", marked.size())
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    static void pin(ServerLevel level, LivingEntity e, Player owner) {
        boolean heavy = isHeavy(e);
        int ticks = heavy ? PIN_TICKS / 2 : PIN_TICKS;
        e.getPersistentData().putLong(TAG_PIN, level.getGameTime() + ticks);
        LastWatchChainsEntity chains = LastWatchChainsEntity.on(e);
        if (chains != null) {
            chains.extend(ticks);
        } else {
            chains = new LastWatchChainsEntity(level, e, owner, ticks, heavy);
            level.addFreshEntity(chains);
        }
        e.invulnerableTime = 0;
        e.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.THROWN), chains, owner), CHAINS_DAMAGE);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, heavy ? 3 : 9, false, false, true), owner);
        if (!heavy) {
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, -10, false, false, false), owner);
        }
        e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, ticks + 20, 0), owner);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), FFSounds.LAST_WATCH_CHAINS.get(), SoundSource.PLAYERS,
                1.4F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------------------------------------ Czuwanie
    /** The Last Watch bow the archer is using: the hand first, then anywhere on him. */
    @Nullable
    public static ItemStack findBow(Player p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof LastWatchBowItem) {
                return s;
            }
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof LastWatchBowItem) {
                return s;
            }
        }
        return null;
    }

    public static int vigil(ItemStack bow) {
        return Mth.clamp(com.jastkub.frozenfortress.util.FFItemData.read(bow).getInt(TAG_VIGIL), 0, VIGIL_MAX);
    }

    static void setVigil(ItemStack bow, int v) {
        int clamped = Mth.clamp(v, 0, VIGIL_MAX);
        com.jastkub.frozenfortress.util.FFItemData.update(bow, t -> t.putInt(TAG_VIGIL, clamped));
    }

    /** A shot that struck: one more in a row (the fifth lights the bow). */
    public static void vigilHit(Player owner) {
        ItemStack bow = findBow(owner);
        if (bow == null) {
            return;
        }
        int before = vigil(bow);
        if (before >= VIGIL_MAX) {
            return;
        }
        setVigil(bow, before + 1);
        if (owner instanceof ServerPlayer sp) {
            boolean full = before + 1 >= VIGIL_MAX;
            sp.playNotifySound(FFSounds.LAST_WATCH_VIGIL.get(), SoundSource.PLAYERS, full ? 1.0F : 0.6F,
                    full ? 1.6F : 0.8F + 0.12F * before);
            if (full) {
                sp.displayClientMessage(Component.translatable("item.frozen_dominion.last_watch_bow.vigil_full")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** A shot that struck nothing: the watch is broken. */
    public static void vigilMiss(Player owner) {
        ItemStack bow = findBow(owner);
        if (bow != null && vigil(bow) > 0) {
            setVigil(bow, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the beam
    /**
     * PROMIEN LATARNI - a full draw. Not an arrow: the lantern's light, thirty blocks of it, through every foe on its
     * line (the first wall stops it). Each foe it passes is struck (+50% if the chains hold him) and marked. With a
     * full Czuwanie it splits in three - the middle beam harder - and spends the Czuwanie.
     */
    public static void fireBeam(ServerLevel level, Player player, ItemStack bow) {
        boolean split = vigil(bow) >= VIGIL_MAX;
        Vec3 look = player.getLookAngle();
        Vec3 origin = player.getEyePosition().add(look.scale(0.6D)).add(0.0D, -0.18D, 0.0D);
        int power = com.jastkub.frozenfortress.registry.FFEnchantments.level(Enchantments.POWER, bow, level);
        int punch = com.jastkub.frozenfortress.registry.FFEnchantments.level(Enchantments.PUNCH, bow, level);
        boolean flame = com.jastkub.frozenfortress.registry.FFEnchantments.level(Enchantments.FLAME, bow, level) > 0;
        float base = BEAM_DAMAGE * (1.0F + 0.08F * power);
        float[] offsets = split ? new float[]{0.0F, -SPLIT_ANGLE, SPLIT_ANGLE} : new float[]{0.0F};
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();
        Map<LivingEntity, Vec3> dirOf = new LinkedHashMap<>();
        LastWatchBeamEntity centre = null;
        for (float off : offsets) {
            Vec3 dir = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + off);
            Vec3 far = origin.add(dir.scale(BEAM_RANGE));
            HitResult wall = level.clip(new ClipContext(origin, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    player));
            double length = wall.getType() == HitResult.Type.MISS ? BEAM_RANGE : origin.distanceTo(wall.getLocation());
            Vec3 end = origin.add(dir.scale(length));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(1.5D),
                    e -> isFoe(player, e))) {
                AABB box = e.getBoundingBox().inflate(BEAM_WIDTH);
                Optional<Vec3> on = box.clip(origin, end);
                if (on.isPresent() || box.contains(origin)) {
                    float dmg = base * (split && off == 0.0F ? SPLIT_CENTRE : 1.0F);
                    if (dmg > hits.getOrDefault(e, 0.0F)) {
                        hits.put(e, dmg);
                        dirOf.put(e, dir);
                    }
                }
            }
            LastWatchBeamEntity beam = new LastWatchBeamEntity(level, origin, dir, (float) length, split);
            level.addFreshEntity(beam);
            if (centre == null) {
                centre = beam;
            }
        }
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.ARROW), centre, player);
        for (Map.Entry<LivingEntity, Float> h : hits.entrySet()) {
            LivingEntity e = h.getKey();
            float dmg = h.getValue() * (isPinned(e) ? PINNED_BONUS : 1.0F);
            e.invulnerableTime = 0;
            if (e.hurt(src, dmg)) {
                e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), player);
                if (flame) {
                    e.igniteForSeconds(5);
                }
                Vec3 d = dirOf.get(e);
                if (punch > 0) {
                    double k = punch * 0.6D * Math.max(0.0D, 1.0D - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    e.push(d.x * k, 0.1D, d.z * k);
                }
            }
            if (e.isAlive()) {
                mark(e, player);
            }
            float size = Mth.clamp(e.getBbWidth() * 1.5F, 0.8F, 2.6F);
            AttackFxEntity.spawn(level, "last_watch_impact", e.position().add(0.0D, e.getBbHeight() * 0.5D - 0.5D * size, 0.0D),
                    player.getYRot(), size, 10, player);
        }
        if (split) {
            setVigil(bow, 0);
        } else if (hits.isEmpty()) {
            vigilMiss(player);
        } else {
            vigilHit(player);
        }
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                split ? FFSounds.LAST_WATCH_SPLIT.get() : FFSounds.LAST_WATCH_BEAM.get(), SoundSource.PLAYERS, 2.2F,
                0.95F + level.random.nextFloat() * 0.1F);
        FFNetwork.playerAnim(player, FrozenFortress.id("last_watch_recoil"), 2);
    }
}
