package me.larping.anticheat.physics;

import me.larping.anticheat.data.PlayerData;
import me.larping.anticheat.util.CollisionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Server-authoritative, per-move physics snapshot.
 *
 * <p>This is the single source of truth for every movement check. It is built
 * once per position packet on the main thread and computes — from real server
 * state and block collision shapes, never from client-claimed flags — all the
 * quantities the checks need:
 *
 * <ul>
 *   <li>deltas and speeds for this and the previous move,</li>
 *   <li>the genuine ground state ({@link CollisionUtil#isOnGround}) vs the
 *       client's claimed {@code onGround}, used for ground-spoof detection,</li>
 *   <li>the player's real base movement speed attribute (so custom SMP items,
 *       effects and walk-speed modifications are always accounted for),</li>
 *   <li>environment: water, lava, cobweb, ice, slime, climbables, wall
 *       contact — via actual block collision shapes and material groups,</li>
 *   <li>potion effects, gliding, riptide, firework, velocity and grace.</li>
 * </ul>
 *
 * Computing this once eliminates duplicate block lookups across checks (the
 * previous design re-ran {@code getBlock}/{@code getCollisionShape} in every
 * check) and, more importantly, guarantees every check reasons from the same
 * verified facts — which is what makes the detection hard to bypass without
 * false-flagging legitimate play.
 */
public final class MovementSnapshot {

    private final Player player;
    private final Location from;
    private final Location to;

    // Deltas (blocks per move packet).
    public final double deltaX, deltaY, deltaZ;
    public final double lastDeltaX, lastDeltaY, lastDeltaZ;
    public final double hSpeed;
    public final double lastHSpeed;

    // Ground / air.
    public final boolean serverGround;
    public final boolean clientGround;
    public final int airTicks;

    // Environment (real blocks).
    public final Block feetBlock;
    public final Block headBlock;
    public final Block belowBlock;
    public final boolean inWater;
    public final boolean inLava;
    public final boolean inWeb;
    public final boolean onIce;
    public final boolean onSlime;
    public final boolean onBouncy;       // slime / bed (bounce launch)
    public final boolean onClimbable;    // ladder / vine / scaffolding / vines
    public final boolean touchingWall;
    public final boolean feetInLiquid;
    public final boolean headInLiquid;

    // Player state (server-verified).
    public final boolean sprinting;
    public final boolean sneaking;
    public final boolean gliding;
    public final boolean climbing;
    public final boolean serverFlying;   // server-granted flight (creative/donor)
    public final boolean insideVehicle;
    public final boolean riptide;
    public final boolean fireworkBoost;

    // Movement physics.
    /** Real base movement speed attribute (blocks/tick walk speed), ~0.1 vanilla. */
    public final double baseSpeed;
    public final int speedAmplifier;     // -1 = no speed effect
    public final int jumpAmplifier;      // -1 = no jump boost
    public final boolean levitation;
    public final boolean slowFalling;
    public final double velocityH;       // active knockback/velocity horizontal (friction decayed)
    public final boolean hasVelocity;

    // Exemption.
    public final boolean fullyExempt;    // hard grace / creative / spectator / bypass perm / dead

    private MovementSnapshot(Player player, PlayerData data, Location from, Location to) {
        this.player = player;
        this.from = from;
        this.to = to;

        this.deltaX = to.getX() - from.getX();
        this.deltaY = to.getY() - from.getY();
        this.deltaZ = to.getZ() - from.getZ();
        this.lastDeltaX = data.deltaX();
        this.lastDeltaY = data.deltaY();
        this.lastDeltaZ = data.deltaZ();
        this.hSpeed = Math.hypot(deltaX, deltaZ);
        this.lastHSpeed = Math.hypot(lastDeltaX, lastDeltaZ);

        // Ground truth from collision shapes.
        this.serverGround = CollisionUtil.isOnGround(to);
        this.clientGround = player.isOnGround();
        this.airTicks = data.airTicks();

        // Blocks.
        this.feetBlock = to.getBlock();
        this.headBlock = to.clone().add(0, 0.9, 0).getBlock();
        this.belowBlock = to.clone().subtract(0, 0.3, 0).getBlock();

        String feetName = feetBlock.getType().name();
        String belowName = belowBlock.getType().name();
        this.inWater = feetBlock.isLiquid() && isWater(feetBlock)
                || belowBlock.isLiquid() && isWater(belowBlock);
        this.inLava = isLava(feetBlock) || isLava(belowBlock) || isLava(headBlock);
        this.feetInLiquid = feetBlock.isLiquid();
        this.headInLiquid = headBlock.isLiquid();
        this.inWeb = feetName.contains("WEB");
        String iceName = belowName;
        this.onIce = iceName.contains("ICE") || iceName.contains("FROSTED");
        this.onSlime = belowName.contains("SLIME");
        this.onBouncy = onSlime || belowName.contains("BED") || feetName.contains("SLIME");
        this.onClimbable = player.isClimbing()
                || feetName.contains("LADDER") || feetName.contains("VINE")
                || feetName.contains("SCAFFOLDING") || feetName.contains("TWISTING")
                || feetName.contains("WEEPING") || feetName.contains("CAVE_VINES");
        this.touchingWall = CollisionUtil.touchingWall(to);

        // Player state.
        this.sprinting = player.isSprinting();
        this.sneaking = player.isSneaking();
        this.gliding = player.isGliding();
        this.climbing = player.isClimbing();
        this.serverFlying = player.isFlying();
        this.insideVehicle = player.isInsideVehicle();
        this.riptide = data.inRiptideGrace();
        this.fireworkBoost = data.hasGlideFireworkBoost();

        // Movement speed attribute — the real, server-side base speed. This
        // automatically honours custom SMP items/passives that change speed.
        // The enum constant was renamed across versions: modern Paper/Spigot
        // (1.21.3+) uses MOVEMENT_SPEED; older builds use GENERIC_MOVEMENT_SPEED.
        // Resolve it reflectively so the plugin runs on both.
        double speed = 0.1;
        int speedAmp = -1, jumpAmp = -1;
        try {
            Attribute speedAttr = resolveSpeedAttribute();
            if (speedAttr != null) {
                AttributeInstance attr = player.getAttribute(speedAttr);
                if (attr != null) speed = attr.getValue();
            }
        } catch (Throwable ignored) {
            // Attribute unavailable: fall back to vanilla base speed (0.1).
        }
        PotionEffect speedFx = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedFx != null) { speedAmp = speedFx.getAmplifier(); }
        PotionEffect jumpFx = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpFx != null) { jumpAmp = jumpFx.getAmplifier(); }
        this.baseSpeed = speed;
        this.speedAmplifier = speedAmp;
        this.jumpAmplifier = jumpAmp;
        this.levitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        this.slowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

        this.velocityH = data.expectedVelocityHorizontal();
        this.hasVelocity = data.hasVelocity();

        this.fullyExempt = data.inHardGrace();
    }

    /**
     * Builds a snapshot. {@code from} is the position before this move
     * (previous packet), {@code to} the current position. {@code data} must
     * already have had {@link PlayerData#updateMovement} called so deltas and
     * air-ticks are current.
     */
    public static MovementSnapshot capture(Player player, PlayerData data, Location from, Location to) {
        return new MovementSnapshot(player, data, from, to);
    }

    /** Cached movement-speed attribute constant (resolved across API versions). */
    private static Attribute speedAttribute;
    private static boolean speedAttributeResolved;

    private static Attribute resolveSpeedAttribute() {
        if (speedAttributeResolved) return speedAttribute;
        speedAttributeResolved = true;
        // Attribute is an enum whose constant was renamed across versions:
        // modern Paper/Spigot (1.21.3+) -> MOVEMENT_SPEED, older -> GENERIC_MOVEMENT_SPEED.
        try {
            for (Attribute attr : Attribute.values()) {
                String n = attr.name();
                if (n.equals("MOVEMENT_SPEED") || n.equals("GENERIC_MOVEMENT_SPEED")) {
                    speedAttribute = attr;
                    break;
                }
            }
        } catch (Throwable ignored) {
            speedAttribute = null;
        }
        return speedAttribute;
    }

    private static boolean isWater(Block b) {
        String n = b.getType().name();
        return b.isLiquid() && (n.contains("WATER") || n.contains("SEAGRASS") || n.contains("KELP")
                || n.contains("BUBBLE"));
    }

    private static boolean isLava(Block b) {
        return b.isLiquid() && b.getType().name().contains("LAVA");
    }

    /**
     * Physics-based maximum horizontal speed (blocks/tick) for this move.
     *
     * <p>Derived from the real movement-speed attribute rather than a hard
     * constant, so custom SMP speed modifiers, sprint-jump physics and speed
     * potions are all modelled. Latency/TPS and SMP-compat slack are additive
     * and bounded so they can never multiply into a bypass. Returns
     * {@link Double#POSITIVE_INFINITY} when speed simply cannot be bounded
     * this tick (gliding, riptide, liquid, web, climbable, velocity) — those
     * are handled by their own dedicated checks/envelopes.
     */
    public double maxGroundHorizontalSpeed(int ping, double tps, boolean pingComp,
                                           boolean tpsComp, double customComp) {
        if (gliding || riptide || inLava) return Double.POSITIVE_INFINITY;
        if (feetInLiquid || headInLiquid || inWeb || onClimbable) return Double.POSITIVE_INFINITY;

        // Vanilla: a walk is ~0.22 b/t and a sprint-jump peaks ~0.36 b/t, i.e.
        // roughly base(0.1) * 3.6. The movement-speed attribute already
        // includes sprint state and speed-potion effects (Paper exposes the
        // live value), so we do NOT multiply for those again — that is what
        // made the old limit stack to ~7x. Use 3.6 as the per-tick physics
        // ceiling, then only bounded slack is added below.
        double limit = baseSpeed * 3.6;

        // Ice/slime friction gives a short launch bonus (single capped factor).
        if (onIce) limit *= 1.30;
        else if (onSlime) limit *= 1.20;
        // Airborne jump arcs travel slightly farther between packets.
        if (!serverGround) limit *= 1.15;
        // Small bounded SMP custom-mechanics allowance.
        limit *= customComp;

        // Server-applied velocity adds directly (the server knows the vector).
        limit += velocityH;

        // Latency / TPS slack: additive and tightly bounded.
        if (pingComp && ping > 90) limit += Math.min(0.16, (ping - 90) / 1600.0);
        if (tpsComp && tps < 19.0) limit += Math.min(0.30, (20.0 - tps) * 0.05);

        return limit;
    }

    /**
     * Number of airborne ticks beyond which a non-falling player is implausible
     * (i.e. must be flying/gliding). Accounts for jump boost, slow falling,
     * bounces, velocity and SMP mechanics; gliding/levitation/riptide/liquid/
     * climbable return a very large value (handled/exempt elsewhere).
     */
    public int maxSustainedAirTicks(double customAirAllowance) {
        if (gliding || levitation || riptide || feetInLiquid || headInLiquid
                || onClimbable || inLava) return Integer.MAX_VALUE;
        int max = 18; // longest legitimate sprint-jump/edge-gap arc
        if (jumpAmplifier >= 0) max += 6 + (jumpAmplifier + 1) * 4;
        if (slowFalling) max += 40;
        if (hasVelocity) max += 12;
        if (onBouncy) max += 16;
        max += (int) customAirAllowance;
        return max;
    }

    public Player player() { return player; }
    public Location from() { return from; }
    public Location to() { return to; }
}
