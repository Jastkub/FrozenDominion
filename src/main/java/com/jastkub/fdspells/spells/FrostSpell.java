package com.jastkub.fdspells.spells;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * What the eight have in common: the Ice school, their id, their numbers, and a voice of their own each.
 *
 * <p>EVERY ONE IS CHARGED: a long
 * cast of its own length (0.4 to 1.5 s), its own pair of Iron's Spells' animations - a charge and a release - and its
 * own charge and release sounds. Six of them were instant and all eight said the same thing.
 */
public abstract class FrostSpell extends AbstractSpell {

    private final ResourceLocation spellId;
    private final DefaultConfig config;
    private final CastType castType;

    protected FrostSpell(String name, SpellRarity minRarity, int maxLevel, double cooldownSeconds, CastType castType,
                         int baseMana, int manaPerLevel, int basePower, int powerPerLevel, int castTime) {
        this.spellId = FDSpells.id(name);
        this.config = new DefaultConfig().setMinRarity(minRarity).setSchoolResource(SchoolRegistry.ICE_RESOURCE)
                .setMaxLevel(maxLevel).setCooldownSeconds(cooldownSeconds).build();
        this.castType = castType;
        this.baseManaCost = baseMana;
        this.manaCostPerLevel = manaPerLevel;
        this.baseSpellPower = basePower;
        this.spellPowerPerLevel = powerPerLevel;
        this.castTime = castTime;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return castType;
    }

    /** Its own charge (tools/gen_cast_sounds.py), played as the cast begins - it lasts as long as the cast. */
    @Override
    public Optional<SoundEvent> getCastStartSound() {
        var s = FDSRegistry.CAST_CHARGE.get(spellId.getPath());
        return Optional.of(s != null ? s.get() : FDSRegistry.FROST_CHARGE.get());
    }

    /** And its own release, as it goes. */
    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        var s = FDSRegistry.CAST_RELEASE.get(spellId.getPath());
        return Optional.of(s != null ? s.get() : FDSRegistry.FROST_RELEASE.get());
    }

    /** The spell's power for this caster at this level (ISS scales it by the caster's ice power). */
    protected float power(int level, LivingEntity caster) {
        return getSpellPower(level, caster);
    }

    protected static MutableComponent line(String key, double value) {
        return Component.translatable("ui.irons_spellbooks." + key, Utils.stringTruncation(value, 1));
    }

    protected static MutableComponent seconds(double ticks) {
        return Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks((float) ticks, 1));
    }

    /** Where the caster looks, on the ground: the block hit within `range`, or the floor under the end of the look. */
    protected static Vec3 groundTarget(Level level, LivingEntity caster, double range) {
        HitResult hit = Utils.raycastForEntity(level, caster, (float) range, true);
        Vec3 at = hit.getLocation();
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            at = Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection()));
        }
        return Utils.moveToRelativeGroundLevel(level, at, 6);
    }
}
