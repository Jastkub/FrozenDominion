package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.event.FFRoll;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A SWINGING AXE over the Rift's way: a double-bitted blade of frosted iron on a rod from the vault, swinging across
 * the way over a gap between two pillars, so a jump has to be timed between its passes. Whoever its blade meets is cut
 * and thrown aside along its swing - off the pillars, into the trench. A roll goes through it (FFRoll).
 *
 * <p>Its swing is a pure function of the world's clock - {@link #angle} - so the server (which strikes) and the client
 * (which draws, PendulumAxeRenderer) agree without a word between them. NBT (from the citadel's generator): Length
 * (blocks from its anchor to the blade's middle), Amplitude (degrees either side), Period (ticks of a whole swing),
 * Phase (ticks), Axis ("z": it swings along z, "x": along x).
 */
public class PendulumAxeBlockEntity extends BlockEntity {

    /** The blade's half-reach along its swing, and how close it must come to a body to strike it. (A little less of
     *  both) */
    public static final double BLADE_HALF = 0.7D, TOUCH = 0.12D;
    private static final float DAMAGE = 4.0F;
    private static final double THROW = 1.05D, LIFT = 0.32D;
    private static final int AGAIN = 16;

    private float length = 6.0F;
    private float amplitude = 30.0F;
    private int period = 56;
    private int phase;
    private boolean alongX;
    private final Map<UUID, Long> struck = new HashMap<>();

    public PendulumAxeBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.PENDULUM_AXE.get(), pos, state);
    }

    public float length() {
        return length;
    }

    public boolean alongX() {
        return alongX;
    }

    /** Its angle (radians) at a moment of the world's clock: positive towards +z (or +x). */
    public double angle(double time) {
        return Math.toRadians(amplitude) * Math.sin(2.0D * Math.PI * (time + phase) / period);
    }

    /** Its angular speed's sign at a moment (+1 swinging towards +, -1 back). */
    private double swingSign(double time) {
        return Math.signum(Math.cos(2.0D * Math.PI * (time + phase) / period));
    }

    /** The blade's middle in the world, at angle `a`. */
    public Vec3 blade(double a) {
        Vec3 anchor = Vec3.atCenterOf(worldPosition);
        double out = Math.sin(a) * length, down = Math.cos(a) * length;
        return alongX ? anchor.add(out, -down, 0.0D) : anchor.add(0.0D, -down, out);
    }

    /** The way its edge points at angle `a` (the swing's tangent). */
    private Vec3 edge(double a) {
        return alongX ? new Vec3(Math.cos(a), Math.sin(a), 0.0D) : new Vec3(0.0D, Math.sin(a), Math.cos(a));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PendulumAxeBlockEntity axe) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        long now = level.getGameTime();
        double a = axe.angle(now), before = axe.angle(now - 1);
        Vec3 mid = axe.blade(a);
        // through the bottom of its swing: the whoosh
        if (Math.signum(a) != Math.signum(before) && sl.getNearestPlayer(mid.x, mid.y, mid.z, 24.0D, false) != null) {
            sl.playSound(null, mid.x, mid.y, mid.z, FFSounds.GREAT_SWING.get(), SoundSource.BLOCKS, 0.9F,
                    0.62F + sl.random.nextFloat() * 0.08F);
        }
        Vec3 edge = axe.edge(a);
        double sign = axe.swingSign(now);
        AABB near = new AABB(mid, mid).inflate(BLADE_HALF + 1.0D);
        for (Player p : level.getEntitiesOfClass(Player.class, near, p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            Long last = axe.struck.get(p.getUUID());
            if (last != null && now - last < AGAIN) {
                continue;
            }
            AABB body = p.getBoundingBox().inflate(TOUCH);
            boolean hit = false;
            for (int k = -4; k <= 4 && !hit; k++) {
                hit = body.contains(mid.add(edge.scale(BLADE_HALF * k / 4.0D)));
            }
            if (!hit) {
                continue;
            }
            axe.struck.put(p.getUUID(), now);
            if (FFRoll.rolling(p)) {
                continue;                                         // rolled through the stroke
            }
            p.hurt(level.damageSources().generic(), DAMAGE);
            Vec3 push = new Vec3(edge.x, 0.0D, edge.z);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(THROW * sign * com.jastkub.frozenfortress.registry.FFEnchantments.steady(p));
                p.setDeltaMovement(push.x, LIFT, push.z);
                p.hurtMarked = true;
            }
            sl.playSound(null, p.getX(), p.getY() + 1.0D, p.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Length")) {
            length = tag.getFloat("Length");
        }
        if (tag.contains("Amplitude")) {
            amplitude = tag.getFloat("Amplitude");
        }
        if (tag.contains("Period")) {
            period = Math.max(10, tag.getInt("Period"));
        }
        phase = tag.getInt("Phase");
        alongX = "x".equals(tag.getString("Axis"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Length", length);
        tag.putFloat("Amplitude", amplitude);
        tag.putInt("Period", period);
        tag.putInt("Phase", phase);
        tag.putString("Axis", alongX ? "x" : "z");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** It reaches far below its block: drawn whenever any of its swing is in view. */
    /** (1.21) the renderer asks for this: its getRenderBoundingBox(be). */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(length * 0.8D + 1.5D, 0.0D, length * 0.8D + 1.5D)
                .expandTowards(0.0D, -(length + 1.5D), 0.0D);
    }
}
