package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.LitanyRuneEntity;
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
 * LITANIA RUN - runes of ice gather and circle the caster. Every time the caster hurts something - a spell, a blade,
 * an arrow - one rune breaks from the ring, flies at it and freezes it. A new casting replaces the old ring.
 */
public class RuneLitanySpell extends FrostSpell {

    public RuneLitanySpell() {
        super("rune_litany", SpellRarity.EPIC, 5, 40.0D, CastType.LONG, 70, 8, 5, 1, 30);
    }

    public static int count(int level) {
        return Math.min(7, 3 + (level + 1) / 2);
    }

    public static int ticks(int level) {
        return 400 + 40 * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("projectile_count", count(level)),
                seconds(ticks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CAST_T_POSE;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.SELF_CAST_TWO_HANDS;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        for (LitanyRuneEntity old : world.getEntitiesOfClass(LitanyRuneEntity.class,
                caster.getBoundingBox().inflate(8.0D), r -> r.ownedBy(caster) && r.orbiting())) {
            old.discard();
        }
        int n = count(level);
        for (int i = 0; i < n; i++) {
            LitanyRuneEntity rune = new LitanyRuneEntity(FDSRegistry.LITANY_RUNE.get(), world);
            rune.moveTo(caster.getX(), caster.getY() + 1.2D, caster.getZ());
            rune.setup(caster, power(level, caster), level, ticks(level));
            rune.orbit(this, i, n);
            world.addFreshEntity(rune);
        }
        super.onCast(world, level, caster, source, data);
    }
}
