package me.larping.anticheat.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player runtime state.
 *
 * <p>Threading model: ALL mutating access happens on the server main thread
 * (move/interact/damage events and the synchronous flush task). The only
 * off-thread access is the async scheduler task that reads
 * {@link #lastFlightCheckMs} / {@link #lastFlushMs} style fields through
 * {@link java.util.concurrent} structures where required. Timing fields are
 * plain longs because they are only touched on the main thread.
 */
public final class PlayerData {

    // ---------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------
    private final UUID uuid;
    private final String name;

    // ---------------------------------------------------------------
    // Movement state (updated every move event, main thread)
    // ---------------------------------------------------------------
    private Location lastLocation;   // position after the most recent processed move
    private Location prevLocation;   // position before the most recent processed move
    private Location safeLocation;
    private double deltaX, deltaY, deltaZ;
    private double lastDeltaX, lastDeltaY, lastDeltaZ;
    private float lastYaw, lastPitch;
    private boolean hasLastRotation = false;
    public long recentSnapMs = 0L;
    public float recentSnapDegrees = 0f;
    private int airTicks = 0;
    private int liquidTicks = 0;
    private int onGroundTicks = 0;
    private boolean wasOnGround = true;

    /** Velocity vector reported by knockback/velocity events (blocks per tick expected). */
    private double velX, velY, velZ;
    private long velocityUntil = 0L;
    private long velocityAppliedMs = 0L;
    private double knockbackStrength = 0.0;
    private double knockbackObserved = 0.0;
    private int knockbackTicks = 0;
    /** True when the active velocity window was combat knockback (vs self-propelled). */
    private boolean velocityFromKnockback = false;

    // ---------------------------------------------------------------
    // Grace periods (millis timestamps). Never a blanket movement bypass:
    // they are only consulted by the checks that legitimately need them.
    // ---------------------------------------------------------------
    private long teleportGraceUntil = 0L;   // covers teleport glitches / blink resync
    private long joinGraceUntil = 0L;
    private long respawnGraceUntil = 0L;
    private long worldChangeGraceUntil = 0L;
    private long riptideGraceUntil = 0L;    // trident riptide charge
    private long glideFireworkUntil = 0L;   // elytra firework boost
    private long lastBlockPlaceMs = 0L;

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        Location spawn = player.getLocation();
        this.lastLocation = spawn.clone();
        this.safeLocation = spawn.clone();
        this.lastYaw = spawn.getYaw();
        this.lastPitch = spawn.getPitch();
        long now = System.currentTimeMillis();
        this.joinGraceUntil = now + 2000L;
        this.teleportGraceUntil = now + 1000L;
    }

    // ---------------------------------------------------------------
    // Movement update
    // ---------------------------------------------------------------
    public void updateMovement(Location now, boolean serverGround) {
        if (now == null || lastLocation == null) return;
        if (lastLocation.getWorld() != null && now.getWorld() != null
                && !lastLocation.getWorld().equals(now.getWorld())) {
            // World change handled separately; just rebase.
            this.lastLocation = now.clone();
            return;
        }

        this.prevLocation = this.lastLocation != null ? this.lastLocation.clone() : null;

        this.lastDeltaX = deltaX;
        this.lastDeltaY = deltaY;
        this.lastDeltaZ = deltaZ;

        this.deltaX = now.getX() - lastLocation.getX();
        this.deltaY = now.getY() - lastLocation.getY();
        this.deltaZ = now.getZ() - lastLocation.getZ();

        // Accumulate observed horizontal displacement while a knockback
        // window is open; the NoKnockback check consumes it after a few ticks.
        if (hasVelocity()) {
            knockbackObserved += Math.hypot(deltaX, deltaZ);
            knockbackTicks++;
        }

        if (serverGround) {
            airTicks = 0;
            onGroundTicks++;
        } else {
            airTicks++;
            onGroundTicks = 0;
        }
        this.wasOnGround = serverGround;

        this.lastLocation = now.clone();
    }

    public void updateRotation(float yaw, float pitch) {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.hasLastRotation = true;
    }

    public double horizontalSpeed() {
        return Math.hypot(deltaX, deltaZ);
    }

    public double lastHorizontalSpeed() {
        return Math.hypot(lastDeltaX, lastDeltaZ);
    }

    /** Shortest signed yaw difference in degrees, range [-180, 180]. */
    public static float yawDelta(float a, float b) {
        float d = (a - b) % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    // ---------------------------------------------------------------
    // Check buffers (decaying confirmations). Bounded, never negative.
    // ---------------------------------------------------------------
    private double speedBuffer, flyBuffer, timerBalance, timerBurst;
    private double phaseBuffer, noKnockbackBuffer, noSlowBuffer;
    private double jesusBuffer, spiderBuffer, stepBuffer, blinkBuffer;
    private double fastPlaceBuffer, fastBreakBuffer, nukerBuffer;
    private double reachBuffer, auraBuffer, groundSpoofBuffer;
    private int timerFastWindows;

    public double buffer(String key) {
        return switch (key) {
            case "speed" -> speedBuffer;
            case "fly" -> flyBuffer;
            case "phase" -> phaseBuffer;
            case "noknockback" -> noKnockbackBuffer;
            case "noslow" -> noSlowBuffer;
            case "jesus" -> jesusBuffer;
            case "spider" -> spiderBuffer;
            case "step" -> stepBuffer;
            case "blink" -> blinkBuffer;
            case "fastplace" -> fastPlaceBuffer;
            case "fastbreak" -> fastBreakBuffer;
            case "nuker" -> nukerBuffer;
            case "reach" -> reachBuffer;
            case "killaura" -> auraBuffer;
            case "groundspoof" -> groundSpoofBuffer;
            default -> 0.0;
        };
    }

    /** Adds delta to a buffer (clamped at [0, cap]) and returns the new value. */
    public double adjustBuffer(String key, double delta, double cap) {
        double v = Math.max(0.0, Math.min(cap, buffer(key) + delta));
        switch (key) {
            case "speed" -> speedBuffer = v;
            case "fly" -> flyBuffer = v;
            case "phase" -> phaseBuffer = v;
            case "noknockback" -> noKnockbackBuffer = v;
            case "noslow" -> noSlowBuffer = v;
            case "jesus" -> jesusBuffer = v;
            case "spider" -> spiderBuffer = v;
            case "step" -> stepBuffer = v;
            case "blink" -> blinkBuffer = v;
            case "fastplace" -> fastPlaceBuffer = v;
            case "fastbreak" -> fastBreakBuffer = v;
            case "nuker" -> nukerBuffer = v;
            case "reach" -> reachBuffer = v;
            case "killaura" -> auraBuffer = v;
            case "groundspoof" -> groundSpoofBuffer = v;
            default -> { }
        }
        return v;
    }

    public void resetBuffers() {
        speedBuffer = flyBuffer = phaseBuffer = noKnockbackBuffer = noSlowBuffer = 0;
        jesusBuffer = spiderBuffer = stepBuffer = blinkBuffer = 0;
        fastPlaceBuffer = fastBreakBuffer = nukerBuffer = 0;
        reachBuffer = auraBuffer = groundSpoofBuffer = 0;
        timerBalance = timerBurst = 0;
        timerFastWindows = 0;
    }

    // ---------------------------------------------------------------
    // Timer check bookkeeping
    // ---------------------------------------------------------------
    public long lastTimerTickMs = 0L;
    public int movePacketsThisWindow = 0;

    public double timerBalance() { return timerBalance; }
    public void timerBalance(double v) { this.timerBalance = v; }
    public double timerBurst() { return timerBurst; }
    public void timerBurst(double v) { this.timerBurst = v; }
    public int timerFastWindows() { return timerFastWindows; }
    public void timerFastWindows(int v) { this.timerFastWindows = v; }

    // ---------------------------------------------------------------
    // Velocity / knockback
    // ---------------------------------------------------------------
    public void applyVelocity(double x, double y, double z) {
        applyVelocity(x, y, z, false);
    }

    /** Combat knockback (from PlayerKnockbackEvent) — validated by NoKnockback. */
    public void applyKnockback(double x, double y, double z) {
        applyVelocity(x, y, z, true);
    }

    private void applyVelocity(double x, double y, double z, boolean fromKnockback) {
        boolean alreadyTrackingKnockback = velocityFromKnockback && hasVelocity();
        this.velX = x;
        this.velY = y;
        this.velZ = z;
        this.knockbackStrength = Math.hypot(x, z);
        this.velocityAppliedMs = System.currentTimeMillis();
        this.velocityUntil = this.velocityAppliedMs + 600L;
        // Don't reset the observation window or de-tag combat knockback when a
        // benign velocity event arrives during an active knockback window.
        if (!alreadyTrackingKnockback) {
            this.knockbackObserved = 0.0;
            this.knockbackTicks = 0;
            this.velocityFromKnockback = fromKnockback;
        }
    }

    public boolean hasVelocity() {
        return System.currentTimeMillis() < velocityUntil;
    }

    /** Expected horizontal movement this tick caused by active knockback (friction decayed). */
    public double expectedVelocityHorizontal() {
        if (!hasVelocity()) return 0.0;
        long age = System.currentTimeMillis() - velocityAppliedMs;
        double ticks = age / 50.0;
        // Vanilla horizontal friction ~0.91 per tick for knockback on ground.
        double decay = Math.pow(0.91, ticks);
        return Math.hypot(velX, velZ) * decay;
    }

    public double knockbackStrength() {
        return hasVelocity() ? knockbackStrength : 0.0;
    }

    /**
     * Called by the anti-knockback check after the player has had a few ticks
     * to respond to a knockback event. Returns the ratio of accumulated
     * observed horizontal displacement vs the physics-expected amount, or
     * null if no knockback window is pending/ready yet. Consumes the record.
     */
    public Double consumeKnockbackObservation() {
        // Only validate combat knockback (not self-propelled velocities like
        // explosions, trident riptide or the player's own movement).
        if (!hasVelocity() || !velocityFromKnockback) return null;
        // Ignore trivial knocks (e.g. slight hits in the air).
        if (knockbackStrength < 0.08) { velocityUntil = 0L; return null; }
        // Wait ~3 movement packets so the response is measurable. Tick count
        // (not wall clock) is used so behaviour is server-tick deterministic.
        if (knockbackTicks < 3) {
            return null;
        }
        // Expected total horizontal travel over the observed window for a
        // friction-decayed knockback at this strength (~0.4 initial for a
        // sprint hit). knockbackStrength is per-tick initial velocity.
        double expected = Math.max(0.12, knockbackStrength * 3.4);
        double ratio = knockbackObserved / expected;
        velocityUntil = 0L; // consume
        return ratio;
    }

    // ---------------------------------------------------------------
    // Grace setters / getters
    // ---------------------------------------------------------------
    public void setTeleportGrace(long ms) { this.teleportGraceUntil = System.currentTimeMillis() + ms; }
    public void setJoinGrace(long ms) { this.joinGraceUntil = System.currentTimeMillis() + ms; }
    public void setRespawnGrace(long ms) { this.respawnGraceUntil = System.currentTimeMillis() + ms; }
    public void setWorldChangeGrace(long ms) { this.worldChangeGraceUntil = System.currentTimeMillis() + ms; }
    public void setRiptideGrace(long ms) { this.riptideGraceUntil = System.currentTimeMillis() + ms; }
    public void setGlideFireworkBoost(long ms) { this.glideFireworkUntil = System.currentTimeMillis() + ms; }

    public boolean inTeleportGrace() { return System.currentTimeMillis() < teleportGraceUntil; }
    public boolean inJoinGrace() { return System.currentTimeMillis() < joinGraceUntil; }
    public boolean inRespawnGrace() { return System.currentTimeMillis() < respawnGraceUntil; }
    public boolean inWorldChangeGrace() { return System.currentTimeMillis() < worldChangeGraceUntil; }
    public boolean inRiptideGrace() { return System.currentTimeMillis() < riptideGraceUntil; }
    public boolean hasGlideFireworkBoost() { return System.currentTimeMillis() < glideFireworkUntil; }

    /** Broad grace: skip checks entirely (login/teleport/world change/respawn). */
    public boolean inHardGrace() {
        return inTeleportGrace() || inJoinGrace() || inRespawnGrace() || inWorldChangeGrace();
    }

    // ---------------------------------------------------------------
    // Placement / breaking tracking
    // ---------------------------------------------------------------
    private final Deque<Long> placementTimes = new ArrayDeque<>();
    private final Deque<Long> breakTimes = new ArrayDeque<>();
    /** blockKey -> time damage started (BlockDamageEvent) */
    private final java.util.HashMap<Long, Long> breakStart = new java.util.HashMap<>();

    public void recordPlacement() {
        long now = System.currentTimeMillis();
        placementTimes.addLast(now);
        while (!placementTimes.isEmpty() && now - placementTimes.peekFirst() > 2000L) {
            placementTimes.pollFirst();
        }
        lastBlockPlaceMs = now;
    }

    public int placementsInLastMs(long windowMs) {
        long now = System.currentTimeMillis();
        int c = 0;
        for (long t : placementTimes) if (now - t <= windowMs) c++;
        return c;
    }

    public long msSinceLastPlace() {
        return placementTimes.isEmpty() ? Long.MAX_VALUE : System.currentTimeMillis() - placementTimes.peekLast();
    }

    public void recordBreakStart(long blockKey) {
        breakStart.putIfAbsent(blockKey, System.currentTimeMillis());
        // Keep the map small.
        if (breakStart.size() > 64) {
            var it = breakStart.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    /** Returns time in ms spent breaking the block, or -1 if no start recorded. */
    public long consumeBreakDuration(long blockKey) {
        Long start = breakStart.remove(blockKey);
        return start == null ? -1L : System.currentTimeMillis() - start;
    }

    public void recordBreak() {
        long now = System.currentTimeMillis();
        breakTimes.addLast(now);
        while (!breakTimes.isEmpty() && now - breakTimes.peekFirst() > 1000L) {
            breakTimes.pollFirst();
        }
    }

    public int breaksInLastSecond() {
        long now = System.currentTimeMillis();
        int c = 0;
        for (long t : breakTimes) if (now - t <= 1000L) c++;
        return c;
    }

    // ---------------------------------------------------------------
    // Attack tracking (killaura multi-target detection)
    // ---------------------------------------------------------------
    private long distinctTargetWindowStart = 0L;
    private int distinctTargets = 0;
    private final Set<UUID> recentTargets = ConcurrentHashMap.newKeySet();

    public void recordAttack(UUID targetId) {
        long now = System.currentTimeMillis();
        if (now - distinctTargetWindowStart > 1000L) {
            distinctTargetWindowStart = now;
            distinctTargets = 0;
            recentTargets.clear();
        }
        if (recentTargets.add(targetId)) distinctTargets++;
    }

    public int distinctTargetsLastSecond() {
        return distinctTargets;
    }

    // ---------------------------------------------------------------
    // Safe / last locations
    // ---------------------------------------------------------------
    public Location lastLocation() { return lastLocation; }
    /** Position before the most recent processed move (for path sampling). */
    public Location prevLocation() { return prevLocation; }
    public Location safeLocation() { return safeLocation; }

    public void safeLocation(Location location) {
        if (location != null && location.getWorld() != null
                && (safeLocation == null || safeLocation.getWorld() == null
                    || location.getWorld().equals(safeLocation.getWorld()))) {
            this.safeLocation = location.clone();
        }
    }

    public void rebase(Location location) {
        this.lastLocation = location.clone();
        resetBuffers();
    }

    // ---------------------------------------------------------------
    // Simple accessors
    // ---------------------------------------------------------------
    public UUID uuid() { return uuid; }
    public String playerName() { return name; }
    public double deltaX() { return deltaX; }
    public double deltaY() { return deltaY; }
    public double deltaZ() { return deltaZ; }
    public double lastDeltaY() { return lastDeltaY; }
    public int airTicks() { return airTicks; }
    public void airTicks(int v) { this.airTicks = v; }
    public int liquidTicks() { return liquidTicks; }
    public void liquidTicks(int v) { this.liquidTicks = v; }
    public int onGroundTicks() { return onGroundTicks; }
    public boolean wasOnGround() { return wasOnGround; }
    public float lastYaw() { return lastYaw; }
    public float lastPitch() { return lastPitch; }
    public boolean hasLastRotation() { return hasLastRotation; }
    public long lastBlockPlaceMs() { return lastBlockPlaceMs; }

    // ---------------------------------------------------------------
    // Debug
    // ---------------------------------------------------------------
    private final Set<String> debugChecks = ConcurrentHashMap.newKeySet();

    public void setDebug(String check, boolean debug) {
        if (debug) debugChecks.add(check.toLowerCase());
        else debugChecks.remove(check.toLowerCase());
    }

    public boolean isDebugging(String check) {
        return debugChecks.contains("all") || debugChecks.contains(check.toLowerCase());
    }

    /** Setback rate limiter: last time this player was set back. */
    private long lastSetbackMs = 0L;
    public boolean trySetbackCooldown(long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now - lastSetbackMs < cooldownMs) return false;
        lastSetbackMs = now;
        return true;
    }
}
