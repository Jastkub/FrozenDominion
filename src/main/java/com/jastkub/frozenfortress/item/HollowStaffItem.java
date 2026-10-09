package com.jastkub.frozenfortress.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowStaffBellEntity;
import com.jastkub.frozenfortress.entity.HollowStaffFxEntity;
import com.jastkub.frozenfortress.entity.HollowStaffMagic;
import com.jastkub.frozenfortress.entity.HollowStaffRuneEntity;
import com.jastkub.frozenfortress.entity.HollowStaffTideEntity;
import com.jastkub.frozenfortress.network.FFNetwork;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.UUID;

/**
 * KOSTUR PUSTEGO KRÓLA - THE STAFF OF THE HOLLOW KING (legendary, 07.10.2026). His own staff remade on the Frost Anvil
 * from the Rime Priestess's crystal, the Shade Shepherd's bell and the Drowned Lady's braid, a Crown Shard in the core
 * (tools/gen_hollow_staff.py draws it and every spell). A caster's weapon: a weak blow, strong skills.
 *
 * <pre>
 *   HOLD use        LITANIA - runes come round him one by one (a sigil under his feet), up to five; let go and
 *                   they fly one after another at what he looks at (or the nearest foe before him), homing:
 *                   each frostbites a little and MARKS for 6 s                  (HollowStaffRuneEntity)
 *   TAP use         DZWON POKUTY - a bell hung over the point he names, its shadow the tell, dropped 0.8 s later:
 *                   a heavy blow, darkness, and its toll knocks the MARKED down   (HollowStaffBellEntity)    9 s
 *   SNEAK + use     CZARNY PRZYPŁYW - a ring of black water out from him, then dragged back with all it caught;
 *                   the MARKED are held where it leaves them 1.5 s              (HollowStaffTideEntity)    12 s
 *   15 casts        REQUIEM, 8 s: he hovers, the staff's crown and halo light, every cooldown is halved and the
 *                   Litany charges twice as fast. The count shows on the item's bar.
 *   passive         what dies carrying his mark rises as his shade for 10 s       (HollowStaffShadeEntity)
 *   combos          a bell dropped on what the Tide holds strikes half again as hard; a rune striking a foe under
 *                   the bell's shadow splits in two
 * </pre>
 *
 * Everything per staff lives in its NBT ({@link #TAG}): the casts toward Requiem, when Requiem ends, when the Bell and
 * the Tide are ready again, when a new chant may begin. The skills run on the server; the client only predicts what
 * the NBT already tells it (a skill on cooldown is not even begun). With Iron's Spells in the pack, in the main hand:
 * +20% ice spell power and +150 mana (looked up by id - never a hard dependency).
 */
public class HollowStaffItem extends Item {

    // ---- the Litany
    public static final int TAP = 6;
    public static final int FIRST_RUNE = 10, RUNE_EVERY = 12, FIRST_RUNE_REQUIEM = 7, RUNE_EVERY_REQUIEM = 6;
    public static final int MAX_RUNES = 5, RECOVER = 8, CHANT_SOUND_EVERY = 28;
    public static final double RUNE_RANGE = 32.0D, CONE_COS = 0.82D;
    // ---- the Bell and the Tide
    public static final int BELL_CD = 180, TIDE_CD = 240;
    public static final double BELL_RANGE = 24.0D;
    // ---- Requiem
    public static final int REQUIEM_CASTS = 15, REQUIEM_T = 160;

    public static final String TAG = "HollowStaff";
    static final String CASTS = "Casts", REQUIEM = "RequiemUntil", BELL = "BellAt", TIDE = "TideAt", RECOVER_AT = "RecoverAt",
            RELEASED = "Released";

    static final ResourceLocation ISS_ICE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_spell_power");
    static final ResourceLocation ISS_MANA = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "max_mana");
    static final ResourceLocation ISS_ICE_ID = FrozenFortress.id("hollow_staff_ice_power");
    static final ResourceLocation ISS_MANA_ID = FrozenFortress.id("hollow_staff_mana");
    public static final float MELEE = 5.0F, MELEE_SPEED = 1.0F;

    @Nullable
    private net.minecraft.world.item.component.ItemAttributeModifiers mainHand;

    public HollowStaffItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    // ================================================================================================ state (custom data)
    /** A COPY of the staff's state (1.21.1: it lives in the stack's custom data); a change goes back by save(). */
    static CompoundTag state(ItemStack stack) {
        return com.jastkub.frozenfortress.util.FFItemData.element(stack, TAG);
    }

    static void save(ItemStack stack, CompoundTag s) {
        com.jastkub.frozenfortress.util.FFItemData.update(stack, t -> t.put(TAG, s));
    }

    @Nullable
    static CompoundTag peek(ItemStack stack) {
        CompoundTag t = com.jastkub.frozenfortress.util.FFItemData.read(stack);
        return t.contains(TAG) ? t.getCompound(TAG) : null;
    }

    public static boolean inRequiem(ItemStack stack, long now) {
        CompoundTag s = peek(stack);
        return s != null && s.getLong(REQUIEM) > now;
    }

    /** The model's state (the "frozen_dominion:requiem" item property): its crown and halo alight. */
    public static boolean requiemLit(ItemStack stack, @Nullable Level level) {
        return level != null && inRequiem(stack, level.getGameTime());
    }

    static int casts(ItemStack stack) {
        CompoundTag s = peek(stack);
        return s == null ? 0 : s.getInt(CASTS);
    }

    static long readyAt(ItemStack stack, String key) {
        CompoundTag s = peek(stack);
        return s == null ? 0L : s.getLong(key);
    }

    static int cooldown(int base, ItemStack stack, long now) {
        return inRequiem(stack, now) ? base / 2 : base;
    }

    /** Is `who` chanting the Litany with this staff now? */
    public static boolean chanting(LivingEntity who) {
        return who.isUsingItem() && who.getUseItem().getItem() instanceof HollowStaffItem;
    }

    // ================================================================================================ the inputs
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        if (player.isShiftKeyDown()) {
            if (readyAt(stack, TIDE) > now) {
                if (player instanceof ServerPlayer sp) {
                    notYet(sp, "tide", readyAt(stack, TIDE) - now);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (player instanceof ServerPlayer sp) {
                castTide(sp, stack, now);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (readyAt(stack, RECOVER_AT) > now) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, s -> s.putBoolean(RELEASED, false));
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Every tick it is held: after the tap's window the chant begins, and a rune comes on each of its beats. */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (level.isClientSide || !(living instanceof ServerPlayer p)) {
            return;
        }
        int used = getUseDuration(stack, living) - remaining;
        long now = level.getGameTime();
        boolean req = inRequiem(stack, now);
        if (used == TAP + 1) {                                     // not a tap: the chant
            FFNetwork.playerAnim(p, FrozenFortress.id("hollow_staff_chant"), 4);
            HollowStaffFxEntity.spawn(level, HollowStaffFxEntity.SIGIL, p.position(), 0.0F, 1.0F, 40).follow(p, 0.05F);
        }
        if (used > TAP && (used - TAP - 1) % CHANT_SOUND_EVERY == 0) {
            say(level, p, FFSounds.HOLLOW_STAFF_CHANT.get(), 0.9F, req ? 1.15F : 1.0F);
        }
        if (used > TAP && used % 10 == 0) {
            for (HollowStaffFxEntity f : level.getEntitiesOfClass(HollowStaffFxEntity.class, p.getBoundingBox().inflate(2.0D),
                    f -> HollowStaffFxEntity.SIGIL.equals(f.kind()) && f.followed() == p.getId())) {
                f.keepAlive(40);
            }
        }
        int first = req ? FIRST_RUNE_REQUIEM : FIRST_RUNE, every = req ? RUNE_EVERY_REQUIEM : RUNE_EVERY;
        if (used >= first && (used - first) % every == 0) {
            int slot = (used - first) / every;
            if (slot < MAX_RUNES) {
                level.addFreshEntity(new HollowStaffRuneEntity(level, p, slot));
                say(level, p, FFSounds.HOLLOW_STAFF_RUNE_FORM.get(), 0.9F, 0.8F + 0.14F * slot);
            }
        }
    }

    /** Let go: a tap rings the Bell; a held chant looses its runes. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide || !(living instanceof ServerPlayer p)) {
            return;
        }
        int used = getUseDuration(stack, living) - remaining;
        long now = level.getGameTime();
        com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, s -> s.putBoolean(RELEASED, true));
        if (used <= TAP) {
            if (readyAt(stack, BELL) > now) {
                notYet(p, "bell", readyAt(stack, BELL) - now);
                return;
            }
            castBell(p, stack, now);
            return;
        }
        List<HollowStaffRuneEntity> runes = HollowStaffRuneEntity.orbiting(p);
        if (runes.isEmpty()) {
            FFNetwork.playerAnim(p, null, 4);
            say(level, p, FFSounds.HOLLOW_STAFF_FIZZLE.get(), 0.7F, 1.0F);
            return;
        }
        LivingEntity target = pickTarget(p);
        Vec3 look = p.getViewVector(1.0F);
        for (int i = 0; i < runes.size(); i++) {
            runes.get(i).launch(target, look, i * HollowStaffRuneEntity.LAUNCH_GAP);
        }
        FFNetwork.playerAnim(p, FrozenFortress.id("hollow_staff_release"), 2);
        com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, s -> s.putLong(RECOVER_AT, now + RECOVER));
        counted(p, stack, now);
    }

    /** Use stopped without a release (another item taken up, a blow that broke it): the body comes back to itself.
     *  Its runes break by themselves (HollowStaffRuneEntity: no chant, no ring). */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (!entity.level().isClientSide && entity instanceof ServerPlayer p) {
            CompoundTag s = peek(stack);
            if (s == null || !s.getBoolean(RELEASED)) {
                FFNetwork.playerAnim(p, null, 4);
            }
        }
    }

    // ================================================================================================ the skills
    private void castBell(ServerPlayer p, ItemStack stack, long now) {
        Level level = p.level();
        Vec3 at = bellPoint(p);
        level.addFreshEntity(new HollowStaffBellEntity(level, p, at));
        long bellAt = now + cooldown(BELL_CD, stack, now);
        com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, s -> s.putLong(BELL, bellAt));
        FFNetwork.playerAnim(p, FrozenFortress.id("hollow_staff_bell"), 2);
        counted(p, stack, now);
    }

    private void castTide(ServerPlayer p, ItemStack stack, long now) {
        Level level = p.level();
        level.addFreshEntity(new HollowStaffTideEntity(level, p));
        long tideAt = now + cooldown(TIDE_CD, stack, now);
        com.jastkub.frozenfortress.util.FFItemData.updateElement(stack, TAG, s -> s.putLong(TIDE, tideAt));
        FFNetwork.playerAnim(p, FrozenFortress.id("hollow_staff_tide"), 2);
        counted(p, stack, now);
    }

    /** The floor under what he looks at: the first foe or wall down his look within BELL_RANGE, else the end of it. */
    static Vec3 bellPoint(Player p) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0F).scale(BELL_RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 reach = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, reach,
                new AABB(eye, reach).inflate(1.0D), e -> HollowStaffMagic.isFoe(p, e));
        Vec3 at = hit != null ? hit.getEntity().position().add(0.0D, 0.1D, 0.0D)
                : (wall.getType() == HitResult.Type.MISS ? end : reach.subtract(p.getViewVector(1.0F).scale(0.3D)));
        return HollowStaffBellEntity.floorUnder(level, at);
    }

    /** What his look rests on, else the nearest foe before him (within ~35 degrees), else nobody. */
    @Nullable
    static LivingEntity pickTarget(Player p) {
        Level level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RUNE_RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 reach = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, reach,
                new AABB(eye, reach).inflate(1.5D), e -> HollowStaffMagic.isFoe(p, e));
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            return le;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0D),
                e -> HollowStaffMagic.inTheFight(p, e) && p.hasLineOfSight(e))) {
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

    // ================================================================================================ Requiem
    /** One cast toward Requiem (not counted while it lasts). */
    private void counted(ServerPlayer p, ItemStack stack, long now) {
        if (inRequiem(stack, now)) {
            return;
        }
        CompoundTag s = state(stack);
        int c = s.getInt(CASTS) + 1;
        if (c >= REQUIEM_CASTS) {
            s.putInt(CASTS, 0);
            save(stack, s);                                         // (saved before Requiem writes its own)
            beginRequiem(p, stack, now);
        } else {
            s.putInt(CASTS, c);
            save(stack, s);
        }
    }

    private void beginRequiem(ServerPlayer p, ItemStack stack, long now) {
        CompoundTag s = state(stack);
        s.putLong(REQUIEM, now + REQUIEM_T);
        for (String k : new String[]{BELL, TIDE}) {               // what is still cooling halves at once
            long left = s.getLong(k) - now;
            if (left > 0) {
                s.putLong(k, now + left / 2);
            }
        }
        save(stack, s);
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 14, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, REQUIEM_T + 10, 0, false, false, true));
        HollowStaffFxEntity.spawn(p.level(), HollowStaffFxEntity.REQUIEM, p.position(), p.getYRot(), 1.0F, 30);
        HollowStaffFxEntity.spawn(p.level(), HollowStaffFxEntity.HALO, p.position(), 0.0F, 1.0F, REQUIEM_T)
                .follow(p, p.getBbHeight() + 0.35F);
        FFNetwork.playerAnim(p, FrozenFortress.id("hollow_staff_requiem"), 2);
        say(p.level(), p, FFSounds.HOLLOW_STAFF_REQUIEM.get(), 2.0F, 1.0F);
        p.displayClientMessage(Component.translatable("msg.frozen_dominion.hollow_staff.requiem")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    // ================================================================================================ every tick
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag s = peek(stack);
        if (s == null) {
            return;
        }
        long now = level.getGameTime();
        long req = s.getLong(REQUIEM);
        if (req != 0 && req <= now) {
            s.remove(REQUIEM);
            say(level, p, FFSounds.HOLLOW_STAFF_REQUIEM_END.get(), 1.0F, 1.0F);
        }
        boolean held = selected || p.getOffhandItem() == stack;
        if (!held) {
            return;
        }
        if (s.getLong(BELL) == now) {
            p.playNotifySound(FFSounds.HOLLOW_STAFF_READY.get(), SoundSource.PLAYERS, 0.6F, 1.25F);
        }
        if (s.getLong(TIDE) == now) {
            p.playNotifySound(FFSounds.HOLLOW_STAFF_READY.get(), SoundSource.PLAYERS, 0.6F, 0.85F);
        }
        boolean busy = s.getLong(BELL) > now || s.getLong(TIDE) > now || s.getLong(REQUIEM) > now;
        if (busy && now % 10 == 0 && !p.isUsingItem()) {
            p.displayClientMessage(status(stack, now), true);
        }
    }

    static Component status(ItemStack stack, long now) {
        long req = readyAt(stack, REQUIEM) - now;
        MutableComponent r = req > 0
                ? Component.translatable("msg.frozen_dominion.hollow_staff.requiem_left", secs(req)).withStyle(ChatFormatting.GOLD)
                : Component.literal(casts(stack) + "/" + REQUIEM_CASTS).withStyle(ChatFormatting.LIGHT_PURPLE);
        return Component.translatable("msg.frozen_dominion.hollow_staff.status", r, left(stack, BELL, now), left(stack, TIDE, now))
                .withStyle(ChatFormatting.GRAY);
    }

    static Component left(ItemStack stack, String key, long now) {
        long t = readyAt(stack, key) - now;
        return t <= 0 ? Component.translatable("msg.frozen_dominion.hollow_staff.ready").withStyle(ChatFormatting.AQUA)
                : Component.translatable("msg.frozen_dominion.hollow_staff.seconds", secs(t)).withStyle(ChatFormatting.DARK_GRAY);
    }

    static String secs(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    private static void notYet(ServerPlayer p, String skill, long ticks) {
        p.displayClientMessage(Component.translatable("msg.frozen_dominion.hollow_staff.cooldown",
                Component.translatable("msg.frozen_dominion.hollow_staff." + skill), secs(ticks))
                .withStyle(ChatFormatting.GRAY), true);
        p.playNotifySound(FFSounds.HOLLOW_STAFF_FIZZLE.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    private static void say(Level level, Entity at, SoundEvent sound, float volume, float pitch) {
        if (level instanceof ServerLevel s) {
            s.playSound(null, at.getX(), at.getY() + 1.0D, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    // ================================================================================================ the bar: Requiem
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return casts(stack) > 0 || readyAt(stack, REQUIEM) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long until = readyAt(stack, REQUIEM);
        if (until > 0) {                                           // Requiem: how much of it is left
            long now = net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                    ? com.jastkub.frozenfortress.client.HollowStaffClient.gameTime() : -1L;   // (only ever drawn there)
            if (now >= 0) {
                return Math.round(13.0F * Mth.clamp((until - now) / (float) REQUIEM_T, 0.0F, 1.0F));
            }
            return 13;
        }
        return Math.round(13.0F * casts(stack) / REQUIEM_CASTS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return readyAt(stack, REQUIEM) > 0 ? 0xFFE7A6 : 0x9C7BFF;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    // ================================================================================================ the hand
    /** (1.21.1: the modifiers name their slot - the main hand, as before - and carry ids, not UUIDs) */
    @Override
    public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        if (mainHand == null) {
            net.minecraft.world.entity.EquipmentSlotGroup hand = net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND;
            net.minecraft.world.item.component.ItemAttributeModifiers.Builder b =
                    net.minecraft.world.item.component.ItemAttributeModifiers.builder();
            b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, MELEE - 1.0F,
                    AttributeModifier.Operation.ADD_VALUE), hand);
            b.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, MELEE_SPEED - 4.0F,
                    AttributeModifier.Operation.ADD_VALUE), hand);
            java.util.Optional<net.minecraft.core.Holder.Reference<Attribute>> ice =
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(ISS_ICE);
            if (ice.isPresent()) {                                 // Iron's Spells is there: its own attributes, by id
                b.add(ice.get(), new AttributeModifier(ISS_ICE_ID, 0.20D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE), hand);
            }
            java.util.Optional<net.minecraft.core.Holder.Reference<Attribute>> mana =
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(ISS_MANA);
            if (mana.isPresent()) {
                b.add(mana.get(), new AttributeModifier(ISS_MANA_ID, 150.0D,
                        AttributeModifier.Operation.ADD_VALUE), hand);
            }
            mainHand = b.build();
        }
        return mainHand;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String k = "item.frozen_dominion.hollow_kings_staff.";
        Level level = context.level();
        tooltip.add(Component.translatable(k + "litany").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "bell").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "tide").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(k + "requiem", REQUIEM_CASTS).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(k + "mark").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(k + "combo").withStyle(ChatFormatting.DARK_AQUA));
        if (level != null) {
            tooltip.add(status(stack, level.getGameTime()));
        }
        tooltip.add(Component.translatable(k + "lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
