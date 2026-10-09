package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HIS WEAPONS ARE MADE IN FRONT OF YOU.
 *
 * <p>THE INTRO. The gate, and then the greatsword, are built out of the air piece by piece. A shard of ice
 * (velkhar_forge_fx's "shard"/"chip", tools/gen_velkhar_forge_fx.py) spirals in out of the room for every box the
 * weapon is made of and, where it lands, that box appears - in ice: the weapon's own geometry drawn translucent and
 * cold, lit from inside, flashing white as each piece locks. The gate fills from its boss out to the rim, the blade
 * from the hilt down to the point. On the beat it is SET (VelkharEntity.INTRO_SHIELD_SET, INTRO_SWORD_SET) the real
 * weapon is there, the ice burns off it in a white flash, a star of light runs its edge, and a ring of frost spikes
 * stands up out of the floor round his feet and sinks again.
 *
 * <p>THE ASCENT'S SWAP (VelkharEntity.ASCENT_PRESENT and on). Frost climbs the raised greatsword from the hilt to the
 * point; on ASCENT_FORGE the blade BURSTS - its own forty boxes, with its own paint, thrown out round his fists - and
 * the pieces hang there, turning, going to ice. From ASCENT_REFORM they fly in, two or three to a piece, and the
 * crossbow is laid down out of them in ice, the stock from the grip forward and then the prod out from the riser;
 * on ASCENT_SOLID the ice is the crossbow in a flash and a glint runs the prod. When it is torn apart at
 * ASCENT_BREAK its pieces are thrown the same way, and fall.
 *
 * <p>Everything here is geometry: his own boxes and the forge model's, drawn by bone. It is timed off the entity's
 * scene clock (VelkharEntity.sceneClock), which starts on the packet the animation controller starts the clip on, so
 * the picture and the clip agree; a king first seen in the middle of a scene gets none of it and falls back on the
 * synced flags. Poses are CAPTURED in renderForBone (his right hand and his left forearm) and drawn in render, after
 * the model pass, as MagusGarbLayer does - and kept relative to the entity rather than to the camera, so a piece
 * left hanging in the air stays where it was however the shot drifts.
 */
public class VelkharForgeLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation FX_GEO = FrozenFortress.id("geo/entity/velkhar_forge_fx.geo.json");
    private static final ResourceLocation FX_TEX = FrozenFortress.id("textures/entity/velkhar_forge_fx.png");
    /** The forge model's pivots (tools/gen_velkhar_forge_fx.py SHARD, CHIP, GLINT, SPIKE), blocks. */
    private static final Vector3f SHARD_PIVOT = new Vector3f(0.0F, 4.5F, 0.0F).div(16.0F);
    private static final Vector3f CHIP_PIVOT = new Vector3f(12.0F, 3.0F, 0.0F).div(16.0F);
    private static final Vector3f GLINT_PIVOT = new Vector3f(24.0F, 4.0F, 0.0F).div(16.0F);
    private static final Vector3f SPIKE_PIVOT = new Vector3f(40.0F, 0.0F, 0.0F).div(16.0F);

    /** The bones whose pose is taken: the weapons hang off these. */
    private static final String[] CAP = {"hand_r", "lower_arm_l"};
    private static final int HAND_R = 0, ARM_L = 1;
    private static final Matrix4f[] POSE = {new Matrix4f(), new Matrix4f()};
    private static final boolean[] CAPTURED = new boolean[CAP.length];

    // ---- the intro's beats on the client's side (the server's are VelkharEntity.INTRO_*)
    /** A gate shard's flight, and the window its pieces land in (INTRO_SHIELD_LOCKS to two before the set). */
    private static final float SHIELD_FLIGHT = 9.0F;
    private static final float SHIELD_LAND_FROM = VelkharEntity.INTRO_SHIELD_LOCKS;
    private static final float SHIELD_LAND_TO = VelkharEntity.INTRO_SHIELD_SET - 2;
    private static final float SWORD_FLIGHT = 6.0F;
    private static final float SWORD_LAND_FROM = VelkharEntity.INTRO_SWORD_LOCKS + 1;
    private static final float SWORD_LAND_TO = VelkharEntity.INTRO_SWORD_SET - 1;
    /** How long the white flash of a finished weapon, its glint and its ring of frost last. */
    private static final float SET_FLASH = 8.0F;
    private static final float GLINT_RUN = 9.0F;
    private static final float RING_UP = 4.0F, RING_HOLD = 10.0F, RING_DOWN = 12.0F;
    // ---- the swap's
    private static final float HANG_OUT = 3.0F;
    private static final float BOW_FLIGHT = 2.5F;
    private static final float BOW_DEPART_SPAN = VelkharEntity.ASCENT_SOLID - VelkharEntity.ASCENT_REFORM - BOW_FLIGHT;
    private static final float BREAK_FALL = 14.0F;

    /** One box of a weapon: whose it is, the box, where its middle is in that bone (blocks), its place in the
     *  order the weapon is built in (0 first, 1 last). */
    private record Piece(String bone, GeoCube cube, Vector3f mid, float rank) {
    }

    /** What a scene left behind for later frames: the blade where it burst, the crossbow where it broke, and
     *  whether the weapons were seen being made (only those get the finishing flash). */
    private static final class Scene {
        int at;
        Matrix4f burst;
        Matrix4f[] broke;
        boolean sawGate, sawBlade;
    }

    private static final Map<Integer, Scene> SCENES = new HashMap<>();
    private static BakedGeoModel piecesOf;
    private static List<Piece> gatePieces, bladePieces, bowPieces;

    public VelkharForgeLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    private static boolean forging(VelkharEntity e) {
        int s = e.getAttackState();
        return s == VelkharEntity.INTRO || s == VelkharEntity.TWIN_ASCENT;
    }

    @Override
    public void renderForBone(PoseStack poseStack, VelkharEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!forging(animatable)) {
            return;
        }
        String name = bone.getName();
        for (int i = 0; i < CAP.length; i++) {
            if (CAP[i].equals(name)) {
                POSE[i].set(poseStack.last().pose());
                CAPTURED[i] = true;
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        try {
            float clock = animatable.sceneClock(partialTick);
            if (!forging(animatable) || clock < 0.0F || animatable.isInvisible()
                    || !CAPTURED[HAND_R] || !CAPTURED[ARM_L]) {
                return;
            }
            BakedGeoModel fx = GeckoLibCache.getBakedModels().get(FX_GEO);
            if (fx == null) {
                return;
            }
            pieces(bakedModel);
            Scene scene = scene(animatable);
            Draw d = new Draw(poseStack, bufferSource, bakedModel, fx, animatable, packedLight, clock, scene);
            if (animatable.getAttackState() == VelkharEntity.INTRO) {
                for (int pass = 0; pass < PASSES; pass++) {
                    d.begin(pass);
                    intro(d);
                }
            } else {
                for (int pass = 0; pass < PASSES; pass++) {
                    d.begin(pass);
                    swap(d);
                }
            }
        } finally {
            java.util.Arrays.fill(CAPTURED, false);
        }
    }

    // ================================================================
    // THE INTRO
    // ================================================================
    private void intro(Draw d) {
        float t = d.clock;
        VelkharEntity e = d.e;
        Matrix4f gate = d.frame(ARM_L, "shield");
        Matrix4f blade = d.frame(HAND_R, "sword");
        if (gate != null) {
            if (e.isShieldGone()) {
                d.scene.sawGate |= build(d, gate, gatePieces, SHIELD_LAND_FROM, SHIELD_LAND_TO, SHIELD_FLIGHT,
                        2.4F, 0.8F, 2, 1.25F);
            } else if (d.scene.sawGate) {
                finished(d, gate, gatePieces, t - VelkharEntity.INTRO_SHIELD_SET);
                rimGlint(d, gate, t - VelkharEntity.INTRO_SHIELD_SET);
            }
        }
        if (blade != null) {
            if (e.isSwordGone()) {
                d.scene.sawBlade |= build(d, blade, bladePieces, SWORD_LAND_FROM, SWORD_LAND_TO, SWORD_FLIGHT,
                        1.9F, 0.7F, 1, 0.95F);
            } else if (d.scene.sawBlade) {
                finished(d, blade, bladePieces, t - VelkharEntity.INTRO_SWORD_SET);
                glint(d, blade, new Vector3f(11.0F, 27.0F, 0.0F).div(16.0F), new Vector3f(11.0F, -19.0F, 0.0F)
                        .div(16.0F), (t - VelkharEntity.INTRO_SWORD_SET) / GLINT_RUN, 1.5F);
            }
        }
        if (d.scene.sawGate) {
            frostRing(d, t - VelkharEntity.INTRO_SHIELD_SET, 12, 1.5F, 2.6F, 1.15F, 0x6A7E);
        }
        if (d.scene.sawBlade) {
            frostRing(d, t - VelkharEntity.INTRO_SWORD_SET, 16, 1.9F, 3.5F, 1.45F, 0x51D);
        }
    }

    /**
     * A weapon being laid down out of flying ice: every piece's shard in the air toward it, and every piece that has
     * landed standing there in ice. `axis` is the axis of the weapon's own frame the shards swirl about (the gate's
     * face normal, the blade's length). True if anything of it was drawn.
     */
    private boolean build(Draw d, Matrix4f frame, List<Piece> pieces, float landFrom, float landTo, float flight,
                          float reach, float reachSpread, int axis, float shardScale) {
        float t = d.clock;
        boolean any = false;
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            float land = landFrom + (landTo - landFrom) * p.rank();
            float u = (t - (land - flight)) / flight;
            if (u < 0.0F) {
                continue;
            }
            any = true;
            if (u < 1.0F) {
                RandomSource rs = RandomSource.create(i * 7919L + (long) (landFrom * 31));
                Vector3f from = randomDir(rs, axis, 0.55F).mul(reach + reachSpread * rs.nextFloat());
                float turn = (rs.nextBoolean() ? 1.0F : -1.0F) * Mth.PI * (1.0F + 0.5F * rs.nextFloat());
                Vector3f at = swirl(p.mid(), from, axis, turn, u);
                Vector3f ahead = swirl(p.mid(), from, axis, turn, Math.min(1.0F, u + 0.05F));
                float s = shardScale * (1.0F - 0.45F * u * u);
                d.fx(frame, (i & 3) == 3 ? Draw.CHIP : Draw.SHARD, at, new Vector3f(ahead).sub(at),
                        t * 25.0F + i * 40.0F, s, Math.min(1.0F, u * 4.0F));
            } else {
                // landed: the piece stands there in ice, and flashes as it locks
                float flash = Math.max(0.0F, 1.0F - (t - land) / 4.0F);
                d.ice(frame, p, 0.52F + 0.4F * flash, flash);
            }
        }
        return any;
    }

    /** The finished weapon: the last of the ice burning off it in a white flash. */
    private void finished(Draw d, Matrix4f frame, List<Piece> pieces, float since) {
        if (since < 0.0F || since > SET_FLASH) {
            return;
        }
        float k = 1.0F - since / SET_FLASH;
        for (Piece p : pieces) {
            d.ice(frame, p, 0.9F * k * k, 1.0F);
        }
    }

    /** Two stars running round the gate's rim from the top of it, meeting at the foot. */
    private void rimGlint(Draw d, Matrix4f gate, float since) {
        float u = since / GLINT_RUN;
        if (u < 0.0F || u > 1.0F) {
            return;
        }
        // the rim of the face, in the gate's own sixteenths (velkhar.geo.json: the boards' frame)
        float x0 = -29.0F, x1 = 0.6F, y0 = -4.0F, y1 = 46.0F, z = -12.6F;
        float w = x1 - x0, h = y1 - y0, half = w + h;
        for (int side = -1; side <= 1; side += 2) {
            float s = u * half;
            float x, y;
            float top = w * 0.5F;
            if (s < top) {
                x = (x0 + x1) * 0.5F + side * s;
                y = y1;
            } else if (s < top + h) {
                x = side > 0 ? x1 : x0;
                y = y1 - (s - top);
            } else {
                x = side > 0 ? x1 - (s - top - h) : x0 + (s - top - h);
                y = y0;
            }
            d.glint(gate, new Vector3f(x, y, z).div(16.0F), Mth.sin(u * Mth.PI) * 1.6F + 0.2F);
        }
    }

    /** A star of light running from `a` to `b` in a frame, swelling in the middle of its run. */
    private void glint(Draw d, Matrix4f frame, Vector3f a, Vector3f b, float u, float size) {
        if (u < 0.0F || u > 1.0F) {
            return;
        }
        float e = u * u * (3.0F - 2.0F * u);
        d.glint(frame, new Vector3f(a).lerp(b, e), Mth.sin(u * Mth.PI) * size + 0.2F);
    }

    /** The ring of frost: spikes standing up out of the floor round his feet, thrown out, then sinking. */
    private void frostRing(Draw d, float since, int n, float r0, float r1, float size, int seed) {
        if (since < 0.0F || since > RING_UP + RING_HOLD + RING_DOWN) {
            return;
        }
        RandomSource rs = RandomSource.create(seed);
        float out = Math.min(1.0F, since / RING_UP);
        out = 1.0F - (1.0F - out) * (1.0F - out);
        float down = Math.max(0.0F, (since - RING_UP - RING_HOLD) / RING_DOWN);
        for (int i = 0; i < n; i++) {
            float a = Mth.TWO_PI * (i + rs.nextFloat() * 0.6F) / n;
            float r = Mth.lerp(out, r0 * 0.6F, r0 + (r1 - r0) * (0.7F + 0.3F * rs.nextFloat()));
            float rise = Mth.clamp((since - rs.nextFloat() * 1.5F) / 2.5F, 0.0F, 1.0F);
            float h = rise * (1.0F - down) * size * (0.7F + 0.5F * rs.nextFloat());
            if (h <= 0.01F) {
                continue;
            }
            d.spike(new Vector3f(Mth.cos(a) * r, -down * 0.4F, Mth.sin(a) * r), a,
                    18.0F + 14.0F * rs.nextFloat(), rs.nextFloat() * 360.0F, size * (0.85F + 0.3F * rs.nextFloat()),
                    h / size);
        }
    }

    // ================================================================
    // THE SWAP (and the crossbow's own end)
    // ================================================================
    private void swap(Draw d) {
        float t = d.clock;
        VelkharEntity e = d.e;
        Matrix4f blade = d.frame(HAND_R, "sword");
        Matrix4f bow = d.frame(HAND_R, "crossbow");
        if (blade == null || bow == null) {
            return;
        }
        // ---- 62-68: frost climbs the raised blade from the hilt to the point, a star riding the front of it
        if (t >= VelkharEntity.ASCENT_PRESENT && t < VelkharEntity.ASCENT_FORGE && e.bowForm() <= 0.0F) {
            float span = VelkharEntity.ASCENT_FORGE - VelkharEntity.ASCENT_PRESENT;
            float front = (t - VelkharEntity.ASCENT_PRESENT) / (span - 1.0F);
            for (Piece p : bladePieces) {
                float on = Mth.clamp((front - p.rank()) * 6.0F, 0.0F, 1.0F);
                if (on > 0.0F) {
                    d.ice(blade, p, 0.45F * on, Math.max(0.0F, 1.0F - (front - p.rank()) * 8.0F));
                }
            }
            glint(d, blade, new Vector3f(11.0F, 40.0F, 0.0F).div(16.0F), new Vector3f(11.0F, -19.0F, 0.0F)
                    .div(16.0F), front, 1.2F);
        }
        // ---- 68 on: the blade in pieces - burst, hanging, flying into the crossbow. WHERE IT WAS HELD UP: the clip
        //      wrenches his hands apart on the very frame of the burst, so the frame the blade bursts from is the
        //      last one before it (kept up to date through the presentation), not the first one after
        if (t >= VelkharEntity.ASCENT_PRESENT && t < VelkharEntity.ASCENT_FORGE && e.bowForm() <= 0.0F) {
            d.scene.burst = new Matrix4f(blade);
        }
        if (t >= VelkharEntity.ASCENT_FORGE && t < VelkharEntity.ASCENT_SOLID + 1.0F && e.bowForm() > 0.0F) {
            if (d.scene.burst == null) {
                d.scene.burst = new Matrix4f(blade);
            }
            shards(d, d.scene.burst, bow, t);
        }
        // ---- the crossbow laid down in ice as its pieces arrive, until it is real
        if (t >= VelkharEntity.ASCENT_REFORM && e.bowForm() > 0.0F && e.bowForm() < VelkharEntity.BOW_SOLID) {
            for (Piece p : bowPieces) {
                float land = arrival(p);
                if (t >= land) {
                    float flash = Math.max(0.0F, 1.0F - (t - land) / 3.0F);
                    d.ice(bowFrame(d, bow, p), p, 0.55F + 0.4F * flash, flash);
                }
            }
        }
        // ---- 81: it is real, the ice burns off it, a star runs the prod
        if (e.bowForm() >= VelkharEntity.BOW_SOLID && !e.isDualWielding()) {
            float since = t - VelkharEntity.ASCENT_SOLID;
            if (since >= 0.0F && since <= SET_FLASH) {
                float k = 1.0F - since / SET_FLASH;
                for (Piece p : bowPieces) {
                    d.ice(bowFrame(d, bow, p), p, 0.9F * k * k, 1.0F);
                }
            }
            Matrix4f limbL = d.child(bow, "bow_limb_l");
            Matrix4f limbR = d.child(bow, "bow_limb_r");
            if (limbL != null && limbR != null && since >= 0.0F && since <= GLINT_RUN) {
                float u = since / GLINT_RUN;
                Vector3f a = limbL.transformPosition(far(d, "bow_limb_l"));
                Vector3f b = limbR.transformPosition(far(d, "bow_limb_r"));
                Matrix4f id = new Matrix4f();
                glint(d, id, a, b, u, 1.3F);
            }
        }
        // ---- 164: it is torn apart, and its pieces are thrown the same way and fall - from where it was held the
        //      frame before (the clip throws his arms wide on the tear itself)
        if (t >= VelkharEntity.ASCENT_BREAK - 8 && t < VelkharEntity.ASCENT_BREAK && !e.isDualWielding()
                && e.bowForm() >= VelkharEntity.BOW_SOLID) {
            d.scene.broke = new Matrix4f[]{new Matrix4f(bow), d.child(bow, "bow_limb_r"), d.child(bow, "bow_limb_l")};
        }
        if (t >= VelkharEntity.ASCENT_BREAK && t < VelkharEntity.ASCENT_BREAK + BREAK_FALL && e.isDualWielding()) {
            if (d.scene.broke == null) {
                d.scene.broke = new Matrix4f[]{new Matrix4f(bow), d.child(bow, "bow_limb_r"),
                        d.child(bow, "bow_limb_l")};
            }
            broken(d, t - VelkharEntity.ASCENT_BREAK);
        }
    }

    /** When a piece of the crossbow lands (stock first, then the prod out from the riser). */
    private static float arrival(Piece p) {
        return VelkharEntity.ASCENT_REFORM + BOW_DEPART_SPAN * p.rank() + BOW_FLIGHT;
    }

    private Matrix4f bowFrame(Draw d, Matrix4f bow, Piece p) {
        if ("crossbow".equals(p.bone())) {
            return bow;
        }
        Matrix4f limb = d.child(bow, p.bone());
        return limb != null ? limb : bow;
    }

    /** The greatsword's pieces: thrown out from where it burst, hanging and turning to ice, then flying - two or
     *  three to a piece - into the crossbow's boxes. */
    private void shards(Draw d, Matrix4f burst, Matrix4f bow, float t) {
        float since = t - VelkharEntity.ASCENT_FORGE;
        int n = bladePieces.size();
        int m = bowPieces.size();
        if (m == 0) {
            return;
        }
        Matrix3f turnOf = new Matrix3f(burst).normal();
        for (int k = 0; k < n; k++) {
            Piece p = bladePieces.get(k);
            Piece goal = bowPieces.get(Math.min(m - 1, k * m / n));
            RandomSource rs = RandomSource.create(k * 104729L + 17L);
            Vector3f home = burst.transformPosition(new Vector3f(p.mid()));
            Vector3f axis = burst.transformPosition(new Vector3f(11.0F / 16.0F, p.mid().y(), 0.0F));
            Vector3f outward = new Vector3f(home).sub(axis);
            if (outward.lengthSquared() < 1.0E-6F) {
                outward.set(rs.nextFloat() - 0.5F, 0.0F, rs.nextFloat() - 0.5F);
            }
            outward.normalize().add((rs.nextFloat() - 0.5F) * 0.9F, (rs.nextFloat() - 0.3F) * 0.6F,
                    (rs.nextFloat() - 0.5F) * 0.9F).normalize();
            float dist = 0.35F + 0.6F * rs.nextFloat();
            float out = Math.min(1.0F, since / HANG_OUT);
            out = 1.0F - (1.0F - out) * (1.0F - out) * (1.0F - out);
            // hanging: drifting a little, slowly turning
            float drift = Math.max(0.0F, since - HANG_OUT);
            Vector3f hang = new Vector3f(home).add(new Vector3f(outward).mul(dist * out))
                    .add(0.0F, 0.04F * Mth.sin(drift * 0.5F + k), 0.0F);
            float leave = arrival(goal) - BOW_FLIGHT;
            float u = (t - leave) / BOW_FLIGHT;
            if (u >= 1.0F) {
                continue;
            }
            Vector3f at = hang;
            float scale = 1.0F;
            if (u > 0.0F) {
                Vector3f target = bowFrame(d, bow, goal).transformPosition(new Vector3f(goal.mid()));
                float e = u * u * (3.0F - 2.0F * u);
                at = new Vector3f(hang).lerp(target, e).add(0.0F, 0.25F * Mth.sin(u * Mth.PI), 0.0F);
                scale = 1.0F - 0.5F * u;
            }
            Vector3f spinAxis = new Vector3f(rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F)
                    .normalize();
            float spin = (0.12F + 0.2F * rs.nextFloat()) * since * (1.0F + 2.0F * Math.max(0.0F, u));
            Quaternionf turn = new Quaternionf().rotationAxis(spin, spinAxis).mul(new Quaternionf()
                    .setFromNormalized(turnOf));
            // going to ice while it hangs: the steel's own paint, and the cold coming up over it
            float cold = Mth.clamp(since / 5.0F, 0.0F, 1.0F);
            float flash = Math.max(0.0F, 1.0F - since / 2.0F);
            d.piece(p, at, turn, scale, cold * 0.7F + flash * 0.3F, flash);
        }
    }

    /** The crossbow torn apart: its pieces thrown out from where it was, falling, turning, gone in BREAK_FALL. */
    private void broken(Draw d, float since) {
        Matrix4f[] at = d.scene.broke;
        for (int k = 0; k < bowPieces.size(); k++) {
            Piece p = bowPieces.get(k);
            Matrix4f frame = "crossbow".equals(p.bone()) ? at[0] : "bow_limb_r".equals(p.bone()) ? at[1] : at[2];
            if (frame == null) {
                continue;
            }
            RandomSource rs = RandomSource.create(k * 31337L + 5L);
            Vector3f home = frame.transformPosition(new Vector3f(p.mid()));
            Vector3f v = new Vector3f(rs.nextFloat() - 0.5F, 0.4F + 0.5F * rs.nextFloat(), rs.nextFloat() - 0.5F)
                    .normalize().mul(0.16F + 0.14F * rs.nextFloat());
            Vector3f pos = new Vector3f(home).add(new Vector3f(v).mul(since)).add(0.0F, -0.03F * since * since, 0.0F);
            Vector3f spinAxis = new Vector3f(rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F)
                    .normalize();
            Quaternionf turn = new Quaternionf().rotationAxis(since * (0.25F + 0.3F * rs.nextFloat()), spinAxis)
                    .mul(new Quaternionf().setFromNormalized(new Matrix3f(frame).normal()));
            float k01 = since / BREAK_FALL;
            d.piece(p, pos, turn, 1.0F - k01 * k01, 0.6F, Math.max(0.0F, 1.0F - since / 3.0F));
        }
    }

    /** The far end of a limb, in its bone (blocks): the middle of its box furthest from its pivot. */
    private static Vector3f far(Draw d, String limb) {
        Vector3f best = new Vector3f();
        float bd = -1.0F;
        GeoBone b = d.model.getBone(limb).orElse(null);
        if (b == null) {
            return best;
        }
        Vector3f pivot = new Vector3f(b.getPivotX(), b.getPivotY(), b.getPivotZ()).div(16.0F);
        for (Piece p : bowPieces) {
            if (limb.equals(p.bone())) {
                float dd = p.mid().distanceSquared(pivot);
                if (dd > bd) {
                    bd = dd;
                    best.set(p.mid());
                }
            }
        }
        return best;
    }

    // ================================================================
    // The pieces of each weapon, and the order they are made in
    // ================================================================
    private static void pieces(BakedGeoModel model) {
        if (model == piecesOf) {
            return;
        }
        piecesOf = model;
        // the gate from its boss out to the rim
        Vector3f boss = new Vector3f(-14.0F, 16.5F, -12.0F).div(16.0F);
        gatePieces = ordered(model, new String[]{"shield"}, p -> p.distance(boss));
        // the blade from the pommel, down the grip and the guard, to the point
        bladePieces = ordered(model, new String[]{"sword"}, p -> -p.y());
        // the crossbow: the stock out from the grip first, then the prod out from the riser
        GeoBone bow = model.getBone("crossbow").orElse(null);
        Vector3f grip = bow == null ? new Vector3f() : new Vector3f(bow.getPivotX(), bow.getPivotY(), bow.getPivotZ())
                .div(16.0F);
        List<Piece> stock = ordered(model, new String[]{"crossbow"}, p -> p.distance(grip));
        List<Piece> prod = ordered(model, new String[]{"bow_limb_r", "bow_limb_l"}, p -> p.distance(grip));
        List<Piece> all = new ArrayList<>();
        int total = stock.size() + prod.size();
        for (Piece p : stock) {
            all.add(new Piece(p.bone(), p.cube(), p.mid(), total <= 1 ? 0.0F : all.size() / (float) (total - 1)));
        }
        for (Piece p : prod) {
            all.add(new Piece(p.bone(), p.cube(), p.mid(), total <= 1 ? 0.0F : all.size() / (float) (total - 1)));
        }
        bowPieces = all;
    }

    private static List<Piece> ordered(BakedGeoModel model, String[] bones, java.util.function.ToDoubleFunction<Vector3f> key) {
        List<Piece> out = new ArrayList<>();
        for (String name : bones) {
            GeoBone b = model.getBone(name).orElse(null);
            if (b == null) {
                continue;
            }
            for (GeoCube c : b.getCubes()) {
                Vector3f mid = middle(c);
                if (mid != null) {
                    out.add(new Piece(name, c, mid, 0.0F));
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> key.applyAsDouble(p.mid())));
        List<Piece> ranked = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            Piece p = out.get(i);
            ranked.add(new Piece(p.bone(), p.cube(), p.mid(), out.size() <= 1 ? 0.0F : i / (float) (out.size() - 1)));
        }
        return ranked;
    }

    /** The middle of a box in its bone (blocks), its own turn about its pivot applied. */
    private static Vector3f middle(GeoCube c) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (GeoQuad q : c.quads()) {
            if (q == null) {
                continue;
            }
            for (GeoVertex v : q.vertices()) {
                any = true;
                lo[0] = Math.min(lo[0], v.position().x());
                lo[1] = Math.min(lo[1], v.position().y());
                lo[2] = Math.min(lo[2], v.position().z());
                hi[0] = Math.max(hi[0], v.position().x());
                hi[1] = Math.max(hi[1], v.position().y());
                hi[2] = Math.max(hi[2], v.position().z());
            }
        }
        if (!any) {
            return null;
        }
        PoseStack ps = new PoseStack();
        RenderUtil.translateToPivotPoint(ps, c);
        RenderUtil.rotateMatrixAroundCube(ps, c);
        RenderUtil.translateAwayFromPivotPoint(ps, c);
        return ps.last().pose().transformPosition(new Vector3f((lo[0] + hi[0]) * 0.5F, (lo[1] + hi[1]) * 0.5F,
                (lo[2] + hi[2]) * 0.5F));
    }

    /** One direction at random, squashed along one axis of the weapon's frame (so the swarm hugs its plane). */
    private static Vector3f randomDir(RandomSource rs, int axis, float squash) {
        Vector3f v = new Vector3f(rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F, rs.nextFloat() - 0.5F);
        if (v.lengthSquared() < 1.0E-4F) {
            v.set(1.0F, 0.0F, 0.0F);
        }
        v.normalize();
        v.setComponent(axis, v.get(axis) * squash);
        return v.normalize();
    }

    /** A shard on its way in: the offset it started at, closing (faster and faster) and turning about `axis`. */
    private static Vector3f swirl(Vector3f target, Vector3f from, int axis, float turn, float u) {
        float closing = (float) Math.pow(Math.max(0.0F, 1.0F - u), 1.6F);
        float a = turn * (1.0F - u);
        Vector3f off = new Vector3f(from).mul(closing);
        Quaternionf q = new Quaternionf();
        if (axis == 0) {
            q.rotationX(a);
        } else if (axis == 1) {
            q.rotationY(a);
        } else {
            q.rotationZ(a);
        }
        return q.transform(off).add(target);
    }

    private Scene scene(VelkharEntity e) {
        Scene s = SCENES.get(e.getId());
        if (s == null || s.at != e.sceneAt) {
            if (SCENES.size() > 8) {
                SCENES.clear();
            }
            s = new Scene();
            s.at = e.sceneAt;
            SCENES.put(e.getId(), s);
        }
        return s;
    }

    // ================================================================
    // Drawing - one render type at a time (a buffer source ends the batch it was writing when another is asked
    // for, so every pass asks once and draws all of its own)
    // ================================================================
    private static final int PASSES = 4;
    /** The forge model's ice lit by the room; its glowmask full bright; his own boxes in their paint; his own boxes
     *  as ice and light. */
    private static final int FX_LIT = 0, FX_GLOW = 1, OWN_LIT = 2, OWN_ICE = 3;

    private final class Draw {
        static final int SHARD = 0, CHIP = 1;
        final PoseStack ps;
        final MultiBufferSource buffers;
        final BakedGeoModel model;
        final BakedGeoModel fx;
        final VelkharEntity e;
        final int light;
        final float clock;
        final Scene scene;
        final Matrix4f base;
        final Matrix4f toLocal;
        final ResourceLocation sheet;
        int pass;
        VertexConsumer vc;

        Draw(PoseStack ps, MultiBufferSource buffers, BakedGeoModel model, BakedGeoModel fx, VelkharEntity e,
             int light, float clock, Scene scene) {
            this.ps = ps;
            this.buffers = buffers;
            this.model = model;
            this.fx = fx;
            this.e = e;
            this.light = light;
            this.clock = clock;
            this.scene = scene;
            this.base = new Matrix4f(ps.last().pose());
            this.toLocal = new Matrix4f(base).invert();
            this.sheet = getRenderer().getTextureLocation(e);
        }

        void begin(int pass) {
            this.pass = pass;
            this.vc = null;
        }

        private VertexConsumer buffer() {
            if (vc == null) {
                vc = buffers.getBuffer(switch (pass) {
                    case FX_LIT -> RenderType.entityTranslucent(FX_TEX);
                    case FX_GLOW -> AutoGlowingTexture.getRenderType(FX_TEX);
                    case OWN_LIT -> RenderType.entityTranslucent(sheet);
                    default -> RenderType.entityTranslucentEmissive(sheet);
                });
            }
            return vc;
        }

        /** A weapon bone's frame, relative to him (not to the camera): the captured pose of the bone it hangs off,
         *  then the bone's own place and turn - never its scale, which the renderer uses to grow and hide it. */
        Matrix4f frame(int captured, String bone) {
            GeoBone b = model.getBone(bone).orElse(null);
            if (b == null) {
                return null;
            }
            return new Matrix4f(toLocal).mul(POSE[captured]).mul(local(b));
        }

        Matrix4f child(Matrix4f parent, String bone) {
            GeoBone b = model.getBone(bone).orElse(null);
            return b == null ? null : new Matrix4f(parent).mul(local(b));
        }

        private Matrix4f local(GeoBone b) {
            PoseStack l = new PoseStack();
            RenderUtil.translateMatrixToBone(l, b);
            RenderUtil.translateToPivotPoint(l, b);
            RenderUtil.rotateMatrixAroundBone(l, b);
            RenderUtil.translateAwayFromPivotPoint(l, b);
            return new Matrix4f(l.last().pose());
        }

        private void at(Matrix4f rel) {
            Matrix4f m = new Matrix4f(base).mul(rel);
            ps.last().pose().set(m);
            ps.last().normal().set(m.normal(new Matrix3f()));
        }

        /** One of his boxes, where it belongs in `frame`, as ice: translucent and cold, lit from inside; `white`
         *  takes it to a white flash. */
        void ice(Matrix4f frame, Piece p, float alpha, float white) {
            if (pass != OWN_ICE || alpha <= 0.01F) {
                return;
            }
            ps.pushPose();
            at(frame);
            float r = Mth.lerp(white, 0.58F, 1.0F), g = Mth.lerp(white, 0.84F, 1.0F);
            getRenderer().renderCube(ps, p.cube(), buffer(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    com.jastkub.frozenfortress.util.FFColor.argb(r, g, 1.0F, Math.min(1.0F, alpha)));
            ps.popPose();
        }

        /** One of his boxes loose in the air, at `pos` (relative to him), turned `turn` about its own middle and
         *  scaled: in its own paint, and as much ice over it as `cold` says. */
        void piece(Piece p, Vector3f pos, Quaternionf turn, float scale, float cold, float white) {
            if ((pass != OWN_LIT && pass != OWN_ICE) || scale <= 0.01F) {
                return;
            }
            if (pass == OWN_ICE && cold <= 0.01F) {
                return;
            }
            ps.pushPose();
            at(new Matrix4f().translation(pos).rotate(turn).scale(scale).translate(-p.mid().x(), -p.mid().y(),
                    -p.mid().z()));
            if (pass == OWN_LIT) {
                getRenderer().renderCube(ps, p.cube(), buffer(), light, OverlayTexture.NO_OVERLAY,
                        com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, 1.0F));
            } else {
                float r = Mth.lerp(white, 0.6F, 1.0F), g = Mth.lerp(white, 0.86F, 1.0F);
                getRenderer().renderCube(ps, p.cube(), buffer(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        com.jastkub.frozenfortress.util.FFColor.argb(r, g, 1.0F, Math.min(1.0F, cold)));
            }
            ps.popPose();
        }

        /** A shard of the forge model flying: at `pos` in `frame`, its length along `heading`, spun about it. */
        void fx(Matrix4f frame, int kind, Vector3f pos, Vector3f heading, float spinDeg, float scale, float alpha) {
            if ((pass != FX_LIT && pass != FX_GLOW) || scale <= 0.01F) {
                return;
            }
            GeoBone bone = fx.getBone(kind == CHIP ? "chip" : "shard").orElse(null);
            if (bone == null) {
                return;
            }
            Vector3f dir = heading.lengthSquared() < 1.0E-8F ? new Vector3f(0.0F, 1.0F, 0.0F)
                    : new Vector3f(heading).normalize();
            Vector3f pivot = kind == CHIP ? CHIP_PIVOT : SHARD_PIVOT;
            ps.pushPose();
            at(new Matrix4f(frame).translate(pos).rotate(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                    dir.x(), dir.y(), dir.z())).rotateY(spinDeg * Mth.DEG_TO_RAD).scale(scale)
                    .translate(-pivot.x(), -pivot.y(), -pivot.z()));
            drawFx(bone, alpha);
            ps.popPose();
        }

        /** A star of light at `pos` in `frame`. */
        void glint(Matrix4f frame, Vector3f pos, float scale) {
            if (pass != FX_GLOW || scale <= 0.01F) {
                return;
            }
            GeoBone bone = fx.getBone("glint").orElse(null);
            if (bone == null) {
                return;
            }
            ps.pushPose();
            at(new Matrix4f(frame).translate(pos).rotateY(clock * 0.35F).rotateX(0.6F).scale(scale)
                    .translate(-GLINT_PIVOT.x(), -GLINT_PIVOT.y(), -GLINT_PIVOT.z()));
            drawFx(bone, 1.0F);
            ps.popPose();
        }

        /** A spike of the ring, its foot at `pos` (relative to his feet, world-aligned), leant out from him. */
        void spike(Vector3f pos, float bearing, float leanDeg, float twistDeg, float scale, float height) {
            if (pass != FX_LIT && pass != FX_GLOW) {
                return;
            }
            GeoBone bone = fx.getBone("spike").orElse(null);
            if (bone == null) {
                return;
            }
            ps.pushPose();
            // the lean is away from him: about the horizontal axis square to the bearing
            at(new Matrix4f().translate(pos).rotate(new Quaternionf().rotationAxis(-leanDeg * Mth.DEG_TO_RAD,
                    -Mth.sin(bearing), 0.0F, Mth.cos(bearing))).rotateY(twistDeg * Mth.DEG_TO_RAD)
                    .scale(scale, scale * height, scale).translate(-SPIKE_PIVOT.x(), -SPIKE_PIVOT.y(), -SPIKE_PIVOT.z()));
            drawFx(bone, 1.0F);
            ps.popPose();
        }

        private void drawFx(GeoBone bone, float alpha) {
            if (pass == FX_LIT) {
                getRenderer().renderCubesOfBone(ps, bone, buffer(), light, OverlayTexture.NO_OVERLAY,
                        com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, alpha));
            } else {
                getRenderer().renderCubesOfBone(ps, bone, buffer(), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, alpha * 0.9F));
            }
        }
    }
}
