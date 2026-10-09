package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.FrostShellEntity;
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
 * PANCERZ SZRONU - a shell of ice closes round the caster and takes the blows (temporary health). When it is spent -
 * or its time runs out - it bursts, and its shards cut everything round about.
 */
public class FrostShellSpell extends FrostSpell {

    public FrostShellSpell() {
        super("frost_shell", SpellRarity.RARE, 5, 30.0D, CastType.LONG, 50, 6, 5, 1, 8);
    }

    public static float absorption(int level) {
        return 6.0F + 2.0F * level;
    }

    public static int ticks(int level) {
        return 600;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("absorption", absorption(level)), line("shatter_damage", power(level, caster)),
                seconds(ticks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.PREPARE_CROSS_ARMS;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        for (FrostShellEntity old : world.getEntitiesOfClass(FrostShellEntity.class,
                caster.getBoundingBox().inflate(4.0D), s -> s.ownedBy(caster))) {
            old.discard();
        }
        FrostShellEntity shell = new FrostShellEntity(FDSRegistry.FROST_SHELL_ENTITY.get(), world);
        shell.moveTo(caster.getX(), caster.getY(), caster.getZ());
        shell.setup(caster, power(level, caster), level, ticks(level));
        shell.close(this, absorption(level));
        world.addFreshEntity(shell);
        super.onCast(world, level, caster, source, data);
    }
}
