package com.jastkub.frozenfortress.client.model;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class VelkharModel extends DefaultedEntityGeoModel<VelkharEntity> {

    /** Phase 1: the king still wearing his crown and his colours. */
    private static final ResourceLocation TEXTURE_SOVEREIGN =
            FrozenFortress.id("textures/entity/velkhar.png");
    /** Phase 2: the crown is shattered and the storm is showing through. */
    private static final ResourceLocation TEXTURE_STORM =
            FrozenFortress.id("textures/entity/velkhar_storm.png");
    /** Phase 3: nothing left of the man - only the winter he swallowed. */
    private static final ResourceLocation TEXTURE_HOLLOW =
            FrozenFortress.id("textures/entity/velkhar_hollow.png");

    /** The same three sheets with the lids down. */
    private static final ResourceLocation TEXTURE_SOVEREIGN_BLINK =
            FrozenFortress.id("textures/entity/velkhar_blink.png");
    private static final ResourceLocation TEXTURE_STORM_BLINK =
            FrozenFortress.id("textures/entity/velkhar_storm_blink.png");
    private static final ResourceLocation TEXTURE_HOLLOW_BLINK =
            FrozenFortress.id("textures/entity/velkhar_hollow_blink.png");

    /** How often he blinks, and for how long. Slow and brief - a boss that
     *  blinks like a person is unsettling; one that blinks constantly is
     *  distracting. */
    private static final int BLINK_PERIOD = 96;
    private static final int BLINK_LENGTH = 3;

    public VelkharModel() {
        super(FrozenFortress.id("velkhar"), true);
    }

    /**
     * Driven off the entity's own age rather than a stored timer, so it needs
     * no syncing and every Velkhar on screen blinks on its own schedule - the
     * id offset stops a boss and his mirrors blinking in lockstep, which would
     * give the illusions away instantly.
     */
    private boolean isBlinking(VelkharEntity entity) {
        if (entity.isDeadOrDying() || entity.isDormant()) {
            return false;
        }
        int phaseOffset = Math.floorMod(entity.getId() * 37, BLINK_PERIOD);
        return Math.floorMod(entity.tickCount + phaseOffset, BLINK_PERIOD) < BLINK_LENGTH;
    }

    /** The same three sheets with the heart awake. */
    private static final ResourceLocation TEXTURE_SOVEREIGN_LIT =
            FrozenFortress.id("textures/entity/velkhar_lit.png");
    private static final ResourceLocation TEXTURE_STORM_LIT =
            FrozenFortress.id("textures/entity/velkhar_storm_lit.png");
    private static final ResourceLocation TEXTURE_HOLLOW_LIT =
            FrozenFortress.id("textures/entity/velkhar_hollow_lit.png");

    /** THE TWIN BLADES' OWN SHEETS: the storm's white gone a step toward the Hollow Magus - the
     *  harness dark between the plates, the gilt and steel darker, the light violet-white (gen_textures.twin_palette).
     *  Worn while the sword is torn in two (isDualWielding), inside phase two. */
    private static final ResourceLocation TEXTURE_TWIN =
            FrozenFortress.id("textures/entity/velkhar_twin.png");
    private static final ResourceLocation TEXTURE_TWIN_BLINK =
            FrozenFortress.id("textures/entity/velkhar_twin_blink.png");
    private static final ResourceLocation TEXTURE_TWIN_LIT =
            FrozenFortress.id("textures/entity/velkhar_twin_lit.png");

    @Override
    public ResourceLocation getTextureResource(VelkharEntity entity) {
        // THE SKIN LAGS THE PHASE FLAG. setPhase(2) fires on the first tick of
        // the transition, but the new look must not arrive until the plates
        // blow - so the texture is chosen off an effective phase that stays
        // one behind until the explosion. Everything below reads `phase`, not
        // entity.getPhase(), so the lag applies to the blink and heart sheets
        // too and he cannot flicker into the storm palette for a single frame.
        int phase = entity.getPhase();
        if (phase == 2 && entity.skinLagsBehindPhase()) {
            phase = 1;
        }
        // The heart wins over the blink: while it is open he is screaming, and
        // a boss who blinks in the middle of that looks bored. It also keeps the
        // sheet count at three rather than six.
        // (and through the third transition until its flash: the Twin Blades lose their mask, THEN become the Magus)
        boolean twin = (phase == 2 && entity.isDualWielding()) || entity.twinLookInTransition();
        if (entity.isHeartLit()) {
            if (twin) {
                return TEXTURE_TWIN_LIT;
            }
            return switch (phase) {
                case 1 -> TEXTURE_SOVEREIGN_LIT;
                case 2 -> TEXTURE_STORM_LIT;
                default -> TEXTURE_HOLLOW_LIT;
            };
        }
        boolean blink = isBlinking(entity);
        if (twin) {
            return blink ? TEXTURE_TWIN_BLINK : TEXTURE_TWIN;
        }
        return switch (phase) {
            case 1 -> blink ? TEXTURE_SOVEREIGN_BLINK : TEXTURE_SOVEREIGN;
            case 2 -> blink ? TEXTURE_STORM_BLINK : TEXTURE_STORM;
            default -> blink ? TEXTURE_HOLLOW_BLINK : TEXTURE_HOLLOW;
        };
    }
}
