package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.IceSpireEntity;
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
 * SMUTEK ZIMY - Winter's Sorrow, the Sovereign's Lament's own skill made a spell: rings of
 * ice spires burst up round the caster one after another, the nearest first. Far weaker than the greatsword's.
 */
public class WintersSorrowSpell extends FrostSpell {

    public WintersSorrowSpell() {
        super("winters_sorrow", SpellRarity.UNCOMMON, 6, 14.0D, CastType.LONG, 40, 5, 3, 1, 15);
    }

    public static int rings(int level) {
        return Math.min(3, 1 + level / 2);
    }

    public static float radius(int ring) {
        return 1.6F + 1.7F * ring;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("ring_count", rings(level)),
                line("radius", radius(rings(level))));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_LONG_CAST;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.STOMP;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        float power = power(level, caster);
        for (int ring = 1; ring <= rings(level); ring++) {
            int n = 6 + 3 * ring;
            float r = radius(ring);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2.0D * i / n + ring * 0.35D;
                IceSpireEntity.raise(world, caster, this, caster.getX() + Math.cos(a) * r, caster.getY(),
                        caster.getZ() + Math.sin(a) * r, power, level, 4 + ring * 4, 0.8F + 0.15F * ring);
            }
        }
        super.onCast(world, level, caster, source, data);
    }
}
