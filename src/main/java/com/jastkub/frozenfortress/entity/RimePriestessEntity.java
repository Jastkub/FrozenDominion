package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * KAPLANKA SZRONU - THE PRIESTESS OF RIME. She kept the Chapel of Rime for the
 * dead court and keeps it still, kneeling at its altar over the book of the
 * rite. She holds the key of the Archive.
 *
 * <p>Her fight is a thing to LEARN, not a sack of health:
 * <ul>
 *   <li><b>THE LITANY</b> - at three quarters, half and a quarter of her
 *       health she goes back to her altar and reads. While she reads nothing
 *       touches her. Runes light on the chapel's floor, a few of each of four
 *       kinds, and one rune turns over her open book. Three verses; at the
 *       Amen every floor that does not bear HER rune - and every place in the
 *       chapel off the runes - freezes. Read the book, stand on its rune. Then
 *       she is spent, bowed over the shut book, and takes half again.</li>
 *   <li><b>THE VEIL</b> - below half, she goes to snow and three of her stand
 *       in the chapel. Only the true one holds her book open (the others'
 *       hands are empty). Strike a reflection and it breaks over you in frost;
 *       strike her and the reflections fall to snow.</li>
 * </ul>
 * And in between, three things of her own: the PAGES torn from her book that
 * circle you and fall on you (each can be struck down), the BELL of penance
 * that drops where her crook points (its shadow and its ring are the
 * warning), and the HOOK of her crook swept low before her. Once, at half
 * her health, the REQUIEM: the stone sisters of the chapel step out of their
 * ice.
 */
public class RimePriestessEntity extends FrostServantEntity implements GateKeeper {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(RimePriestessEntity.class, EntityDataSerializers.BOOLEAN);
    /** The rune over her book while she reads (-1: none). */
    private static final EntityDataAccessor<Integer> RUNE =
            SynchedEntityData.defineId(RimePriestessEntity.class, EntityDataSerializers.INT);

    public static final int HOOK = 1, PAGES = 2, BELL = 3, LITANY = 4, SPENT = 5, AWAKEN = 6, REQUIEM = 7, VEIL = 8,
            AMEN = 9, INTRO = 10;
    /** HER ENTRANCE (tools/gen_priestess.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts its
     *  shots to them): the book opened at prayer, two leaves turned, the book shut, she rises, the crook at the floor. */
    public static final int INTRO_T = 156, INTRO_OPEN = 12, INTRO_SHUT = 64, INTRO_RISE = 70, INTRO_POINT = 92;
    public static final int[] INTRO_PAGES = {36, 50};
    /** HER DEATH (tools/gen_priestess.py DEATH_*: the "death" clip - change one, change both; BossScenes' death film is
     *  cut to them): thrown up, hanging in the air, she drops out of it (DROP) onto her knees (KNEEL); the book slips
     *  from her hand (BOOK) and falls shut (BOOK_FALL later); the halo slips (HALO) and shatters on the stones
     *  (HALO_FALL later); she goes over on her back (TIP to FALL) and the light leaves her mask (EYES). The clip runs
     *  DEATH_LAG behind her deathTime: the controller blends into it first. */
    public static final int DEATH_T = 80, DEATH_DROP = 20, DEATH_KNEEL = 25, DEATH_BOOK = 34, DEATH_HALO = 42,
            DEATH_TIP = 60, DEATH_FALL = 70, DEATH_EYES = 72;
    public static final int DEATH_BOOK_FALL = 5, DEATH_HALO_FALL = 6, DEATH_LAG = 4;

    private static final String P = "animation.rime_priestess.";
    private static final RawAnimation PRAY = RawAnimation.begin().thenLoop(P + "pray");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation READ = RawAnimation.begin().thenLoop(P + "read");
    private static final RawAnimation AMEN_ANIM = RawAnimation.begin().thenPlay(P + "amen");
    private static final RawAnimation SPENT_ANIM = RawAnimation.begin().thenLoop(P + "spent");
    private static final RawAnimation PAGES_ANIM = RawAnimation.begin().thenPlay(P + "pages");
    private static final RawAnimation BELL_ANIM = RawAnimation.begin().thenPlay(P + "bell");
    private static final RawAnimation HOOK_ANIM = RawAnimation.begin().thenPlay(P + "hook");
    private static final RawAnimation REQUIEM_ANIM = RawAnimation.begin().thenPlay(P + "requiem");
    private static final RawAnimation VEIL_ANIM = RawAnimation.begin().thenPlay(P + "veil");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");

    private static final double LEASH = 20.0D;
    /** Ticks of the Litany: three verses, then the Amen. */
    static final int VERSE_2 = 40, VERSE_3 = 80, AMEN_AT = 110;
    private static final float AMEN_DAMAGE = 22.0F;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.rime_priestess"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private BlockPos home;
    private boolean requiemDone;
    /** How many of her three Litanies she has read. */
    private int litanies;
    private boolean litanyPending;
    private boolean veilForMirrors;
    /** THE STEP AWAY: her next veil carries her out of your reach rather than to the altar or into three. */
    private boolean blinkAway;
    /** Ticks until she may step away again. */
    int blinkCooldown;
    private final List<UUID> runes = new ArrayList<>();
    private final List<UUID> mirrors = new ArrayList<>();
    private int safeRune;

    public RimePriestessEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 100;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 30.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
        entityData.define(RUNE, -1);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** The rune turning over her book (-1 when she is not reading). */
    public int bookRune() {
        return entityData.get(RUNE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new PriestessGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ the bar
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isDormant() && !isDeadOrDying()) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    private void tickBossBar() {
        if (isDormant() || isDeadOrDying()) {
            bossEvent.removeAllPlayers();
            return;
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        int st = getAttackState();
        bossEvent.setColor(st == LITANY || st == AMEN ? BossEvent.BossBarColor.WHITE
                : st == SPENT ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.PURPLE);
        if (level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.getPlayers(p -> p.distanceToSqr(this) < 40.0D * 40.0D)) {
                bossEvent.addPlayer(p);
            }
            for (ServerPlayer p : List.copyOf(bossEvent.getPlayers())) {
                if (p.distanceToSqr(this) >= 56.0D * 56.0D || p.level() != level()) {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        clearRunes(false);
        dispelMirrors();
        super.die(source);
    }

    // ------------------------------------------------------------------ her rite
    @Override
    public void aiStep() {
        super.aiStep();
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
        if (level().isClientSide) {
            if (!isDormant() && random.nextInt(4) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(), getX() + (random.nextDouble() - 0.5D),
                        getY() + 1.2D, getZ() + (random.nextDouble() - 0.5D), 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            Player near = level().getNearestPlayer(this, 11.0D);
            if (near != null && !near.isCreative() && !near.isSpectator() && hasLineOfSight(near) && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                awaken();
            }
            if (tickCount % 80 == 0) {
                playSound(FFSounds.RIMEWEAVER_IDLE.get(), 0.8F, 0.6F);       // the rite, murmured
            }
        } else {
            if (getAttackState() == AWAKEN && attackTicks > 40) {
                setAttackState(0);
            }
            if (getAttackState() == INTRO) {
                introBeats(attackTicks);
            }
            if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > LEASH * LEASH && !isAttacking()) {
                setTarget(null);
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            }
            if (getAttackState() == LITANY) {
                tickLitany();
            } else if (getAttackState() == AMEN && attackTicks > 16) {
                setAttackState(SPENT);
            } else if (getAttackState() == SPENT && attackTicks > 80) {
                setAttackState(0);
            }
            mirrors.removeIf(id -> !(level() instanceof ServerLevel s) || !(s.getEntity(id) instanceof PriestessMirrorEntity));
        }
        tickBossBar();
    }


    /** Its gate down a second (BossGateBlockEntity): it wakes now, for whoever it shut in with it. */
    @Override
    public void gateShut() {
        if (!isDormant() || !isAlive()) {
            return;
        }
        net.minecraft.world.entity.player.Player p = GateKeeper.shutInWith(this);
        awaken();
        if (p != null) {
            setTarget(p);
        }
    }

    private void awaken() {
        entityData.set(DORMANT, false);
        // her entrance, once for each who sees it (BossCutscenes), shot as a film: at prayer over her book, a leaf
        // turned, the book shut, she rises - or, when everyone here has seen it, the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T)) {
            setAttackState(INTRO);
            return;
        }
        setAttackState(AWAKEN);
        level().playSound(null, blockPosition(), FFSounds.RIMEWEAVER_CAST.get(), SoundSource.HOSTILE, 1.6F, 0.5F);
        level().playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.4F, 0.5F);
    }

    /** Which way she faces through her entrance: her post's (the nave before her). */
    private float introYaw;

    /** Her entrance's beats (the clip's), and her kept before her altar through it. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_OPEN + 4) {
            playSound(FFSounds.RIME_PRIESTESS_PAGE.get(), 1.0F, 0.85F);   // the book opened
        }
        if (t == 22) {
            playSound(FFSounds.RIMEWEAVER_IDLE.get(), 0.9F, 0.6F);         // the rite, murmured over it
        }
        for (int p : INTRO_PAGES) {
            if (t == p + 1) {
                playSound(FFSounds.RIME_PRIESTESS_PAGE.get(), 1.3F, 0.95F + random.nextFloat() * 0.1F);
            }
        }
        if (t == INTRO_SHUT + 2) {
            playSound(FFSounds.RIME_PRIESTESS_BOOK_SHUT.get(), 1.6F, 1.0F);
        }
        if (t == INTRO_RISE - 4) {
            playSound(SoundEvents.BELL_BLOCK, 1.2F, 0.5F);                // her bell, as she rises
        }
        if (t >= INTRO_T) {
            setAttackState(0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isDormant() && source.getEntity() != null) {
            awaken();
        }
        int st = getAttackState();
        if (st == INTRO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                       // her entrance: a scene is not a fight
        }
        if (st == LITANY || st == AMEN || (st == VEIL && litanyPending)) {
            // while she reads, the rite keeps her: nothing gets through
            if (!level().isClientSide && level() instanceof ServerLevel s && source.getEntity() != null) {
                s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.6D, getZ(), 8, 0.4D, 0.6D, 0.4D, 0.02D);
                playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 0.6F);
            }
            return false;
        }
        if (st == SPENT) {
            amount *= 1.5F;                                     // spent after the Amen: open
        }
        if (st == AWAKEN || st == VEIL) {
            amount *= 0.5F;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof Player && !mirrors.isEmpty()) {
            dispelMirrors();                                    // found: the reflections fall to snow
        }
        // struck at arm's length, she does not stand and take the next: she steps away
        if (hit && !level().isClientSide && isAlive() && getAttackState() == 0 && blinkCooldown <= 0
                && source.getEntity() instanceof Player p && p.distanceTo(this) < 4.0D && random.nextFloat() < 0.6F) {
            beginBlink();
        }
        return hit;
    }

    // ------------------------------------------------------------------ THE LITANY
    boolean litanyDue() {
        float[] at = {0.75F, 0.50F, 0.25F};
        return litanies < at.length && getHealth() <= getMaxHealth() * at[litanies];
    }

    /** Back to her altar, through the snow - and then she reads. */
    void beginLitany() {
        litanyPending = true;
        setAttackState(VEIL);
    }

    private void startReading() {
        litanyPending = false;
        litanies++;
        getNavigation().stop();
        setAttackState(LITANY);
        if (!(level() instanceof ServerLevel s) || home == null) {
            return;
        }
        // the floor of the chapel, in tiles of three, each with a rune
        List<int[]> tiles = new ArrayList<>();
        for (int i = -6; i <= 6; i++) {
            for (int j = -6; j <= 6; j++) {
                int cx = home.getX() + i * 3, cz = home.getZ() + j * 3;
                Integer y = floorY(s, cx, cz);
                if (y == null) {
                    continue;
                }
                int stand = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (standable(s, new BlockPos(cx + dx, y, cz + dz))) {
                            stand++;
                        }
                    }
                }
                if (stand >= 5) {
                    tiles.add(new int[]{cx, y, cz, random.nextInt(4)});
                }
            }
        }
        safeRune = random.nextInt(4);
        long safe = tiles.stream().filter(t -> t[3] == safeRune).count();
        for (int k = 0; safe < Math.min(4, tiles.size()) && k < 50; k++) {
            int[] t = tiles.get(random.nextInt(tiles.size()));
            if (t[3] != safeRune) {
                t[3] = safeRune;
                safe++;
            }
        }
        for (int[] t : tiles) {
            LitanyRuneEntity r = new LitanyRuneEntity(level(), this, t[3], new Vec3(t[0] + 0.5D, t[1], t[2] + 0.5D));
            s.addFreshEntity(r);
            runes.add(r.getUUID());
        }
        entityData.set(RUNE, safeRune);
        playSound(SoundEvents.BELL_RESONATE, 2.0F, 0.7F);
        playSound(FFSounds.RIMEWEAVER_CAST.get(), 1.8F, 0.6F);
    }

    private void tickLitany() {
        int t = attackTicks;
        if (t == VERSE_2 || t == VERSE_3) {
            setVerse(t == VERSE_2 ? 2 : 3);
            playSound(SoundEvents.BELL_BLOCK, 2.0F, t == VERSE_2 ? 0.6F : 0.8F);
        }
        if (t >= AMEN_AT) {
            amen();
        }
    }

    private void setVerse(int verse) {
        if (level() instanceof ServerLevel s) {
            for (UUID id : runes) {
                if (s.getEntity(id) instanceof LitanyRuneEntity r) {
                    r.setVerse(verse);
                }
            }
        }
    }

    /** THE AMEN: every floor not bearing her rune freezes - and every place in the chapel off the runes. */
    private void amen() {
        setAttackState(AMEN);
        entityData.set(RUNE, -1);
        if (!(level() instanceof ServerLevel s) || home == null) {
            return;
        }
        List<LitanyRuneEntity> tiles = new ArrayList<>();
        for (UUID id : runes) {
            if (s.getEntity(id) instanceof LitanyRuneEntity r) {
                tiles.add(r);
            }
        }
        for (Player p : s.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(home).inflate(28.0D, 12.0D, 28.0D),
                p -> !p.isCreative() && !p.isSpectator() && p.isAlive())) {
            boolean safe = false;
            for (LitanyRuneEntity r : tiles) {
                if (r.rune() == safeRune && Math.abs(p.getX() - r.getX()) <= 1.6D && Math.abs(p.getZ() - r.getZ()) <= 1.6D
                        && p.getY() > r.getY() - 0.5D && p.getY() < r.getY() + 1.6D) {
                    safe = true;
                    break;
                }
            }
            if (!safe) {
                p.hurt(damageSources().indirectMagic(this, this), AMEN_DAMAGE);
                // AND SHE IS MENDED BY IT: every soul off the rune is three tenths of her health back -
                // a wrong step costs the fight, not only the hearts
                heal(getMaxHealth() * 0.30F);
                s.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 2.2D, getZ(), 30, 0.6D, 1.0D, 0.6D, 0.05D);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3), this);
                p.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 100, 1), this);
                p.setTicksFrozen(Math.max(p.getTicksFrozen(), 200));
                s.sendParticles(FFParticles.ICE_SHARD.get(), p.getX(), p.getY() + 1.0D, p.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.15D);
            }
        }
        for (LitanyRuneEntity r : tiles) {
            if (r.rune() != safeRune) {
                s.sendParticles(FFParticles.ICE_SHARD.get(), r.getX(), r.getY() + 0.3D, r.getZ(), 12, 1.0D, 0.2D, 1.0D, 0.08D);
            }
        }
        clearRunes(true);
        playSound(SoundEvents.GLASS_BREAK, 2.4F, 0.5F);
        playSound(FFSounds.ICE_SHATTER.get(), 2.4F, 0.5F);
        playSound(SoundEvents.BELL_BLOCK, 2.4F, 0.4F);
    }

    private void clearRunes(boolean burst) {
        if (level() instanceof ServerLevel s) {
            for (UUID id : runes) {
                if (s.getEntity(id) instanceof LitanyRuneEntity r) {
                    r.discard();
                }
            }
        }
        runes.clear();
        entityData.set(RUNE, -1);
    }

    private Integer floorY(ServerLevel s, int x, int z) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = new BlockPos(x, home.getY() + dy, z);
            if (standable(s, p)) {
                return p.getY();
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel s, BlockPos p) {
        return s.getBlockState(p).getCollisionShape(s, p).isEmpty()
                && s.getBlockState(p.above()).getCollisionShape(s, p.above()).isEmpty()
                && !s.getBlockState(p.below()).getCollisionShape(s, p.below()).isEmpty();
    }

    // ------------------------------------------------------------------ THE VEIL and her reflections
    boolean mirrorsDue() {
        return getHealth() < getMaxHealth() * 0.5F && mirrors.isEmpty();
    }

    void beginMirrors() {
        veilForMirrors = true;
        setAttackState(VEIL);
    }

    /** Gone to snow: back by the altar (for the Litany), or three of her somewhere in the chapel. */
    void veil() {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), getX(), getY() + 1.4D, getZ(), 60, 0.5D, 1.2D, 0.5D, 0.05D);
        playSound(FFSounds.VELKHAR_TELEPORT.get(), 1.0F, 1.4F);
        if (litanyPending) {
            if (home != null) {
                teleportTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
            }
            return;
        }
        if (blinkAway) {
            blinkAway = false;
            Vec3 to = stepAwaySpot(s);
            if (to != null) {
                teleportTo(to.x, to.y, to.z);
                s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), to.x, to.y + 1.4D, to.z, 40, 0.5D, 1.2D, 0.5D, 0.05D);
                getNavigation().stop();
            }
            return;
        }
        if (veilForMirrors) {
            veilForMirrors = false;
            List<Vec3> spots = new ArrayList<>();
            for (int attempt = 0; attempt < 30 && spots.size() < 3; attempt++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = 4.0D + random.nextDouble() * 6.0D;
                BlockPos c = home != null ? home : blockPosition();
                Integer y = floorY(s, (int) Math.floor(c.getX() + Math.cos(a) * r), (int) Math.floor(c.getZ() + Math.sin(a) * r));
                if (y == null) {
                    continue;
                }
                Vec3 v = new Vec3(Math.floor(c.getX() + Math.cos(a) * r) + 0.5D, y, Math.floor(c.getZ() + Math.sin(a) * r) + 0.5D);
                if (spots.stream().allMatch(o -> o.distanceToSqr(v) > 9.0D)) {
                    spots.add(v);
                }
            }
            if (spots.size() < 2) {
                return;
            }
            java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
            Vec3 me = spots.get(0);
            teleportTo(me.x, me.y, me.z);
            for (int k = 1; k < spots.size(); k++) {
                PriestessMirrorEntity m = new PriestessMirrorEntity(level(), this, spots.get(k));
                m.setTarget(getTarget());
                s.addFreshEntity(m);
                mirrors.add(m.getUUID());
                s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), m.getX(), m.getY() + 1.4D, m.getZ(), 40, 0.5D, 1.2D, 0.5D, 0.05D);
            }
            s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), me.x, me.y + 1.4D, me.z, 40, 0.5D, 1.2D, 0.5D, 0.05D);
        }
    }

    /** Begin THE STEP AWAY: gone to snow, back out of reach (the veil, quick). */
    void beginBlink() {
        blinkAway = true;
        blinkCooldown = 60;
        setAttackState(VEIL);
    }

    /**
     * Where she steps to: seven to eleven blocks from her quarry, on the chapel's floor, within her leash,
     * and where she can see it from (she means to go on casting).
     */
    @javax.annotation.Nullable
    private Vec3 stepAwaySpot(ServerLevel s) {
        if (home == null) {
            return null;
        }
        LivingEntity t = getTarget();
        Vec3 from = t != null ? t.position() : position();
        Vec3 best = null;
        double bestScore = -1.0E9D;
        for (int attempt = 0; attempt < 28; attempt++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = 7.0D + random.nextDouble() * 4.0D;
            int x = (int) Math.floor(from.x + Math.cos(a) * r), z = (int) Math.floor(from.z + Math.sin(a) * r);
            if ((x + 0.5D - home.getX()) * (x + 0.5D - home.getX()) + (z + 0.5D - home.getZ()) * (z + 0.5D - home.getZ())
                    > (LEASH - 3.0D) * (LEASH - 3.0D)) {
                continue;
            }
            Integer y = floorY(s, x, z);
            if (y == null) {
                continue;
            }
            Vec3 v = new Vec3(x + 0.5D, y, z + 0.5D);
            double score = random.nextDouble();
            if (t != null) {
                net.minecraft.world.phys.HitResult sight = s.clip(new net.minecraft.world.level.ClipContext(
                        v.add(0.0D, 2.0D, 0.0D), t.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                score += sight.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 10.0D : 0.0D;
            }
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    void dispelMirrors() {
        if (level() instanceof ServerLevel s) {
            for (UUID id : mirrors) {
                if (s.getEntity(id) instanceof PriestessMirrorEntity m) {
                    m.fade();
                }
            }
        }
        mirrors.clear();
    }

    // ------------------------------------------------------------------ her own three
    /** The crook swept low before her: it trips and throws back whoever stood in front. */
    void hook() {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5D),
                e -> e != this && !(e instanceof FrostServantEntity) && e.isAlive())) {
            Vec3 to = v.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > 4.4D || flat.normalize().dot(fwd) < 0.1D) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), 9.0F);
            v.knockback(1.2D, -fwd.x, -fwd.z);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.7F);
        playSound(SoundEvents.CHAIN_HIT, 1.2F, 0.8F);
    }

    /** Leaves torn from the book: they circle the target, then fall on it. */
    void pages(LivingEntity target) {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        int n = getHealth() < getMaxHealth() * 0.5F ? 5 : 4;
        Vec3 from = position().add(0.0D, 2.2D, 0.0D).add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.6D));
        for (int i = 0; i < n; i++) {
            FrostPageEntity p = new FrostPageEntity(level(), this, target, from, (Math.PI * 2.0D * i) / n, 4.0F);
            s.addFreshEntity(p);
        }
        playSound(SoundEvents.BOOK_PAGE_TURN, 2.0F, 0.6F);
        playSound(FFSounds.RIMEWEAVER_CAST.get(), 1.2F, 1.3F);
    }

    /** The bell of penance, where her crook points: a shadow, then the bell, then its ring. */
    void bell(LivingEntity target) {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        PenanceBellEntity b = new PenanceBellEntity(level(), this, target.position());
        s.addFreshEntity(b);
        playSound(SoundEvents.BELL_RESONATE, 1.6F, 1.2F);
    }

    /** The stone sisters of the chapel answer her: every statue near that can wake, wakes. */
    void requiem(LivingEntity target) {
        requiemDone = true;
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        playSound(SoundEvents.BELL_RESONATE, 2.0F, 0.5F);
        int woken = 0;
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-22, -4, -22), c.offset(22, 6, 22))) {
            if (s.getBlockEntity(p) instanceof CitadelStatueBlockEntity statue && statue.awakenNow(s)) {
                woken++;
            }
        }
        if (woken == 0) {
            for (int k = 0; k < 2; k++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                BlockPos at = BlockPos.containing(getX() + Math.cos(a) * 4.0D, getY(), getZ() + Math.sin(a) * 4.0D);
                RimeweaverEntity m = FFEntities.RIMEWEAVER.get().create(s);
                if (m == null) {
                    continue;
                }
                m.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                if (!s.noCollision(m)) {
                    continue;
                }
                m.finalizeSpawn(s, s.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
                m.setTarget(target);
                s.addFreshEntity(m);
            }
        }
        // A PARTY: one more of her dead for every other fighter, sent at them (PartyScaling)
        for (net.minecraft.world.entity.player.Player other : com.jastkub.frozenfortress.event.PartyScaling.others(this, target, com.jastkub.frozenfortress.event.PartyScaling.extra(this))) {
            for (int tries = 0; tries < 8; tries++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                BlockPos at = BlockPos.containing(getX() + Math.cos(a) * 4.0D, getY(), getZ() + Math.sin(a) * 4.0D);
                RimeweaverEntity m = FFEntities.RIMEWEAVER.get().create(s);
                if (m == null) {
                    break;
                }
                m.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                if (!s.noCollision(m)) {
                    continue;
                }
                m.finalizeSpawn(s, s.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
                m.setTarget(other);
                s.addFreshEntity(m);
                break;
            }
        }
    }

    boolean requiemDue() {
        return !requiemDone && getHealth() < getMaxHealth() * 0.5F;
    }

    /** Kept for the candles of the old rite still standing in older worlds. */
    void candleOut(FrostCandleEntity candle) {
    }

    // ------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("Requiem", requiemDone);
        tag.putInt("Litanies", litanies);
        if (home != null) {
            tag.putIntArray("Home", new int[]{home.getX(), home.getY(), home.getZ()});
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) {
            entityData.set(DORMANT, tag.getBoolean("Dormant"));
        }
        requiemDone = tag.getBoolean("Requiem");
        litanies = tag.getInt("Litanies");
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
    }

    // ------------------------------------------------------------------ looks and sounds
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            // into the entrance at once (its first frame is the sleep it wakes from): its clip runs on the very
            // ticks the scene's cuts and sounds are timed to (BossScenes) - and out of it as from any other
            state.getController().setTransitionLength(getAttackState() == INTRO ? 0 : 4);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(PRAY);
            }
            switch (getAttackState()) {
                case HOOK: return state.setAndContinue(HOOK_ANIM);
                case PAGES: return state.setAndContinue(PAGES_ANIM);
                case BELL: return state.setAndContinue(BELL_ANIM);
                case LITANY: return state.setAndContinue(READ);
                case AMEN: return state.setAndContinue(AMEN_ANIM);
                case SPENT: return state.setAndContinue(SPENT_ANIM);
                case REQUIEM: return state.setAndContinue(REQUIEM_ANIM);
                case VEIL: return state.setAndContinue(VEIL_ANIM);
                case AWAKEN: return state.setAndContinue(AWAKEN_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    /** Her body lies until her death scene goes to black (BossCutscenes: the clip, a breath, the card). */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** Her death's sounds, on its clip's beats: the spell that held her up breaking, the knees, the book shut on the
     *  floor ("the last page... is turned"), the halo shattering behind her (its shards flying), the fall. */
    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_DROP) {
            playSound(FFSounds.CRYSTAL_CHIME.get(), 1.2F, 0.5F);
        }
        if (t == DEATH_KNEEL) {
            playSound(FFSounds.ICE_IMPACT.get(), 1.0F, 0.8F);
        }
        if (t == DEATH_BOOK + DEATH_BOOK_FALL) {
            playSound(FFSounds.RIME_PRIESTESS_BOOK_SHUT.get(), 1.6F, 0.8F);
        }
        if (t == DEATH_HALO + DEATH_HALO_FALL) {
            playSound(FFSounds.ICE_SHATTER.get(), 1.3F, 1.25F);
            Vec3 at = position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(-0.5D));
            s.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.15D, at.z, 24, 0.25D, 0.05D, 0.25D, 0.12D);
        }
        if (t == DEATH_FALL) {
            playSound(FFSounds.ICE_IMPACT.get(), 0.9F, 0.6F);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : FFSounds.RIMEWEAVER_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.RIMEWEAVER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.RIMEWEAVER_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    // ------------------------------------------------------------------ the fight
    static class PriestessGoal extends Goal {
        private final RimePriestessEntity mob;
        private int cooldown;
        private final int[] perAttack = new int[10];

        PriestessGoal(RimePriestessEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant() && mob.getTarget() != null && mob.getTarget().isAlive()
                    && mob.getAttackState() != AWAKEN && mob.getAttackState() != INTRO;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            int st = mob.getAttackState();
            if (st != AWAKEN && st != INTRO && st != LITANY && st != AMEN && st != SPENT) {
                mob.setAttackState(0);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            for (int i = 0; i < perAttack.length; i++) {
                if (perAttack[i] > 0) {
                    perAttack[i]--;
                }
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();
            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (dist > 8.0D) {
                    mob.getNavigation().moveTo(target, 1.0D);
                } else if (dist < 3.0D) {
                    mob.getNavigation().stop();
                } else {
                    mob.getNavigation().moveTo(target, 0.6D);
                }
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                boolean sighted = mob.hasLineOfSight(target);
                if (mob.litanyDue()) {
                    mob.beginLitany();
                    return;
                }
                int chosen = 0;
                if (mob.requiemDue()) {
                    chosen = REQUIEM;
                } else if (mob.mirrorsDue() && perAttack[VEIL] <= 0) {
                    perAttack[VEIL] = 520;
                    mob.beginMirrors();
                    return;
                } else if (dist < 4.2D && perAttack[HOOK] <= 0) {
                    chosen = HOOK;
                } else if (dist < 4.5D && mob.blinkCooldown <= 0) {
                    mob.beginBlink();                           // crowded, and the crook spent: away
                    return;
                } else if (dist < 18.0D && sighted && perAttack[PAGES] <= 0) {
                    chosen = PAGES;
                } else if (dist >= 3.0D && perAttack[BELL] <= 0) {
                    chosen = BELL;
                }
                if (chosen != 0) {
                    perAttack[chosen] = switch (chosen) {
                        case PAGES -> 130;
                        case BELL -> 150;
                        case HOOK -> 40;
                        default -> 0;
                    };
                    mob.setAttackState(chosen);
                }
                return;
            }
            mob.getNavigation().stop();
            if (state != LITANY && state != AMEN && state != SPENT) {
                mob.getLookControl().setLookAt(target, 14.0F, 14.0F);
            }
            int t = mob.attackTicks;
            // every blow heard coming: the wind-up in sound, then the strike
            switch (state) {
                case HOOK -> {
                    if (t == 3) {
                        mob.playSound(SoundEvents.ARMOR_EQUIP_CHAIN, 1.3F, 0.7F);         // the crook drawn back
                    }
                    if (t == 10) {
                        mob.hook();
                    }
                    if (t > 18) {
                        finish(10);
                    }
                }
                case PAGES -> {
                    if (t == 2 || t == 6) {
                        mob.playSound(SoundEvents.BOOK_PAGE_TURN, 1.6F, t == 2 ? 1.0F : 1.3F);   // riffled
                    }
                    if (t == 10) {
                        mob.pages(target);
                    }
                    if (t > 20) {
                        finish(12);
                    }
                }
                case BELL -> {
                    if (t == 6) {
                        mob.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 0.6F);       // the crook raised
                    }
                    if (t == 20) {
                        mob.bell(target);
                    }
                    if (t > 30) {
                        finish(12);
                    }
                }
                case REQUIEM -> {
                    if (t == 6) {
                        mob.playSound(SoundEvents.BELL_RESONATE, 2.0F, 0.4F);
                        // her line in the fight, once, as the stone sisters are called (BossVoice): "Witness the power
                        // of true devotion."
                        BossVoice.fightLine(mob, "rime_priestess");
                    }
                    if (t == 10) {
                        mob.playSound(FFSounds.RIMEWEAVER_IDLE.get(), 1.6F, 0.4F);        // the dirge begun
                    }
                    if (t == 24) {
                        mob.requiem(target);
                    }
                    if (t > 40) {
                        finish(20);
                    }
                }
                case VEIL -> {
                    if (t == 4) {
                        mob.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 1.4F);   // the veil drawn
                    }
                    if (t == 12) {
                        mob.veil();
                    }
                    if (t > 20) {
                        if (mob.litanyPending) {
                            mob.startReading();
                        } else {
                            finish(10);
                        }
                    }
                }
                case LITANY -> {
                    // the rite runs itself (aiStep); she only reads
                }
                case AMEN, SPENT -> {
                    // the rite runs these out itself (aiStep)
                }
                default -> finish(10);
            }
        }

        private void finish(int rest) {
            mob.setAttackState(0);
            cooldown = rest;
        }
    }
}
