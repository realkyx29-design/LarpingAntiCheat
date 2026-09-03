package me.larping.anticheat.modifiers;

/**
 * A player's <b>legitimate</b> capabilities for the current event, derived from
 * real server state: game mode, potion effects, the movement-speed attribute,
 * worn armour, held item attribute modifiers, vanilla enchantments, and
 * recognised custom-enchantment abilities.
 *
 * <p>Every check reads its limits from here instead of using a hard-coded
 * vanilla constant. When a custom item legitimately changes what a player can
 * do (faster movement, higher jumps, a sword that hits harder, a pickaxe that
 * mines faster or breaks an area), these fields reflect that so the player is
 * never flagged for using it.
 *
 * <p>All multipliers are {@code 1.0} for an unmodified survival player.
 */
public final class Capabilities {

    // ---- Game mode / flight ----
    public boolean creative;
    public boolean spectator;
    public boolean adventure;
    /** Server allows flight (creative, spectator, or a server-granted fly). */
    public boolean allowedFlight;
    /** Player is currently flying with server permission (creative/donor fly). */
    public boolean flying;
    /** Gliding with an elytra. */
    public boolean gliding;

    // ---- Movement ----
    /** Live movement-speed attribute value (already includes item/effect modifiers). */
    public double movementSpeed = 0.1;
    /** Total walk/run speed multiplier from custom equipment/enchants (1 = none). */
    public double speedMultiplier = 1.0;
    /** Jump-ability multiplier from Jump Boost / custom boots (1 = normal jump). */
    public double jumpMultiplier = 1.0;
    public int speedAmplifier = -1;
    public int jumpAmplifier = -1;
    public int hasteAmplifier = -1;
    public int conduitPowerAmplifier = -1;
    public boolean levitation;
    public boolean slowFalling;
    public boolean dolphinsGrace;
    public boolean inVehicle;

    // ---- Combat ----
    /**
     * Maximum melee damage (in half-hearts, post-modifier) the player's current
     * weapon plus effects/enchants can produce on a single non-critical hit,
     * with headroom. A hit dealing more than this is suspicious.
     */
    public double maxWeaponDamage = 6.0;
    /** Extra damage multiplier/flat bonus recognised from custom sword enchants. */
    public double customDamageBonus = 0.0;

    // ---- Mining ----
    /** Mining-speed multiplier from tool + Efficiency + Haste + custom enchants (1 = bare hand). */
    public double miningSpeedMultiplier = 1.0;
    /**
     * Radius (in blocks) the player may legitimately break in one mining action
     * due to an area-mining ability (0/1 = single block). 2 = 3x3-ish area.
     */
    public int areaMineRadius = 0;
    /** True when the held tool is recognised as granting area-mining. */
    public boolean areaMining;
    /** The held item is a pickaxe (or recognised mining tool). */
    public boolean miningTool;

    /** Human-readable description of recognised custom modifiers (for alerts/debug). */
    public String customSummary = "";

    /** True if any non-vanilla custom modifier was recognised this event. */
    public boolean hasCustomModifiers() {
        return speedMultiplier > 1.0001
                || jumpMultiplier > 1.0001
                || miningSpeedMultiplier > 1.0001
                || customDamageBonus > 0.0001
                || areaMineRadius > 0;
    }
}
