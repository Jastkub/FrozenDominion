package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.projectile.IceArrowEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * KRÓLEWSKI SZRON - KINGSRIME. The second step of the Everfrost gear: every
 * piece of it is an Everfrost piece taken to the smithing table with the
 * Kingsrime template and a Kingsrime ingot. Also the two Everfrost pieces the
 * set was missing - a sword and a shield - so the ladder is the same for
 * every piece.
 */
public final class KingsrimeItems {

    private KingsrimeItems() {
    }

    // ================================================================
    // the materials
    // ================================================================
    // (the armour's material: FFArmorMaterials.KINGSRIME - 1.21.1 keeps materials in a registry)

    public static final Tier KINGSRIME_TIER = new Tier() {
        @Override
        public int getUses() {
            return 3300;
        }

        @Override
        public float getSpeed() {
            return 10.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 6.0F;
        }

        @Override
        public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL;   // (it was level 5: above netherite)
        }

        @Override
        public int getEnchantmentValue() {
            return 20;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(FFItems.KINGSRIME_INGOT.get());
        }
    };

    // ================================================================
    // THE ARMOUR: Everfrost's own (its Rimeguard charge comes with it), in
    // three dimensions, and - where Iron's Spells is installed - fifteen per
    // cent of ice spell power from EVERY piece, sixty with the set
    // ================================================================
    public static class KingsrimeArmor extends EverfrostArmorItem implements GeoItem {
        private static final float[] ICE_POWER = {0.15F, 0.15F, 0.15F, 0.15F};
        private static final ResourceLocation[] ICE_IDS = {
                FrozenFortress.id("kingsrime_ice_power_helmet"),
                FrozenFortress.id("kingsrime_ice_power_chestplate"),
                FrozenFortress.id("kingsrime_ice_power_leggings"),
                FrozenFortress.id("kingsrime_ice_power_boots")};
        private static final ResourceLocation ISS_ICE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_spell_power");

        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public KingsrimeArmor(ArmorItem.Type type) {
            super(com.jastkub.frozenfortress.registry.FFArmorMaterials.KINGSRIME, type, new Item.Properties()
                    .rarity(Rarity.EPIC).fireResistant()
                    .durability(com.jastkub.frozenfortress.registry.FFArmorMaterials.KINGSRIME_DURABILITY[type.ordinal()]));
        }

        /** True when all four pieces are Kingsrime. */
        public static boolean hasFullKingsrime(Player player) {
            for (ItemStack stack : player.getInventory().armor) {
                if (!(stack.getItem() instanceof KingsrimeArmor)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            net.minecraft.world.item.component.ItemAttributeModifiers base = super.getDefaultAttributeModifiers(stack);
            java.util.Optional<net.minecraft.core.Holder.Reference<Attribute>> ice =
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(ISS_ICE);
            if (ice.isEmpty()) {
                return base;                                    // no Iron's Spells: no bonus, nothing breaks
            }
            int i = getType().ordinal();
            return base.withModifierAdded(ice.get(), new AttributeModifier(ICE_IDS[i], ICE_POWER[i],
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    net.minecraft.world.entity.EquipmentSlotGroup.bySlot(getType().getSlot()));
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            for (int k = 0; k < 3; k++) {
                tooltip.add(Component.translatable("item.frozen_dominion.kingsrime.set" + k)
                        .withStyle(k == 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            }
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(new IClientItemExtensions() {
                private software.bernie.geckolib.renderer.GeoArmorRenderer<?> renderer;

                @Override
                public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                               EquipmentSlot slot, HumanoidModel<?> original) {
                    if (renderer == null) {
                        renderer = new com.jastkub.frozenfortress.client.render.KingsrimeArmorRenderer();
                    }
                    renderer.prepForRender(entity, stack, slot, original);
                    return renderer;
                }
            });
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    // ================================================================
    // THE SWORDS
    // ================================================================
    public static class EverfrostSword extends SwordItem {
        public EverfrostSword() {
            super(FFTiers.EVERFROST, new Item.Properties().rarity(Rarity.RARE).fireResistant()
                    .attributes(SwordItem.createAttributes(FFTiers.EVERFROST, 3, -2.4F)));
        }

        @Override
        public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
            FFSwing.swing(entity, FFSwing.blade(), true);
            return super.onEntitySwing(stack, entity);
        }
    }

    /** Frostbite on every hit; every eighth drives a line of ice spikes ahead (it was every third). */
    public static class KingsrimeSword extends SwordItem {
        private static final String HITS = "frozen_dominion:hits";
        private static final int SPIKES_EVERY = 8;

        public KingsrimeSword() {
            // 1 + the tier's 6 + 11 = 18
            super(KINGSRIME_TIER, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordItem.createAttributes(KINGSRIME_TIER, 11, -2.4F)));
        }

        @Override
        public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
            FFSwing.swing(entity, FFSwing.blade(), true);
            return super.onEntitySwing(stack, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), attacker);
            int hits = com.jastkub.frozenfortress.util.FFItemData.read(stack).getInt(HITS) + 1;
            if (hits >= SPIKES_EVERY) {
                hits = 0;
                spikes(attacker);
            }
            final int h = hits;
            com.jastkub.frozenfortress.util.FFItemData.update(stack, t -> t.putInt(HITS, h));
            return super.hurtEnemy(stack, target, attacker);
        }

        private static void spikes(LivingEntity attacker) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 dir = Vec3.directionFromRotation(0.0F, attacker.getYRot());
            java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
            for (int i = 1; i <= 5; i++) {
                Vec3 p = attacker.position().add(dir.scale(i * 1.0D));
                level.sendParticles(FFParticles.ICE_SHARD.get(), p.x, p.y + 0.3D, p.z, 8, 0.25D, 0.5D, 0.25D, 0.1D);
                for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(p.x - 1.0D, p.y - 0.5D, p.z - 1.0D, p.x + 1.0D, p.y + 2.0D, p.z + 1.0D),
                        e -> e != attacker && e.isAlive() && !(e instanceof Player && attacker instanceof Player))) {
                    if (struck.add(v)) {
                        v.hurt(attacker.damageSources().mobAttack(attacker), 6.0F);
                        v.setDeltaMovement(v.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
                        v.hurtMarked = true;
                    }
                }
            }
            level.playSound(null, attacker.blockPosition(), FFSounds.FROST_RELEASE.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.kingsrime_sword.desc1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("item.frozen_dominion.kingsrime_sword.desc2").withStyle(ChatFormatting.BLUE));
        }
    }

    // ================================================================
    // THE SHIELDS: a real shield (it blocks as one), drawn in 3D
    // ================================================================
    public static class FrostShield extends ShieldItem implements GeoItem {
        private final String model;
        private final boolean kingsrime;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public FrostShield(String model, boolean kingsrime) {
            super(new Item.Properties().durability(kingsrime ? 1680 : 1120)
                    .rarity(kingsrime ? Rarity.EPIC : Rarity.RARE).fireResistant());
            this.model = model;
            this.kingsrime = kingsrime;
        }

        public boolean isKingsrime() {
            return kingsrime;
        }

        @Override
        public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
            return repair.is(kingsrime ? FFItems.KINGSRIME_INGOT.get() : FFItems.EVERFROST_INGOT.get());
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            String key = "item.frozen_dominion." + model;
            tooltip.add(Component.translatable(key + ".desc1").withStyle(ChatFormatting.BLUE));
            if (kingsrime) {
                tooltip.add(Component.translatable(key + ".desc2").withStyle(ChatFormatting.BLUE));
            }
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(new IClientItemExtensions() {
                private BlockEntityWithoutLevelRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new com.jastkub.frozenfortress.client.render.FrostShieldRenderer(model, kingsrime);
                    }
                    return renderer;
                }
            });
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    // ================================================================
    // THE BOW: the Everfrost bow's ice arrows, harder, and at full draw they
    // pierce and break in a burst of ice where they strike
    // ================================================================
    public static final String KINGSRIME_ARROW = "frozen_dominion:kingsrime";

    public static class KingsrimeBow extends FFTools.EverfrostBow {
        public KingsrimeBow() {
            super(new Item.Properties().stacksTo(1).durability(1100).rarity(Rarity.EPIC).fireResistant());
        }

        @Override
        protected void prepareArrow(IceArrowEntity arrow, float power) {
            arrow.setBaseDamage(arrow.getBaseDamage() + 2.0D);
            if (power >= 1.0F) {
                FFArrows.setPierceLevel(arrow, (byte) 2);
                arrow.getPersistentData().putBoolean(KINGSRIME_ARROW, true);
            }
        }

        @Override
        protected float velocity() {
            return 3.6F;
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.frozen_dominion.kingsrime_bow.desc1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("item.frozen_dominion.kingsrime_bow.desc2").withStyle(ChatFormatting.BLUE));
        }
    }

    /** A burst of ice where a full-drawn Kingsrime arrow strikes. */
    public static void arrowBurst(Level level, Vec3 at, @Nullable LivingEntity owner) {
        if (!(level instanceof ServerLevel s)) {
            return;
        }
        s.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y, at.z, 30, 0.6D, 0.6D, 0.6D, 0.12D);
        s.playSound(null, at.x, at.y, at.z, FFSounds.ICE_SHATTER.get(), SoundSource.PLAYERS, 1.2F, 1.1F);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.2D),
                e -> e != owner && e.isAlive() && !(e instanceof Player && owner instanceof Player))) {
            v.hurt(owner != null ? s.damageSources().mobAttack(owner) : s.damageSources().magic(), 4.0F);
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), owner);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
        }
    }

    // ================================================================
    // THE TEMPLATE
    // ================================================================
    public static SmithingTemplateItem template() {
        String k = "item." + FrozenFortress.MODID + ".smithing_template.kingsrime_upgrade.";
        return new SmithingTemplateItem(
                Component.translatable(k + "applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable(k + "ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable(k + "upgrade").withStyle(ChatFormatting.GRAY),
                Component.translatable(k + "base_slot_description"),
                Component.translatable(k + "additions_slot_description"),
                List.of(ResourceLocation.parse("item/empty_armor_slot_helmet"),
                        ResourceLocation.parse("item/empty_slot_sword"),
                        ResourceLocation.parse("item/empty_armor_slot_chestplate"),
                        ResourceLocation.parse("item/empty_armor_slot_shield"),
                        ResourceLocation.parse("item/empty_armor_slot_leggings"),
                        ResourceLocation.parse("item/empty_armor_slot_boots")),
                List.of(ResourceLocation.parse("item/empty_slot_ingot")));
    }

    /** Frostbite and a hard chill on whoever strikes a raised Frost shield. */
    public static void onShieldBlock(Player player, FrostShield shield, LivingEntity attacker) {
        int held = player.getTicksUsingItem();
        boolean perfect = shield.isKingsrime() && held <= 10;
        attacker.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, shield.isKingsrime() ? 80 : 50, 0), player);
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, perfect ? 50 : 30, perfect ? 6 : 1));
        if (perfect) {
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1));
            player.level().playSound(null, player.blockPosition(), FFSounds.CRYSTAL_CHIME.get(), SoundSource.PLAYERS,
                    1.4F, 0.8F);
            if (player.level() instanceof ServerLevel s) {
                s.sendParticles(FFParticles.ICE_SHARD.get(), attacker.getX(), attacker.getY(0.5D), attacker.getZ(),
                        24, 0.4D, 0.6D, 0.4D, 0.1D);
            }
        }
    }

    /** True for the court's own: the set bonuses and the shields' frost spare them nothing. */
    public static boolean isWinterServant(LivingEntity e) {
        return e instanceof FrostServantEntity;
    }
}
