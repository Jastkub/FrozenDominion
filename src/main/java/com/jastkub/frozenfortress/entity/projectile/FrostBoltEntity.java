package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A shard of hardened rime hurled by Rimeweavers and Velkhar.
 * Flies flat and fast, bites with Frostbite on impact.
 */
public class FrostBoltEntity extends AbstractHurtingProjectile
        implements software.bernie.geckolib.animatable.GeoEntity {

    private float damage = 6.9F;

    /**
     * How big this one is drawn, where 1 is the default splinter.
     *
     * <p>Per entity rather than per renderer because three different things
     * fire these: the heart barrage wants a few large slow crystals, and the
     * Rimeweaver and the player's tool want the small quick ones they have
     * always had. One renderer, three sizes.
     */
    /**
     * WHOSE PROJECTILE THIS IS.
     *
     * <p>Velkhar's crystal and the colossus's shard are the same entity doing
     * the same job, and they must not look the same doing it. His is a long
     * thin needle thrown by something precise; its is a short thick slab
     * broken off a floor by something with no hands worth the name. Firing
     * his needles out of the golem makes the golem a second Velkhar with a
     * bigger hitbox.
     *
     * <p>A flag rather than a second entity type. Everything about the two is
     * identical - the flight, the arc, the steering, the trail, the damage
     * plumbing - and the only difference is which geometry and which sheet get
     * drawn, which the renderer can answer per entity. A whole parallel class,
     * registry entry, renderer and spawn packet to change two resource paths
     * is a lot of surface for one decision.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HEAVY =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    FrostBoltEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SIZE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    FrostBoltEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    public void setSize(float size) {
        entityData.set(SIZE, size);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    /**
     * ITS THRUST, a vector of its own (what AbstractHurtingProjectile kept in xPower/yPower/zPower up to 1.20).
     *
     * <p>Since 1.21 the vanilla class keeps only a magnitude (accelerationPower) and pushes along whichever way the
     * bolt is already moving - which leaves nowhere to put an arc that pulls down while the bolt flies level, or a
     * steer that pulls sideways. So the bolt keeps the old vector here and adds it itself, the way the old class did:
     * every tick the motion becomes (motion + thrust) x inertia. The vanilla push is set to nothing.
     */
    public double xPower;
    public double yPower;
    public double zPower;

    {
        this.accelerationPower = 0.0D;
    }

    public FrostBoltEntity(EntityType<? extends FrostBoltEntity> type, Level level) {
        super(type, level);
    }

    public FrostBoltEntity(Level level, LivingEntity shooter, double dx, double dy, double dz) {
        this(FFEntities.FROST_BOLT.get(), level, shooter, dx, dy, dz);
    }

    /** One of another shape, flying the same way (the Rimeweaver's shuttle). */
    protected FrostBoltEntity(EntityType<? extends FrostBoltEntity> type, Level level, LivingEntity shooter,
                              double dx, double dy, double dz) {
        // what AbstractHurtingProjectile(type, shooter, dx, dy, dz, level) did up to 1.20: placed at the shooter's
        // feet, thrust 0.1 along the aim, standing still until the first tick pushes it
        super(type, level);
        moveTo(shooter.getX(), shooter.getY(), shooter.getZ(), getYRot(), getXRot());
        reapplyPosition();
        double d0 = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d0 != 0.0D) {
            this.xPower = dx / d0 * 0.1D;
            this.yPower = dy / d0 * 0.1D;
            this.zPower = dz / d0 * 0.1D;
        }
        setOwner(shooter);
        setRot(shooter.getYRot(), shooter.getXRot());
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /**
     * Scales how fast it flies, where 1 is the speed it was built with.
     *
     * <p>AbstractHurtingProjectile keeps its thrust in xPower/yPower/zPower and
     * re-applies it every tick, so slowing one down means scaling those rather
     * than the delta - set the delta alone and the next tick puts it back.
     */
    /**
     * How much it sags per tick, in blocks. Zero is a flat shot.
     *
     * <p>Worth having for the heart barrage above all: with a real trail
     * behind each crystal, an arc draws a curve in the air instead of a line,
     * and a dozen of them on slightly different curves is a fountain. A flat
     * volley with trails is a bundle of parallel streaks, which is the look
     * the barrage already had.
     */
    private double arc;
    /**
     * A LITTLE STEERING, and the emphasis is on little.
     *
     * <p>These are lobbed, slow and big - which is what makes the barrage read
     * as a chest emptying itself rather than as a machine gun, and also what
     * makes every single one of them miss. A parabola aimed at where somebody
     * WAS, travelling at a third of a block a tick, arrives about two seconds
     * later at a place they left immediately; walking sideways beats the whole
     * attack.
     *
     * <p>So they correct, gently, for the first part of the flight. The number
     * matters more than it looks: hard steering turns a lobbed crystal into a
     * seeker, and a seeker there would delete the arc - the thing bends toward
     * the target rather than falling toward it and the fountain becomes a
     * volley of darts. At this strength it closes maybe a block of lead over
     * two seconds, which is the difference between "impossible to hit anyone"
     * and "you have to keep moving".
     */
    private static final double HOME = 0.045D;
    private java.util.UUID homing;
    private int steerLeft;

    /** Steer gently toward this one for `ticks`, then give up. */
    public void home(net.minecraft.world.entity.LivingEntity mark, int ticks) {
        this.homing = mark == null ? null : mark.getUUID();
        this.steerLeft = ticks;
    }

    public void setArc(double dropPerTick) {
        this.arc = dropPerTick;
    }

    public void setSpeed(double scale) {
        this.xPower *= scale;
        this.yPower *= scale;
        this.zPower *= scale;
        setDeltaMovement(getDeltaMovement().scale(scale));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity living) {
            LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
            living.hurt(damageSources().mobProjectile(this, owner), damage);
            living.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), owner);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            ServerLevel server = (ServerLevel) level();
            if (blast > 0.0F) {
                burst(server);
            } else {
                server.sendParticles(FFParticles.ICE_SHARD.get(),
                        getX(), getY(), getZ(), 12, 0.2D, 0.2D, 0.2D, 0.12D);
            }
            playSound(FFSounds.FROST_BOLT_HIT.get(), 1.0F, 1.0F + random.nextFloat() * 0.3F);
            discard();
        }
    }

    /**
     * IT OPENS A CIRCLE. The answer becomes the floor, not the line.
     *
     * <p>Damage falls off toward the rim rather than being flat: standing at
     * the edge of a blast should cost something and should not cost what
     * standing in the middle of it costs, or there is no reason to have
     * moved at all.
     */
    private void burst(ServerLevel server) {
        server.sendParticles(FFParticles.SHOCKWAVE.get(),
                getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // ---- A THIRD OF THE MOTES IT USED TO THROW. What sold this as an
        //      explosion was a hundred particles, and a hundred particles is
        //      a puff of smoke whatever colour it is. The burst is GEOMETRY
        //      now - see below - and the particles are down to what a real
        //      impact throws off the edges of it.
        server.sendParticles(FFParticles.ICE_SHARD.get(),
                getX(), getY() + 0.3D, getZ(), 24, blast * 0.5D, 0.4D, blast * 0.5D, 0.4D);
        server.sendParticles(FFParticles.FROST_SWIRL.get(),
                getX(), getY() + 0.5D, getZ(), 12, blast * 0.4D, 0.5D, blast * 0.4D, 0.18D);
        playSound(FFSounds.FROST_RELEASE.get(), 3.0F, 0.72F);
        net.minecraft.world.entity.Entity owner = getOwner();
        for (net.minecraft.world.entity.LivingEntity victim
                : level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        getBoundingBox().inflate(blast),
                        e -> e.isAlive() && e != owner
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(getOwner(), e))) {
            double d = victim.position().distanceTo(position());
            if (d > blast) {
                continue;
            }
            float falloff = (float) (1.0D - 0.55D * (d / blast));
            victim.hurt(owner instanceof net.minecraft.world.entity.LivingEntity le
                    ? damageSources().mobAttack(le) : damageSources().magic(),
                    this.damage * falloff);
            victim.setTicksFrozen(Math.min(victim.getTicksFrozen() + 60, 260));
        }
        // ---- AND THE FLOOR KEEPS IT.
        //
        // Only the heavy ones, because only the colossus can drain it back:
        // frosting the room off Velkhar's barrage would cover the arena in
        // fuel for a mechanic he does not have, and a field of white that
        // never goes anywhere is scenery, not pressure.
        if (isHeavy()) {
            // ================================================================
            // THE ICE BURST. A ring of real spikes, thrown outward.
            //
            // An explosion made of particles is the same event every time and
            // it is over before the eye has finished reading it - which is why
            // the bombs never felt like they landed. This is a RING OF SOLIDS
            // erupting out of the floor, with the delay on each one scaled by
            // how far out it is, so the eruption travels: the middle goes
            // first and the rim a quarter of a second later. That travel is
            // the entire read, and no particle count buys it.
            //
            // Two rings, offset half a step against each other, because a
            // single ring of eight is a clock face. The inner ring is the
            // impact and the outer is what it pushed.
            //
            // Cheap on purpose: fourteen entities that live about a second,
            // against the hundred-odd particles they replace.
            for (int ring = 0; ring < 2; ring++) {
                int n = ring == 0 ? 6 : 8;
                double r = blast * (ring == 0 ? 0.42D : 0.92D);
                double spin = ring * (Math.PI / n) + random.nextDouble() * 0.4D;
                for (int i = 0; i < n; i++) {
                    double a = spin + Math.PI * 2.0D * i / n;
                    double sx = getX() + Math.cos(a) * r;
                    double sz = getZ() + Math.sin(a) * r;
                    // ---- ON THE FLOOR, NOT AT THE HEIGHT IT EXPLODED.
                    //
                    // These were spawned at the bolt's own Y, which is right
                    // only when it lands flat. A bomb that bursts against a
                    // body, or against a wall, or on the lip of a step is
                    // several blocks up - and a ring of ice spikes standing in
                    // mid air is the single most broken-looking thing this
                    // fight can produce. It is also what the report showed.
                    //
                    // So each one looks DOWN for something to stand on and is
                    // simply not spawned if there is nothing within six
                    // blocks. A missing spike is invisible; a floating one is
                    // not.
                    Double sy = groundUnder(server, sx, getY() + 1.0D, sz);
                    if (sy == null) {
                        continue;
                    }
                    new IceSpikeSpawner(server, this).at(
                            sx, sy, sz, this.damage * 0.22F, ring * 4 + i % 2);
                }
            }
            // A PATCH, NOT A FIELD. At 1.15x the blast three bombs frosted
            // twenty blocks of floor between them, which stops reading as
            // "the ground froze where that landed" and starts reading as a
            // bug - which is exactly how it was reported. Two thirds of the
            // blast, and RimeHandler thins it toward the rim on top of that.
            com.jastkub.frozenfortress.event.RimeHandler.freeze(
                    server, position(), blast * 0.66D);
        }
    }

    /**
     * The top of whatever is under a point, or null if nothing is.
     *
     * <p>Six blocks of search, which is enough for a step, a lip or a body
     * without being enough to plant a spike on a different floor.
     */
    private static Double groundUnder(net.minecraft.server.level.ServerLevel level,
                                      double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos at =
                new net.minecraft.core.BlockPos.MutableBlockPos(
                        net.minecraft.util.Mth.floor(x),
                        net.minecraft.util.Mth.floor(y),
                        net.minecraft.util.Mth.floor(z));
        for (int i = 0; i < 6; i++) {
            net.minecraft.core.BlockPos below = at.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return (double) at.getY();
            }
            at.move(0, -1, 0);
        }
        return null;
    }

    /**
     * One spike, placed. A named thing rather than a six-argument constructor
     * call inside two nested loops - the loops are about the SHAPE of the
     * burst and should read that way.
     */
    private record IceSpikeSpawner(net.minecraft.server.level.ServerLevel level,
                                   FrostBoltEntity from) {
        void at(double x, double y, double z, float damage, int delay) {
            com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity spike =
                    new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                            level,
                            from.getOwner() instanceof net.minecraft.world.entity.LivingEntity le
                                    ? le : null,
                            x, y, z, damage, delay);
            level.addFreshEntity(spike);
        }
    }

    @Override
    public void tick() {
        // THE CORRECTION GOES ON THE THRUST, for the same reason the arc does:
        // AbstractHurtingProjectile rebuilds its motion from xPower/yPower/
        // zPower every tick, so anything written to the delta alone is gone
        // before it moves. The vertical component is deliberately left out of
        // the steer - the arc owns Y, and a bolt allowed to correct upward
        // would flatten its own parabola out over the flight.
        if (steerLeft > 0 && homing != null
                && level() instanceof ServerLevel sl
                && sl.getEntity(homing) instanceof net.minecraft.world.entity.LivingEntity mark
                && mark.isAlive()) {
            steerLeft--;
            double speed = Math.sqrt(xPower * xPower + zPower * zPower);
            if (speed > 1.0E-5D) {
                double dx = mark.getX() - getX();
                double dz = mark.getZ() - getZ();
                double flat = Math.sqrt(dx * dx + dz * dz);
                if (flat > 1.0E-4D) {
                    double wx = dx / flat * speed;
                    double wz = dz / flat * speed;
                    double nx = xPower * (1.0D - HOME) + wx * HOME;
                    double nz = zPower * (1.0D - HOME) + wz * HOME;
                    double norm = Math.sqrt(nx * nx + nz * nz);
                    if (norm > 1.0E-5D) {
                        this.xPower = nx / norm * speed;
                        this.zPower = nz / norm * speed;
                    }
                }
            }
        }
        if (arc != 0.0D) {
            // applied to the thrust, not to the delta: AbstractHurtingProjectile
            // re-derives its motion from xPower/yPower/zPower every tick, so a
            // change to the delta alone is gone by the next one
            this.yPower -= arc;
            sinkOntoTarget();
        }
        super.tick();
        // the thrust (see xPower): the vanilla tick has just left the motion at motion x inertia with nothing added,
        // so adding thrust x the same inertia gives what the old tick did - (motion + thrust) x inertia
        if (!isRemoved()) {
            double f = isInWater() ? getLiquidInertia() : getInertia();
            setDeltaMovement(getDeltaMovement().add(xPower * f, yPower * f, zPower * f));
        }
        // The tail is GEOMETRY now - see ProjectileTrail, drawn by
        // IceCrystalRenderer. Particles laid along the step were the closest a
        // particle can get to a streak, and it was still a dotted line.
        if (tickCount > 100) {
            discard();
        }
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return FFParticles.FROST_SWIRL.get();
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    // ---- THE THRUST KEPT AND SENT the way the old class did (see xPower): saved as "power", and the spawn packet
    //      carries it in its motion slot, the client taking its direction at 0.1 - and its motion from the server.
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("power", newDoubleList(xPower, yPower, zPower));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.accelerationPower = 0.0D;
        if (tag.contains("power", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("power", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (list.size() == 3) {
                this.xPower = list.getDouble(0);
                this.yPower = list.getDouble(1);
                this.zPower = list.getDouble(2);
            }
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        net.minecraft.world.entity.Entity owner = getOwner();
        Vec3 at = serverEntity.getPositionBase();
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(getId(), getUUID(), at.x, at.y, at.z,
                serverEntity.getLastSentXRot(), serverEntity.getLastSentYRot(), getType(),
                owner == null ? 0 : owner.getId(), new Vec3(xPower, yPower, zPower), 0.0D);
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // (since 1.21 super takes that slot for the motion; here it is the thrust, and the bolt starts still)
        setDeltaMovement(Vec3.ZERO);
        double x = packet.getXa(), y = packet.getYa(), z = packet.getZa();
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d != 0.0D) {
            this.xPower = x / d * 0.1D;
            this.yPower = y / d * 0.1D;
            this.zPower = z / d * 0.1D;
        }
    }

    /**
     * (1.21) Turned back by something that deflects projectiles (a breeze): the thrust turns with it, as it did when
     * the old class was struck, and keeps its strength; the vanilla push stays at nothing.
     */
    @Override
    protected void onDeflection(@javax.annotation.Nullable net.minecraft.world.entity.Entity deflector, boolean byAttack) {
        super.onDeflection(deflector, byAttack);
        this.accelerationPower = 0.0D;
        Vec3 v = getDeltaMovement();
        double power = Math.sqrt(xPower * xPower + yPower * yPower + zPower * zPower);
        if (v.lengthSqr() > 1.0E-8D) {
            Vec3 d = v.normalize().scale(power);
            this.xPower = d.x;
            this.yPower = d.y;
            this.zPower = d.z;
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    /**
     * NOT A THING ANYTHING ELSE CAN HIT.
     *
     * <p>AbstractHurtingProjectile is pickable by default, because that is how
     * a ghast's fireball can be batted back. This one cannot be batted back -
     * hurt() above refuses every point of damage - so the flag bought nothing
     * and cost a crash: Projectile.canHitEntity accepts anything pickable, so
     * other mods' projectiles were selecting a crystal in mid-air as the thing
     * they had just hit. LegendaryMonsters' annihilation bomb then cast it
     * straight to LivingEntity without an instanceof and took the server down
     * with it.
     *
     * <p>That cast is their bug and it will fire on any pickable non-living
     * entity, a vanilla fireball included. This is simply not leaving one of
     * mine lying in the road. The orb, the prison and the ward pillar stay
     * pickable on purpose - being breakable is what they are for.
     */
    @Override
    public boolean isPickable() {
        return false;
    }

    // ---- IT HAS A MODEL NOW.
    //
    // This was registered with NothingRenderer, which draws exactly what its
    // name says. Every attack that fires one - the ice barrage above all - was
    // therefore five invisible projectiles and their particle trails, and the
    // report was the obvious one: "the ice barrage is just particles". It was.
    private static final software.bernie.geckolib.animation.RawAnimation STILL =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.still");
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(
            software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(
                this, "still", 0, state -> state.setAndContinue(STILL)));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /**
     * The circle this one opens where it lands, or 0 for a plain hit.
     *
     * <p>A bomb and a dart are the same object travelling; they differ only in
     * what happens when they stop. Server-side only - nothing about the blast
     * is drawn from the entity, so the client has no use for the number.
     */
    private float blast;

    /** Make this one burst on impact rather than simply landing. */
    public void setBlast(float radius) {
        this.blast = radius;
    }

    /** Draw this one as the colossus's shard instead of Velkhar's crystal. */
    public void setHeavy(boolean heavy) {
        entityData.set(HEAVY, heavy);
    }

    public boolean isHeavy() {
        return entityData.get(HEAVY);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(HEAVY, false);
        builder.define(SIZE, 1.0F);
    }

    /**
     * KEEPS THE ARC HONEST WHEN THE TARGET MOVES.
     *
     * <p>The steering above corrects X and Z and deliberately leaves Y to the
     * arc. That is right while the target holds still and wrong the moment it
     * does not: the horizontal line goes on tracking a player who is closing,
     * while the vertical profile stays frozen at whatever was true when the
     * crystal left his chest. The bolt therefore arrives EARLY in its own
     * parabola - still climbing, or barely over the top - and sails above
     * their head. Phase three tells the player to close the distance, so this
     * fired on exactly the behaviour the fight is asking for.
     *
     * <p>The fix re-solves the throw rather than steering it. Each tick it
     * works out how many ticks are left from the horizontal motion, asks where
     * the current parabola would put it at that moment, and only if that is
     * over their head does it replace the vertical thrust with the one that
     * lands. DOWNWARD ONLY: a bolt that is falling short keeps its arc, so
     * this can turn an overshoot into a hit but can never flatten the fountain
     * into a volley of darts, which is the thing the arc exists for.
     *
     * <p>The sums are exact for AbstractHurtingProjectile's integration, which
     * steps the position BEFORE the thrust - hence n(n-1)/2 rather than the
     * n&sup2;/2 of continuous motion, and the (n-1)n(n+1)/6 term for the arc
     * that is still to be applied over those remaining ticks. Approximating
     * either one is what made earlier attempts drive the crystal into the
     * floor a few blocks short.
     */
    private void sinkOntoTarget() {
        if (steerLeft <= 0 || homing == null
                || !(level() instanceof ServerLevel sl)
                || !(sl.getEntity(homing) instanceof net.minecraft.world.entity.LivingEntity mark)
                || !mark.isAlive()) {
            return;
        }
        double dx = mark.getX() - getX();
        double dz = mark.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.3D) {
            return;
        }
        net.minecraft.world.phys.Vec3 v = getDeltaMovement();
        double vFlat = Math.sqrt(v.x * v.x + v.z * v.z);
        double aFlat = Math.sqrt(xPower * xPower + zPower * zPower);
        if (aFlat < 1.0E-9D) {
            return;
        }
        // flat = n*vFlat + aFlat*n*(n-1)/2, solved for n
        double qa = aFlat / 2.0D;
        double qb = vFlat - aFlat / 2.0D;
        double disc = qb * qb + 4.0D * qa * flat;
        if (disc <= 0.0D) {
            return;
        }
        double n = (-qb + Math.sqrt(disc)) / (2.0D * qa);
        // ---- NOT IN THE LAST HALF SECOND. The solution carries a 1/n&sup2;,
        //      so as the crystal arrives the correction it asks for runs away
        //      to infinity and one late tick can fling it at the floor. By
        //      then it is committed anyway - that is the point of the attack.
        if (n < 6.0D) {
            return;
        }
        double dy = mark.getY() + 0.9D - getY();
        double sag = arc * (n - 1.0D) * n * (n + 1.0D) / 6.0D;
        double arrive = n * v.y + yPower * n * (n - 1.0D) / 2.0D - sag;
        if (arrive <= dy + 0.9D) {
            return;
        }
        double need = 2.0D * (dy - n * v.y + sag) / (n * (n - 1.0D));
        if (need < yPower) {
            this.yPower = need;
        }
    }
}
