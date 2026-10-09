package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * TEMPERING (the crafting plan's fourth phase): a part of one of the citadel's
 * creatures, set in the Frost Anvil's core with a piece of Everfrost or Kingsrime gear, tempers it - the recipes
 * (recipes/temper_*.json, tools/gen_temper_recipes.py) leave the kind in the item's tag, and this reads it:
 *
 * <ul>
 *   <li><b>fang</b> (Frostmaw's Fang) - a blade: +1.5 damage, and a quarter of its blows lay Frostbite;</li>
 *   <li><b>rivet</b> (Sentinel's Rivet) - a shield: +2 armour and a tenth less knockback while it is held;</li>
 *   <li><b>fletching</b> (Stillbow's Fletching) - a bow: its arrows strike a fifth harder;</li>
 *   <li><b>prism</b> (Rimeweaver's Prism) - armour: +1 toughness, and +4% ice spell power where Iron's Spells is;</li>
 *   <li><b>attuned</b> (Attuned Prism) - armour: +1 heart;</li>
 *   <li><b>core</b> (Warden's Core) - a chestplate or leggings: +1.5 toughness and a tenth less knockback.</li>
 * </ul>
 * One temper to a piece (tempering again replaces it); upgrading Everfrost to Kingsrime keeps it (the anvil carries the
 * piece's tag over).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FFTemper {

    public static final String TAG = "frozen_dominion:temper";
    private static final ResourceLocation ISS_ICE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_spell_power");

    private FFTemper() {
    }

    public static String temper(ItemStack stack) {
        return com.jastkub.frozenfortress.util.FFItemData.read(stack).getString(TAG);
    }

    /** One modifier id per kind, attribute and slot - stable, so a piece's bonus never stacks with itself. */
    private static ResourceLocation id(String kind, String attr, EquipmentSlot slot) {
        return FrozenFortress.id("temper/" + kind + "/" + attr + "/" + slot.getName());
    }

    private static void add(ItemAttributeModifierEvent e, String kind, Holder<Attribute> a, String name, double v,
                            AttributeModifier.Operation op, EquipmentSlot slot) {
        e.addModifier(a, new AttributeModifier(id(kind, name, slot), v, op), EquipmentSlotGroup.bySlot(slot));
    }

    /**
     * (1.21.1: the event comes once per stack, not once per slot asked about, and each modifier names the slot it
     * holds in - so each bonus is added for the very slots it held in before, with the same per-slot ids.)
     */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent e) {
        ItemStack stack = e.getItemStack();
        String kind = temper(stack);
        if (kind.isEmpty()) {
            return;
        }
        EquipmentSlot armourSlot = stack.getItem() instanceof ArmorItem armour ? armour.getEquipmentSlot() : null;
        switch (kind) {
            case "fang" -> add(e, kind, Attributes.ATTACK_DAMAGE, "damage", 1.5D, AttributeModifier.Operation.ADD_VALUE,
                    EquipmentSlot.MAINHAND);
            case "rivet" -> {
                if (stack.getItem() instanceof ShieldItem) {
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND}) {
                        add(e, kind, Attributes.ARMOR, "armor", 2.0D, AttributeModifier.Operation.ADD_VALUE, slot);
                        add(e, kind, Attributes.KNOCKBACK_RESISTANCE, "kb", 0.1D, AttributeModifier.Operation.ADD_VALUE, slot);
                    }
                }
            }
            case "prism" -> {
                if (armourSlot != null) {
                    add(e, kind, Attributes.ARMOR_TOUGHNESS, "tough", 1.0D, AttributeModifier.Operation.ADD_VALUE, armourSlot);
                    java.util.Optional<Holder.Reference<Attribute>> ice =
                            net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(ISS_ICE);
                    if (ice.isPresent()) {
                        add(e, kind, ice.get(), "ice", 0.04D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, armourSlot);
                    }
                }
            }
            case "attuned" -> {
                if (armourSlot != null) {
                    add(e, kind, Attributes.MAX_HEALTH, "health", 2.0D, AttributeModifier.Operation.ADD_VALUE, armourSlot);
                }
            }
            case "core" -> {
                if (armourSlot != null) {
                    add(e, kind, Attributes.ARMOR_TOUGHNESS, "tough", 1.5D, AttributeModifier.Operation.ADD_VALUE, armourSlot);
                    add(e, kind, Attributes.KNOCKBACK_RESISTANCE, "kb", 0.1D, AttributeModifier.Operation.ADD_VALUE, armourSlot);
                }
            }
            default -> {
            }
        }
    }

    /** A fang-tempered blade: a quarter of its blows lay Frostbite for three seconds. (LivingDamageEvent.Pre: where
     *  1.20.1's LivingIncomingDamageEvent was - in actuallyHurt, past the invulnerability frames.) */
    @SubscribeEvent
    public static void onBlow(LivingDamageEvent.Pre e) {
        if (!(e.getSource().getDirectEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) {
            return;
        }
        if ("fang".equals(temper(attacker.getMainHandItem())) && attacker.getRandom().nextFloat() < 0.25F) {
            e.getEntity().addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), attacker);
        }
    }

    /** A fletching-tempered bow: the arrow it looses strikes a fifth harder. */
    @SubscribeEvent
    public static void onArrow(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || e.loadedFromDisk() || !(e.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof Player p)) {
            return;
        }
        for (ItemStack held : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (held.getItem() instanceof BowItem && "fletching".equals(temper(held))) {
                arrow.setBaseDamage(arrow.getBaseDamage() * 1.2D);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        String kind = temper(e.getItemStack());
        if (!kind.isEmpty()) {
            e.getToolTip().add(1, Component.translatable("tooltip.frozen_dominion.temper." + kind)
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
