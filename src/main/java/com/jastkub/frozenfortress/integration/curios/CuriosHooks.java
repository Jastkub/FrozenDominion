package com.jastkub.frozenfortress.integration.curios;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * What the four artifacts do while worn.
 *
 * <p>Everything here is a <em>passive</em> that runs off {@code curioTick} or
 * an attribute. The reactive halves - what the Crown does on a kill, what the
 * Lodestone does on a hit - live in
 * {@link com.jastkub.frozenfortress.event.ArtifactEffects}, because those need
 * Forge's damage events rather than a tick.
 *
 * <p>Items are registered through {@code registerCurio} rather than made to
 * implement {@code ICurioItem} themselves, which keeps every item class free
 * of Curios types and all of this in one readable place.
 */
public final class CuriosHooks {

    public static void register() {
        CuriosApi.registerCurio(FFItems.CROWN_OF_THE_HOLLOW_KING.get(), new Crown());
        CuriosApi.registerCurio(FFItems.FROSTHEART_TOTEM.get(), new Totem());
        CuriosApi.registerCurio(FFItems.SOVEREIGNS_SIGNET.get(), new Signet());
        CuriosApi.registerCurio(FFItems.WARDENS_LODESTONE.get(), new Lodestone());
        CuriosApi.registerCurio(FFItems.HEARTH_AMULET.get(), new Amulet());
        CuriosApi.registerCurio(FFItems.FROSTWALKER_BAND.get(), new Band());
        CuriosApi.registerCurio(FFItems.GLACIER_RING.get(), new Amulet());  // (its work is FFEnchantments.steady)
        CuriosApi.registerCurio(FFItems.LAMPLIGHTER_CANDLE.get(), new Amulet()); // (its work is the client's: FrostVeil)
    }

    /**
     * Everything worn in its Curios slots (and their cosmetic ones), taken off it into `out`, each with its slot (Id,
     * Cosmetic, Index, Item) - for a Frost Shrine, before Curios (or a gravestone mod) can drop them; put back by
     * {@link #putBack}. What carries the curse of vanishing is left where it is, for the game to destroy.
     */
    public static void takeAll(LivingEntity entity, net.minecraft.nbt.ListTag out) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().forEach((id, handler) -> {
            for (int c = 0; c < 2; c++) {
                var h = c == 0 ? handler.getStacks() : handler.getCosmeticStacks();
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack st = h.getStackInSlot(i);
                    if (!st.isEmpty() && !net.minecraft.world.item.enchantment.EnchantmentHelper.hasVanishingCurse(st)) {
                        net.minecraft.nbt.CompoundTag one = new net.minecraft.nbt.CompoundTag();
                        one.putString("Id", id);
                        one.putBoolean("Cosmetic", c == 1);
                        one.putInt("Index", i);
                        one.put("Item", st.save(new net.minecraft.nbt.CompoundTag()));
                        out.add(one);
                        h.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        }));
    }

    /** One taken by {@link #takeAll} back in its own slot; false if that slot is gone or taken (put it elsewhere). */
    public static boolean putBack(LivingEntity entity, net.minecraft.nbt.CompoundTag one, ItemStack stack) {
        return CuriosApi.getCuriosInventory(entity).resolve().map(inv -> {
            var handler = inv.getCurios().get(one.getString("Id"));
            if (handler == null) {
                return false;
            }
            var h = one.getBoolean("Cosmetic") ? handler.getCosmeticStacks() : handler.getStacks();
            int i = one.getInt("Index");
            if (i >= h.getSlots() || !h.getStackInSlot(i).isEmpty()) {
                return false;
            }
            h.setStackInSlot(i, stack);
            return true;
        }).orElse(false);
    }

    /** The live stack of {@code item} in a Curios slot, or an empty stack. */
    public static ItemStack findEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .flatMap(inventory -> inventory.findFirstCurio(item))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return !findEquipped(entity, item).isEmpty();
    }

    /**
     * Two-line slot blurb. Both keys always exist, which keeps this off
     * I18n.exists() - that class is client-only and this one is loaded on
     * the server too.
     */
    public static void appendSlotTooltip(List<Component> tooltip, String slot) {
        tooltip.add(Component.translatable("item.frozen_dominion.curios." + slot));
        tooltip.add(Component.translatable("item.frozen_dominion.curios." + slot + "2"));
    }

    // ================================================================
    // Crown of the Hollow King - head
    // ================================================================

    /**
     * The cold stops being something you survive and starts being something
     * you hand out: nothing can freeze the wearer, and everything the wearer
     * hits freezes instead. The kill reaction that pairs with this is in
     * ArtifactEffects.
     */
    private static final class Crown implements ICurioItem {

        @Override
        public void curioTick(SlotContext context, ItemStack stack) {
            LivingEntity wearer = context.entity();
            if (wearer == null || wearer.level().isClientSide) {
                return;
            }
            wearer.setTicksFrozen(0);
            if (wearer.hasEffect(FFEffects.FROSTBITE.get())) {
                wearer.removeEffect(FFEffects.FROSTBITE.get());
            }
            // A crown should be visible from across the room.
            if (wearer.tickCount % 6 == 0 && wearer.level() instanceof ServerLevel level) {
                level.sendParticles(FFParticles.FROST_SWIRL.get(),
                        wearer.getX(), wearer.getEyeY() + 0.45D, wearer.getZ(),
                        1, 0.22D, 0.06D, 0.22D, 0.0D);
            }
        }
    }

    // ================================================================
    // Frostheart Totem - charm
    // ================================================================

    /** Purely a carrier; the save itself is in FrostheartTotemHandler. */
    private static final class Totem implements ICurioItem {

        @Override
        public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
            // Safe for this one only: it has no use() of its own and no
            // armour slot to compete with, so nothing else wants the click.
            return true;
        }
    }

    // ================================================================
    // Hearth Amulet - necklace
    // ================================================================

    /** Its work is done by the Stormcrown, which asks for it before it lays its Chill. */
    /** The Frostwalker's Band (bracelet): what slowness is on its wearer goes, and no more takes (CommonEvents). */
    private static final class Band implements ICurioItem {

        @Override
        public void curioTick(SlotContext context, ItemStack stack) {
            LivingEntity wearer = context.entity();
            if (wearer != null && !wearer.level().isClientSide
                    && wearer.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)) {
                wearer.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            }
        }

        @Override
        public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
            return true;
        }
    }

    private static final class Amulet implements ICurioItem {

        @Override
        public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
            return true;
        }
    }

    // ================================================================
    // Sovereign's Signet - ring
    // ================================================================

    /**
     * Rimewalk. Water goes solid under the wearer, and sprinting lays down a
     * wake of cold that anything chasing them has to run through. A flat
     * movement bonus was the boring version of "this ring is about moving".
     */
    private static final class Signet implements ICurioItem {

        private static final int FROST_WALKER_RADIUS = 3;
        private static final double WAKE_RADIUS = 3.2D;

        @Override
        public void curioTick(SlotContext context, ItemStack stack) {
            LivingEntity wearer = context.entity();
            if (wearer == null || wearer.level().isClientSide) {
                return;
            }

            if (wearer.onGround()) {
                FrostWalkerEnchantment.onEntityMoved(wearer, wearer.level(),
                        wearer.blockPosition(), FROST_WALKER_RADIUS);
            }

            if (!wearer.isSprinting() || !(wearer.level() instanceof ServerLevel level)) {
                return;
            }

            level.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                    wearer.getX(), wearer.getY() + 0.1D, wearer.getZ(),
                    3, 0.3D, 0.05D, 0.3D, 0.02D);

            // The wake only bites every half second, so a sprint past a mob
            // is a glancing chill rather than a damage aura.
            if (wearer.tickCount % 10 != 0) {
                return;
            }
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                    wearer.getBoundingBox().inflate(WAKE_RADIUS),
                    other -> other != wearer && other.isAlive()
                            && !other.isAlliedTo(wearer) && !(other instanceof Player))) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), wearer);
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 80));
            }
        }
    }

    // ================================================================
    // Warden's Lodestone - belt
    // ================================================================

    /**
     * What held the vault's guardians in place, worn. It anchors the bearer
     * against knockback and drags loose things to them - the passive shape
     * of the same pull its right-click uses on the living.
     */
    private static final class Lodestone implements ICurioItem {

        private static final double ANCHOR = 0.4D;
        private static final double MAGNET_RADIUS = 7.5D;
        private static final double MAGNET_PULL = 0.32D;

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
                SlotContext context, UUID uuid, ItemStack stack) {
            Multimap<Attribute, AttributeModifier> modifiers = LinkedHashMultimap.create();
            modifiers.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(uuid,
                    "frozen_dominion:wardens_lodestone", ANCHOR,
                    AttributeModifier.Operation.ADDITION));
            return modifiers;
        }

        @Override
        public void curioTick(SlotContext context, ItemStack stack) {
            if (!(context.entity() instanceof Player player) || player.level().isClientSide
                    || player.tickCount % 4 != 0) {
                return;
            }
            Vec3 centre = player.position().add(0.0D, 0.4D, 0.0D);

            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(MAGNET_RADIUS),
                    // Respect the pickup delay, or this yanks back everything
                    // the player just dropped on purpose.
                    e -> e.isAlive() && !e.hasPickUpDelay())) {
                drag(item, centre);
            }
            for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class,
                    player.getBoundingBox().inflate(MAGNET_RADIUS), Entity::isAlive)) {
                drag(orb, centre);
            }
        }

        private static void drag(Entity entity, Vec3 towards) {
            Vec3 delta = towards.subtract(entity.position());
            if (delta.lengthSqr() < 1.0D) {
                return;
            }
            entity.setDeltaMovement(entity.getDeltaMovement()
                    .add(delta.normalize().scale(MAGNET_PULL)));
            entity.hasImpulse = true;
        }
    }

    private CuriosHooks() {
    }
}
