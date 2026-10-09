package com.jastkub.frozenfortress.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneWandFxEntity;
import com.jastkub.frozenfortress.entity.BoneWandRingEntity;
import com.jastkub.frozenfortress.entity.BoneWandSkeletonEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ROZDZKA UMARLYCH - THE WAND OF THE DEAD (07.10.2026). The
 * smith's iron skull on an Everfrost shaft, its grip a Frost Skeleton's own vertebra - the 1% drop that is the whole
 * gate (forged on the Frost Anvil: fitting / ingot / vertebra, top to bottom). A caller, not a fighter: a weak blow,
 * and the dead of the citadel at its master's word. Its worth is numbers and distraction - every servant is one of
 * the citadel's pests (BoneWandSkeletonEntity).
 *
 * <pre>
 *   use            ZEW GROBU - a ring of the grave opens on the floor where he looks (up to 16 blocks; under the foe
 *                  his look rests on, if any) and two Frost Skeletons claw up out of it as his servants, 40 s.
 *                  Never more than four: the oldest goes to dust                (BoneWandRingEntity)       12 s
 *   sneak + use    ROZKAZ - every servant goes for the foe under his crosshair (or the nearest before him),
 *                  half again as quick for 5 s, leaping at it first; a skull of green frost marks it     6 s
 * </pre>
 *
 * Both cool in the stack's NBT ({@link #TAG}: the game time each is ready again); the item's bar fills back as the
 * Call cools, and the wand's skull goes dark while it does (the "frozen_dominion:bone_wand_spent" item property,
 * BoneWandClient). Held, the action bar says what is cooling and how many servants he has.
 */
public class BoneWandItem extends Item {

    public static final int SUMMON_CD = 240, COMMAND_CD = 120;
    public static final int SERVANTS_PER_CALL = 2;
    public static final double SUMMON_RANGE = 16.0D, COMMAND_RANGE = 24.0D, CONE_COS = 0.8D;
    /** How far down from where his look ends it seeks a floor. */
    static final double FLOOR_DROP = 8.0D;
    public static final float MELEE = 3.0F, MELEE_SPEED = 1.6F;

    public static final String TAG = "BoneWand";
    static final String SUMMON = "SummonAt", COMMAND = "CommandAt";

    @Nullable
    private net.minecraft.world.item.component.ItemAttributeModifiers mainHand;

    public BoneWandItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    // ================================================================================================ state (custom data)
    /** Sets one of the wand's ready-at times (1.21.1: its state lives in the stack's custom data). */
    static void setReadyAt(ItemStack stack, String key, long at) {
        com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, t -> t.putLong(key, at));
    }

    public static long readyAt(ItemStack stack, String key) {
        CompoundTag t = com.jastkub.frozenfortress.util.FFItemData.read(stack);
        return t.contains(TAG) ? t.getCompound(TAG).getLong(key) : 0L;
    }

    /** Is the Call still cooling at game time `now`? (The model's dark skull, the bar.) */
    public static boolean spent(ItemStack stack, long now) {
        return now >= 0 && readyAt(stack, SUMMON) > now;
    }

    // ================================================================================================ the inputs
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        String key = player.isShiftKeyDown() ? COMMAND : SUMMON;
        if (readyAt(stack, key) > now) {
            if (player instanceof ServerPlayer sp) {
                notYet(sp, key, readyAt(stack, key) - now);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer sp) {
            if (key.equals(COMMAND)) {
                command(sp, stack, now);
            } else {
                summon(sp, stack, now);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // ================================================================================================ Zew Grobu
    private void summon(ServerPlayer p, ItemStack stack, long now) {
        Vec3 at = summonPoint(p);
        if (at == null) {
            fizzle(p, "nowhere");
            return;
        }
        Level level = p.level();
        Vec3 to = at.subtract(p.position());
        float yaw = to.horizontalDistanceSqr() > 1.0E-4D
                ? (float) (Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F : p.getYRot();
        level.addFreshEntity(new BoneWandRingEntity(level, p, at, yaw, SERVANTS_PER_CALL, BoneWandRingEntity.FULL));
        setReadyAt(stack, SUMMON, now + SUMMON_CD);
        FFNetwork.playerAnim(p, FrozenFortress.id("bone_wand_summon"), 2);
        if (level instanceof ServerLevel s) {
            s.playSound(null, at.x, at.y + 0.5D, at.z, FFSounds.BONE_WAND_SUMMON.get(), SoundSource.PLAYERS, 1.6F,
                    0.95F + p.getRandom().nextFloat() * 0.1F);
            Vec3 tip = wandTip(p);
            s.sendParticles(ParticleTypes.SCULK_SOUL, tip.x, tip.y, tip.z, 4, 0.08D, 0.08D, 0.08D, 0.01D);
        }
    }

    /**
     * Where the grave opens: the floor under the foe his look rests on (within {@link #SUMMON_RANGE}), else the floor
     * where his look meets the ground - or, if it meets a wall or nothing, the floor under where it ends. Null if
     * there is no floor with room over it for a skeleton.
     */
    @Nullable
    static Vec3 summonPoint(Player p) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(SUMMON_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 reach = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        EntityHitResult foe = ProjectileUtil.getEntityHitResult(level, p, eye, reach, new AABB(eye, reach).inflate(1.0D),
                e -> BoneWandSkeletonEntity.isFoe(p, e));
        if (foe != null) {
            return floorUnder(level, foe.getEntity().position().add(0.0D, 0.5D, 0.0D), p);
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
            return floorUnder(level, hit.getLocation().add(0.0D, 0.5D, 0.0D), p);
        }
        Vec3 from = hit.getType() == HitResult.Type.MISS ? end : reach.subtract(look.scale(0.5D));
        return floorUnder(level, from, p);
    }

    /**
     * The first floor under `from` (within {@link #FLOOR_DROP} blocks: a slab, a stair, a carpet will do) with room
     * over it for a skeleton to stand.
     */
    @Nullable
    static Vec3 floorUnder(Level level, Vec3 from, Entity by) {
        BlockHitResult down = level.clip(new ClipContext(from, from.add(0.0D, -FLOOR_DROP, 0.0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, by));
        if (down.getType() != HitResult.Type.BLOCK || down.getDirection() != Direction.UP) {
            return null;
        }
        Vec3 at = down.getLocation();
        boolean room = level.noCollision(new AABB(at.x - 0.3D, at.y + 0.01D, at.z - 0.3D, at.x + 0.3D, at.y + 1.8D,
                at.z + 0.3D));
        return room ? at : null;
    }

    // ================================================================================================ Rozkaz
    private void command(ServerPlayer p, ItemStack stack, long now) {
        List<BoneWandSkeletonEntity> mine = BoneWandSkeletonEntity.servants(p.level(), p);
        if (mine.isEmpty()) {
            fizzle(p, "no_servants");
            return;
        }
        LivingEntity target = pickTarget(p);
        if (target == null) {
            fizzle(p, "no_target");
            return;
        }
        for (BoneWandSkeletonEntity s : mine) {
            s.command(target, BoneWandSkeletonEntity.COMMAND_T);
        }
        List<BoneWandFxEntity> marks = p.level().getEntitiesOfClass(BoneWandFxEntity.class,
                target.getBoundingBox().inflate(2.0D), f -> BoneWandFxEntity.MARK.equals(f.kind())
                        && f.followed() == target.getId());
        if (marks.isEmpty()) {
            float size = Mth.clamp(target.getBbWidth() / 0.7F, 0.8F, 2.4F);
            BoneWandFxEntity.spawn(p.level(), BoneWandFxEntity.MARK, target.position(), 0.0F, size,
                    BoneWandSkeletonEntity.COMMAND_T).follow(target, target.getBbHeight() + BoneWandFxEntity.MARK_GAP);
        } else {
            marks.forEach(f -> f.extend(BoneWandSkeletonEntity.COMMAND_T));
        }
        setReadyAt(stack, COMMAND, now + COMMAND_CD);
        FFNetwork.playerAnim(p, FrozenFortress.id("bone_wand_command"), 2);
        say(p, FFSounds.BONE_WAND_COMMAND.get(), 1.2F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        if (p.level() instanceof ServerLevel s) {
            Vec3 tip = wandTip(p);
            s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, tip.x, tip.y, tip.z, 5, 0.05D, 0.05D, 0.05D, 0.02D);
        }
        p.displayClientMessage(Component.translatable("msg.frozen_dominion.bone_wand.commanded", mine.size(),
                target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
    }

    /** What his look rests on (within {@link #COMMAND_RANGE}), else the nearest foe before him (~37 deg), else none. */
    @Nullable
    static LivingEntity pickTarget(Player p) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(COMMAND_RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 reach = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, reach, new AABB(eye, reach).inflate(1.5D),
                e -> BoneWandSkeletonEntity.isFoe(p, e));
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            return le;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20.0D),
                e -> BoneWandSkeletonEntity.isFoe(p, e) && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p))
                        && p.hasLineOfSight(e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d < 0.5D || to.scale(1.0D / d).dot(look) < CONE_COS) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // ================================================================================================ every tick
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) {
            return;
        }
        boolean held = selected || p.getOffhandItem() == stack;
        if (!held) {
            return;
        }
        long now = level.getGameTime();
        if (readyAt(stack, SUMMON) == now) {
            p.playNotifySound(FFSounds.BONE_WAND_READY.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        }
        if (now % 10 == 0) {
            int n = BoneWandSkeletonEntity.servants(level, p).size();
            if (n > 0 || readyAt(stack, SUMMON) > now || readyAt(stack, COMMAND) > now) {
                p.displayClientMessage(status(stack, now, n), true);
            }
        }
    }

    static Component status(ItemStack stack, long now, int servants) {
        return Component.translatable("msg.frozen_dominion.bone_wand.status", left(stack, SUMMON, now),
                left(stack, COMMAND, now), Component.literal(servants + "/" + BoneWandSkeletonEntity.MAX_PER_OWNER)
                        .withStyle(servants > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY);
    }

    static Component left(ItemStack stack, String key, long now) {
        long t = readyAt(stack, key) - now;
        return t <= 0 ? Component.translatable("msg.frozen_dominion.bone_wand.ready").withStyle(ChatFormatting.GREEN)
                : Component.translatable("msg.frozen_dominion.bone_wand.seconds", secs(t)).withStyle(ChatFormatting.DARK_GRAY);
    }

    static String secs(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    private static void notYet(ServerPlayer p, String key, long ticks) {
        String skill = key.equals(COMMAND) ? "command" : "summon";
        p.displayClientMessage(Component.translatable("msg.frozen_dominion.bone_wand.cooldown",
                Component.translatable("msg.frozen_dominion.bone_wand." + skill), secs(ticks))
                .withStyle(ChatFormatting.GRAY), true);
        p.playNotifySound(FFSounds.BONE_WAND_FIZZLE.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    private static void fizzle(ServerPlayer p, String why) {
        p.displayClientMessage(Component.translatable("msg.frozen_dominion.bone_wand." + why)
                .withStyle(ChatFormatting.GRAY), true);
        say(p, FFSounds.BONE_WAND_FIZZLE.get(), 0.7F, 1.0F);
    }

    private static void say(Entity at, SoundEvent sound, float volume, float pitch) {
        if (at.level() instanceof ServerLevel s) {
            s.playSound(null, at.getX(), at.getY() + 1.0D, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    /** About where the wand's skull is (for the particles that go with its casting). */
    static Vec3 wandTip(Player p) {
        Vec3 look = p.getViewVector(1.0F);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        right = right.lengthSqr() < 1.0E-4D ? Vec3.ZERO : right.normalize();
        return p.getEyePosition().add(look.scale(0.9D)).add(right.scale(0.35D)).add(0.0D, -0.25D, 0.0D);
    }

    // ================================================================================================ the bar: the Call
    @Override
    public boolean isBarVisible(ItemStack stack) {
        long now = clientNow();
        return now >= 0 && readyAt(stack, SUMMON) > now;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long now = clientNow();
        long left = readyAt(stack, SUMMON) - now;
        return Math.round(13.0F * Mth.clamp(1.0F - left / (float) SUMMON_CD, 0.0F, 1.0F));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x8CF5B4;
    }

    /** The client world's game time (the bar is only ever drawn there), or -1. */
    static long clientNow() {
        return net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                ? com.jastkub.frozenfortress.client.BoneWandClient.gameTime() : -1L;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ================================================================================================ the hand
    /** (1.21.1: the modifiers name their slot - the main hand, as before - and carry ids, not UUIDs) */
    @Override
    public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        if (mainHand == null) {
            net.minecraft.world.entity.EquipmentSlotGroup hand = net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND;
            mainHand = net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, MELEE - 1.0F,
                            AttributeModifier.Operation.ADD_VALUE), hand)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, MELEE_SPEED - 4.0F,
                            AttributeModifier.Operation.ADD_VALUE), hand)
                    .build();
        }
        return mainHand;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String k = "item.frozen_dominion.bone_wand.";
        tooltip.add(Component.translatable(k + "summon").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(k + "command").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(k + "servants", BoneWandSkeletonEntity.MAX_PER_OWNER)
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
