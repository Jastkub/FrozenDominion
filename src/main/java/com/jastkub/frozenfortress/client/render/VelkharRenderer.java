package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.client.model.VelkharModel;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

public class VelkharRenderer extends FrostGeoRenderer<VelkharEntity> {



    /** The churning mantle around the beam. */
    private static final ResourceLocation BEAM_OUTER =
            FrozenFortress.id("textures/entity/velkhar_beam_outer.png");
    /** The white-hot core inside it. */
    private static final ResourceLocation BEAM_INNER =
            FrozenFortress.id("textures/entity/velkhar_beam_inner.png");
    /** HIS CHAIN, OF ICE: the Turnkey's link and manacle (tools/gen_ice_chain.py), drawn bone by bone. */
    private static final ResourceLocation ICE_CHAIN_GEO =
            FrozenFortress.id("geo/entity/velkhar_ice_chain.geo.json");
    private static final ResourceLocation ICE_CHAIN =
            FrozenFortress.id("textures/entity/velkhar_ice_chain.png");
    /** The Turnkey's link, three times over: 0.56 long, 0.38 wide, 0.41 from one to the next (gen_ice_chain.py's
     *  LINK_LENGTH and LINK_PITCH, in model pixels). */
    private static final float LINK_SCALE = 3.0F;
    private static final float LINK_LENGTH = 3.0F / 16.0F * LINK_SCALE;
    private static final float LINK_PITCH = 2.2F / 16.0F * LINK_SCALE;
    /** The bones' pivots (they stand apart in the bind pose) and the manacle's hinge eye, the chain's end - pixels. */
    private static final float LINK_PIVOT_Y = 2.0F / 16.0F;
    private static final float MANACLE_PIVOT_Y = 10.0F / 16.0F;
    private static final org.joml.Vector3fc CHAIN_EYE = new Vector3f(0.0F, 1.7F, -3.9F);

    /** Must match VelkharEntity.BEAM_ORIGIN_HEIGHT or the beam will hit
     *  somewhere other than where it is drawn. */
    private static final float BEAM_ORIGIN_Y =
            (float) VelkharEntity.BEAM_ORIGIN_HEIGHT;
    // Slimmed to about 60%: at the old width the core filled the
    // screen at close range and you could not see what it was aimed at.
    private static final float OUTER_RADIUS = 0.45F;
    private static final float INNER_RADIUS = 0.15F;
    /** World length one tile of the texture covers, before scrolling. */
    private static final float TILE_LENGTH = 8.0F;

    public VelkharRenderer(EntityRendererProvider.Context context) {
        super(context, new VelkharModel());
        // Wide enough to match the new shoulder span; a small shadow under a
        // big silhouette is what makes a boss look pasted onto the floor.
        this.shadowRadius = 1.7F;
        // The eyes, the crown, the throne on his back and the ice in his
        // armour light themselves from here on. The fortress interior is dark
        // by design, and a face that depends on the room's light is a face
        // that is never actually visible - which is exactly what happened.
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        // The third-phase face burns on its own layer, breathing and blinking,
        // so the eyes and the grin are alive rather than two constant dots.
        addRenderLayer(new FaceGlowLayer(this));
        // And the gate burns while it is untouchable - the only readout the
        // player gets for "hitting this does nothing right now".
        addRenderLayer(new ShieldGlowLayer(this));
        // And what is inside him gets out during the beam and at the end.
        addRenderLayer(new InnerLightLayer(this));
        // THE MASK AS A REAL MESH. Draws nothing until the OBJ exists, so
        // this is inert until an actual sculpt is dropped in; once it is
        // there it takes over from the cuboid faceplate entirely.
        addRenderLayer(new MaskMeshLayer(this));
        // THE MAGUS'S CLOTHES: coat, robe, mantle, bell sleeves, the crown of shards and the circling crystals
        addRenderLayer(new MagusGarbLayer(this));
        addRenderLayer(new VelkharStaffLayer(this));
        // The arc the blade leaves. Last, so it draws over the body rather
        // than being buried by the plate it sweeps across.
        addRenderLayer(new BladeTrailLayer(this));
        // The wake his whole BODY leaves when it crosses the room faster than
        // the eye follows. Before the damage overlay, so cracks draw over it.
        addRenderLayer(new MotionTrailLayer(this));
        // And the armour breaking up under him. After the trail, so a cut
        // passing over a cracked plate is drawn over it rather than under.
        addRenderLayer(new BattleDamageLayer(this));
        // AFTER the damage layer, so the light sits in the cracks the damage
        // layer just drew rather than under them.
        // the gauntlet filling with frost before the fist goes out
        addRenderLayer(new FistChargeLayer(this));
        addRenderLayer(new ShatterGlowLayer(this));
        // and the light that gets OUT. Last, so the beams draw over
        // everything including the glow in the cracks they came from.
        addRenderLayer(new ShatterBeamLayer(this));
        // HIS WEAPONS MADE IN FRONT OF YOU (08.10.2026): the gate and the blade built out of flying ice in the intro,
        // the greatsword bursting and laid down again as the crossbow in the ascent - his own boxes and the forge's
        // shards, after everything else so the ice draws over the man it is forming round
        addRenderLayer(new VelkharForgeLayer(this));
    }

    /** The bones that are a weapon rather than a part of him. */
    private static final java.util.Set<String> WEAPONS =
            java.util.Set.of("sword", "twin", "twin_r", "ice_staff", "shield", "crossbow");

    /**
     * DOES THIS KING CARRY THIS WEAPON? Asked per entity, at draw time.
     *
     * <p>THE SHARED MODEL IS THE WHOLE PROBLEM. Every Velkhar in the world -
     * the fortress king, a second one from an egg, the afterimages, the
     * copies - is drawn from ONE cached BakedGeoModel, and {@code setHidden}
     * writes onto that shared object. So the last one to render decided what
     * all of them were holding. Reported exactly: summon several, kill one,
     * and the sword blinks in and out on every one of them.
     *
     * <p>No arrangement of "set it every frame" fixes that, because the state
     * being set is not per-entity in the first place. So nothing is set at
     * all. {@link #renderRecursively} simply declines to draw a weapon bone
     * for an entity that is not carrying it, which is a decision taken during
     * that entity's own draw and cannot leak into anybody else's.
     *
     * <p>The rule itself is deliberately blunt:
     * <ul>
     *   <li>phase one and two - the GREATSWORD;
     *   <li>once he has torn it in half - the TWINS, and nothing else;
     *   <li>phase three - the STAFF, and nothing else;
     *   <li>and the greatsword also goes while it is genuinely out of his
     *       hand: thrown, planted in the floor, or not yet claimed.
     * </ul>
     */
    private static boolean carries(VelkharEntity boss, String bone) {
        boolean paired = boss.isDualWielding();
        boolean mage = boss.getPhase() >= 3;
        return switch (bone) {
            // ONLY WHILE IT EXISTS. The crossbow is made once, fired, and
            // broken, all inside one attack - so the flag that says whether it
            // is in his hand is the same float that says how much of it has
            // been made, and there is nothing else to ask.
            // AND ONLY ONCE IT IS REAL (08.10.2026): below BOW_SOLID it is still
            // ice being laid down out of the greatsword's pieces, and that is
            // VelkharForgeLayer's to draw; from the tear (the twins, DUAL) its
            // pieces are thrown, and the real one is gone on that frame.
            case "crossbow" -> boss.bowForm() >= VelkharEntity.BOW_SOLID && !paired;
            // (in the third transition the Twin Blades keep their blades until they let them go - its tick 20)
            case "twin", "twin_r" -> paired && (!mage || boss.twinLookInTransition());
            case "ice_staff" -> mage;
            case "shield" -> {
                if (boss.isShieldGone()) {
                    yield false;
                }
                // He is still wearing it right up to the beat the phase-two
                // break tears it off, and never after.
                yield boss.getAttackState() == VelkharEntity.P2_TRANSITION
                        ? boss.shedStage() < 1
                        : boss.getPhase() < 2 || boss.isDiscardingShield();
            }
            case "sword" -> {
                if (paired || mage) {
                    yield false;
                }
                // ONE WEAPON PER FIST. The crossbow is made out of the greatsword
                // (08.10.2026): from the tick it BURSTS (BOW_FORM leaves zero on
                // ASCENT_FORGE) its pieces are VelkharForgeLayer's, flying, and
                // the blade itself is gone.
                if (boss.bowForm() > 0.0F) {
                    yield false;
                }
                int st = boss.getAttackState();
                boolean canBeGone = st == VelkharEntity.BLADE_THROW
                        || st == VelkharEntity.GRAVE_BLADE
                        || st == VelkharEntity.INTRO
                        || st == VelkharEntity.GOLEM_RITE
                        || boss.isDormant();
                yield !(canBeGone && boss.isSwordGone());
            }
            default -> true;
        };
    }

    @Override
    public void renderRecursively(PoseStack poseStack, VelkharEntity animatable,
                                  software.bernie.geckolib.cache.object.GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        boolean weapon = WEAPONS.contains(bone.getName());
        boolean carried = !weapon || carries(animatable, bone.getName());
        if (!carried) {
            // LEAVE IT AS WE FOUND IT.
            //
            // Returning here used to be the whole story, and that was the last
            // leak: the bone keeps whatever SCALE the previous entity left on
            // it, and the model is shared. Kill a king in phase one with the
            // command - so he never hands the blade over through a transition
            // - and whatever stub scale his final frame wrote stayed on the
            // bone for the next king to inherit. Killing him properly happened
            // to end in a phase that rewrote it, which is exactly why the bug
            // depended on HOW he died.
            //
            // So nothing is skipped dirty. The bone goes back to full size on
            // the way out, and only visibility carries the decision.
            if (weapon) {
                bone.setScaleX(1.0F);
                bone.setScaleY(1.0F);
                bone.setScaleZ(1.0F);
            }
            return;
        }
        if (weapon) {
            // POSITIVELY CLAIM IT, every frame, for this entity.
            //
            // Removing the old setHidden calls closed one hole and opened a
            // worse one: nothing was left that could ever set this bone back
            // to VISIBLE. The flag lives on the model every Velkhar shares, so
            // one write of `true` from anywhere - an older build still running
            // in the same session, the afterimage renderer, anything added
            // later - became permanent, and no respawn could undo it. That is
            // "once he loses the sword it never comes back", and it is why the
            // death animation could still show a blade the living one lacked.
            //
            // So carrying a weapon is asserted rather than assumed. Shared
            // state is only safe if every frame states what it wants.
            bone.setHidden(false);
        }
        if ("sword".equals(bone.getName())) {
            // The ramp is real only while the blade is being thrown or grown
            // back; in every other state it is simply whole, whatever a late
            // or stale synced float says.
            int st = animatable.getAttackState();
            boolean ramping = st == VelkharEntity.BLADE_THROW
                    || st == VelkharEntity.GRAVE_BLADE
                    || st == VelkharEntity.INTRO;
            bone.setScaleX(1.0F);
            bone.setScaleZ(1.0F);
            bone.setScaleY(ramping ? Math.max(0.02F, animatable.swordForm()) : 1.0F);
        }
        if ("twin".equals(bone.getName()) || "twin_r".equals(bone.getName())) {
            // GROWN, NOT HANDED OVER. Scaled on Y alone and from the grip end,
            // so the blade extends out of a fist that always has something in
            // it - the same rule the greatsword and the wand are forged under.
            // Scaling all three axes from nothing is a toy being inflated.
            float grown = animatable.twinForm();
            bone.setScaleX(1.0F);
            bone.setScaleZ(1.0F);
            bone.setScaleY(Math.max(0.02F, grown));
        }
        if ("crossbow".equals(bone.getName())) {
            // WHOLE WHENEVER IT IS DRAWN. It used to grow here, stock first and
            // girth after, over the greatsword shrinking in the same fist - a
            // crossfade. It is laid down out of the sword's pieces in ice now
            // (VelkharForgeLayer) and only drawn once it is real (carries), so
            // there is nothing left to grow; set every frame all the same, the
            // model being shared.
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
        }
        if ("shield".equals(bone.getName())) {
            // ================================================================
            // THE SHOT COMES OFF IT, and that is all that happens.
            //
            // An arrow used to drive the full block reaction: the heavy clang,
            // the brace, and a tick on the counter that eventually became a
            // shield bash. A boss who plays his big defensive answer because
            // somebody plinked him from thirty blocks is a boss whose tells
            // mean nothing.
            //
            // So the guard twitches instead. One short kick on the arm that
            // decays out - no clip, no state, nothing interruptible, nothing
            // the player has to answer. It reads as the shield turning the
            // shot aside rather than as him deciding to block it.
            //
            // Squared so it snaps and then settles rather than sliding back
            // linearly, which is what a struck object does.
            // ---- ABSOLUTE, NOT ACCUMULATED. This read the bone's current
            //      offset and wrote back a smaller one, which looks like a
            //      nudge and is not: renderRecursively runs once per FRAME,
            //      not once per tick, so at sixty frames a second a seven tick
            //      twitch applied the kick twenty-odd times over. The shield
            //      was thrown tens of units off the model and only came back
            //      when the twitch expired - "the shield vanishes for a split
            //      second when an arrow hits him", exactly.
            //
            //      Set from the flick alone and the frame rate cannot matter.
            //      Nothing else writes to this bone, so owning it outright for
            //      seven ticks costs nothing.
            // ---- IT ROCKS, IT DOES NOT HOP.
            //
            // Two things made the first version read as the shield jumping a
            // few centimetres sideways. It MOVED - a translation on a strapped
            // shield is the arm being knocked off the body, which is a much
            // bigger event than a shot glancing off. And it started at full
            // deflection on the tick of the hit, so the first frame was a step
            // change with no rise: the eye sees a jump cut, not an impact.
            //
            // So: rotation only, on the strap axis, and shaped like a real
            // impulse - zero at the moment of contact, peaking about a fifth
            // of the way through, easing back over the rest. The peak is eight
            // degrees, which is a shudder rather than a parry.
            float flick = animatable.shieldFlick();
            if (flick > 0.0F) {
                float since = 1.0F - flick;
                float env = Mth.sin((float) Math.pow(since, 0.45D) * Mth.PI);
                bone.setRotZ(-env * 0.14F);
            }
        }
        if ("ice_staff".equals(bone.getName())) {
            // FORGED, NOT ISSUED. The wand is drawn off the phase flag, and
            // that flag flips on the FIRST tick of the transition - so it used
            // to blink into a fist still busy tearing a mask off. It grows out
            // of the grip instead, across the forge.
            //
            // Length leads and thickness follows: the rod reaches its full
            // reach while still thin, then fills out over the last third, so
            // the read is "drawn out of his hand and then set" rather than
            // "inflated". Same pivot rule as the greatsword - the grip stays
            // put, the head is what travels.
            float form = animatable.getAttackState() == VelkharEntity.P3_TRANSITION
                    ? Math.max(0.02F, animatable.staffForm()) : 1.0F;
            float girth = 0.25F + 0.75F * Math.min(1.0F, Math.max(0.0F, (form - 0.55F) / 0.45F));
            bone.setScaleY(form);
            bone.setScaleX(girth);
            bone.setScaleZ(girth);
            // THE STAFF MADE AGAIN (08.10.2026): the old wand's cubes (and its turning ring) are not drawn - the
            // pose at this bone, its scale in it, goes to VelkharStaffLayer, which draws the new one there
            if (!isReRender) {
                poseStack.pushPose();
                software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(poseStack, bone);
                VelkharStaffLayer.capture(poseStack);
                poseStack.popPose();
            }
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * One plate off, children put back.
     *
     * <p>setHidden() also hides children - it calls setChildrenHidden() on the
     * way through, which is not obvious from the name and has already broken
     * the visor once in this file. The plate bones hang off the harness bones,
     * so hiding a pauldron plate would take the arm with it.
     */
    private static void shedPlate(software.bernie.geckolib.cache.object.BakedGeoModel model,
                                  String bone, boolean gone) {
        model.getBone(bone).ifPresent(b -> {
            b.setHidden(gone);
            b.setChildrenHidden(false);
        });
    }

    /**
     * The shield only exists in phase one. The transition animation hurls it
     * away and shrinks it to nothing, but an animation only holds while it is
     * playing - without this it would blink straight back onto his arm the
     * moment the next attack started.
     */
    /**
     * Every bone between the root and a hand, plus the hands themselves.
     *
     * <p>These are the ONLY route to a weapon bone, and that is the whole
     * reason this list exists. See {@link #clearWeaponPath}.
     */
    private static final String[] WEAPON_PATH = {
            "body", "chest",
            "arm_r", "lower_arm_r", "hand_r",
            "arm_l", "lower_arm_l", "gauntlet_l",
            "sword", "twin", "twin_r", "ice_staff", "crossbow", "shield",
    };

    /**
     * THE SWORD THAT SOMETIMES IS NOT THERE. This is the mechanism, and it is
     * not a rule about swords at all.
     *
     * <p>GeckoLib does not descend into a hidden bone. {@code setHidden(true)}
     * also sets {@code childrenHidden}, and {@code renderRecursively} checks
     * both before it recurses - so a single write anywhere onto an ANCESTOR of
     * the sword means the override that puts the sword back is never reached.
     * Not "draws it wrong": never runs at all. Every careful rule in
     * {@link #carries} sits below that gate and is skipped with it.
     *
     * <p>And the flag lives on the cached BakedGeoModel, which every Velkhar
     * in the world shares - the king, a second one from an egg, the copies,
     * the afterimages - and which is only ever rebuilt when the resource cache
     * is. That is exactly why leaving the world and coming back used to fix
     * it, and why nothing in a running session ever could: the state that was
     * wrong was not on the boss, it was on the model, and the boss had no way
     * to reach it any more.
     *
     * <p>So this stops hunting for whoever wrote the flag. The path to his
     * hands is forced OPEN once per draw, before any recursion happens, and it
     * is done positively rather than defensively - shared state is only safe
     * if every frame states what it wants. Whatever wrote "hidden" - an older
     * build still running in the session, a layer, a copy, something added
     * next year - is overwritten before it can matter.
     *
     * <p>Opening the path is not the same as drawing a weapon. What he
     * actually carries is still decided per entity in
     * {@link #renderRecursively}, which declines to DRAW rather than hiding
     * anything; these two mechanisms do not overlap.
     */
    private static void clearWeaponPath(
            software.bernie.geckolib.cache.object.BakedGeoModel model) {
        for (String name : WEAPON_PATH) {
            model.getBone(name).ifPresent(b -> {
                b.setHidden(false);
                b.setChildrenHidden(false);
            });
        }
    }

    @Override
    public void preRender(PoseStack poseStack, VelkharEntity animatable,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        // BEFORE ANYTHING ELSE, and before any recursion: see clearWeaponPath.
        clearWeaponPath(model);
        // THE SHIELD IS DECIDED AT DRAW TIME NOW, with the weapons - see
        // carries(). It was the last bone left writing visibility onto the
        // shared model, and it duly produced the same bug the sword had:
        // summon a king after an earlier one died holding no shield, and the
        // new one arrives bare, because the dying one wrote "hidden" onto the
        // model they both draw from.
        // The greatsword is somewhere else while it is thrown. Driven off a
        // synced flag rather than off the throw animation, so an interrupt
        // cannot put it back in a hand that is supposed to be empty.
        // THE BLADE GROWS OUT OF THE HILT rather than appearing whole.
        // Scaled on Y only, from a pivot at the grip, so what travels is the
        // POINT - the hand keeps something in it the whole time and the blade
        // extends out of it, which is what "forming" looks like. Scaling all
        // three axes would shrink it toward the fist and read as a toy.
        // THE SWORD'S SCALE USED TO BE SET HERE, and that was the last piece
        // of shared state left on it. preRender writes onto the model EVERY
        // Velkhar draws from, and it runs well before this entity's cubes are
        // emitted - so a dying king mid-throw, still scaling his blade down to
        // a stub, was stamping 0.02 onto the bone a freshly summoned one was
        // about to draw with. The new king's sword was then present, not
        // hidden, and two per cent of its height: invisible, and silent,
        // because no visibility rule had rejected it.
        //
        // It is applied in renderRecursively now, on the bone, in the instant
        // before it is drawn - which is the only moment that belongs to one
        // entity alone.
        // THE OTHER HALF. It does not exist until he breaks the greatsword,
        // and it goes with the first half when the blade is thrown - they are
        // one weapon in two pieces, not two weapons.
        // THE SHADOW BLADE grows out of nothing and goes back to it. Scaled
        // rather than switched: a sword that size appearing on one frame is a
        // prop being turned on, and the whole read of the attack is that it is
        // being CONJURED. Scaled from the grip end only - the Y scale is what
        // makes it extend, and leaving X and Z near full the whole time stops
        // it looking like a toy growing into a sword.
        // Visibility is driven by the ATTACK STATE as well as the ramp. The
        // ramp alone is one synced float, and if it is late or lost the blade
        // simply never appears - which is a silent failure on the one attack
        // whose entire point is a thing you can see. The state is the same
        // information arriving by a second road.
        // WHAT HE CARRIES IS NOT DECIDED HERE ANY MORE. See carries() and the
        // renderRecursively override below: setHidden() writes to a model that
        // every Velkhar in the world shares, so it could never be right for
        // more than one of them at a time.
        // ONE SWORD AT A TIME. The greatsword is in the same fist the ice one
        // forms in, and leaving both there put a normal blade through the
        // middle of a conjured one for the whole attack. Hidden here rather
        // than in the block above because that block is driven by swordForm,
        // which knows nothing about this attack; a later statement wins, and
        // this is the one place that knows both numbers.
        // THE PAIR, AND THE GREATSWORD GOES WITH THEM.
        //
        // Before this the twin appeared and the greatsword STAYED, so tearing
        // the blade in half produced a man holding a full-length greatsword
        // and a second shorter one - which says he found another weapon, not
        // that this one came apart. Two matched blades and no greatsword is
        // the whole read of the transformation.
        // THE GAUNTLET STAYS. Hiding the bone to "shatter" it took the hand
        // with it and left a stump on his arm - that was the wrong reading of
        // "it should break". The gauntlet is his fist; it does not vanish. It
        // CRACKS - the fracture sheet spreads across it through the second
        // phase - and the tear at 350 is where those cracks flash and throw
        // shards, which the FURY handler already does. Breaking is a thing that
        // happens ON it, not the removal of it.

        // THE THIRD-PHASE WEAPON IS A STAFF, NOT THE GREATSWORD.
        //
        // He is a mage from the third phase on. The greatsword was torn into
        // the twins and the twins were dropped at the transition, so nothing
        // should be in his hand but the focus he forges out of the cold - a
        // staff, held for casting and for tearing the floor open to call the
        // colossus. Until now nothing hid the greatsword in the third phase,
        // so its `sword` bone - which ramps back to full whenever it is not
        // actively thrown - simply reappeared, and he cast phase three holding
        // the weapon he is supposed to have destroyed. That is the sword the
        // report keeps meaning: not one that fails to load, one that loads
        // when it should be gone.
        //
        // The staff hides during the golem rite, where he conjures a blade of
        // ice to plunge into the ground instead - two focuses at once would
        // read as him holding a spare.
        // AND THE WAND STAYS IN HIS HAND FOR THE RITE. It used to be hidden for
        // it, because the rite conjured a second tool - an ice blade - and dug
        // the gate open with that while his actual focus hung unused. The wand
        // is what opens the floor for the Monstrosity now, so it is never taken
        // out of his fist in the third phase.
        // The visor comes off with the man. Kept visible through the whole
        // third-phase transition so it can be seen falling.
        // THE MESH IS THE WHOLE HEAD. The imported helm carries its own skull
        // and its own crown, so when it is present the cuboid head, crown and
        // faceplate all go out - leaving three sets of geometry fighting for
        // the same space would show as z-fighting through every gap in the
        // helm. In the third phase the mask comes off and the skull under it
        // is the point, so the head comes back then.
        boolean mesh = MaskMeshLayer.available();
        // THE REVEAL WAS SHOWING THE OLD FACE.
        //
        // "Unmasked" used to mean "past the transition", and the transition
        // runs a hundred and twelve ticks while the visor actually leaves on
        // fifty-eight. So for the fifty-four ticks in between - nearly three
        // seconds, with the faceplate visibly tumbling to the floor - he was
        // still wearing the HELM head: the cube rolled forty-five degrees with
        // a corner to the camera, which is a masked silhouette with nothing
        // masking it. The one moment the whole third act is built around
        // revealed the face it was supposed to be taking away.
        //
        // The head swaps on the frame the mask comes off.
        boolean tearing = animatable.isDiscardingVisor();
        boolean unmasked = animatable.getPhase() >= 3 && animatable.isMaskOff();
        // setHidden() ALSO HIDES CHILDREN - it calls setChildrenHidden() on the
        // way through, which is not obvious from the name and is exactly what
        // broke this. The visor rides on the head, so hiding the head took the
        // visor down with it, the layer was never walked for that bone, and the
        // mesh had no transform to draw with. Children are put back explicitly
        // after every hide here.
        // ...and the visor keeps being drawn for the REST of the tear, because
        // the whole point is watching it fall off a head that is now a skull.
        // Its own bone carries it away and shrinks it to nothing at tick 86;
        // hiding it here the instant the head swapped would delete it in mid
        // air instead.
        model.getBone("visor").ifPresent(bone -> {
            bone.setHidden(mesh || (unmasked && !tearing));
            bone.setChildrenHidden(false);
        });
        // TWO HEADS, AND THE THIRD PHASE WEARS THE OTHER ONE.
        //
        // The normal head cube is rotated -45 about Y, so a CORNER points at
        // the player and his face is two plates raking away either side of it.
        // That is right for a masked helm - it is what gives the visor its
        // faceted look - and it is wrong for a face. A skull painted across
        // that pair has to describe every feature as a distance from a shared
        // edge, put the nose and the middle teeth on a fold, and then be read
        // at forty-five degrees.
        //
        // head_bare is the same head NOT rotated, and it only exists once the
        // mask is off. One flat plate facing the player, which is what a face
        // wants to be. Swapped here rather than in the model because it is a
        // phase-three thing, not a geometry thing.
        // AND THE SKULL HAS TO MOVE WITH HIM.
        //
        // head_bare is driven by no clip at all - every one of the sixty-five
        // animations that nods or turns a head turns "head". So from the frame
        // of the swap the face stops dead: the neck goes on moving, the helm's
        // keys go on playing on a bone nobody can see, and the skull rides
        // along as a rigid block. The unmasking is the worst possible place to
        // notice it, because that clip is still animating the head at 78 and
        // at 112 - he would tear the mask off and turn to stone.
        //
        // The helm's animation is copied onto the skull as a DELTA from each
        // bone's own rest pose, never as an absolute: the helm sits rolled
        // forty-five degrees at rest and the skull deliberately does not, so
        // copying raw values would put that twist straight back on the face.
        // Done unconditionally because the baked model is SHARED between every
        // Velkhar on screen - skipping it would leave the last one's pose on
        // the bone for the next.
        model.getBone("head").ifPresent(helm -> model.getBone("head_bare").ifPresent(skull -> {
            var helmRest = helm.getInitialSnapshot();
            var skullRest = skull.getInitialSnapshot();
            skull.setRotX(skullRest.getRotX() + (helm.getRotX() - helmRest.getRotX()));
            skull.setRotY(skullRest.getRotY() + (helm.getRotY() - helmRest.getRotY()));
            skull.setRotZ(skullRest.getRotZ() + (helm.getRotZ() - helmRest.getRotZ()));
            skull.setPosX(helm.getPosX());
            skull.setPosY(helm.getPosY());
            skull.setPosZ(helm.getPosZ());
        }));
        model.getBone("head").ifPresent(bone -> {
            bone.setHidden(unmasked || (mesh && !unmasked));
            bone.setChildrenHidden(false);
        });
        model.getBone("head_bare").ifPresent(bone -> {
            bone.setHidden(!unmasked);
            bone.setChildrenHidden(false);
        });
        // THE HOOD, and it has to be hidden explicitly rather than inherited.
        //
        // It hangs off head_bare, and head_bare is hidden for the first two
        // phases - but hiding a bone here is always followed by
        // setChildrenHidden(false), because that is what keeps the crown on a
        // hidden helm. So a child of a hidden bone still draws, and without
        // this line the cowl would be floating round his helmet from the first
        // second of the fight.
        //
        // It exists because the sides and the back of the bare head were never
        // designed - one box with a face on its front leaves five faces that
        // are whatever the material happened to be, and the third phase points
        // the camera at exactly those. The hood removes them rather than
        // painting them, and by standing forward of the face at the cheeks and
        // overhanging it at the brow it also MAKES the recess three passes of
        // texture work were trying to imitate. A painted shadow on a flat
        // plate is a drawing of a shadow; this one is cast.
        // the hood is the Magus's: before the third transition's flash the skull is bare (the Twin Blades unmasked)
        boolean magus = animatable.wearsMagus();
        model.getBone("hood").ifPresent(bone -> {
            bone.setHidden(!unmasked || !magus);
            bone.setChildrenHidden(false);
        });
        // AND THE KNIGHT'S PIECES GO WITH THE KNIGHT: the
        // pauldrons and the tassets are not the Magus's - his mantle and his coat are (MagusGarbLayer). Set for
        // every king on every frame, as everything here: the model is shared.
        for (String knight : new String[] {"pauldron_r", "pauldron_l", "tasset_f", "tasset_b"}) {
            model.getBone(knight).ifPresent(bone -> {
                bone.setHidden(magus);
                bone.setChildrenHidden(false);
            });
        }
        // ONE CROWN, TWO ORIENTATIONS.
        //
        // He never loses it - it comes off when he dies and at no other time -
        // but WHICH head it is sitting on changes, and the two heads do not
        // agree about where front is. The helm is rolled forty-five degrees so
        // a corner leads; the bare skull under it is square. A circlet built
        // to match the helm therefore sits on the skull turned forty-five
        // degrees off it, corners where the face's flat sides are, and reads
        // as a hat knocked sideways - which is the report.
        //
        // crown_bare is the same crown cloned with that angle taken back out
        // (see the model), so each head wears the copy that lines up with it.
        // Never both, and never neither.
        model.getBone("crown").ifPresent(bone -> {
            bone.setHidden(unmasked || (mesh && !unmasked));
            bone.setChildrenHidden(false);
        });
        model.getBone("crown_bare").ifPresent(bone -> {
            bone.setHidden(!unmasked);
            bone.setChildrenHidden(false);
        });

        // THE SIEGE PLATE COMES OFF IN PIECES.
        //
        // Read off health, not off a flag, because the point is that the
        // player can WATCH it happen: every plate that goes is a receipt for
        // damage already done, and by the time the breastplate is the only
        // thing left he knows the phase is nearly over. Ordered outermost
        // first - the loose arm cops rattle off long before the cuirass.
        //
        // Hidden wholesale from the second phase on. What is left underneath
        // is the harness that was always there, which is why phase two gets a
        // lean swordsman without a second body being modelled for him.
        float wear = animatable.getHealth() / Math.max(1.0F, animatable.getMaxHealth());
        // STRIPPED WHEN THE ARMOUR ACTUALLY COMES OFF, not when the phase
        // number changes. setPhase(2) fires on the first tick of the
        // transition because everything else keys off it, so reading the
        // phase here stripped him before the cinematic that strips him had
        // begun - the plates were gone seconds before they were shown
        // exploding. shedStage advances only at the detonations.
        boolean breaking = animatable.getAttackState() == VelkharEntity.P2_TRANSITION;
        boolean stripped = breaking ? animatable.shedStage() >= 2
                                    : animatable.getPhase() >= 2;
        // AND THE HEALTH THRESHOLDS ARE SUSPENDED WHILE IT BREAKS.
        //
        // This is what actually defeated the last two attempts. The plates are
        // shed progressively off `wear`, and the phase break happens at low
        // health by definition - so every threshold below has already fired
        // long before the transition starts, and there was simply no armour
        // left for the cinematic to blow off him. Nothing I changed about
        // phases or timings could have fixed that, because the plates were
        // never being hidden by a phase in the first place.
        //
        // So the last of it is put back for the duration and taken at the
        // second detonation. The plates the player watched rattle off during
        // the fight are gone for good; what returns for the break is the
        // shell he is actually wearing when it lets go.
        if (breaking && animatable.shedStage() < 2) {
            wear = 1.0F;
        }
        // THE ARMOUR COMES OFF ONCE, AT THE BREAK. It used to shed a plate at
        // a time on health thresholds - 0.82, 0.68, 0.54, 0.42, 0.34 - which
        // sounds like good escalation and quietly destroys the thing it is
        // escalating toward: by the time he reaches the phase transition most
        // of the plates are already gone, so the one cinematic moment in the
        // fight detonates armour that is not there.
        //
        // That is the report "the armour falls off before it cracks". It was
        // not a bug in the transition at all - the transition was fine and had
        // simply been robbed in advance.
        //
        // Wear still shows: the battle damage layer darkens and cracks the
        // plates as he loses health, so the progression is visible without
        // spending the pieces the break needs.
        shedPlate(model, "plate_arm_r", stripped);
        shedPlate(model, "plate_arm_l", stripped);
        shedPlate(model, "plate_skirt", stripped);
        shedPlate(model, "plate_pauld_r", stripped);
        shedPlate(model, "plate_torso", stripped);
        // ONE PAULDRON STAYS, AND IT IS THE MEASUREMENT THAT SAYS SO.
        //
        // Filling the phase-two figure with black and profiling it by height
        // gives a shoulder half-width of 17.8 against a waist of 15.4 - a
        // ratio of 0.86, which is a COLUMN. The wedge the model's own comments
        // describe is real, but it lives entirely in the pauldron plates, so
        // shedding both of them is the moment he stops having a silhouette.
        // The second phase was the one that lost the shape.
        //
        // Keeping one puts that side back to 23.5 - a ratio of 0.66, which is
        // the wedge the reference was measured at - and does it while BREAKING
        // THE SYMMETRY, which the figure has none of anywhere else. Two
        // problems, one plate.
        //
        // The LEFT one, because that is the gauntlet arm: the heaviest thing
        // he has left already hangs on that side, so the mass agrees with
        // itself rather than arguing across the middle.
        shedPlate(model, "plate_pauld_l", false);

        // THE CAPE FALLS ONTO HIS BACK WHEN THE BACKPLATE LEAVES IT.
        //
        // The cloth hangs at z 7.6 because that is what it takes to clear
        // plate_torso, which stands 7.2 proud - wear a cape over armour and it
        // sits as far out as the armour is thick. Shed the armour and that
        // reasoning evaporates: the deepest thing left on his back is the
        // chest at 5.8, so the cape goes on floating an unexplained 1.8 out
        // from a body it is supposed to be hanging on.
        //
        // So it closes the gap as the plates go. Not a separate model and not
        // a second cape - the same cloth, moved to where the back now is.
        //
        // Done here rather than in the clips because it has to hold in EVERY
        // phase-two state, and there are forty of them. A bone offset written
        // into idle_duel would be correct while he stands still and wrong the
        // moment he swung.
        final float cling = stripped ? -1.4F : 0.0F;
        for (String cloth : new String[] {"cape1", "cape2", "cape3"}) {
            model.getBone(cloth).ifPresent(b -> b.setPosZ(cling));
        }

        // THE EYES. Driven from here and not from a baked animation, because
        // what they should be doing is decided by STATE - which attack he is
        // in, which phase, whether he has been rocked - and a clip cannot read
        // state; it can only be started. From here they react to whatever he
        // is doing without every attack animation having to remember to say so.
        //
        // Scale, not position: the bone's pivot is the keel between the two
        // bars, so widening opens them outward from the nose the way a glare
        // does. Narrowing Y while stretching X is the whole trick - a long
        // thin light reads as a squint, a short tall one as a flinch.
        model.getBone("eyes").ifPresent(bone -> {
            float t = animatable.tickCount + partialTick;
            float sx, sy;
            if (animatable.isDormant()) {
                sx = 0.55f;                       // asleep on the throne
                sy = 0.16f;
            } else if (animatable.isStaggered()) {
                sx = 0.80f;                       // rocked: they screw shut
                sy = 0.35f;
            } else {
                // a slow breath so they are never dead still
                float breath = 1.0f + 0.055f * Mth.sin(t * 0.11f);
                if (animatable.getAttackState() != 0) {
                    // committing to a blow: narrowed and drawn wide
                    sx = breath * 1.30f;
                    sy = breath * 0.62f;
                } else {
                    sx = breath;
                    sy = breath;
                }
            }
            // the deeper he goes the harder they burn
            float phase = 1.0f + 0.12f * (animatable.getPhase() - 1);
            bone.setScaleX(sx * phase);
            bone.setScaleY(sy * phase);
            bone.setScaleZ(sx * phase);
        });

        // THE HALO TURNS. Driven from here rather than from a looping
        // animation because a baked loop has one fixed speed, and the whole
        // point is that the speed says what he is doing.
        //
        // The ring spins and the sunburst is counter-rotated by the same
        // amount, which cancels out through the parent and leaves the ice
        // blades standing still: a fan that swept a full circle would drag its
        // spikes straight through his own shoulders and head every revolution.
        //
        // Bone rotations are RADIANS here; the animation format is degrees.
        // ---- ONCE PER FRAME, NOT ONCE PER PASS.
        //
        // This ADDS to the angle it just read, so it is only correct if it
        // runs exactly once before the model is drawn. preRender runs again
        // for every re-render - the glow layer, the eye layer, anything that
        // draws the model a second time - and each of those added another
        // full spin on top, so on frames carrying an extra pass the ring
        // jumped ahead and snapped back. That is the "nieruchoma aureola
        // sometimes glitches, as if it were moving too": the sunburst is
        // cancelled through its parent, and the cancellation only balances
        // while both bones have been turned the same number of times.
        //
        // Same shape of bug as the shield jumping when an arrow hit it, and
        // the same fix: do it on the real pass and leave the re-renders alone.
        if (!isReRender) {
            float spin = (float) Math.toRadians(animatable.haloAngle(partialTick));
            model.getBone("halo").ifPresent(bone -> bone.setRotZ(bone.getRotZ() + spin));
            model.getBone("halo_shards").ifPresent(bone -> bone.setRotZ(bone.getRotZ() - spin));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(VelkharEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // GONE INTO HIS STORM (the whiteout - VelkharEntity.tickWhiteout): nothing of him is drawn. GeckoLib leaves out
        // an invisible body, but the layers are drawn apart from it - his face's light, his blade's trail - and those
        // would hang in the white with no man under them
        if (entity.isInvisible() && (net.minecraft.client.Minecraft.getInstance().player == null
                || !net.minecraft.client.Minecraft.getInstance().player.isSpectator())) {
            return;
        }
        // ON THE SPIRE, ON ITS PLATFORM: the spire's rise comes every tick, his place
        // every few and eased in after it, so going up he trailed it by a block and more - feet in the ice
        double lift = onTower(entity, partialTick);
        poseStack.pushPose();
        poseStack.translate(0.0D, lift, 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        float length = entity.getBeamLength();
        if (length > 0.1F) {
            renderBeam(entity, partialTick, poseStack, bufferSource, length);
        }
        // THE FLASH OF THE THIRD TRANSITION (VelkharEntity.P3_FLASH): the Twin Blades become the Magus inside it
        float flash = entity.tickCount - entity.magusFlashAt + partialTick;
        if (flash >= 0.0F && flash < FLASH_TICKS) {
            renderFlash(poseStack, bufferSource, flash / FLASH_TICKS, 7.0F, 0.92F, 0.97F, 1.0F, 1.0F);
        }
        // THE PHASE BREAK'S LIGHT (08.10.2026: "lepsza i ladniejsza animacje pekania zbroi"): on the frame the plate
        // goes, the light that was holding it together leaves him all at once - the same shell and floor ring as the
        // third transition's flash, colder, smaller and quicker, under the pieces flying out through it
        float broke = entity.tickCount - entity.breakFlashAt + partialTick;
        if (broke >= 0.0F && broke < BREAK_FLASH_TICKS) {
            renderFlash(poseStack, bufferSource, broke / BREAK_FLASH_TICKS, 5.2F, 0.72F, 0.90F, 1.0F, 0.85F);
        }
        if (entity.chainTarget() >= 0) {
            renderChain(entity, partialTick, poseStack, bufferSource, 1.0D, packedLight);
            // THE SECOND STRAND. Only the skyhook throws two, and it is drawn
            // as the same chain with its lateral offset flipped rather than as
            // a separate object - one weapon thrown twice, so the two halves
            // sag identically and read as a pair of hands doing one thing.
            if (entity.isTwinChain()) {
                renderChain(entity, partialTick, poseStack, bufferSource, -1.0D, packedLight);
            }
        }
        poseStack.popPose();
    }

    /**
     * How far up he has to be drawn to stand on the ice tower he is riding up (TWIN_ASCENT), this frame: the platform
     * where the spire is drawn (IceTowerEntity.platformY(partialTick)) less where he is drawn. Only ever UP - nothing of
     * him belongs inside the spire, and a hop off the top (above it) is his own.
     */
    public static double onTower(VelkharEntity entity, float partialTick) {
        if (entity.getAttackState() != VelkharEntity.TWIN_ASCENT) {
            return 0.0D;
        }
        double x = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
        for (com.jastkub.frozenfortress.entity.boss.IceTowerEntity tower : entity.level().getEntitiesOfClass(
                com.jastkub.frozenfortress.entity.boss.IceTowerEntity.class,
                entity.getBoundingBox().inflate(2.0D, 12.0D, 2.0D))) {
            double dx = tower.getX() - x;
            double dz = tower.getZ() - z;
            if (dx * dx + dz * dz > 1.0D) {
                continue;
            }
            double up = tower.platformY(partialTick) - y;
            if (up > 0.0D && up < 4.0D) {
                return up;
            }
        }
        return 0.0D;
    }

    /**
     * The chain, from his sword fist to whatever it is hooked into - A CHAIN OF ICE, LINK BY LINK.
     *
     * <p>The Turnkey's link and manacle cut from ice (tools/gen_ice_chain.py, velkhar_ice_chain.geo.json): the bones
     * are drawn here one at a time, each link laid along the curve from his fist and every other one turned a quarter
     * round it, as a chain's links lie. The curve is the old one - a parabola whose sag flattens out as
     * {@code chainReach} closes, so the moment it goes taut is the moment the haul starts - and the links are spaced
     * along its true length, so a longer throw is more links, never longer ones. At its end the MANACLE: tumbling
     * round its flight line while the chain is thrown, and closed flat round the one it bit, sized to them, the chain
     * running into its hinge. Two passes over the same placements: the ice, translucent and lit by the room, then its
     * cold core lit from inside (the glowmask: every face's middle and the hinge).
     */
    private void renderChain(VelkharEntity entity, float partialTick, PoseStack poseStack,
                             MultiBufferSource bufferSource, double side, int packedLight) {
        net.minecraft.world.entity.Entity hooked =
                entity.level().getEntity(entity.chainTarget());
        if (hooked == null) {
            return;
        }
        BakedGeoModel geo = software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().get(ICE_CHAIN_GEO);
        GeoBone link = geo == null ? null : geo.getBone("link").orElse(null);
        GeoBone manacle = geo == null ? null : geo.getBone("manacle").orElse(null);
        if (link == null || manacle == null) {
            return;
        }
        float reach = smoothReach(entity, partialTick);
        // Both ends in the entity's own local space: the pose stack is already
        // translated to his interpolated position, so everything is relative
        // to that and nothing has to un-rotate the model's own yaw.
        Vec3 origin = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        // The hand offset is taken against the SAME (un-interpolated) position it
        // was built from, so it stays a pure local offset; mixing it with the
        // interpolated origin would make the chain's near end jitter by exactly
        // one frame of his movement.
        Vec3 from = entity.chainHand().subtract(entity.position());
        if (side < 0.0D) {
            // Mirror the hand across his own facing: split the offset into
            // along-the-body and across-the-body parts and flip only the
            // second, so the other hand ends up where the other hand is
            // regardless of which way he happens to be turned.
            float yaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
                    * ((float) Math.PI / 180.0F);
            double ax = -Mth.sin(yaw);
            double az = Mth.cos(yaw);
            double along = from.x * ax + from.z * az;
            double across = from.x * az - from.z * ax;
            from = new Vec3(ax * along - az * across, from.y, az * along + ax * across);
        }
        // the middle of whoever it is meant for: the manacle closes round them there
        Vec3 heart = new Vec3(
                Mth.lerp(partialTick, hooked.xOld, hooked.getX()),
                Mth.lerp(partialTick, hooked.yOld, hooked.getY()) + hooked.getBbHeight() * 0.55D,
                Mth.lerp(partialTick, hooked.zOld, hooked.getZ())).subtract(origin);
        // the manacle: wide enough to go round them (its opening is 4.2 of its 7)
        float ring = Mth.clamp((hooked.getBbWidth() + 0.2F) * 16.0F / 4.2F, 2.6F, 6.0F);
        Vec3 home = new Vec3(from.x - heart.x, 0.0D, from.z - heart.z);
        home = home.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : home.normalize();
        // its hinge turned to him; while it flies it tumbles round its line, and lands flat
        float tumble = (1.0F - reach) * (float) Math.PI * 3.0F;
        Quaternionf turn = new Quaternionf()
                .rotationAxis(tumble, (float) home.x, 0.0F, (float) home.z)
                .rotateY((float) Math.atan2(-home.x, -home.z));
        Vector3f eye = turn.transform(new Vector3f(CHAIN_EYE).mul(ring / 16.0F));
        // closed round them, its hinge is the chain's end; thrown, the hinge rides the chain's tip
        Vec3 end = heart.add(eye.x, eye.y, eye.z);
        Vec3 to = from.add(end.subtract(from).scale(reach));
        Vec3 ringAt = to.subtract(eye.x, eye.y, eye.z);
        float sag = (1.0F - reach) * 2.6F + (float) to.subtract(from).length() * 0.06F;

        // ---- the curve, and its length
        int fine = 40;
        Vec3[] curve = new Vec3[fine + 1];
        double[] run = new double[fine + 1];
        for (int i = 0; i <= fine; i++) {
            float t = i / (float) fine;
            curve[i] = from.add(to.subtract(from).scale(t)).add(0.0D, -sag * (t - t * t) * 4.0D, 0.0D);
            run[i] = i == 0 ? 0.0D : run[i - 1] + curve[i].distanceTo(curve[i - 1]);
        }
        double total = run[fine];
        // ---- the links: as many as fit at the Turnkey's spacing, spread to end exactly at the hinge
        java.util.List<PoseStack.Pose> links = new java.util.ArrayList<>();
        int n = total <= LINK_LENGTH ? 1 : (int) Math.round((total - LINK_LENGTH) / LINK_PITCH) + 1;
        double pitch = n > 1 ? (total - LINK_LENGTH) / (n - 1) : 0.0D;
        for (int k = 0, i = 0; k < n; k++) {
            double s = n > 1 ? LINK_LENGTH * 0.5D + pitch * k : total * 0.5D;
            while (i < fine - 1 && run[i + 1] < s) {
                i++;
            }
            double f = Mth.clamp((s - run[i]) / Math.max(1.0E-6D, run[i + 1] - run[i]), 0.0D, 1.0D);
            Vec3 at = curve[i].lerp(curve[i + 1], f);
            Vec3 dir = curve[i + 1].subtract(curve[i]);
            if (dir.lengthSqr() < 1.0E-8D) {
                dir = new Vec3(0.0D, -1.0D, 0.0D);
            }
            dir = dir.normalize();
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z)
                    .rotateY(k % 2 == 0 ? 0.0F : (float) Math.PI * 0.5F));
            poseStack.scale(LINK_SCALE, LINK_SCALE, LINK_SCALE);
            poseStack.translate(0.0F, -LINK_PIVOT_Y, 0.0F);
            links.add(poseStack.last());
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.translate(ringAt.x, ringAt.y, ringAt.z);
        poseStack.mulPose(turn);
        poseStack.scale(ring, ring, ring);
        poseStack.translate(0.0F, -MANACLE_PIVOT_Y, 0.0F);
        PoseStack.Pose cuff = poseStack.last();
        poseStack.popPose();

        // ---- the ice, then its light
        drawChainBones(poseStack, links, cuff, link, manacle,
                bufferSource.getBuffer(RenderType.entityTranslucent(ICE_CHAIN)), packedLight, 0.84F, 0.95F, 1.0F, 1.0F);
        drawChainBones(poseStack, links, cuff, link, manacle,
                bufferSource.getBuffer(software.bernie.geckolib.cache.texture.AutoGlowingTexture.getRenderType(ICE_CHAIN)),
                0xF000F0, 0.80F, 0.95F, 1.0F, 0.9F);
    }

    /** How long the light of the third transition's flash lasts, ticks, and what it is drawn with (any white). */
    private static final float FLASH_TICKS = 16.0F;
    /** And the phase break's. */
    private static final float BREAK_FLASH_TICKS = 11.0F;
    private static final ResourceLocation FLASH_TEX = FrozenFortress.id("textures/environment/whiteout_flake.png");

    /**
     * THE FLASH, AS GEOMETRY: a shell of
     * white light bursting off him - from his own size to seven blocks out in the first half of it, thinning as it goes
     * - and a ring of it running out along the floor. Drawn from both sides, so it is seen from inside as well.
     * `k` runs 0 to 1 over the flash; `reach` is how far the shell gets (blocks), the colour its light, `strength`
     * how bright it starts (the phase break's is a little dimmer than the Magus's).
     */
    private void renderFlash(PoseStack poseStack, MultiBufferSource bufferSource, float k, float reach,
                             float red, float green, float blue, float strength) {
        float out = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
        float radius = 0.8F + reach * out;
        float alpha = 0.85F * strength * (1.0F - k) * (1.0F - k);
        float[] tint = {red, green, blue};
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(FLASH_TEX));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float cy = 2.4F;
        int seg = 18, rings = 9;
        for (int j = 0; j < rings; j++) {
            float p0 = (float) Math.PI * j / rings - (float) Math.PI * 0.5F;
            float p1 = (float) Math.PI * (j + 1) / rings - (float) Math.PI * 0.5F;
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg;
                float a1 = Mth.TWO_PI * (i + 1) / seg;
                float[][] q = {sphere(a0, p0, radius, cy), sphere(a1, p0, radius, cy),
                        sphere(a1, p1, radius, cy), sphere(a0, p1, radius, cy)};
                for (int v = 0; v < 4; v++) {
                    flashVertex(vc, m, n, q[v], tint, alpha);
                }
                for (int v = 3; v >= 0; v--) {
                    flashVertex(vc, m, n, q[v], tint, alpha);
                }
            }
        }
        // the ring along the floor, running out ahead of the shell
        float r0 = radius * 1.25F, r1 = r0 + 0.9F;
        float ring = 0.9F * strength * (1.0F - k);
        for (int i = 0; i < 32; i++) {
            float a0 = Mth.TWO_PI * i / 32.0F;
            float a1 = Mth.TWO_PI * (i + 1) / 32.0F;
            float[][] q = {{Mth.cos(a0) * r0, 0.06F, Mth.sin(a0) * r0}, {Mth.cos(a1) * r0, 0.06F, Mth.sin(a1) * r0},
                    {Mth.cos(a1) * r1, 0.06F, Mth.sin(a1) * r1}, {Mth.cos(a0) * r1, 0.06F, Mth.sin(a0) * r1}};
            for (int v = 0; v < 4; v++) {
                flashVertex(vc, m, n, q[v], tint, v < 2 ? ring : 0.0F);
            }
            for (int v = 3; v >= 0; v--) {
                flashVertex(vc, m, n, q[v], tint, v < 2 ? ring : 0.0F);
            }
        }
    }

    private static float[] sphere(float yaw, float pitch, float r, float cy) {
        float c = Mth.cos(pitch);
        return new float[]{Mth.cos(yaw) * c * r, cy + Mth.sin(pitch) * r, Mth.sin(yaw) * c * r};
    }

    private static void flashVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float[] tint, float alpha) {
        vc.vertex(m, p[0], p[1], p[2])
                .color(tint[0], tint[1], tint[2], alpha)
                .uv(0.5F, 0.5F)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(n, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** Per king: the reach of the tick before and of this one, and which tick that was. */
    private static final java.util.Map<Integer, float[]> REACH = new java.util.HashMap<>();

    /** How far the chain has been thrown, between ticks: the synced reach moves a ninth of the throw a tick, and drawn
     *  a tick at a time the tumbling manacle stuttered round. A new throw (the reach falling back) starts clean. */
    private static float smoothReach(VelkharEntity entity, float partialTick) {
        float now = Mth.clamp(entity.chainReach(), 0.0F, 1.0F);
        if (REACH.size() > 16) {
            REACH.keySet().removeIf(id -> entity.level().getEntity(id) == null);
        }
        float[] r = REACH.computeIfAbsent(entity.getId(), k -> new float[]{now, now, entity.tickCount});
        if ((int) r[2] != entity.tickCount) {
            r[0] = now < r[1] - 0.3F ? now : r[1];
            r[1] = now;
            r[2] = entity.tickCount;
        }
        return Mth.lerp(partialTick, r[0], r[1]);
    }

    /** Every placed link, then the manacle, into one buffer (one pass of the chain). */
    private void drawChainBones(PoseStack poseStack, java.util.List<PoseStack.Pose> links, PoseStack.Pose cuff,
                                GeoBone link, GeoBone manacle, VertexConsumer buffer, int light,
                                float r, float g, float b, float a) {
        for (PoseStack.Pose at : links) {
            placed(poseStack, at, link, buffer, light, r, g, b, a);
        }
        placed(poseStack, cuff, manacle, buffer, light, r, g, b, a);
    }

    private void placed(PoseStack poseStack, PoseStack.Pose at, GeoBone bone, VertexConsumer buffer, int light,
                        float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.last().pose().set(at.pose());
        poseStack.last().normal().set(at.normal());
        renderCubesOfBone(poseStack, bone, buffer, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, r, g, b, a);
        poseStack.popPose();
    }

    /**
     * Draws the Hollow Winter beam as real geometry rather than a column of
     * particles: a translucent mantle with a full-bright core threaded down
     * the middle, both scrolling along their length so the thing reads as
     * something being poured rather than something standing still.
     *
     * Corner positions are built straight from the world-space direction
     * vector instead of rotating the pose stack, so the beam always points
     * exactly where the server says it does.
     */
    private void renderBeam(VelkharEntity entity, float partialTick, PoseStack poseStack,
                            MultiBufferSource bufferSource, float length) {
        // Taken from the same helper the damage test uses, so what is drawn
        // and what actually burns can never point in different directions.
        net.minecraft.world.phys.Vec3 dir =
                VelkharEntity.beamDirection(entity.getBeamYaw(), entity.getBeamPitch());
        net.minecraft.world.phys.Vec3 hint = Math.abs(dir.y) > 0.995D
                ? new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D)
                : new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D);
        net.minecraft.world.phys.Vec3 right = dir.cross(hint).normalize();
        net.minecraft.world.phys.Vec3 up = right.cross(dir).normalize();

        float time = entity.tickCount + partialTick;
        float scroll = -time * 0.22F;
        float vLen = length / TILE_LENGTH;
        // a slow pulse so the beam breathes while it burns
        float pulse = 1.0F + 0.12F * Mth.sin(time * 0.9F);

        poseStack.pushPose();
        poseStack.translate(0.0D, BEAM_ORIGIN_Y, 0.0D);
        PoseStack.Pose pose = poseStack.last();

        // mantle: translucent, wide, dim
        drawTube(pose, bufferSource.getBuffer(RenderType.entityTranslucent(BEAM_OUTER)),
                dir, right, up, length, OUTER_RADIUS * pulse,
                scroll, vLen, 0.45F, 0.78F, 1.0F, 0.55F);
        // core: full-bright, narrow, near white
        drawTube(pose, bufferSource.getBuffer(RenderType.entityTranslucentEmissive(BEAM_INNER)),
                dir, right, up, length, INNER_RADIUS * pulse,
                scroll * 1.7F, vLen * 2.0F, 0.85F, 0.97F, 1.0F, 0.95F);

        poseStack.popPose();
    }

    /**
     * A square tube running from the origin out to {@code length} along
     * (dx, dz), built as four quads. Both faces of each quad are emitted so
     * the beam is solid from inside as well as outside.
     */
    /** A square tube along an arbitrary 3D axis. The corners are built from
     *  the beam's own basis rather than assumed horizontal, so a beam angled
     *  down at a player draws as the same solid shaft as a level one. */
    private void drawTube(PoseStack.Pose pose, VertexConsumer buffer,
                          net.minecraft.world.phys.Vec3 dir,
                          net.minecraft.world.phys.Vec3 right,
                          net.minecraft.world.phys.Vec3 up,
                          float length, float radius,
                          float vOffset, float vLength,
                          float r, float g, float b, float a) {
        net.minecraft.world.phys.Vec3 end = dir.scale(length);
        net.minecraft.world.phys.Vec3[] corners = {
                right.scale(radius).add(up.scale(radius)),
                right.scale(radius).subtract(up.scale(radius)),
                right.scale(-radius).subtract(up.scale(radius)),
                right.scale(-radius).add(up.scale(radius)),
        };
        for (int i = 0; i < 4; i++) {
            net.minecraft.world.phys.Vec3 c0 = corners[i];
            net.minecraft.world.phys.Vec3 c1 = corners[(i + 1) % 4];
            quad(pose, buffer, c0, c1, end, vOffset, vLength, r, g, b, a);
            quad(pose, buffer, c1, c0, end, vOffset, vLength, r, g, b, a);
        }
    }

    private void quad(PoseStack.Pose pose, VertexConsumer buffer,
                      net.minecraft.world.phys.Vec3 c0, net.minecraft.world.phys.Vec3 c1,
                      net.minecraft.world.phys.Vec3 end,
                      float v0, float vLen, float r, float g, float b, float a) {
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        vertex(buffer, mat, nrm, (float) c0.x, (float) c0.y, (float) c0.z, 0.0F, v0, r, g, b, a);
        vertex(buffer, mat, nrm, (float) c1.x, (float) c1.y, (float) c1.z, 1.0F, v0, r, g, b, a);
        vertex(buffer, mat, nrm, (float) (c1.x + end.x), (float) (c1.y + end.y), (float) (c1.z + end.z),
                1.0F, v0 + vLen, r, g, b, a);
        vertex(buffer, mat, nrm, (float) (c0.x + end.x), (float) (c0.y + end.y), (float) (c0.z + end.z),
                0.0F, v0 + vLen, r, g, b, a);
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, Matrix3f nrm,
                        float x, float y, float z, float u, float v,
                        float r, float g, float b, float a) {
        buffer.vertex(mat, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)          // full bright: the beam lights itself
                .normal(nrm, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
