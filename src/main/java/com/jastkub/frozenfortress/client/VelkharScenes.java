package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * THE KING'S SCENES IN SHOTS: his scenes held the
 * camera in the player's eyes, turned on him. Now each has shots of its own round him, cut from one to the next as it
 * runs - wide, low, close - laid out about the way he faced when it began, the camera itself moved
 * (Camera.setPosition, as BossScenes does it - by reflection, no mixin), and drawn in toward him wherever a wall stands
 * between him and where a shot would put it. In the summoning the last shots are the Ice Monstrosity's.
 *
 * <p>His scenes (VelkharEntity.cutscene): 1 the throne rise, 2 the crown falls, 3 the gauntlet shatters, 4 the Hollow
 * Magus, 5 his end, 6 the summoning.
 */
public final class VelkharScenes {

    /** One shot: from tick `at` of the scene; the camera at an angle off his facing (degrees, + to his right), a
     *  distance and a height over his feet, moving from the first to the second; looking at a height on him. */
    private record Shot(int at, float a0, float d0, float h0, float a1, float d1, float h1, float look0, float look1,
                        boolean golem) {
    }

    private static final Shot[][] SHOTS = new Shot[7][];

    static {
        // 1 THE THRONE RISE: the hall wide from high before him; low at his side as he stands, looking up; HIS SHIELD AND
        // HIS SWORD MADE: from his shield side round to
        // his front, the whole of his arms in it, 80-120; then his face, for the line (126)
        SHOTS[1] = new Shot[]{new Shot(0, 24, 15, 7, 16, 13, 6, 0.6F, 0.7F, false),
                new Shot(45, -62, 6.5F, 1.2F, -50, 6, 1.6F, 0.75F, 0.85F, false),
                new Shot(80, -24, 9.5F, 2.2F, -4, 8.5F, 2.4F, 0.5F, 0.52F, false),
                new Shot(120, 8, 5.5F, 3.2F, 3, 4.5F, 3.3F, 0.9F, 0.92F, false)};
        // 2 THE CROWN FALLS: before him; close at his right on the armour as it splits; wide from behind and above
        SHOTS[2] = new Shot[]{new Shot(0, 14, 9, 3.5F, 8, 8, 3.2F, 0.75F, 0.8F, false),
                new Shot(32, 58, 5, 2.6F, 48, 4.5F, 2.8F, 0.65F, 0.7F, false),
                new Shot(66, 155, 12, 8, 165, 13, 9, 0.6F, 0.55F, false)};
        // 3 THE GAUNTLET SHATTERS: low before him, up at the blade; close on his hands; wide as the two blades are his
        SHOTS[3] = new Shot[]{new Shot(0, -28, 7, 1.0F, -20, 6.5F, 1.2F, 0.7F, 0.75F, false),
                new Shot(24, 72, 7.0F, 2.6F, 64, 6.6F, 2.8F, 0.5F, 0.55F, false),
                new Shot(50, 18, 13, 6, 26, 14, 7, 0.65F, 0.7F, false)};
        // 4 THE HOLLOW MAGUS: before him as the swordsman burns away; from under him as he leaves the floor; wide high
        SHOTS[4] = new Shot[]{new Shot(0, 10, 10, 4, 4, 9, 4, 0.75F, 0.8F, false),
                new Shot(42, -24, 6, 0.5F, -14, 5.5F, 0.6F, 0.9F, 1.1F, false),
                new Shot(84, 38, 16, 10, 46, 17, 11, 0.7F, 0.75F, false)};
        // 5 HIS END: before him; close on his face; high over him as he comes apart
        SHOTS[5] = new Shot[]{new Shot(0, 20, 9, 4, 12, 8, 3.8F, 0.75F, 0.8F, false),
                new Shot(48, 6, 4.8F, 3.4F, 2, 4.3F, 3.4F, 0.9F, 0.92F, false),
                new Shot(96, 175, 14, 12, 185, 15, 13, 0.4F, 0.3F, false)};
        // 6 THE SUMMONING: on him as the wand goes into the floor; then the Monstrosity, wide, and low under her bellow
        SHOTS[6] = new Shot[]{new Shot(0, 26, 8, 3, 18, 7.5F, 3, 0.6F, 0.55F, false),
                new Shot(40, 32, 18, 8, 22, 16, 7, 0.55F, 0.62F, true),
                new Shot(90, -40, 10, 1.5F, -30, 9, 1.8F, 0.7F, 0.8F, true)};
    }

    private static final Method SET_POSITION = method();
    private static final Field DETACHED = field();
    private static boolean cameraOk = SET_POSITION != null && DETACHED != null;

    /** The scene going and where it began: which, the client tick it started, his feet and his facing then. */
    private static int scene;
    private static long started;
    private static Vec3 anchor = Vec3.ZERO;
    private static float anchorYaw;
    private static int seenFor = -1;
    /** Each shot's pull toward him (1 as written, less when a wall is in the way), worked out once. */
    private static float[] fit = new float[0];
    /**
     * ON HIS FACE WHILE HE SPEAKS: whatever shot the
     * scene is in, a sentence of his (ClientEvents.lineSpoken) cuts to his face from before him - a little to his right,
     * pushing in slowly - and back to the scene's own when it is said. Laid off the way he faces as he begins it, not
     * as the scene began (he turns in them). When it began (-1: not speaking), his facing then, its pull to him.
     */
    private static long faceFrom = -1L;
    private static float faceYaw;
    private static float faceFit = -1.0F;
    /**
     * The scene's shot the face cut was taken in. NOT BACK TO IT: the caption fades out a few ticks before his scene
     * ends, and going back for those ticks to a shot laid out about where he began - the throne, behind him once he has
     * leapt - flashed the back of him. The face holds until the scene cuts to a shot of its own.
     */
    private static int faceShot = -1;
    private static final float FACE_ANGLE = 16.0F, FACE_FAR = 4.8F, FACE_NEAR = 4.0F, FACE_EYE = 0.8F, FACE_LOOK = 0.9F;
    /**
     * THE CROSSBOW MADE OUT OF THE GREATSWORD, CLOSE: the tower scene's shots were laid about the floor he stood on when it began, and by the
     * forge he is up on the spire - it played in a wide shot, small and below. From a little before the blade is
     * presented until the crossbow is shouldered (VelkharEntity.ASCENT_PRESENT to ASCENT_FORGED, his scene clock) the
     * camera is on his hands where he is, off his left front and drifting across to his right, at chest height.
     */
    private static float forgeYaw;
    private static float forgeFit = -1.0F;
    private static boolean forgeOn;
    private static final float FORGE_FROM = VelkharEntity.ASCENT_PRESENT - 8, FORGE_TO = VelkharEntity.ASCENT_SEEN + 2;

    private VelkharScenes() {
    }

    @Nullable
    private static Method method() {
        try {
            return ObfuscationReflectionHelper.findMethod(Camera.class, "m_90581_", Vec3.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Field field() {
        try {
            return ObfuscationReflectionHelper.findField(Camera.class, "f_90560_");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Is one of his scenes moving the camera now? (Its last frame no older than a few ticks: a scene ends without
     *  telling this class - the frames stop asking.) And not once HE says it is over: the few ticks' grace kept the
     *  view swung out behind the player after his scene had ended - the camera now comes home on the tick it ends. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (subject != null && (subject.isRemoved() || subject.cutscene() != scene)) {
            return false;
        }
        return scene != 0 && cameraOk && mc.level != null && mc.level.getGameTime() - lastFrame < 3L;
    }

    /** Whose scene it is. */
    @Nullable
    private static VelkharEntity subject;

    private static long lastFrame = Long.MIN_VALUE / 2;

    /**
     * His scene's camera, this frame: true if it was placed (ClientEvents keeps out then - only the scene's blows are
     * added on top). `golem` the Monstrosity of the summoning, if she is out.
     */
    public static boolean camera(ViewportEvent.ComputeCameraAngles event, VelkharEntity boss, @Nullable Entity golem) {
        Minecraft mc = Minecraft.getInstance();
        int c = boss.cutscene();
        if (!cameraOk || mc.level == null || c <= 0 || c >= SHOTS.length || SHOTS[c] == null) {
            scene = 0;
            return false;
        }
        long now = mc.level.getGameTime();
        if (c != scene || boss.getId() != seenFor || now - lastFrame > 20L) {
            scene = c;
            seenFor = boss.getId();
            subject = boss;
            started = now;
            anchor = boss.position();
            anchorYaw = boss.yBodyRot;
            fit = new float[SHOTS[c].length];
            java.util.Arrays.fill(fit, -1.0F);
            faceFrom = -1L;
            faceShot = -1;
        }
        lastFrame = now;
        float t = (now - started) + (float) event.getPartialTick();
        float clock = boss.getAttackState() == VelkharEntity.TWIN_ASCENT
                ? boss.sceneClock((float) event.getPartialTick()) : -1.0F;
        if (clock >= FORGE_FROM && clock <= FORGE_TO) {
            if (!forgeOn) {
                forgeOn = true;
                forgeYaw = boss.yBodyRot;
                forgeFit = -1.0F;
            }
            float k = (clock - FORGE_FROM) / (FORGE_TO - FORGE_FROM);
            k = 0.5F - 0.5F * Mth.cos(k * Mth.PI);
            float h = boss.getBbHeight();
            // where he is DRAWN: on the spire the renderer lifts him onto its platform, ahead of the entity itself
            Vec3 feet = boss.getPosition((float) event.getPartialTick()).add(0.0D,
                    com.jastkub.frozenfortress.client.render.VelkharRenderer.onTower(boss, (float) event.getPartialTick()),
                    0.0D);
            Vec3 want = around(feet, forgeYaw, Mth.lerp(k, -26.0F, 18.0F), Mth.lerp(k, 7.6F, 6.4F), h * 0.86F);
            Vec3 mid = feet.add(0.0D, h * 0.55D, 0.0D);
            if (forgeFit < 0.0F) {
                forgeFit = clearance(mc, mid, around(feet, forgeYaw, -26.0F, 7.6F, h * 0.86F),
                        around(feet, forgeYaw, 18.0F, 6.4F, h * 0.86F));
            }
            return aim(event, mid.add(want.subtract(mid).scale(forgeFit)), feet.add(0.0D, h * 0.64D, 0.0D), t);
        }
        forgeOn = false;
        Shot[] shots = SHOTS[c];
        int i = 0;
        while (i + 1 < shots.length && t >= shots[i + 1].at) {
            i++;
        }
        if (!ClientEvents.lineSpoken() && (faceFrom < 0L || i != faceShot)) {
            faceFrom = -1L;
            faceShot = -1;
        } else {
            if (faceFrom < 0L) {
                faceFrom = now;
                faceShot = i;
                faceYaw = boss.yBodyRot;
                faceFit = -1.0F;
            }
            float ft = (now - faceFrom) + (float) event.getPartialTick();
            float h = boss.getBbHeight();
            Vec3 feet = boss.getPosition((float) event.getPartialTick());
            Vec3 want = around(feet, faceYaw, FACE_ANGLE, Mth.lerp(Mth.clamp(ft / 60.0F, 0.0F, 1.0F), FACE_FAR, FACE_NEAR),
                    h * FACE_EYE);
            Vec3 mid = feet.add(0.0D, h * 0.55D, 0.0D);
            if (faceFit < 0.0F) {
                faceFit = clearance(mc, mid, want, want);
            }
            return aim(event, mid.add(want.subtract(mid).scale(faceFit)), feet.add(0.0D, h * FACE_LOOK, 0.0D), t);
        }
        Shot s = shots[i];
        Entity subject = s.golem && golem != null && golem.isAlive() ? golem : boss;
        float end = i + 1 < shots.length ? shots[i + 1].at : s.at + 60.0F;
        float u = Mth.clamp((t - s.at) / Math.max(1.0F, end - s.at), 0.0F, 1.0F);
        u = 0.5F - 0.5F * Mth.cos(u * Mth.PI);
        // laid about where he began - but in the tower scene (3) he goes UP the spire inside it, and a shot left at the
        // floor's height had the camera inside him as he rose through it: there it rides up with him
        Vec3 feet = subject != boss ? subject.position()
                : c == 3 ? new Vec3(anchor.x, boss.getPosition((float) event.getPartialTick()).y
                        + com.jastkub.frozenfortress.client.render.VelkharRenderer.onTower(boss,
                        (float) event.getPartialTick()), anchor.z) : anchor;
        float h = subject.getBbHeight();
        Vec3 mid = feet.add(0.0D, h * 0.55D, 0.0D);
        if (fit[i] < 0.0F) {
            fit[i] = clearance(mc, mid, place(s, 0.0F, feet), place(s, 1.0F, feet));
        }
        Vec3 want = place(s, u, feet);
        Vec3 cam = mid.add(want.subtract(mid).scale(fit[i]));
        Vec3 look = subject.getPosition((float) event.getPartialTick())
                .add(0.0D, h * Mth.lerp(u, s.look0, s.look1), 0.0D);
        return aim(event, cam, look, t);
    }

    /** The camera put at `cam`, turned on `look` (a breath of drift in it). */
    private static boolean aim(ViewportEvent.ComputeCameraAngles event, Vec3 cam, Vec3 look, float t) {
        try {
            SET_POSITION.invoke(event.getCamera(), cam);
            DETACHED.setBoolean(event.getCamera(), true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            cameraOk = false;
            scene = 0;
            return false;
        }
        Vec3 d = look.subtract(cam);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        event.setYaw((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F + Mth.sin(t * 0.07F) * 0.4F);
        event.setPitch((float) (-Mth.atan2(d.y, Math.max(1.0E-4D, flat)) * Mth.RAD_TO_DEG) + Mth.sin(t * 0.05F) * 0.3F);
        event.setRoll(Mth.sin(t * 0.03F) * 0.5F);
        return true;
    }

    /** Where shot `s` puts the camera at `u` (0..1), about `feet`, in the frame of his facing as the scene began. */
    private static Vec3 place(Shot s, float u, Vec3 feet) {
        return around(feet, anchorYaw, Mth.lerp(u, s.a0, s.a1), Mth.lerp(u, s.d0, s.d1), Mth.lerp(u, s.h0, s.h1));
    }

    /** A point `dist` off `feet` at `angle` degrees off the facing `yaw` (+ to his right), `height` up. */
    private static Vec3 around(Vec3 feet, float yaw, float angle, float dist, float height) {
        float a = angle * Mth.DEG_TO_RAD;
        float y = yaw * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(y), 0.0D, Mth.cos(y));
        Vec3 right = new Vec3(-Mth.cos(y), 0.0D, -Mth.sin(y));
        Vec3 dir = fwd.scale(Mth.cos(a)).add(right.scale(Mth.sin(a)));
        return feet.add(dir.scale(dist)).add(0.0D, height, 0.0D);
    }

    /** How much of the way from his middle to the shot's two ends is clear of the hall (1 all; else short of the wall). */
    private static float clearance(Minecraft mc, Vec3 mid, Vec3 a, Vec3 b) {
        float f = 1.0F;
        for (Vec3 to : new Vec3[]{a, b}) {
            Vec3 d = to.subtract(mid);
            double len = d.length();
            if (len < 1.0E-3D) {
                continue;
            }
            HitResult hit = mc.level.clip(new ClipContext(mid, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    mc.player));
            // his own ice on the floor round him (the shove's spikes, the floor he tore) is not a wall: a hit down
            // there, or right by him, pulled the shot into the middle of him. Walls are what it keeps out of
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(mid) > 2.5D
                    && hit.getLocation().y > mid.y - 1.2D) {
                double reach = hit.getLocation().distanceTo(mid) - 0.6D;
                f = (float) Math.min(f, Math.max(0.15D, reach / len));
            }
        }
        return f;
    }
}
