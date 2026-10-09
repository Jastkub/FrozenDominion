package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFEffects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

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
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FFTemper {

    public static final String TAG = "frozen_dominion:temper";
    private static final ResourceLocation ISS_ICE = new ResourceLocation("irons_spellbooks", "ice_spell_power");

    private FFTemper() {
    }

    public static String temper(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(TAG) : "";
    }

    /** One modifier id per kind, attribute and slot - stable, so a piece's bonus never stacks with itself. */
    private static UUID id(String kind, String attr, EquipmentSlot slot) {
        return UUID.nameUUIDFromBytes(("frozen_dominion:temper/" + kind + "/" + attr + "/" + slot.getName()).getBytes());
    }

    private static void add(ItemAttributeModifierEvent e, String kind, Attribute a, String name, double v,
                            AttributeModifier.Operation op) {
        e.addModifier(a, new AttributeModifier(id(kind, name, e.getSlotType()), "Temper: " + kind, v, op));
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent e) {
        ItemStack stack = e.getItemStack();
        String kind = temper(stack);
        if (kind.isEmpty()) {
            return;
        }
        EquipmentSlot slot = e.getSlotType();
        boolean armourSlot = stack.getItem() instanceof ArmorItem armour && armour.getEquipmentSlot() == slot;
        switch (kind) {
            case "fang" -> {
                if (slot == EquipmentSlot.MAINHAND) {
                    add(e, kind, Attributes.ATTACK_DAMAGE, "damage", 1.5D, AttributeModifier.Operation.ADDITION);
                }
            }
            case "rivet" -> {
                if (stack.getItem() instanceof ShieldItem && (slot == EquipmentSlot.OFFHAND || slot == EquipmentSlot.MAINHAND)) {
                    add(e, kind, Attributes.ARMOR, "armor", 2.0D, AttributeModifier.Operation.ADDITION);
                    add(e, kind, Attributes.KNOCKBACK_RESISTANCE, "kb", 0.1D, AttributeModifier.Operation.ADDITION);
                }
            }
            case "prism" -> {
                if (armourSlot) {
                    add(e, kind, Attributes.ARMOR_TOUGHNESS, "tough", 1.0D, AttributeModifier.Operation.ADDITION);
                    Attribute ice = ForgeRegistries.ATTRIBUTES.getValue(ISS_ICE);
                    if (ice != null) {
                        add(e, kind, ice, "ice", 0.04D, AttributeModifier.Operation.MULTIPLY_BASE);
                    }
                }
            }
            case "attuned" -> {
                if (armourSlot) {
                    add(e, kind, Attributes.MAX_HEALTH, "health", 2.0D, AttributeModifier.Operation.ADDITION);
                }
            }
            case "core" -> {
                if (armourSlot) {
                    add(e, kind, Attributes.ARMOR_TOUGHNESS, "tough", 1.5D, AttributeModifier.Operation.ADDITION);
                    add(e, kind, Attributes.KNOCKBACK_RESISTANCE, "kb", 0.1D, AttributeModifier.Operation.ADDITION);
                }
            }
            default -> {
            }
        }
    }

    /** A fang-tempered blade: a quarter of its blows lay Frostbite for three seconds. */
    @SubscribeEvent
    public static void onBlow(LivingHurtEvent e) {
        if (!(e.getSource().getDirectEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) {
            return;
        }
        if ("fang".equals(temper(attacker.getMainHandItem())) && attacker.getRandom().nextFloat() < 0.25F) {
            e.getEntity().addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), attacker);
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
