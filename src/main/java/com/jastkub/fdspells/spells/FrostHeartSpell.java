package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.FrostHeartEntity;
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
 * SERCE MROZU - a heart of ice set down where the caster looks. It beats, and every beat sends a ring of frost out
 * over the ground that hurts and slows what it passes. It beats until its time runs out or somebody breaks it.
 */
public class FrostHeartSpell extends FrostSpell {

    public FrostHeartSpell() {
        super("frost_heart", SpellRarity.RARE, 5, 28.0D, CastType.LONG, 60, 6, 4, 1, 24);
    }

    public static int ticks(int level) {
        return 200 + 30 * level;
    }

    public static float radius(int level) {
        return 4.5F + 0.5F * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("radius", radius(level)), seconds(ticks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CAST_KNEELING_PRAYER;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.TOUCH_GROUND_ANIMATION;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        Vec3 at = groundTarget(world, caster, 16.0D);
        FrostHeartEntity heart = new FrostHeartEntity(FDSRegistry.FROST_HEART_ENTITY.get(), world);
        heart.moveTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
        heart.setup(caster, power(level, caster), level, ticks(level));
        heart.arm(this, radius(level));
        world.addFreshEntity(heart);
        super.onCast(world, level, caster, source, data);
    }
}
