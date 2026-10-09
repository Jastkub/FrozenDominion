package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.entity.FrostShacklesEntity;
import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * OKOWY - chains of ice tear up out of the ground round the foe the caster points at and hold it where it stands:
 * it cannot walk, jump or be pushed off until they break.
 */
public class FrostShacklesSpell extends FrostSpell {

    public FrostShacklesSpell() {
        super("frost_shackles", SpellRarity.UNCOMMON, 5, 16.0D, CastType.LONG, 35, 4, 3, 1, 12);
    }

    public static int holdTicks(int level) {
        return 40 + 10 * level;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(line("damage", power(level, caster)), seconds(holdTicks(level)));
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_RAY_CHARGE;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ONE_HANDED_RAY_SHOOT;
    }

    @Override
    public boolean checkPreCastConditions(Level world, int level, LivingEntity caster, MagicData data) {
        return Utils.preCastTargetHelper(world, caster, data, this, 24, 0.35F);
    }

    @Override
    public void onCast(Level world, int level, LivingEntity caster, CastSource source, MagicData data) {
        if (world instanceof ServerLevel sl && data.getAdditionalCastData() instanceof TargetEntityCastData t) {
            LivingEntity target = t.getTarget(sl);
            if (target != null) {
                FrostShacklesEntity chains = new FrostShacklesEntity(FDSRegistry.FROST_SHACKLES_ENTITY.get(), world);
                chains.moveTo(target.getX(), target.getY(), target.getZ());
                chains.setup(caster, power(level, caster), level, holdTicks(level));
                chains.bind(this, target);
                world.addFreshEntity(chains);
            }
        }
        super.onCast(world, level, caster, source, data);
    }
}
