package me.larping.anticheat.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in defaults for every check: tuning slots {@code v1..v3} and the set
 * of named config keys that map into them.
 *
 * <p>Slot meanings per check:
 * <ul>
 *   <li><b>speed</b>: v1 = base horizontal limit blocks/tick (sprint-jump), v2 = min confirmations</li>
 *   <li><b>fly</b>: v1 = max legitimate airborne ticks without falling, v2 = min confirmations</li>
 *   <li><b>timer</b>: v1 = balance flag threshold (ticks), v2 = min sustained fast windows, v3 = allowed speed factor</li>
 *   <li><b>phase</b>: v1 = min confirmations, v2 = collision sample step (blocks)</li>
 *   <li><b>groundspoof</b>: v1 = min confirmations</li>
 *   <li><b>jesus</b>: v1 = min confirmations</li>
 *   <li><b>spider</b>: v1 = min confirmations (per sustained climb), v2 = total climb ticks required</li>
 *   <li><b>step</b>: v1 = max legitimate upward delta per move (blocks), v2 = min confirmations</li>
 *   <li><b>blink</b>: v1 = max horizontal delta per move (blocks), v2 = max vertical delta (blocks)</li>
 *   <li><b>noknockback</b>: v1 = min observed/expected displacement ratio, v2 = min confirmations</li>
 *   <li><b>noslow</b>: v1 = min confirmations</li>
 *   <li><b>reach</b>: v1 = max attack distance eye-to-hitbox (blocks), v2 = min confirmations</li>
 *   <li><b>killaura</b>: v1 = max rotation snap per move (degrees), v2 = min confirmations</li>
 *   <li><b>scaffold</b>: v1 = max placements/sec, v2 = block-place reach (blocks), v3 = min confirmations</li>
 *   <li><b>fastbreak</b>: v1 = min fraction of vanilla break time allowed, v2 = min confirmations</li>
 *   <li><b>nuker</b>: v1 = max breaks/sec, v2 = block-break reach (blocks)</li>
 * </ul>
 */
public final class CheckDefaults {

    public static final String SPEED = "speed";
    public static final String FLY = "fly";
    public static final String TIMER = "timer";
    public static final String PHASE = "phase";
    public static final String GROUND_SPOOF = "groundspoof";
    public static final String JESUS = "jesus";
    public static final String SPIDER = "spider";
    public static final String STEP = "step";
    public static final String BLINK = "blink";
    public static final String NO_KNOCKBACK = "noknockback";
    public static final String NO_SLOW = "noslow";
    public static final String REACH = "reach";
    public static final String KILL_AURA = "killaura";
    public static final String WEAPON_DAMAGE = "weapondamage";
    public static final String SCAFFOLD = "scaffold";
    public static final String FAST_BREAK = "fastbreak";
    public static final String NUKER = "nuker";

    /**
     * Tuning entry whose insertion order defines the v1/v2/v3 slots.
     * Order is significant and must match the slot documentation at the top
     * of this class (v1 first, then v2, then v3).
     */
    public record Tuning(String key, double value) { }

    public record Def(CheckConfig config, Map<String, Double> tuning) { }

    private static final Map<String, Def> DEFAULTS = new LinkedHashMap<>();

    static {
        // name, sensitivity, alert, setback, punish, ordered tuning (v1,v2,v3)
        put(SPEED, 0.60, 6.0, 12.0, 30.0,
                t("max-horizontal-per-tick", 0.362), t("min-confirmations", 6.0));
        put(FLY, 0.60, 5.0, 10.0, 25.0,
                t("max-air-ticks", 18.0), t("min-confirmations", 4.0));
        put(TIMER, 0.60, 6.0, 12.0, 30.0,
                t("balance-threshold", 9.0), t("min-fast-windows", 10.0), t("allowed-factor", 1.16));
        put(PHASE, 0.70, 4.0, 8.0, 20.0,
                t("min-confirmations", 3.0), t("sample-step", 0.5));
        put(GROUND_SPOOF, 0.60, 5.0, 10.0, 25.0,
                t("min-confirmations", 3.0));
        put(JESUS, 0.60, 5.0, 10.0, 25.0,
                t("min-confirmations", 6.0));
        put(SPIDER, 0.60, 5.0, 10.0, 25.0,
                t("min-confirmations", 6.0), t("min-climb-ticks", 10.0));
        put(STEP, 0.60, 5.0, 10.0, 25.0,
                t("max-step-height", 0.85), t("min-confirmations", 2.0));
        put(BLINK, 0.80, 4.0, 8.0, 20.0,
                t("max-horizontal-delta", 6.0), t("max-vertical-delta", 5.0));
        put(NO_KNOCKBACK, 0.55, 6.0, 14.0, 30.0,
                t("min-velocity-ratio", 0.35), t("min-confirmations", 3.0));
        put(NO_SLOW, 0.55, 6.0, 12.0, 30.0,
                t("min-confirmations", 4.0));
        put(REACH, 0.65, 5.0, 12.0, 30.0,
                t("max-distance", 3.05), t("min-confirmations", 3.0));
        put(KILL_AURA, 0.55, 6.0, 14.0, 30.0,
                t("max-rotation-snap", 55.0), t("min-confirmations", 2.0));
        // Weapon damage validates against the real held item's max damage;
        // the check flags large spikes and cancels only sustained over-damage.
        put(WEAPON_DAMAGE, 0.60, 8.0, 12.0, 30.0,
                t("damage-headroom", 4.0), t("min-confirmations", 3.0));
        put(SCAFFOLD, 0.60, 5.0, 12.0, 30.0,
                t("max-placements-per-sec", 14.0), t("max-reach", 4.5), t("min-confirmations", 4.0));
        put(FAST_BREAK, 0.60, 5.0, 12.0, 30.0,
                t("min-break-fraction", 0.55), t("min-confirmations", 2.0));
        put(NUKER, 0.70, 4.0, 10.0, 25.0,
                t("max-breaks-per-sec", 14.0), t("max-reach", 5.2));
    }

    private static Tuning t(String key, double value) {
        return new Tuning(key, value);
    }

    private static void put(String name, double sens, double alert, double setback,
                            double punish, Tuning... tuning) {
        Map<String, Double> map = new LinkedHashMap<>();
        double v1 = 0, v2 = 0, v3 = 0;
        for (int i = 0; i < tuning.length; i++) {
            map.put(tuning[i].key(), tuning[i].value());
            if (i == 0) v1 = tuning[i].value();
            else if (i == 1) v2 = tuning[i].value();
            else if (i == 2) v3 = tuning[i].value();
        }
        DEFAULTS.put(name, new Def(
                new CheckConfig(name, true, sens, alert, setback, punish, v1, v2, v3),
                Map.copyOf(map)));
    }

    public static Def def(String name) {
        Def d = DEFAULTS.get(name.toLowerCase());
        if (d == null) {
            return new Def(new CheckConfig(name, true, 0.5, 6.0, 12.0, 30.0, 0, 0, 0), Map.of());
        }
        return d;
    }

    public static CheckConfig get(String name) {
        return def(name).config();
    }

    public static Map<String, Def> all() {
        return DEFAULTS;
    }

    private CheckDefaults() { }
}
