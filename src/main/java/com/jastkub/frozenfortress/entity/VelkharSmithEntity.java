package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.FFAdvancements;
import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * KOWAL VELKHARA - the king's smith, kept in his cell off the forge, chained by both wrists to rings in
 * the floor beside the Frost Anvil and his unfinished work, the king's armour on its stand.
 *
 * <p>CHAINED while the forge's Overseer lives: he will not trade (a shake of the head, the chains rattle). Once no
 * Overseer is left alive near him - checked every two seconds, five times running, so an Overseer in a chunk not yet
 * loaded does not free him early - his chains snap and he is free; the Overseer's death can also free him at once
 * ({@link #releaseNear}). Free, he trades for everfrost shards: the plain things a delver runs short of, the Saddle
 * of the Monstrosity, and the Wand's Fitting. He cannot be hurt, and never leaves his cell.
 * (tools/gen_smith.py)
 */
public class VelkharSmithEntity extends AbstractVillager implements GeoEntity {

    private static final EntityDataAccessor<Boolean> CHAINED =
            SynchedEntityData.defineId(VelkharSmithEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * The way he faces, from the server. Each
     * side used to take it from its own first tick - the client before the server's rotation had reached it, so it
     * held him one way while every packet from the server put him the other, and he shook between the two. NaN until
     * the server has set it.
     */
    private static final EntityDataAccessor<Float> FACING_YAW =
            SynchedEntityData.defineId(VelkharSmithEntity.class, EntityDataSerializers.FLOAT);
    /** The forge's Overseer (ForgeOverseerEntity): by its id, so the smith does not depend on its class. */
    private static final ResourceLocation OVERSEER = ResourceLocation.fromNamespaceAndPath(FrozenFortress.MODID, "forge_overseer");

    private static final String P = "animation.velkhar_smith.";
    private static final RawAnimation CHAINED_ANIM = RawAnimation.begin().thenLoop(P + "chained");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    /* PLAYED ONCE, whatever the file says: these are triggered, and a triggered clip that holds its last frame pauses the controller
     * there for good - GeckoLib lets it go only once it has stopped, and a held clip never stops - so the first strike
     * (struck while the player was still far off) was the only one: every trigger of the same clip after it did nothing. */
    private static final RawAnimation TRADE = RawAnimation.begin().then(P + "trade", software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation REFUSE = RawAnimation.begin().then(P + "refuse", software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation FREED = RawAnimation.begin().then(P + "freed", software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);
    /** AT HIS WORK: free, now and then he
     *  strikes the anvil before him three times (tools/gen_smith.py "hammer": the strikes land at these ticks). */
    private static final RawAnimation HAMMER = RawAnimation.begin().then(P + "hammer", software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);
    private static final int[] HAMMER_HITS = {10, 20, 30};
    private int hammerIn = 100;
    private int hammering = -1;
    @javax.annotation.Nullable
    private BlockPos anvil;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int keeperGone;

    public VelkharSmithEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHAINED, true);
        builder.define(FACING_YAW, Float.NaN);
    }

    public boolean isChained() {
        return entityData.get(CHAINED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }


    @Override
    public void aiStep() {
        super.aiStep();
        {
            // held where he was put, chained or free: the body never turns, the head
            // only looks about
            float chainYaw = entityData.get(FACING_YAW);
            if (Float.isNaN(chainYaw)) {
                if (level().isClientSide) {
                    return;                                     // (not known yet: the server's turn to say)
                }
                chainYaw = getYRot();
                entityData.set(FACING_YAW, chainYaw);
            }
            setYRot(chainYaw);
            yRotO = chainYaw;
            yBodyRot = chainYaw;
            yBodyRotO = chainYaw;
            float look = net.minecraft.util.Mth.wrapDegrees(yHeadRot - chainYaw);
            yHeadRot = chainYaw + net.minecraft.util.Mth.clamp(look, -70.0F, 70.0F);
        }
        // AT HIS ANVIL, CHAINED OR FREE: chained he works for the king, free for himself - he struck it only once freed, and stood idle
        // through the whole of the time a player sees him in his chains
        if (!level().isClientSide) {
            work();
        }
        if (!level().isClientSide && !isChained()) {
            return;
        }
        if (level().isClientSide || !isChained()) {
            return;
        }
        if (tickCount % 40 == 0) {
            EntityType<?> overseer = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(OVERSEER);
            boolean keeper = overseer != null && !level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(48.0D), e -> e.getType() == overseer && e.isAlive()).isEmpty();
            keeperGone = keeper ? 0 : keeperGone + 1;
            if (keeperGone >= 5) {
                free();
            }
        }
        if (random.nextInt(160) == 0) {
            playSound(SoundEvents.CHAIN_STEP, 0.6F, 0.7F + random.nextFloat() * 0.2F);   // he shifts in his chains
        }
    }

    /** At his anvil: every few seconds three strikes on it - heard, and the sparks seen. */
    private void work() {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        if (hammering >= 0) {
            hammering++;
            for (int hit : HAMMER_HITS) {
                if (hammering == hit && anvil != null) {
                    s.playSound(null, anvil, SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.45F,
                            1.15F + random.nextFloat() * 0.2F);
                    s.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, anvil.getX() + 0.5D,
                            anvil.getY() + 1.05D, anvil.getZ() + 0.5D, 6, 0.15D, 0.05D, 0.15D, 0.12D);
                    s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), anvil.getX() + 0.5D,
                            anvil.getY() + 1.05D, anvil.getZ() + 0.5D, 3, 0.1D, 0.05D, 0.1D, 0.05D);
                }
            }
            if (hammering > 44) {
                hammering = -1;
            }
            return;
        }
        if (getTradingPlayer() != null || --hammerIn > 0) {
            return;
        }
        hammerIn = 80 + random.nextInt(81);                    // every four to eight seconds
        if (anvil == null || !isAnvil(anvil)) {
            anvil = null;
            double best = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(blockPosition().offset(-4, -1, -4), blockPosition().offset(4, 1, 4))) {
                if (isAnvil(q) && q.distSqr(blockPosition()) < best) {
                    best = q.distSqr(blockPosition());
                    anvil = q.immutable();
                }
            }
        }
        if (anvil == null) {
            return;                                             // (his anvil taken away: nothing to strike)
        }
        // (he was set facing it, and his ankle's chain keeps him so: he never turns to it)
        hammering = 0;
        triggerAnim("main", "hammer");
    }

    /** The Frost Anvil he was set at, or any anvil (the forge's own). */
    private boolean isAnvil(BlockPos q) {
        net.minecraft.world.level.block.state.BlockState b = level().getBlockState(q);
        return b.is(com.jastkub.frozenfortress.registry.FFBlocks.FROST_ANVIL.get())
                || b.is(net.minecraft.tags.BlockTags.ANVIL);
    }

    /** His chains snap: free, and open for trade. */
    public void free() {
        if (!isChained() || !(level() instanceof ServerLevel s)) {
            return;
        }
        entityData.set(CHAINED, false);
        triggerAnim("main", "freed");
        playSound(SoundEvents.CHAIN_BREAK, 1.4F, 0.8F);
        playSound(SoundEvents.ANVIL_LAND, 0.6F, 1.4F);
        for (Player p : s.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32.0D))) {
            p.displayClientMessage(Component.translatable("entity.frozen_dominion.velkhar_smith.freed"), true);
        }
        FFAdvancements.grantNearby(s, blockPosition(), 32.0D, "smith", "freed");
    }

    /** The Overseer's death: every smith near `pos` is free at once (ForgeOverseerEntity's hook). */
    public static void releaseNear(Level level, BlockPos pos, double radius) {
        for (VelkharSmithEntity smith : level.getEntitiesOfClass(VelkharSmithEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(radius))) {
            smith.free();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isChained()) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("entity.frozen_dominion.velkhar_smith.chained"), true);
                triggerAnim("main", "refuse");
                playSound(SoundEvents.CHAIN_HIT, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isTrading() || !isAlive()) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && !getOffers().isEmpty()) {
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private static net.minecraft.world.item.trading.ItemCost shards(int n) {
        return new net.minecraft.world.item.trading.ItemCost(FFItems.EVERFROST_SHARD.get(), n);
    }

    /** What he forges for everfrost shards: the plain things, the saddle, the wand's fitting. */
    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        offers.add(new MerchantOffer(shards(2), new ItemStack(Items.TORCH, 8), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(4), new ItemStack(Items.ARROW, 16), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(3), new ItemStack(Items.BREAD, 6), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(4), new ItemStack(Items.COOKED_BEEF, 4), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(3), new ItemStack(Items.FLINT_AND_STEEL), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(6), new ItemStack(Items.SHIELD), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(8), new ItemStack(Items.IRON_PICKAXE), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(10), new ItemStack(Items.IRON_SWORD), 999, 0, 0.0F));
        offers.add(new MerchantOffer(shards(16), java.util.Optional.of(
                new net.minecraft.world.item.trading.ItemCost(FFItems.EVERFROST_INGOT.get(), 2)),
                new ItemStack(FFItems.MONSTROSITY_SADDLE.get()), 3, 0, 0.0F));
        offers.add(new MerchantOffer(shards(FITTING_SHARDS), new ItemStack(FFItems.WAND_FITTING.get()), 1, 0, 0.0F));
        // THE BONE LORD'S FEMUR, shod in everfrost: the Femur of the Chase (08.10.2026)
        offers.add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(FFItems.BONE_LORD_FEMUR.get()), java.util.Optional.of(new net.minecraft.world.item.trading.ItemCost(FFItems.EVERFROST_INGOT.get(), 2)),
                new ItemStack(FFItems.CHASE_FEMUR.get()), 8, 0, 0.0F));
    }

    /** The Wand's Fitting: 30, was 20. */
    private static final int FITTING_SHARDS = 30;

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        triggerAnim("main", "trade");
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.SMITHING_TABLE_USE;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.ANVIL_USE : SoundEvents.CHAIN_HIT;
    }

    @javax.annotation.Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob other) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * HE DOES NOT TURN. After every aiStep the body's own control
     * (Mob's BodyRotationControl) pulled his body round to within 40 degrees of wherever his head was looking, and the
     * next aiStep set it back: each frame drawn from the one to the other. So while he looked far round he shook and
     * turned, and while he looked ahead he stood. His body stays the way he was set; only the head looks about.
     */
    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        float yaw = entityData.get(FACING_YAW);
        if (Float.isNaN(yaw)) {
            return super.tickHeadTurn(yRot, animStep);
        }
        yBodyRot = yaw;
        return animStep;
    }

    /** (his ankle's chain runs a block and a half behind him to the wall: drawn while any of it is in sight) */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.6D, 0.0D, 1.6D);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Chained", isChained());
        float yaw = entityData.get(FACING_YAW);
        if (!Float.isNaN(yaw)) {
            tag.putFloat("ChainYaw", yaw);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Chained")) {
            entityData.set(CHAINED, tag.getBoolean("Chained"));
        }
        if (tag.contains("ChainYaw")) {
            entityData.set(FACING_YAW, tag.getFloat("ChainYaw"));
        }
        // a smith from before the fitting's price went up keeps his old offer in his save: priced anew, its use kept
        if (offers != null) {
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer o = offers.get(i);
                if (o.getResult().is(FFItems.WAND_FITTING.get()) && o.getBaseCostA().getCount() != FITTING_SHARDS) {
                    offers.set(i, new MerchantOffer(shards(FITTING_SHARDS), java.util.Optional.empty(), o.getResult().copy(),
                            o.getUses(), o.getMaxUses(), 0, 0.0F));
                }
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isChained()) {
                return state.setAndContinue(CHAINED_ANIM);
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }).triggerableAnim("trade", TRADE).triggerableAnim("refuse", REFUSE).triggerableAnim("freed", FREED)
                .triggerableAnim("hammer", HAMMER));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
