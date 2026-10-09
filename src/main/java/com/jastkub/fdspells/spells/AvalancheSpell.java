package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.AvalancheWaveEntity;
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

import java.util.List;

/**
 * LAWINA - a wave of ice chunks rolls out along the ground from the caster's feet and bowls over what it meets:
 * hurt, thrown up and on, slowed. Follows the ground a step up or down; a wall ends it.
 */
public class AvalancheSpell extends FrostSpell {

    public AvalancheSpell() {
        super("avalanche", SpellRarity.UNCOMMON, 6, 18.0D, CastType.LONG, 40, 5, 8, 2, 16);
    }

    public static float distance(int level) {
        return 14.0F + 2.0F * level;
    }

    public static float width(int level) {
        return 3.0F + 0.4F * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("distance", distance(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.STOMP;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        AvalancheWaveEntity wave = new AvalancheWaveEntity(FDSRegistry.AVALANCHE_WAVE.get(), world);
        float yaw = caster.getYRot();
        double r = Math.toRadians(yaw);
        wave.moveTo(caster.getX() - Math.sin(r) * 1.2D, caster.getY(), caster.getZ() + Math.cos(r) * 1.2D, yaw, 0.0F);
        wave.setup(caster, power(level, caster), level, 200);
        wave.launch(this, distance(level), width(level));
        world.addFreshEntity(wave);
        super.onCast(world, level, caster, source, data);
    }
}
