package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.HeartOfWinterItem;
import com.jastkub.frozenfortress.registry.FFEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class CommonEvents {

    /** The advancements held back for a boss's scene (FFAdvancements), handed out once it is over. */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                com.jastkub.frozenfortress.FFAdvancements.tick(server);
            }
        }
    }

    /** NOTHING IS BUILT IN THE MONSTROSITY'S ARENA: no pillar of dirt to fight from. */
    // ------------------------------------------------------------------------------------------------ enchantments
    /** ROZLUPANIE: two and a half a level more on the king's own (FFEnchantments). */
    @SubscribeEvent
    public static void onShatteringHit(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || !com.jastkub.frozenfortress.entity.FFAllies.ofTheKing(event.getEntity())) {
            return;
        }
        int lvl = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                com.jastkub.frozenfortress.registry.FFEnchantments.SHATTERING.get(), attacker.getMainHandItem());
        if (lvl > 0) {
            event.setAmount(event.getAmount() + 2.5F * lvl);
        }
    }

    /** A tamed Monstrosity strikes whatever its rider strikes from its back. */
    @SubscribeEvent
    public static void onRiderStrikes(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide
                && event.getSource().getEntity() instanceof Player rider
                && rider.getVehicle() instanceof com.jastkub.frozenfortress.entity.HollowGolemEntity mount
                && mount.isTamed()) {
            mount.riderStruck(event.getEntity());
        }
    }

    /** PEWNY KROK: every knockback on the wearer 20% a level shorter. */
    @SubscribeEvent
    public static void onSureFooting(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        double k = com.jastkub.frozenfortress.registry.FFEnchantments.steady(event.getEntity());
        if (k < 1.0D) {
            event.setStrength((float) (event.getStrength() * k));
        }
    }

    /** ECHO KROLA: an arrow loosed from such a bow carries the echo... */
    @SubscribeEvent
    public static void onEchoLoosed(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()
                || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)
                || !(arrow.getOwner() instanceof net.minecraft.world.entity.LivingEntity shooter)) {
            return;
        }
        int lvl = Math.max(
                net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        com.jastkub.frozenfortress.registry.FFEnchantments.KINGS_ECHO.get(), shooter.getMainHandItem()),
                net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        com.jastkub.frozenfortress.registry.FFEnchantments.KINGS_ECHO.get(), shooter.getOffhandItem()));
        if (lvl > 0) {
            arrow.getPersistentData().putInt("FrozenDominionEcho", lvl);
        }
    }

    /** ...and where it strikes, a spike of ice comes up out of the floor a second later (IceSpikeEntity, its owner's). */
    @SubscribeEvent
    public static void onEchoStrikes(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)
                || arrow.level().isClientSide) {
            return;
        }
        int lvl = arrow.getPersistentData().getInt("FrozenDominionEcho");
        if (lvl <= 0) {
            return;
        }
        arrow.getPersistentData().remove("FrozenDominionEcho");          // once an arrow
        net.minecraft.world.phys.Vec3 at = event.getRayTraceResult().getLocation();
        net.minecraft.world.level.Level level = arrow.level();
        net.minecraft.core.BlockPos.MutableBlockPos p = net.minecraft.core.BlockPos.containing(at).mutable();
        double floor = at.y;
        for (int dy = 0; dy < 8; dy++, p.move(0, -1, 0)) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                floor = p.getY() + 1.0D;
                break;
            }
        }
        net.minecraft.world.entity.LivingEntity owner = arrow.getOwner() instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
        level.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(level, owner,
                at.x, floor, at.z, 4.0F + 2.0F * lvl, 20));
    }

    /** A fire the Shade Shepherd put out smoulders a while and will not take a spark (ShadeLight.smouldering). */
    @SubscribeEvent
    public static void onRelight(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        net.minecraft.world.level.Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(event.getPos());
        net.minecraft.world.item.ItemStack it = event.getItemStack();
        if (st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                && !st.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                && (it.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || it.is(net.minecraft.world.item.Items.FIRE_CHARGE))
                && com.jastkub.frozenfortress.entity.ShadeLight.smouldering(level, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            level.playSound(null, event.getPos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.6F);
            event.getEntity().displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.frozen_dominion.fire_smoulders"), true);
        }
    }

    @SubscribeEvent
    public static void onPlaceInArena(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel)
                || (event.getEntity() instanceof Player p && p.isCreative())) {
            return;
        }
        // LIGHTING A HEARTH IS NOT BUILDING: Forge reports flint
        // and steel (or a fire charge) on a campfire as a placement - a campfire put where a campfire stood
        if (event.getPlacedBlock().getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                && event.getBlockSnapshot().getReplacedBlock().getBlock()
                instanceof net.minecraft.world.level.block.CampfireBlock) {
            return;
        }
        for (com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity f
                : com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity.SERVER) {
            if (!f.isRemoved() && f.active() && f.getLevel() == event.getLevel() && f.inRoom(event.getPos())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof Player p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.frozen_dominion.trap_no_build"), true);
                }
                return;
            }
        }
        for (com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity h
                : com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity.SERVER) {
            if (!h.isRemoved() && h.getLevel() == event.getLevel() && h.inArena(event.getPos())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof Player p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.frozen_dominion.arena_no_build"), true);
                }
                return;
            }
        }
        // NOTHING IS BUILT IN THE CITADEL - by a
        // player; but the Frost Anvil, which has to be set down to be worked at
        if (event.getEntity() instanceof Player p
                && !event.getPlacedBlock().is(com.jastkub.frozenfortress.registry.FFBlocks.FROST_ANVIL.get())
                && inCitadel((net.minecraft.server.level.ServerLevel) event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.frozen_dominion.citadel_no_build"), true);
        }
    }

    /** THE CROWN'S HOLD (CrownHoldEffect): under it no block gives - the pick does nothing, on either side. */
    @SubscribeEvent
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getEntity().hasEffect(FFEffects.CROWN_HOLD.get()) && !event.getEntity().isCreative()) {
            event.setNewSpeed(0.0F);
        }
    }

    @SubscribeEvent
    public static void onBreakUnderCrown(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer().hasEffect(FFEffects.CROWN_HOLD.get()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    /** No bucket emptied in the citadel either (water and lava are building too). */
    @SubscribeEvent
    public static void onBucketInCitadel(net.minecraftforge.event.entity.player.FillBucketEvent event) {
        Player p = event.getEntity();
        if (p == null || p.isCreative() || !(p.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(event.getEmptyBucket().getItem() instanceof net.minecraft.world.item.BucketItem bucket)
                || bucket.getFluid() == net.minecraft.world.level.material.Fluids.EMPTY
                || !(event.getTarget() instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return;
        }
        if (inCitadel(level, hit.getBlockPos()) || inCitadel(level, hit.getBlockPos().relative(hit.getDirection()))) {
            event.setCanceled(true);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.frozen_dominion.citadel_no_build"), true);
        }
    }

    private static final net.minecraft.resources.ResourceLocation CITADEL =
            new net.minecraft.resources.ResourceLocation(FrozenFortress.MODID, "frozen_citadel");

    /** Is `pos` within one of the citadel's pieces (its structure)? */
    public static boolean inCitadel(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.levelgen.structure.Structure s = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(CITADEL);
        return s != null && level.structureManager().getStructureWithPieceAt(pos, s).isValid();
    }

    /**
     * THE FUNNY ADVANCEMENTS: the minibosses' deaths,
     * to everyone near; and dying of a trap room's cold, to whoever did it.
     */
    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        net.minecraft.world.entity.LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof net.minecraft.server.level.ServerLevel s)) {
            return;
        }
        // a boss's death scene, for whoever has not seen it (BossCutscenes)
        com.jastkub.frozenfortress.BossCutscenes.play(dead, com.jastkub.frozenfortress.BossCutscenes.DEATH,
                com.jastkub.frozenfortress.BossCutscenes.deathTicks(dead));
        if (dead instanceof net.minecraft.server.level.ServerPlayer p) {
            if (event.getSource().is(com.jastkub.frozenfortress.FFDamage.HEART_FROST)) {
                com.jastkub.frozenfortress.FFAdvancements.grant(p, "frozen_dinner", "frozen");
            }
            return;
        }
        String adv = switch (String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()))) {
            case "frozen_dominion:drowned_lady" -> "drowned";
            case "frozen_dominion:ice_aurochs" -> "aurochs";
            case "frozen_dominion:shade_shepherd" -> "shepherd";
            case "frozen_dominion:forge_overseer" -> "overseer";
            case "frozen_dominion:turnkey" -> "turnkey";
            case "frozen_dominion:rime_priestess" -> "priestess";
            default -> null;
        };
        if (adv != null) {
            com.jastkub.frozenfortress.FFAdvancements.grantNearby(s, dead.blockPosition(), 48.0D, adv, "defeated");
        }
    }

    /** A boss's scene holds the player watching it: nothing hurts him until it is over (BossCutscenes). */
    @SubscribeEvent
    public static void onHeldHurt(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide
                && com.jastkub.frozenfortress.BossCutscenes.held(p)) {
            event.setCanceled(true);
        }
    }

    /** The Winter blessing survives death and dimension changes. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(HeartOfWinterItem.BLESSING_TAG)) {
            event.getEntity().getPersistentData().putBoolean(HeartOfWinterItem.BLESSING_TAG, true);
            HeartOfWinterItem.applyBlessing(event.getEntity());
        }
    }

    /** Blessed players shrug off the cold entirely - and so does a full set of Kingsrime, and anyone (or anything)
     *  with a Potion of Warmth in them while it lasts. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        // THE FROSTWALKER'S BAND (07.10.2026): nothing slows its wearer
        if (event.getEffectInstance().getEffect() == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN
                && com.jastkub.frozenfortress.integration.curios.CuriosHooks.isEquipped(event.getEntity(),
                com.jastkub.frozenfortress.registry.FFItems.FROSTWALKER_BAND.get())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        if (event.getEffectInstance().getEffect() != FFEffects.FROSTBITE.get()) {
            return;
        }
        if (event.getEntity().hasEffect(FFEffects.WARMTH.get())
                || event.getEntity() instanceof Player player
                && (player.getPersistentData().getBoolean(HeartOfWinterItem.BLESSING_TAG)
                || com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor.hasFullKingsrime(player))) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** A Frost shield takes the blow and gives back the cold. */
    @SubscribeEvent
    public static void onShieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getUseItem().getItem() instanceof com.jastkub.frozenfortress.item.KingsrimeItems.FrostShield shield
                && event.getDamageSource().getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            com.jastkub.frozenfortress.item.KingsrimeItems.onShieldBlock(player, shield, attacker);
        }
    }

    /** A full-drawn Kingsrime arrow breaks in a burst of ice where it strikes. */
    @SubscribeEvent
    public static void onArrowImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        net.minecraft.world.entity.projectile.Projectile p = event.getProjectile();
        if (p.level().isClientSide || !p.getPersistentData().getBoolean(
                com.jastkub.frozenfortress.item.KingsrimeItems.KINGSRIME_ARROW)) {
            return;
        }
        com.jastkub.frozenfortress.item.KingsrimeItems.arrowBurst(p.level(), event.getRayTraceResult().getLocation(),
                p.getOwner() instanceof net.minecraft.world.entity.LivingEntity owner ? owner : null);
        if (event.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            p.getPersistentData().putBoolean(com.jastkub.frozenfortress.item.KingsrimeItems.KINGSRIME_ARROW, false);
        }
    }

    /** Rimeguard charging: the plate drinks the cold that hits it. */
    @SubscribeEvent
    public static void onWearerHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && com.jastkub.frozenfortress.item.EverfrostArmorItem.hasFullSet(player)
                && event.getSource().getEntity() instanceof com.jastkub.frozenfortress.entity.FrostServantEntity) {
            com.jastkub.frozenfortress.item.EverfrostArmorItem.setCharge(player,
                    com.jastkub.frozenfortress.item.EverfrostArmorItem.getCharge(player) + 1);
        }
    }

    /** ...and spends it all on the next blow the wearer lands. */
    @SubscribeEvent
    public static void onWearerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide
                || !com.jastkub.frozenfortress.item.EverfrostArmorItem.hasFullSet(player)) {
            return;
        }
        int charge = com.jastkub.frozenfortress.item.EverfrostArmorItem.getCharge(player);
        if (charge <= 0 || !(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity victim)) {
            return;
        }
        com.jastkub.frozenfortress.item.EverfrostArmorItem.setCharge(player, 0);
        victim.hurt(player.damageSources().playerAttack(player), 2.0F * charge);
        // KINGSRIME: a full charge does not just bite - it breaks over the blow
        // as a burst of ice round the one struck, once in thirty seconds
        long now = player.level().getGameTime();
        if (charge >= com.jastkub.frozenfortress.item.EverfrostArmorItem.maxCharge()
                && com.jastkub.frozenfortress.item.KingsrimeItems.KingsrimeArmor.hasFullKingsrime(player)
                && now >= player.getPersistentData().getLong("frozen_dominion:crown_lament")) {
            player.getPersistentData().putLong("frozen_dominion:crown_lament", now + 600L);
            for (net.minecraft.world.entity.LivingEntity e : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, victim.getBoundingBox().inflate(4.0D),
                    e -> e != player && e.isAlive() && !(e instanceof Player))) {
                e.hurt(player.damageSources().playerAttack(player), 8.0F);
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(FFEffects.FROSTBITE.get(), 120, 1), player);
            }
            if (player.level() instanceof net.minecraft.server.level.ServerLevel s) {
                s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(),
                        victim.getX(), victim.getY(0.5D), victim.getZ(), 80, 2.0D, 0.8D, 2.0D, 0.18D);
            }
            player.level().playSound(null, victim.blockPosition(),
                    com.jastkub.frozenfortress.registry.FFSounds.ICE_SHATTER.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.6F, 0.6F);
        }
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                FFEffects.FROSTBITE.get(), 40 + 30 * charge, charge >= 4 ? 1 : 0), player);
        player.level().playSound(null, player.blockPosition(),
                com.jastkub.frozenfortress.registry.FFSounds.CRYSTAL_CHIME.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.3F);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(),
                    victim.getX(), victim.getY(0.6D), victim.getZ(),
                    6 * charge, 0.4D, 0.5D, 0.4D, 0.12D);
        }
    }

    /**
     * FROSTBITE STOPS YOU MENDING. Anything at all - regeneration, a golden
     * apple, natural healing off a full hunger bar, a totem, another mod's
     * lifesteal - is refused for as long as it is on you.
     *
     * <p>It is a cancel and not a reduction on purpose. Halved healing is a
     * fight you play exactly the same way and win more slowly; no healing is a
     * fight where the cold is a clock, and either you break off and let it run
     * out or you finish what you are doing before it finishes you. That is
     * also what finally gives the periodic bite a job: a heart and a half over
     * a few seconds is noise next to a golden apple, and a countdown when
     * there is no apple.
     *
     * <p>LivingHealEvent rather than anything inside the effect, because
     * healing arrives through a dozen doors and this is the one they all pass
     * through - and it catches other mods' healing for free, which an override
     * of our own damage path never could.
     */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        // (08.10.2026: Velkhar's own seal on every wound he cut is gone - "usunac te mechanike blokady leczenia"; his
        // blows lay Frostbite on, and that is all, which a Potion of Warmth keeps off)
        if (event.getEntity().hasEffect(FFEffects.FROSTBITE.get())) {
            event.setCanceled(true);
        }
    }

    /** Blessed players take no freeze damage. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)
                && player.getPersistentData().getBoolean(HeartOfWinterItem.BLESSING_TAG)) {
            player.setTicksFrozen(0);
            event.setCanceled(true);
        }
    }
}
