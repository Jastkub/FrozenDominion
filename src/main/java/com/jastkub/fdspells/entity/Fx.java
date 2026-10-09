package com.jastkub.fdspells.entity;

/**
 * What FxRenderer needs to know about a thing it draws: which model (geo/entity/KIND, textures/entity/KIND,
 * animations/entity/KIND with its looping "animation.KIND.loop"), whether it is turned to where it faces, and how big
 * it stands this frame.
 */
public interface Fx {

    String kind();

    /** Turned to its yaw (and pitch, see pitched()) - a wave, a javelin; or not at all - a heart, a ring. */
    default boolean faces() {
        return false;
    }

    /** Its pitch counts too (xRot, positive = nose down). */
    default boolean pitched() {
        return false;
    }

    /** Scale in x/z this frame (a ring grows). */
    default float spread(float partialTick) {
        return 1.0F;
    }

    /** Scale overall this frame (things that form and melt). */
    default float size(float partialTick) {
        return 1.0F;
    }
}
