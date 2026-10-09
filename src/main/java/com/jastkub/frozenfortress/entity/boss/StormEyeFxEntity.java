package com.jastkub.frozenfortress.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * What every body of the Eye of the Storm shares: it is geometry and nothing else - no physics, no collision, never
 * saved (the arena raises the lasting ones again), drawn far outside its own little box (so never frustum-culled),
 * and nobody can hit it.
 */
public abstract class StormEyeFxEntity extends Entity {

    protected StormEyeFxEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.blocksBuilding = false;
    }

    /** The king this belongs to, by entity id (synced by the subclasses that draw a line to him). */
    @Nullable
    protected static VelkharEntity kingById(Level level, int id) {
        return id >= 0 && level.getEntity(id) instanceof VelkharEntity v ? v : null;
    }

    /** The king, anything wearing his face, his court, anything of the storm, and anything any of them threw. */
    public static boolean kingsWork(@Nullable Entity who) {
        if (who == null) {
            return false;
        }
        if (who instanceof VelkharEntity || who instanceof VelkharCloneEntity || who instanceof StormEyeMirrorEntity
                || who instanceof StormEyeAnchorEntity || who instanceof StormEyeFxEntity
                || who instanceof com.jastkub.frozenfortress.entity.FrostServantEntity) {
            return true;
        }
        if (who instanceof Projectile p) {
            return kingsWork(p.getOwner());
        }
        return false;
    }

    /** Somebody the storm's attacks are for: alive, not of the king's side, not a creative or spectating player. */
    public static boolean foe(LivingEntity e) {
        return e.isAlive() && !kingsWork(e) && !com.jastkub.frozenfortress.entity.FFAllies.ofTheKing(e)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** A blow of the storm, through the king's own damage funnel when he is there to own it. */
    protected static void stormHit(ServerLevel level, @Nullable VelkharEntity king, LivingEntity victim, float raw) {
        if (king != null && king.isAlive()) {
            king.stormStrikeRaw(victim, raw);
        } else {
            victim.hurt(level.damageSources().magic(), raw * 0.4F);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0D * 160.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
