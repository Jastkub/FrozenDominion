package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FFSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, FrozenFortress.MODID);

    // --- Velkhar, the Hollow Sovereign ---
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_IDLE = register("entity.velkhar.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_HURT = register("entity.velkhar.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_DEATH = register("entity.velkhar.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_ROAR = register("entity.velkhar.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SWING = register("entity.velkhar.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_IMPACT = register("entity.velkhar.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SLAM = register("entity.velkhar.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_TELEPORT = register("entity.velkhar.teleport");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CAST = register("entity.velkhar.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_GRAB = register("entity.velkhar.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_COUNTER = register("entity.velkhar.counter");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_PHASE2 = register("entity.velkhar.phase2");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_PHASE3 = register("entity.velkhar.phase3");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_WHISPER = register("entity.velkhar.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_STEP = register("entity.velkhar.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LAUGH = register("entity.velkhar.laugh");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_TAUNT = register("entity.velkhar.taunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BREATH = register("entity.velkhar.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CROWN_FALL = register("entity.velkhar.crown_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_FINALE = register("entity.velkhar.finale");
    /** Overhead sword strike that lands - the wind-up, the hit, the ground. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CLEAVE = register("entity.velkhar.cleave");
    /** The blizzard he steps through. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BLIZZARD = register("entity.velkhar.blizzard");
    /** A blade opening the air - the sound the combo is built around. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_AIRCUT = register("entity.velkhar.aircut");
    /** Shield driven forward: a struck bell with a body behind it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_BASH = register("entity.velkhar.shield_bash");
    /** Four bright raps for a hit that the guard ate. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_CLANG = register("entity.velkhar.shield_clang");
    /** An anvil set on stone: the wall going down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_SET = register("entity.velkhar.shield_set");
    /** Blade on rim. Contempt, not damage. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_GUARD_TAP = register("entity.velkhar.guard_tap");
    /** His own recordings: a real shield being struck, and a blade on its rim. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_HIT = register("entity.velkhar.shield_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BLADE_ON_SHIELD = register("entity.velkhar.blade_on_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_SLAM = register("entity.velkhar.shield_slam");
    /** Steel worked back out of the stone he buried it in. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SWORD_PULL = register("entity.velkhar.sword_pull");
    /** The shield drinking in cold before he throws it forward. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_SHIELD_CHARGE = register("entity.velkhar.shield_charge");
    /** A heel brought down hard enough to split the floor. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_STOMP = register("entity.velkhar.stomp");
    /** The thrown blade turning over in the air. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BLADE_WHIRL = register("entity.velkhar.blade_whirl");
    // --- Spoken lines (Jastkub's own recordings) ---
    /** Thrown at whoever just took a hit from him. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_PATHETIC = register("entity.velkhar.pathetic");
    /** As the duel begins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_INTRO = register("entity.velkhar.line_intro");
    /** Entering the second phase. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_PHASE2 = register("entity.velkhar.line_phase2");
    /** Entering the third. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_PHASE3 = register("entity.velkhar.line_phase3");
    /** The Last Winter, at a tenth of his health. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_LAST = register("entity.velkhar.line_last");
    /** When a charge or a spin actually connects. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_TOO_SLOW = register("entity.velkhar.line_too_slow");
    /** At a challenger who is nearly finished. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_LAUGH = register("entity.velkhar.line_laugh");
    /** After shrugging off a hit while still barely marked. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_IS_THAT_ALL = register("entity.velkhar.line_is_that_all");
    /** When greed gets punished. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_PREDICTABLE = register("entity.velkhar.line_predictable");
    /** Over a challenger who did not survive. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_DISAPPOINT = register("entity.velkhar.line_disappoint");
    /** Waking on the throne. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_AWAKEN = register("entity.velkhar.line_awaken");
    /** Calling the court. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_SERVE = register("entity.velkhar.line_serve");
    /** A teleport strike. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_BEHIND = register("entity.velkhar.line_behind");
    /** The ceiling comes down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_GLACIER = register("entity.velkhar.line_glacier");
    /** The floor answers in spikes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_DEPTHS = register("entity.velkhar.line_depths");
    /** A riposte in the last phase. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_ENOUGH = register("entity.velkhar.line_enough");
    /** The nova goes out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FREEZE = register("entity.velkhar.line_freeze");
    /** Sealing someone in ice. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_EMBRACE = register("entity.velkhar.line_embrace");
    /** A hand closed on something living. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_SOWARM = register("entity.velkhar.line_sowarm");
    /** The heart beam opens. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FORGOTTEN = register("entity.velkhar.line_forgotten");
    /** His own end. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FADES = register("entity.velkhar.line_fades");
    /** Staggered out of the Last Winter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_CONFUSED = register("entity.velkhar.line_confused");
    /** The cataclysm begins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_RULED = register("entity.velkhar.line_ruled");
    /** A challenger close to the end. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_SILENT = register("entity.velkhar.line_silent");
    /** Winter collapses on them. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_STATUE = register("entity.velkhar.line_statue");

    // --- THE CUTSCENE LINES.
    //
    // Separate from the barks above, because they are used differently. A bark
    // is thrown out mid-fight and may well be talked over; each of these is
    // pinned to one beat of one scene, the scene HOLDS for it, and the words
    // go on the screen at the same time. They are the only lines the player is
    // guaranteed to hear whole.
    /** Off the throne, weapons formed, before the fight starts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_THRONE = register("entity.velkhar.cut_throne");
    /** The moment his armour and shield give way. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_PHASE2 = register("entity.velkhar.cut_phase2");
    /** The greatsword comes apart and he is holding two. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_SPLIT = register("entity.velkhar.cut_split");
    /** The king is gone and the mage is what is left. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_PHASE3 = register("entity.velkhar.cut_phase3");
    /** Called down at the hole as the colossus starts to climb out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_GOLEM = register("entity.velkhar.cut_golem");
    /** The last blow has landed and he knows it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_CUT_DEATH = register("entity.velkhar.cut_death");
    /** Every take left in the Audacity folder (07.10.2026). */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_CORPSE = register("entity.velkhar.line_corpse");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_COLD = register("entity.velkhar.line_cold");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_PRESERVE = register("entity.velkhar.line_preserve");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_REST = register("entity.velkhar.line_rest");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_REST_FOREVER = register("entity.velkhar.line_rest_forever");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_BLIZZARD = register("entity.velkhar.line_blizzard");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_JUDGE = register("entity.velkhar.line_judge");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_SNOW = register("entity.velkhar.line_snow");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_WIND = register("entity.velkhar.line_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FORTRESS = register("entity.velkhar.line_fortress");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_WITNESS = register("entity.velkhar.line_witness");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_GATE = register("entity.velkhar.line_gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FINISHED = register("entity.velkhar.line_finished");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_STORM = register("entity.velkhar.line_storm");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_ALWAYS = register("entity.velkhar.line_always");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_CLAIMS = register("entity.velkhar.line_claims");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_JOURNEY = register("entity.velkhar.line_journey");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_GLORY = register("entity.velkhar.line_glory");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FROZEN = register("entity.velkhar.line_frozen");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_LINE_FREEZE_NOW = register("entity.velkhar.line_freeze_now");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_GROAN = register("entity.velkhar.groan");

    // --- Boss music ---
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VELKHAR_PHASE1 = register("music.velkhar.phase1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VELKHAR_PHASE2 = register("music.velkhar.phase2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VELKHAR_PHASE3 = register("music.velkhar.phase3");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VELKHAR_OUTRO = register("music.velkhar.outro");
    /** The citadel's theme: "Dark Cave" by SunixMuz, CC BY 4.0. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_CITADEL = register("music.citadel_theme");
    /** The Monstrosity's statue waking: a bass impact, then the ice pouring off it as it shakes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MONSTROSITY_STATUE_WAKE = register("entity.ice_monstrosity.statue_wake");
    /** The Frost Skeleton (tools/gen_skeleton_sounds.py): bone and frost, no voice. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_IDLE = register("entity.frost_skeleton.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_HURT = register("entity.frost_skeleton.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_STEP = register("entity.frost_skeleton.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_SWING = register("entity.frost_skeleton.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_RISE = register("entity.frost_skeleton.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_COLLAPSE = register("entity.frost_skeleton.collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_REFORM = register("entity.frost_skeleton.reform");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SKELETON_SHATTER = register("entity.frost_skeleton.shatter");
    /** The court's two miniboss fights, behind their dropped gates. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TURNKEY = register("music.turnkey_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_PRIESTESS = register("music.priestess_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DROWNED = register("music.drowned_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_AUROCHS = register("music.aurochs_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SHEPHERD = register("music.shepherd_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_OVERSEER = register("music.overseer_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LAMPLIGHTER = register("music.lamplighter_fight");
    /** The Bone Lord's chase over the Chasm of Bones. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BONE_LORD = register("music.bone_lord_fight");
    /** The Ice Monstrosity's fight, in its prison. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MONSTROSITY = register("music.monstrosity_fight");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_SILENT_WINTER = register("music_disc.silent_winter");

    // --- Fortress ambience ---
    public static final DeferredHolder<SoundEvent, SoundEvent> FORTRESS_WIND = register("ambient.fortress.wind");

    // --- Frostbound Sentinel ---
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_IDLE = register("entity.sentinel.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_HURT = register("entity.sentinel.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_DEATH = register("entity.sentinel.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_SHIELD = register("entity.sentinel.shield");
    /** The tower shield taking a blow from the front (three takes). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_BLOCK = register("entity.sentinel.block");
    /** The guard broken: the shield knocked wide. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_GUARD_BREAK = register("entity.sentinel.guard_break");
    /** The bow drawn - ice creaking. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_DRAW = register("entity.stillbow.draw");
    /** The rain of arrows coming down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_RAIN = register("entity.stillbow.rain");
    /** The rime ward laid on the court. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_WARD = register("entity.rimeweaver.ward");
    /** The ring of cold on the floor, humming. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_CIRCLE = register("entity.rimeweaver.circle");
    /** The pack's low growl - the tell of the pounce. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_GROWL = register("entity.frostmaw.growl");
    /** The howl that quickens the pack. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_HOWL = register("entity.frostmaw.howl");
    /** Jaws snapping shut. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_BITE = register("entity.frostmaw.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_SWING = register("entity.sentinel.swing");

    // --- Rimeweaver ---
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_IDLE = register("entity.rimeweaver.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_HURT = register("entity.rimeweaver.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_DEATH = register("entity.rimeweaver.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMEWEAVER_CAST = register("entity.rimeweaver.cast");

    // --- Stillbow ---
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_IDLE = register("entity.stillbow.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_HURT = register("entity.stillbow.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_DEATH = register("entity.stillbow.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_SHOOT = register("entity.stillbow.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILLBOW_BLINK = register("entity.stillbow.blink");

    // --- Frostmaw ---
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_IDLE = register("entity.frostmaw.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_HURT = register("entity.frostmaw.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_DEATH = register("entity.frostmaw.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_ROAR = register("entity.frostmaw.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROSTMAW_BREATH = register("entity.frostmaw.breath");

    // --- Vault Warden ---
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_AWAKEN = register("entity.vault_warden.awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_HURT = register("entity.vault_warden.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_DEATH = register("entity.vault_warden.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_SLAM = register("entity.vault_warden.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_STEP = register("entity.vault_warden.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_CORE = register("entity.vault_warden.core");
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_STOMP = register("entity.vault_warden.stomp");
    /** Wide stone arm sweep: the drag through the air, then the hit. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VAULT_WARDEN_SWIPE = register("entity.vault_warden.swipe");

    // --- Hrimthar, the Buried Colossus ---
    //
    // He was borrowing Velkhar's impact, stomp, roar and aircut, and a
    // miniboss that sounds exactly like the boss standing six feet away is a
    // reskin with more health. Every one of these is built out of stone and
    // ice with the voice mixed UNDER the rock rather than over it - he is a
    // shell with a light in it and nothing in him has a throat.
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_EMERGE = register("entity.ice_monstrosity.emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_STEP = register("entity.ice_monstrosity.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_SLAM = register("entity.ice_monstrosity.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_SWEEP = register("entity.ice_monstrosity.sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_STOMP = register("entity.ice_monstrosity.stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_ROAR = register("entity.ice_monstrosity.roar");
    /** Its bomb's own two sounds (tools/gen_golem_bomb_sounds.py): the orb gathering, and the shot. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_BOMB_CHARGE = register("entity.ice_monstrosity.bomb_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_BOMB_FIRE = register("entity.ice_monstrosity.bomb_fire");
    /** Its avalanche rolling, and its frost breath (tools/gen_golem_storm_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_AVALANCHE = register("entity.ice_monstrosity.avalanche");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_BREATH = register("entity.ice_monstrosity.breath");
    /** The Lamplighter's own (tools/gen_lamplighter_sounds.py): the pole in the air, on a body, the lantern on stone, the beam's frost. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_WHOOSH = register("entity.lamplighter.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_STAFF_HIT = register("entity.lamplighter.staff_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_SLAM = register("entity.lamplighter.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_FREEZE = register("entity.lamplighter.freeze");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_HURT = register("entity.ice_monstrosity.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_DEATH = register("entity.ice_monstrosity.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_IDLE = register("entity.ice_monstrosity.idle");
    /** Ice carrying weight it does not want to: long, low, and constant. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_CREAK = register("entity.ice_monstrosity.creak");
    /** Something inside him giving way. Short, bright and unpleasant. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_SPLIT = register("entity.ice_monstrosity.split");

    // --- Generic ice / spells ---
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_SPIKE_EMERGE = register("misc.ice_spike_emerge");
    /** A real crack running through ice - and only that (three takes). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_CRACK = register("misc.ice_crack");
    /** A trap room's cold: your own heart, slowing (ColdOverlay). */
    public static final DeferredHolder<SoundEvent, SoundEvent> COLD_HEARTBEAT = register("misc.cold_heartbeat");
    /** The Forge Overseer (tools/gen_forge_overseer_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_IDLE = register("entity.forge_overseer.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_HURT = register("entity.forge_overseer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_DEATH = register("entity.forge_overseer.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_AWAKEN = register("entity.forge_overseer.awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_ROAR = register("entity.forge_overseer.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_SWING = register("entity.forge_overseer.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_SLAM = register("entity.forge_overseer.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_SHOCKWAVE = register("entity.forge_overseer.shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_TONGS = register("entity.forge_overseer.tongs");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_GRAB = register("entity.forge_overseer.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_SLAG_STRIKE = register("entity.forge_overseer.slag_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_SLAG_HIT = register("entity.forge_overseer.slag_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_QUENCH = register("entity.forge_overseer.quench");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_PLATE_BREAK = register("entity.forge_overseer.plate_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_PLATE_LAND = register("entity.forge_overseer.plate_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_FROST_FADE = register("entity.forge_overseer.frost_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_STEP = register("entity.forge_overseer.step");
    /** The keepers' entrance scenes (BossScenes, the "intro" clips; tools/gen_audio_intros.py): what they do in them,
     *  and the scenes' own cut and title. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_OVERSEER_ANVIL = register("entity.forge_overseer.anvil");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURNKEY_KEYS = register("entity.turnkey.keys");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURNKEY_LOCK = register("entity.turnkey.lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIME_PRIESTESS_PAGE = register("entity.rime_priestess.page");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIME_PRIESTESS_BOOK_SHUT = register("entity.rime_priestess.book_shut");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMPLIGHTER_KINDLE = register("entity.lamplighter.kindle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTSCENE_WHOOSH = register("misc.cutscene.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTSCENE_TITLE = register("misc.cutscene.title");
    /** The Shade Shepherd and his shades (tools/gen_shade_shepherd_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_IDLE = register("entity.shade_shepherd.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_HURT = register("entity.shade_shepherd.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_DEATH = register("entity.shade_shepherd.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_WAKE = register("entity.shade_shepherd.wake");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_BELL = register("entity.shade_shepherd.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_HOOK_TELL = register("entity.shade_shepherd.hook_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_HOOK = register("entity.shade_shepherd.hook");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_HOOK_HIT = register("entity.shade_shepherd.hook_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_INHALE = register("entity.shade_shepherd.inhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_EXHALE = register("entity.shade_shepherd.exhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_SNUFF = register("entity.shade_shepherd.snuff");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_WHISTLE = register("entity.shade_shepherd.whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_SPLIT = register("entity.shade_shepherd.split");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_STEP = register("entity.shade_shepherd.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_GUST = register("entity.shade_shepherd.gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_SHEPHERD_GUST_BREAK = register("entity.shade_shepherd.gust_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_BREATH = register("entity.shade.breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_STEP = register("entity.shade.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_HISS = register("entity.shade.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_LUNGE = register("entity.shade.lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_HURT = register("entity.shade.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_DEATH = register("entity.shade.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_RISE = register("entity.shade.rise");
    /** The Ice Aurochs (tools/gen_ice_aurochs_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_IDLE = register("entity.ice_aurochs.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_HURT = register("entity.ice_aurochs.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_DEATH = register("entity.ice_aurochs.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_BELLOW = register("entity.ice_aurochs.bellow");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_SCRAPE = register("entity.ice_aurochs.scrape");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_SNORT = register("entity.ice_aurochs.snort");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_STEP = register("entity.ice_aurochs.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_GALLOP = register("entity.ice_aurochs.gallop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_DOUSE = register("entity.ice_aurochs.douse");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_CRASH = register("entity.ice_aurochs.crash");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_DIZZY = register("entity.ice_aurochs.dizzy");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_TOSS = register("entity.ice_aurochs.toss");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_REAR = register("entity.ice_aurochs.rear");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_STOMP = register("entity.ice_aurochs.stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_RING = register("entity.ice_aurochs.ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_AUROCHS_KICK = register("entity.ice_aurochs.kick");
    /** The Drowned Lady (tools/gen_drowned_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_IDLE = register("entity.drowned_lady.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_HURT = register("entity.drowned_lady.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_DEATH = register("entity.drowned_lady.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_WAIL = register("entity.drowned_lady.wail");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_LASH_TELL = register("entity.drowned_lady.lash_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_LASH = register("entity.drowned_lady.lash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_ICE_GROAN = register("entity.drowned_lady.ice_groan");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_ICE_CRACK = register("entity.drowned_lady.ice_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_ICE_BREAK = register("entity.drowned_lady.ice_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_SPLASH = register("entity.drowned_lady.splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_GRASP_TELL = register("entity.drowned_lady.grasp_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_GRASP = register("entity.drowned_lady.grasp");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_TIDE_TELL = register("entity.drowned_lady.tide_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_TIDE = register("entity.drowned_lady.tide");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROWNED_LADY_HANDS_BREAK = register("entity.drowned_lady.hands_break");
    // the ice's own vocabulary (tools/gen_ice_family_sounds.py): one sound stood for a hundred things
    /** A thing of ice coming apart: the snap, the ring, the shards raining away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_SHATTER = register("misc.ice_shatter");
    /** Ice or stone landing hard: a crunching thud, packed ice crushed under it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_IMPACT = register("misc.ice_impact");
    /** Ice being made: crackle thickening and rising to a glassy swell. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_CHARGE = register("misc.frost_charge");
    /** Cold let go: a snap and a rush of freezing air falling away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_RELEASE = register("misc.frost_release");
    /** Ice forced against stone: stick-slip friction, a creak. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_GRIND = register("misc.ice_grind");
    /** Something small and high: a struck crystal, a tell. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_CHIME = register("misc.crystal_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_BOLT_FIRE = register("misc.frost_bolt_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_BOLT_HIT = register("misc.frost_bolt_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_PRISON = register("misc.ice_prison");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD_LOOP = register("misc.blizzard_loop");

    /**
     * ONE VOICE PER SPELL, because the third phase had one voice for all of
     * them.
     *
     * <p>Every cast in the mage phase played misc.velkhar_cast at a slightly
     * different pitch - the rings building, four stones torn out of the floor,
     * the seekers cut out of the air, the heart emptying itself. Pitch-shifting
     * one sample across a whole phase does not hide that it is one sample; it
     * advertises it. These are shaped after what each attack DOES, and they
     * are deliberately different lengths as well as different timbres.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_ASSEMBLE = register("misc.spell_assemble");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_UPROOT = register("misc.spell_uproot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_TEAR = register("misc.spell_tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_HEART = register("misc.spell_heart");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_STORM = register("misc.spell_storm");
    /** The charge in his storm. A three-second bed, not an event - see
     *  gen_spell_sounds.spell_current. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_CURRENT = register("misc.spell_current");

    // ================================================================
    // A VOICE PER CAST.
    //
    // Nine of the third phase's attacks shared two files between them -
    // VELKHAR_CAST and VELKHAR_ROAR at slightly different pitches - which is
    // why the phase read as one repeated event however different the attacks
    // looked. Pitch-shifting one sample is the thing that makes a repeat
    // obvious rather than the thing that hides it.
    //
    // Each of these is shaped after what its attack DOES, and they are
    // deliberately different lengths as well as different timbres: a set of
    // samples that all last three quarters of a second is still one event
    // however differently it is voiced. See tools/gen_spell_sounds.py.
    // ================================================================
    /** Four orbs cut out of the air - four plucks, rising, so the ear counts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_ORBS = register("misc.spell_orbs");
    /** Columns coming up through the floor: the break first, the rise after. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_COLUMNS = register("misc.spell_columns");
    /** The healing pillars. The only consonant cast he has, on purpose. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_WARDS = register("misc.spell_wards");
    /** The beam WINDING UP - one note climbing, ending on a snap. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_LANCE = register("misc.spell_lance");
    /** The executioner's blade pulled out of the air. The biggest one. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_DOOM = register("misc.spell_doom");
    /** Two places changing hands: two sweeps crossing, no clear direction. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_MIRROR = register("misc.spell_mirror");
    /** The greatsword being worked into a crossbow. Work, not magic. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_FORGE = register("misc.spell_forge");
    /**
     * The Winter Beam's sustain, cut to exactly the length of the burn.
     *
     * <p>The beam used to stutter misc.blizzard_loop every five ticks at pitch
     * 0.5. Minecraft resamples on pitch, so each of those copies was eight
     * seconds long and the last one started on the beam's final frame - the
     * sound outlived the light by eight seconds, under nineteen other copies.
     * This is played ONCE, at pitch 1.0, and ends when the beam does.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BEAM_LOOP = register("entity.velkhar_beam_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE = register("misc.shockwave");
    /** The boss gate's portcullis let go: chain running out, bars down their grooves, the clang on the sill at
     *  0.35 s - the gate's 8th tick (tools/gen_gate_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_GATE_DROP = register("block.boss_gate_drop");

    /** The Bow of the Last Watch (tools/gen_last_watch_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_DRAW = register("item.last_watch.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_READY = register("item.last_watch.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_BEAM = register("item.last_watch.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_SPLIT = register("item.last_watch.split");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_SHOOT = register("item.last_watch.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_MARK = register("item.last_watch.mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_CHAINS = register("item.last_watch.chains");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_CHAINS_BREAK = register("item.last_watch.chains_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_BACKSTEP = register("item.last_watch.backstep");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_SENTINEL_RISE = register("item.last_watch.sentinel_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_SENTINEL_SHOT = register("item.last_watch.sentinel_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_SENTINEL_END = register("item.last_watch.sentinel_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_RAIN = register("item.last_watch.rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAST_WATCH_VIGIL = register("item.last_watch.vigil");
    // ---- OKO BURZY - THE EYE OF THE STORM (tools/gen_storm_eye_sounds.py) ----
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_ASCENT = register("entity.storm_eye.ascent");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_WIND = register("entity.storm_eye.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_THUNDER = register("entity.storm_eye.thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_RUNE = register("entity.storm_eye.rune");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_SHOCK = register("entity.storm_eye.shock");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_GALE = register("entity.storm_eye.gale");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_ORB = register("entity.storm_eye.orb");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_ORB_BURST = register("entity.storm_eye.orb_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_ANCHOR_RISE = register("entity.storm_eye.anchor_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_ANCHOR_BREAK = register("entity.storm_eye.anchor_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_CRACK = register("entity.storm_eye.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_MELT = register("entity.storm_eye.melt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_FREEZE = register("entity.storm_eye.freeze");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_GUST = register("entity.storm_eye.gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_FALL = register("entity.storm_eye.fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_CLOSE = register("entity.storm_eye.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_EYE_MIRROR = register("entity.storm_eye.mirror");
    // THE WHITEOUT - phase two's blizzard (tools/gen_whiteout_sounds.py): its howl, his run and his copies' (iron feet and
    // air), a copy bursting, him going down
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_WIND = register("entity.whiteout.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_RUSH = register("entity.whiteout.rush");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_GHOST = register("entity.whiteout.ghost");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_SHATTER = register("entity.whiteout.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_STUMBLE = register("entity.whiteout.stumble");
    /** One footfall of the king's run out of the white (08.10.2026) - his copies have none. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHITEOUT_STEP = register("entity.whiteout.step");
    /** The Wand of the Dead's (tools/gen_bone_wand_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_WAND_SUMMON = register("item.bone_wand.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_WAND_COMMAND = register("item.bone_wand.command");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_WAND_CRUMBLE = register("item.bone_wand.crumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_WAND_READY = register("item.bone_wand.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_WAND_FIZZLE = register("item.bone_wand.fizzle");
    /** The Kingsrime sword's skills (tools/gen_kingsrime_skill_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CRESCENT = register("item.kingsrime_crescent");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CRESCENT_HIT = register("item.kingsrime_crescent_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_WRATH = register("item.kingsrime_wrath");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_WRATH_READY = register("item.kingsrime_wrath_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_STEP = register("item.kingsrime_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_BLADES_BURST = register("item.kingsrime_blades_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CROWN_RISE = register("item.kingsrime_crown_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CROWN_READY = register("item.kingsrime_crown_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CORONATION = register("item.kingsrime_coronation");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_CROWN_SINK = register("item.kingsrime_crown_sink");
    public static final DeferredHolder<SoundEvent, SoundEvent> KINGSRIME_READY = register("item.kingsrime_ready");
    /** The Staff of the Hollow King's (tools/gen_hollow_staff_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_CHANT = register("item.hollow_staff.chant");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_RUNE_FORM = register("item.hollow_staff.rune_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_RUNE_FIRE = register("item.hollow_staff.rune_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_RUNE_HIT = register("item.hollow_staff.rune_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_BELL_SUMMON = register("item.hollow_staff.bell_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_BELL_FALL = register("item.hollow_staff.bell_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_BELL_TOLL = register("item.hollow_staff.bell_toll");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_TIDE = register("item.hollow_staff.tide");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_TIDE_PULL = register("item.hollow_staff.tide_pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_MARK = register("item.hollow_staff.mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_REQUIEM = register("item.hollow_staff.requiem");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_REQUIEM_END = register("item.hollow_staff.requiem_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_SHADE_RISE = register("item.hollow_staff.shade_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_SHADE_ATTACK = register("item.hollow_staff.shade_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_SHADE_FADE = register("item.hollow_staff.shade_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_READY = register("item.hollow_staff.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_STAFF_FIZZLE = register("item.hollow_staff.fizzle");
    /** Zmora Tronu's (tools/gen_throne_bane_sounds.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_STACK = register("item.throne_bane_stack");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_AWAKEN = register("item.throne_bane_awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_CHARGE = register("item.throne_bane_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_CHARGE_HIT = register("item.throne_bane_charge_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_LEAP = register("item.throne_bane_leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_SLAM = register("item.throne_bane_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_PLUNGE = register("item.throne_bane_plunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_IMPACT = register("item.throne_bane_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_WINDUP = register("item.throne_bane_windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_READY = register("item.throne_bane_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_SLASH = register("item.throne_bane_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_CRESCENT = register("item.throne_bane_crescent");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_STUN = register("item.throne_bane_stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_COMBO = register("item.throne_bane_combo");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRONE_BANE_DENIED = register("item.throne_bane_denied");
    /** The Shade Shepherd's pulse in the ears of the one it struck (tools/gen_shade_deafen.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_DEAFEN = register("entity.shade_shepherd.deafen");
    /** Our blades swung (tools/gen_blade_sounds.py): the swords, and the Lament and the Crownbreaker. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_SWING = register("item.blade_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> GREAT_SWING = register("item.great_swing");
    /** A stroke of its own for each kind of blow. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_BACKHAND = register("item.blade_backhand");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLADE_THRUST = register("item.blade_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> GREAT_BACKHAND = register("item.great_backhand");
    public static final DeferredHolder<SoundEvent, SoundEvent> GREAT_THRUST = register("item.great_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> GREAT_SLAM = register("item.great_slam");
    /** One of the dead hurled by the Bone Lord, screaming as it flies (tools/gen_bone_lord_scream.py: a stand-in the
     *  user may record over - keep the name). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_LORD_THROWN_SCREAM = register("entity.bone_lord.thrown_scream");
    /** The Bone Lord's footfall: thud, crack and the rattle of his bones, loud in the middle of the range where any
     *  speakers play it (tools/gen_bone_lord_step.py; BoneLordClient plays it on the clip's foot-plants). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_LORD_STEP = register("entity.bone_lord.step");
    /** Velkhar's weapons made out of ice and his plate giving way:
     *  the cold drawn in, a piece locking into place, the weapon finished, the greatsword bursting, a seam splitting. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_FORGE_GATHER = register("entity.velkhar.forge_gather");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_FORGE_LOCK = register("entity.velkhar.forge_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_WEAPON_SET = register("entity.velkhar.weapon_set");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_BLADE_SHATTER = register("entity.velkhar.blade_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> VELKHAR_ARMOUR_CRACK = register("entity.velkhar.armour_crack");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name.replace('.', '_'),
                () -> SoundEvent.createVariableRangeEvent(FrozenFortress.id(name.replace('.', '_'))));
    }

    private FFSounds() {
    }
}
