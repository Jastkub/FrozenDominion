package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FFParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FrozenFortress.MODID);

    /** Swirling frost mist, used for spell casts and ambience. */
    public static final RegistryObject<SimpleParticleType> FROST_SWIRL =
            PARTICLE_TYPES.register("frost_swirl", () -> new SimpleParticleType(false));

    /** Sharp shards of ice thrown by impacts and shattering. */
    public static final RegistryObject<SimpleParticleType> ICE_SHARD =
            PARTICLE_TYPES.register("ice_shard", () -> new SimpleParticleType(false));

    /** Heavy snowflakes of the arena blizzard. */
    public static final RegistryObject<SimpleParticleType> BLIZZARD_FLAKE =
            PARTICLE_TYPES.register("blizzard_flake", () -> new SimpleParticleType(false));

    /** Dark-cyan soul wisps of the Hollow Winter (phase 3). */
    public static final RegistryObject<SimpleParticleType> SOUL_FROST =
            PARTICLE_TYPES.register("soul_frost", () -> new SimpleParticleType(false));

    /** Expanding ring used by shockwaves and slams. */
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE =
            PARTICLE_TYPES.register("shockwave", () -> new SimpleParticleType(true));

    private FFParticles() {
    }
}
