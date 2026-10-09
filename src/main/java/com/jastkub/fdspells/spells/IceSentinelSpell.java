package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.IceSentinelEntity;
import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * LODOWY STRAŻNIK - a sentinel of ice rises before the caster: a tower shield in front, a sword. It keeps by its
 * summoner, goes for what the summoner fights, and takes blows on the shield. One at a time: a new one replaces it.
 */
public class IceSentinelSpell extends FrostSpell {

    public IceSentinelSpell() {
        super("ice_sentinel", SpellRarity.EPIC, 5, 60.0D, CastType.LONG, 90, 10, 6, 1, 30);
    }

    public static int ticks(int level) {
        return 800 + 100 * level;
    }

    public static float health(int level) {
        return 30.0F + 6.0F * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("hp", health(level)), line("damage", power(level, caster)), seconds(ticks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_LONG_CAST;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_LONG_CAST_FINISH;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        for (IceSentinelEntity old : world.getEntitiesOfClass(IceSentinelEntity.class,
                caster.getBoundingBox().inflate(48.0D), s -> s.getSummoner() == caster)) {
            old.onUnSummon();
        }
        Vec3 look = caster.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 at = caster.position().add(look.scale(2.0D));
        IceSentinelEntity s = new IceSentinelEntity(FDSRegistry.ICE_SENTINEL.get(), world);
        s.moveTo(at.x, caster.getY(), at.z, caster.getYRot(), 0.0F);
        s.summon(caster, health(level), power(level, caster), ticks(level));
        world.addFreshEntity(s);
        super.onCast(world, level, caster, source, data);
    }
}
