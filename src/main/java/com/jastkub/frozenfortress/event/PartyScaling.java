package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.config.FFConfig;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A BOSS FOR A PARTY. The court's keepers, the Lamplighter, the Vault Warden, the Ice Monstrosity
 * and the king count who came to fight them, and answer it:
 *
 * <ul>
 *   <li>HEALTH: half again for every fighter past the first (healthPerPlayer), counted when it wakes and again whenever
 *   somebody else walks in - only ever up, its wounds kept as a share of the bar. The bar says by how many: "×3".</li>
 *   <li>DAMAGE: a tenth more per fighter past the first (damagePerPlayer), on whatever of its reaches a player.</li>
 *   <li>ITS ATTENTION: every eight seconds, if whoever it is on has barely touched it while somebody else has been
 *   cutting it apart, it turns on them (they already turn on whoever just hit them - this is the other half).</li>
 *   <li>ITS ATTACKS (each boss, at its own call sites - {@link #extra}, {@link #others}): the Monstrosity's avalanche
 *   and the king's judgment swords come for every fighter, the Aurochs runs one charge into the next, the Priestess's
 *   requiem and the Shepherd's herd grow with the party.</li>
 *   <li>THE REWARD: everyone who fought it gets their own trophy - a copy that only they can pick up - and the king
 *   stands a hoard chest for each. Keys stay single: one door needs one key.</li>
 * </ul>
 *
 * <p>Who counts: a player who can be in a fight ({@link VelkharEntity#inTheFight} - alive, not creative, not a
 * spectator) within {@link #REACH} of it while it is awake (after somebody, or hurt). Every one counted is written down
 * as having fought it - the trophy list. Both live in its persistent data, so a fight survives a restart.
 *
 * <p>All the numbers are in the COMMON config (party), and the whole thing turns off there.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PartyScaling {

    /** Who counts the party: the keepers that reset, and the three that do not. */
    public static final Set<String> SCALED;

    static {
        Set<String> s = new HashSet<>(KeeperReset.KEEPERS);
        s.add("frozen_dominion:vault_warden");
        s.add("frozen_dominion:ice_monstrosity");
        s.add("frozen_dominion:velkhar");
        SCALED = Set.copyOf(s);
    }

    /** Their trophies - the parts the legendary arms are made of. Each fighter gets their own. */
    private static final Set<String> TROPHIES = Set.of(
            "frozen_dominion:turnkey_chain", "frozen_dominion:shepherd_bell", "frozen_dominion:priestess_crystal",
            "frozen_dominion:overseer_hammerhead", "frozen_dominion:aurochs_horn", "frozen_dominion:drowned_braid",
            "frozen_dominion:lantern_lens", "frozen_dominion:warden_core", "frozen_dominion:monstrosity_skull",
            "frozen_dominion:bone_lord_femur", "frozen_dominion:lamplighter_candle",
            "frozen_dominion:kingsrime_seal");

    /** How near it a fighter has to be to count, across and up or down (the size of their halls). */
    public static final double REACH = 40.0D, REACH_Y = 24.0D;
    /** How often its attention is weighed, and how much more the other one has to have dealt. */
    private static final int FOCUS_EVERY = 160;
    private static final float FOCUS_RATIO = 3.0F, FOCUS_MIN = 12.0F;

    private static final UUID HEALTH_ID = UUID.fromString("6b0b7c52-1f53-4a8e-9a43-0f1d2c3b7e11");
    private static final String SIZE = "ffParty", WHO = "ffPartyWho";
    /** Marks our "×N" on its bar's name, so it can be found and replaced (and read by the client). */
    public static final String BADGE = "ffParty";

    /** Damage each player has dealt it since its attention was last weighed. Server side, not kept. */
    private static final Map<LivingEntity, Map<UUID, Float>> THREAT = new WeakHashMap<>();
    private static final Map<Class<?>, Field> BAR = new ConcurrentHashMap<>();

    private PartyScaling() {
    }

    // ------------------------------------------------------------------------------------------------ the config
    private static boolean on() {
        return !FFConfig.loaded || FFConfig.COMMON.partyScaling.get();
    }

    private static double healthPer() {
        return FFConfig.loaded ? FFConfig.COMMON.partyHealthPerPlayer.get() : 0.5D;
    }

    private static double damagePer() {
        return FFConfig.loaded ? FFConfig.COMMON.partyDamagePerPlayer.get() : 0.1D;
    }

    private static int cap() {
        return FFConfig.loaded ? FFConfig.COMMON.partyMaxCounted.get() : 8;
    }

    /** Its attacks come for every fighter / its summons grow (partyAttacks). */
    public static boolean extraAttacks() {
        return !FFConfig.loaded || FFConfig.COMMON.partyAttacks.get();
    }

    private static boolean focus() {
        return !FFConfig.loaded || FFConfig.COMMON.partyFocus.get();
    }

    private static boolean trophies() {
        return !FFConfig.loaded || FFConfig.COMMON.partyTrophies.get();
    }

    // ------------------------------------------------------------------------------------------------ queries
    /** Does it count a party? (Not a tamed Monstrosity: that one is somebody's.) */
    public static boolean scaled(LivingEntity e) {
        if (e instanceof HollowGolemEntity g && g.isTamed()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && SCALED.contains(id.toString());
    }

    /** How many it has counted fighting it (1 alone, or when it all is off). */
    public static int size(LivingEntity boss) {
        if (!on()) {
            return 1;
        }
        return Math.max(1, boss.getPersistentData().getInt(SIZE));
    }

    /** How many more of a thing the party earns it: one for each fighter past the first (0 when that is off). */
    public static int extra(LivingEntity boss) {
        return extraAttacks() ? size(boss) - 1 : 0;
    }

    /** Its health as a multiple of what it has alone - its damage ceilings grow with it. */
    public static float healthFactor(LivingEntity boss) {
        return (float) (1.0D + healthPer() * (size(boss) - 1));
    }

    /** The fighters near it other than `besides`, nearest first, at most `max` of them. */
    public static List<Player> others(LivingEntity boss, LivingEntity besides, int max) {
        List<Player> out = new ArrayList<>();
        if (max <= 0) {
            return out;
        }
        for (Player p : fighters(boss)) {
            if (p != besides) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(boss)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Everyone near it who can be in a fight. */
    public static List<Player> fighters(LivingEntity boss) {
        List<Player> out = new ArrayList<>();
        for (Player p : boss.level().players()) {
            double dx = p.getX() - boss.getX(), dz = p.getZ() - boss.getZ();
            if (VelkharEntity.inTheFight(p) && dx * dx + dz * dz <= REACH * REACH
                    && Math.abs(p.getY() - boss.getY()) <= REACH_Y) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everyone it has counted, whether or not they are still here. */
    public static Set<UUID> fought(LivingEntity boss) {
        Set<UUID> out = new HashSet<>();
        ListTag list = boss.getPersistentData().getList(WHO, Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            out.add(NbtUtils.loadUUID(t));
        }
        return out;
    }

    /** How many hoards the king leaves: one, or one for each who fought him (personal rewards on). */
    public static int hoards(LivingEntity king) {
        return on() && trophies() ? Math.max(1, Math.min(cap(), fought(king).size())) : 1;
    }

    // ------------------------------------------------------------------------------------------------ the count
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.tickCount % 20 != 7 || !(e.level() instanceof ServerLevel) || e.isDeadOrDying() || !on() || !scaled(e)) {
            return;
        }
        boolean awake = e.getHealth() < e.getMaxHealth() || e instanceof Mob m && m.getTarget() != null;
        if (!awake) {
            return;
        }
        List<Player> here = fighters(e);
        CompoundTag d = e.getPersistentData();
        // ---- who fought it
        ListTag who = d.getList(WHO, Tag.TAG_INT_ARRAY);
        Set<UUID> known = fought(e);
        for (Player p : here) {
            if (known.add(p.getUUID())) {
                who.add(NbtUtils.createUUID(p.getUUID()));
            }
        }
        d.put(WHO, who);
        // ---- how many: only ever up
        int was = Math.max(1, d.getInt(SIZE));
        int now = Math.max(was, Math.min(cap(), here.size()));
        if (now != was || d.getInt(SIZE) == 0) {
            d.putInt(SIZE, now);
            applyHealth(e, now);
        }
        badge(e, now);
        // ---- its attention
        if (focus() && e instanceof Mob m && e.tickCount % FOCUS_EVERY == 7) {
            weigh(m, here);
        }
    }

    /** Max health for `n` fighters, its wounds kept as a share of the bar. */
    private static void applyHealth(LivingEntity e, int n) {
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) {
            return;
        }
        float share = e.getHealth() / Math.max(1.0F, e.getMaxHealth());
        a.removeModifier(HEALTH_ID);
        if (n > 1) {
            a.addPermanentModifier(new AttributeModifier(HEALTH_ID, "Frozen Dominion party", healthPer() * (n - 1),
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        e.setHealth(Math.max(1.0F, share * e.getMaxHealth()));
    }

    /** "×N" on its bar - after the name, golden, marked so it can be found again. Nothing alone. */
    private static void badge(LivingEntity e, int n) {
        ServerBossEvent bar = bar(e);
        if (bar == null) {
            return;
        }
        Component name = bar.getName();
        if (count(name) == (n > 1 ? n : 0)) {
            return;
        }
        MutableComponent base = strip(name).copy();
        if (n > 1) {
            base.append(Component.literal(" ×" + n).withStyle(s -> s.withColor(ChatFormatting.GOLD).withInsertion(BADGE)));
        }
        bar.setName(base);
    }

    /** The party size written on a bar's name (0: none). Client and server alike. */
    public static int count(Component name) {
        for (Component s : name.getSiblings()) {
            if (BADGE.equals(s.getStyle().getInsertion())) {
                String digits = s.getString().replaceAll("[^0-9]", "");
                try {
                    return digits.isEmpty() ? 0 : Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** The name without our "×N". */
    public static Component strip(Component name) {
        boolean has = false;
        for (Component s : name.getSiblings()) {
            has |= BADGE.equals(s.getStyle().getInsertion());
        }
        if (!has) {
            return name;
        }
        MutableComponent out = MutableComponent.create(name.getContents()).setStyle(name.getStyle());
        for (Component s : name.getSiblings()) {
            if (!BADGE.equals(s.getStyle().getInsertion())) {
                out.append(s);
            }
        }
        return out;
    }

    /** Its boss bar, whatever its class calls the field. */
    private static ServerBossEvent bar(LivingEntity e) {
        Field f = BAR.computeIfAbsent(e.getClass(), PartyScaling::findBar);
        if (f == NONE) {
            return null;
        }
        try {
            return (ServerBossEvent) f.get(e);
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private static final Field NONE;

    static {
        try {
            NONE = PartyScaling.class.getDeclaredField("BADGE");
        } catch (NoSuchFieldException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Field findBar(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (ServerBossEvent.class.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return NONE;
    }

    // ------------------------------------------------------------------------------------------------ attention
    /** Whoever has been cutting it apart, if whoever it is on has barely touched it. */
    private static void weigh(Mob m, List<Player> here) {
        Map<UUID, Float> threat = THREAT.computeIfAbsent(m, k -> new HashMap<>());
        LivingEntity cur = m.getTarget();
        Player top = null;
        float best = 0.0F;
        for (Player p : here) {
            float t = threat.getOrDefault(p.getUUID(), 0.0F);
            if (t > best) {
                best = t;
                top = p;
            }
        }
        if (top != null && cur != top && best >= FOCUS_MIN) {
            float mine = cur != null ? threat.getOrDefault(cur.getUUID(), 0.0F) : 0.0F;
            if (cur == null || !VelkharEntity.inTheFight(cur) || best >= mine * FOCUS_RATIO) {
                m.setTarget(top);
            }
        }
        threat.replaceAll((k, v) -> v * 0.25F);       // what was dealt long ago weighs less
    }

    // ------------------------------------------------------------------------------------------------ damage
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !on()) {
            return;
        }
        // ITS BLOWS: a tenth more a fighter, on players
        if (victim instanceof Player && event.getSource().getEntity() instanceof LivingEntity by && scaled(by)) {
            int n = size(by);
            if (n > 1) {
                event.setAmount(event.getAmount() * (float) (1.0D + damagePer() * (n - 1)));
            }
            return;
        }
        // ITS WOUNDS: who is dealing them
        if (event.getSource().getEntity() instanceof Player p && scaled(victim)) {
            THREAT.computeIfAbsent(victim, k -> new HashMap<>()).merge(p.getUUID(), event.getAmount(), Float::sum);
        }
    }

    // ------------------------------------------------------------------------------------------------ the reward
    /** A trophy for each who fought it, theirs to pick up; one key for all. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel sl) || !on() || !trophies() || !scaled(e)) {
            return;
        }
        Set<UUID> who = fought(e);
        if (who.size() < 2) {
            return;
        }
        List<ItemEntity> add = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack st = drop.getItem();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
            if (id == null || !TROPHIES.contains(id.toString())) {
                continue;
            }
            boolean first = true;
            for (UUID u : who) {
                ItemEntity mine = first ? drop : new ItemEntity(sl, drop.getX(), drop.getY(), drop.getZ(), st.copy());
                mine.setTarget(u);                          // only they can pick it up
                mine.setExtendedLifetime();
                mine.setDefaultPickUpDelay();
                if (!first) {
                    mine.setDeltaMovement(sl.random.nextGaussian() * 0.08D, 0.25D, sl.random.nextGaussian() * 0.08D);
                    add.add(mine);
                }
                first = false;
            }
        }
        event.getDrops().addAll(add);
        for (UUID u : who) {
            ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(u);
            if (sp != null && !add.isEmpty()) {
                sp.displayClientMessage(Component.translatable("message.frozen_dominion.party_trophy")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
