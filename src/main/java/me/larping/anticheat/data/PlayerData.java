package me.larping.anticheat.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates runtime tracking data for each online player.
 * Tracks movement history, placement rates, grace periods, buffers, and packet/timer timestamps.
 */
public final class PlayerData {
    private final Player player;
    private Location lastLocation;
    private Location safeLocation;

    private long exemptUntil = 0;
    private long lastActionTime = 0;

    // Grace period timestamps
    private long teleportGraceUntil = 0;
    private long velocityGraceUntil = 0;
    private long damageGraceUntil = 0;
    private long joinGraceUntil = 0;
    private long respawnGraceUntil = 0;
    private long worldChangeGraceUntil = 0;

    // Movement state
    private double deltaX, deltaY, deltaZ;
    private int airTicks = 0;
    private int speedBuffer = 0;
    private int flyBuffer = 0;
    private int timerBuffer = 0;
    private long lastPacketTime = 0;

    // Scaffold tracking
    private final LinkedList<Long> placementTimestamps = new LinkedList<>();

    // Debug tracking per check
    private final Set<String> debugChecks = ConcurrentHashMap.newKeySet();

    public PlayerData(Player player) {
        this.player = player;
        this.lastLocation = player.getLocation().clone();
        this.safeLocation = player.getLocation().clone();
        this.joinGraceUntil = System.currentTimeMillis() + 2000;
    }

    public Player player() {
        return player;
    }

    public Location lastLocation() {
        return lastLocation;
    }

    public Location safeLocation() {
        return safeLocation;
    }

    public void safeLocation(Location location) {
        if (location != null && location.getWorld() != null && location.getWorld().equals(player.getWorld())) {
            this.safeLocation = location.clone();
        }
    }

    public void updateMovement(Location now) {
        if (now == null) return;
        this.deltaX = now.getX() - lastLocation.getX();
        this.deltaY = now.getY() - lastLocation.getY();
        this.deltaZ = now.getZ() - lastLocation.getZ();

        if (now.getY() <= lastLocation.getY() + 0.01 || player.isOnGround()) {
            airTicks = 0;
        } else {
            airTicks++;
        }
        this.lastLocation = now.clone();
    }

    public double horizontalSpeed() {
        return Math.hypot(deltaX, deltaZ);
    }

    public double deltaY() {
        return deltaY;
    }

    public int airTicks() {
        return airTicks;
    }

    public int speedBuffer() {
        return speedBuffer;
    }

    public int speedBuffer(int value) {
        this.speedBuffer = Math.max(0, value);
        return this.speedBuffer;
    }

    public int flyBuffer() {
        return flyBuffer;
    }

    public int flyBuffer(int value) {
        this.flyBuffer = Math.max(0, value);
        return this.flyBuffer;
    }

    public int timerBuffer() {
        return timerBuffer;
    }

    public int timerBuffer(int value) {
        this.timerBuffer = Math.max(0, value);
        return this.timerBuffer;
    }

    public long lastPacketTime() {
        return lastPacketTime;
    }

    public void recordPacketTime(long time) {
        this.lastPacketTime = time;
    }

    public void exempt(long ticks) {
        this.exemptUntil = Math.max(this.exemptUntil, System.currentTimeMillis() + (ticks * 50L));
    }

    public void setTeleportGrace(long ms) { this.teleportGraceUntil = System.currentTimeMillis() + ms; }
    public void setVelocityGrace(long ms) { this.velocityGraceUntil = System.currentTimeMillis() + ms; }
    public void setDamageGrace(long ms) { this.damageGraceUntil = System.currentTimeMillis() + ms; }
    public void setRespawnGrace(long ms) { this.respawnGraceUntil = System.currentTimeMillis() + ms; }
    public void setWorldChangeGrace(long ms) { this.worldChangeGraceUntil = System.currentTimeMillis() + ms; }

    public boolean isGraceful() {
        long now = System.currentTimeMillis();
        return now < exemptUntil ||
               now < teleportGraceUntil ||
               now < velocityGraceUntil ||
               now < damageGraceUntil ||
               now < joinGraceUntil ||
               now < respawnGraceUntil ||
               now < worldChangeGraceUntil;
    }

    public void recordPlacement() {
        long now = System.currentTimeMillis();
        synchronized (placementTimestamps) {
            placementTimestamps.addLast(now);
            while (!placementTimestamps.isEmpty() && now - placementTimestamps.getFirst() > 3000) {
                placementTimestamps.removeFirst();
            }
        }
        lastActionTime = now;
    }

    public int getRecentPlacementsPerSec() {
        long now = System.currentTimeMillis();
        synchronized (placementTimestamps) {
            return (int) placementTimestamps.stream().filter(t -> now - t <= 1000).count();
        }
    }

    public void setDebug(String check, boolean debug) {
        if (debug) debugChecks.add(check.toLowerCase());
        else debugChecks.remove(check.toLowerCase());
    }

    public boolean isDebugging(String check) {
        return debugChecks.contains("all") || debugChecks.contains(check.toLowerCase());
    }

    public long lastAction() {
        return lastActionTime;
    }
}
