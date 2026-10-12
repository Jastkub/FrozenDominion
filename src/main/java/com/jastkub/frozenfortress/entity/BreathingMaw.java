package com.jastkub.frozenfortress.entity;

/** A maw that breathes frost as geometry (FrostmawBreathLayer): how strong the gust is now, and how far it reaches. */
public interface BreathingMaw {
    float breathPower(float partialTick);

    float breathReach(float partialTick);
}
