package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.IceSpireEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * SCIEZKA SZRONU - the Rime Path, the Kingsrime sword's spikes made a spell (12.10.2026, the same comment): a line of
 * ice spires driven out ahead along the ground, one a tick, each a little bigger than the last; a wall or a drop ends
 * it. Weak, quick, and it shows a foe exactly where not to stand.
 */
public class RimePathSpell extends FrostSpell {

    public RimePathSpell() {
        super("rime_path", SpellRarity.UNCOMMON, 6, 8.0D, CastType.LONG, 25, 4, 4, 1, 8);
    }

    public static int length(int level) {
        return 8 + level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), line("distance", length(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.TOUCH_GROUND_ANIMATION;
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        float power = power(level, caster);
        Vec3 dir = Vec3.directionFromRotation(0.0F, caster.getYRot());
        Vec3 from = caster.position().add(dir.scale(1.5D));
        int n = length(level);
        for (int k = 0; k < n; k++) {
            Vec3 p = from.add(dir.scale(k));
            Vec3 ground = Utils.moveToRelativeGroundLevel(world, p, 2);
            if (Math.abs(ground.y - caster.getY()) > 2.5D) {
                break;                                          // a wall, or a drop: the path ends there
            }
            IceSpireEntity.raise(world, caster, this, ground.x, ground.y, ground.z, power, level, 2 + k,
                    0.7F + 0.6F * k / n);
        }
        super.onCast(world, level, caster, source, data);
    }
}
