package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.FrostJavelinEntity;
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
 * OSZCZEP MROZU - a javelin of ice, thrown. Into a foe it drives home and chills; into the ground it stands as a
 * pillar of ice for a while: cover to hide behind, and a creature that runs into it is stunned.
 */
public class FrostJavelinSpell extends FrostSpell {

    public FrostJavelinSpell() {
        super("frost_javelin", SpellRarity.COMMON, 8, 9.0D, CastType.LONG, 25, 3, 10, 2, 10);
    }

    public static int pillarTicks(int level) {
        return 160 + 20 * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), seconds(pillarTicks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.THROW_SINGLE_ITEM;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        FrostJavelinEntity jav = new FrostJavelinEntity(FDSRegistry.FROST_JAVELIN_ENTITY.get(), world);
        Vec3 look = caster.getLookAngle();
        Vec3 at = caster.getEyePosition().add(look.scale(0.8D)).add(0.0D, -0.25D, 0.0D);
        jav.setPos(at.x, at.y, at.z);
        jav.arm(caster, this, power(level, caster), level, look.scale(2.2D));
        world.addFreshEntity(jav);
        super.onCast(world, level, caster, source, data);
    }
}
