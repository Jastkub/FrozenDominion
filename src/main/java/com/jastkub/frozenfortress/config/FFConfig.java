package com.jastkub.frozenfortress.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Everything about the fight that somebody might reasonably want to turn off
 * or retune without recompiling.
 *
 * <p>Split across two files on purpose. The gameplay half is COMMON, because
 * it decides what actually happens to entities and has to be the same for
 * everyone on a server; the darkness is CLIENT, because it is a filter over
 * one person's camera and nobody else is affected by their choice.
 *
 * <h2>Why the damage knobs are MULTIPLIERS</h2>
 *
 * <p>Because the numbers in the code are not in one unit. Some of his attacks
 * are written in "hearts" that get multiplied by sixteen on the way out, and
 * some are written in raw damage points - a hangover from the two eras this
 * boss was built in. Exposing those figures directly would hand out a config
 * where 1.9 and 26.0 sit next to each other and mean comparable blows, which
 * is a trap. A multiplier has no unit, cannot be misread, and 1.0 always means
 * "as shipped".
 *
 * <p>They stack: an attack's own multiplier is applied to its damage constant,
 * and the phase multiplier is applied in the funnel every blow passes through.
 * Setting the combo to 2.0 and phase two to 0.5 leaves the phase-two combo
 * where it started.
 */
public final class FFConfig {

    // ================================================================
    // COMMON - gameplay
    // ================================================================

    public static final class Common {

        public final ModConfigSpec.BooleanValue tornadoSparesCreative;
        public final ModConfigSpec.BooleanValue velkharControlsWeather;
        public final ModConfigSpec.BooleanValue cleaveBreaksFortress;

        public final ModConfigSpec.DoubleValue phase1;
        public final ModConfigSpec.DoubleValue phase2;
        public final ModConfigSpec.DoubleValue phase25;
        public final ModConfigSpec.DoubleValue phase3;

        public final ModConfigSpec.DoubleValue combo;
        public final ModConfigSpec.DoubleValue shieldBash;
        public final ModConfigSpec.DoubleValue shieldPunish;
        public final ModConfigSpec.DoubleValue shieldPulse;
        public final ModConfigSpec.DoubleValue spinCharge;
        public final ModConfigSpec.DoubleValue chainPull;
        public final ModConfigSpec.DoubleValue groundStomp;
        public final ModConfigSpec.DoubleValue riftStomp;
        public final ModConfigSpec.DoubleValue shadowStrike;
        public final ModConfigSpec.DoubleValue risingSlash;
        public final ModConfigSpec.DoubleValue bladeThrow;
        public final ModConfigSpec.DoubleValue heartBarrage;
        public final ModConfigSpec.DoubleValue slam;
        public final ModConfigSpec.DoubleValue beam;
        public final ModConfigSpec.DoubleValue ringShards;

        public final ModConfigSpec.DoubleValue doomBlade;
        public final ModConfigSpec.DoubleValue graveBlade;
        public final ModConfigSpec.DoubleValue iceBoulder;
        public final ModConfigSpec.DoubleValue shadowShard;
        public final ModConfigSpec.DoubleValue iceOrb;

        public final ModConfigSpec.BooleanValue partyScaling;
        public final ModConfigSpec.DoubleValue partyHealthPerPlayer;
        public final ModConfigSpec.DoubleValue partyDamagePerPlayer;
        public final ModConfigSpec.IntValue partyMaxCounted;
        public final ModConfigSpec.BooleanValue partyAttacks;
        public final ModConfigSpec.BooleanValue partyFocus;
        public final ModConfigSpec.BooleanValue partyTrophies;

        Common(ModConfigSpec.Builder b) {
            b.comment("How the fight behaves. Must match across a server.").push("fight");

            tornadoSparesCreative = b
                    .comment("Leave players in creative and spectator alone when the tornado pulls.",
                             "The funnel sets velocity rather than adding it, specifically so that",
                             "knockback resistance cannot ignore it - which also means a creative",
                             "player gets dragged around like anyone else. Off = it grabs them too.")
                    .define("tornadoSparesCreative", true);

            velkharControlsWeather = b
                    .comment("Let him change the world's weather.",
                             "Covers the ten-minute storm that arrives with the second phase and",
                             "the blizzard attack, and the clear-up afterwards. Off leaves the sky",
                             "entirely alone - the arena effects and the snow around him stay,",
                             "only the real weather stops being touched.")
                    .define("velkharControlsWeather", true);

            cleaveBreaksFortress = b
                    .comment("Let the seismic cleave smash the fortress's own blocks.",
                             "It breaks whatever stands in its path - trees, ice, snow, stone -",
                             "but the fortress is ninety per cent ordinary stone, so",
                             "left on, every fight carves new holes through the throne hall",
                             "for good. Off by default: the arena survives the boss. The sealed",
                             "blocks of the outer shell are unbreakable either way.")
                    .define("cleaveBreaksFortress", false);

            b.pop();
            b.comment("Damage multipliers. 1.0 is as shipped. See the class note:",
                      "an attack's own multiplier and its phase multiplier both apply.")
                    .push("damage");

            b.comment("Applied to every blow he lands in that phase.").push("phases");
            phase1 = mul(b, "phase1", "Shield and greatsword.");
            phase2 = mul(b, "phase2", "Stormblade.");
            phase25 = mul(b, "phase2_5", "Twin blades. Already the sharpest stretch at 1.0.");
            phase3 = mul(b, "phase3", "Hollow Magus. Also the phase whose blows pierce armour.");
            b.pop();

            b.comment("Individual attacks.").push("attacks");
            combo = mul(b, "combo", "The gauntlet combo, and everything scaled off it.");
            shieldBash = mul(b, "shieldBash", "Bash, shield wall and riposte.");
            shieldPunish = mul(b, "shieldPunish", "The punish for greed on his guard.");
            shieldPulse = mul(b, "shieldPulse", "The shove.");
            spinCharge = mul(b, "spinCharge", "The charge across the hall.");
            chainPull = mul(b, "chainPull", "Chain drag and the blow on the end of it.");
            groundStomp = mul(b, "groundStomp", "The stomp, before distance falloff.");
            riftStomp = mul(b, "riftStomp", "The lane that splits the floor.");
            shadowStrike = mul(b, "shadowStrike", "Thrust, dash slash and the teleport strike.");
            risingSlash = mul(b, "risingSlash", "The lift.");
            bladeThrow = mul(b, "bladeThrow", "The thrown greatsword.");
            heartBarrage = mul(b, "heartBarrage", "One crystal of the barrage out of his chest.");
            slam = mul(b, "slam", "The slam. Enormous by design - it is the thing you do not eat.");
            beam = mul(b, "beam", "The third-phase beam, per damage tick.");
            ringShards = mul(b, "ringShards", "One splinter of the two rings. Forty-five go out.");
            b.pop();

            b.comment("His projectiles. These carry no guaranteed minimum, so armour",
                      "already reduces them fully.").push("projectiles");
            doomBlade = mul(b, "doomBlade", "The doom blade.");
            graveBlade = mul(b, "graveBlade", "The blade out of the floor.");
            iceBoulder = mul(b, "iceBoulder", "The thrown boulder.");
            shadowShard = mul(b, "shadowShard", "The shadow shard.");
            iceOrb = mul(b, "iceOrb", "One orb of the four-orb barrage.");
            b.pop();

            b.pop();

            b.comment("Multiplayer. The keepers, the Lamplighter, the Vault Warden, the Ice Monstrosity",
                      "and Velkhar count the players fighting them (alive, not creative or spectator,",
                      "within 40 blocks) and grow with them. Counted when they wake and again when",
                      "someone joins; it only ever goes up during a fight. Alone, nothing changes.")
                    .push("party");
            partyScaling = b
                    .comment("Turn the whole thing on or off.")
                    .define("enabled", true);
            partyHealthPerPlayer = b
                    .comment("Extra max health per player past the first, as a share of its base.",
                             "0.5 = two players face 150%, four face 250%. Its damage ceilings grow alike.")
                    .defineInRange("healthPerPlayer", 0.5D, 0.0D, 10.0D);
            partyDamagePerPlayer = b
                    .comment("Extra damage per player past the first, on everything of its that hits a player.")
                    .defineInRange("damagePerPlayer", 0.1D, 0.0D, 10.0D);
            partyMaxCounted = b
                    .comment("Never counts more players than this.")
                    .defineInRange("maxCounted", 8, 1, 64);
            partyAttacks = b
                    .comment("Its attacks come for every player: the Monstrosity's avalanche and Velkhar's",
                             "judgment swords for each, the Aurochs chains charges, the Priestess's requiem",
                             "and the Shepherd's herd grow with the party.")
                    .define("extraAttacks", true);
            partyFocus = b
                    .comment("Every eight seconds it turns on whoever has dealt it the most, if its",
                             "current target has barely touched it.")
                    .define("focusTopDamage", true);
            partyTrophies = b
                    .comment("Each player who fought it gets their own trophy (only they can pick it up),",
                             "and Velkhar leaves a hoard chest for each. Keys stay single.")
                    .define("personalTrophies", true);
            b.pop();
        }

        private static ModConfigSpec.DoubleValue mul(ModConfigSpec.Builder b,
                                                       String name, String what) {
            return b.comment(what).defineInRange(name, 1.0D, 0.0D, 100.0D);
        }
    }

    // ================================================================
    // CLIENT - what one person sees
    // ================================================================

    public static final class Client {

        public final ModConfigSpec.BooleanValue phaseThreeDarkness;

        Client(ModConfigSpec.Builder b) {
            b.comment("Visual only. Yours alone - it changes nothing for anyone else.")
                    .push("visuals");

            phaseThreeDarkness = b
                    .comment("Sink the world into glacial dusk once he reaches the third phase.",
                             "This is a fog and fog-colour override that fades in as you get",
                             "within thirty blocks of him. Off restores the world's own lighting",
                             "and view distance; nothing about the fight itself changes.")
                    .define("phaseThreeDarkness", true);

            b.pop();
        }
    }

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ModConfigSpec> common =
                new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        Pair<Client, ModConfigSpec> client =
                new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    /**
     * True only once the file has actually been read.
     *
     * <p>Every one of these is touched from entity code that can run before the
     * config is loaded - a world opening on a dedicated server, a /reload, the
     * data generator - and reading a spec value before then throws. Each site
     * asks this first and falls back to the shipped behaviour, so the mod is
     * never broken by being early.
     */
    public static boolean loaded = false;

    public static boolean tornadoSparesCreative() {
        return !loaded || COMMON.tornadoSparesCreative.get();
    }

    public static boolean cleaveBreaksFortress() {
        return loaded && COMMON.cleaveBreaksFortress.get();
    }

    public static boolean weather() {
        return !loaded || COMMON.velkharControlsWeather.get();
    }

    public static boolean darkness() {
        try {
            return !CLIENT_SPEC.isLoaded() || CLIENT.phaseThreeDarkness.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static float mul(ModConfigSpec.DoubleValue value) {
        return loaded ? value.get().floatValue() : 1.0F;
    }

    private FFConfig() {
    }
}
