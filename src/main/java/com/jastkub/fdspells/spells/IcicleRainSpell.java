package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.IcicleRainEntity;
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
 * SOPLE ZE SKLEPIENIA - where the caster looks, a ring of shadows darkens the ground, and over each a little later an
 * icicle falls out of the air and bursts. One after another, so whoever stands there has to move.
 */
public class IcicleRainSpell extends FrostSpell {

    public IcicleRainSpell() {
        super("icicle_rain", SpellRarity.RARE, 6, 22.0D, CastType.LONG, 55, 6, 7, 1, 20);
    }

    public static int count(int level) {
        return 5 + level;
    }

    public static float radius(int level) {
        return 3.0F + 0.3F * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("projectile_count", count(level)),
                line("radius", radius(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_OVERHEAD;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_LONG_CAST_FINISH;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        Vec3 at = groundTarget(world, caster, 24.0D);
        IcicleRainEntity rain = new IcicleRainEntity(FDSRegistry.ICICLE_RAIN_CLOUD.get(), world);
        rain.moveTo(at.x, at.y, at.z);
        rain.setup(caster, power(level, caster), level, 4 * count(level) + 40);
        rain.start(this, count(level), radius(level));
        world.addFreshEntity(rain);
        super.onCast(world, level, caster, source, data);
    }
}
