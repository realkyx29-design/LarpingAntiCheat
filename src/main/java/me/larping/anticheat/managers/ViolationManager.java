package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.config.ConfigManager;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Violation tracking, alerts, setbacks and punishments.
 *
 * <p>Performance/anti-spam:
 * <ul>
 *   <li>All config reads use the cached {@link me.larping.anticheat.config.ConfigManager.Snapshot}.</li>
 *   <li>Alerts are rate-limited per (player, check) so a flapping check cannot
 *       spam staff chat or flood packets.</li>
 *   <li>Setbacks are rate-limited per player and only teleport to a location
 *       the player was legitimately standing on.</li>
 *   <li>Punishment commands fire at most once per configured stage per player.</li>
 * </ul>
 */
public final class ViolationManager {

    public enum Setback { NONE, MOVEMENT }

    private final LarpingAntiCheat plugin;

    /** uuid -> (check name -> VL) */
    private final Map<UUID, Map<String, Double>> violationsMap = new ConcurrentHashMap<>();
    /** uuid -> "check:alert" -> last alert epoch ms */
    private final Map<String, Long> lastAlertMs = new ConcurrentHashMap<>();
    /** uuid -> highest punishment stage index already executed */
    private final Map<UUID, Integer> punishedStage = new ConcurrentHashMap<>();

    public ViolationManager(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Report a check failure.
     *
     * @param amount     raw VL weight for this flag (typically 0.2–1.0)
     * @param confidence 0..1 detection confidence
     * @param detail     short human-readable diagnostic string
     * @param setback    whether this check may set the player back
     * @return the new total VL for this check
     */
    public double flag(Player player, String checkName, String checkType,
                       double amount, double confidence, String detail, Setback setback) {
        ConfigManager.Snapshot cfg = plugin.configManager().get();

        // Exempt players (staff / bypass permission) never accrue VL.
        if (player.hasPermission(cfg.exemptPermission())) {
            return 0.0;
        }

        CheckConfig cc = cfg.check(checkName);

        Map<String, Double> playerVl =
                violationsMap.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        // Sensitivity mildly scales weight; never lets VL be entirely dodged.
        double weight = amount * (0.5 + cc.sensitivity() * 0.5);
        double total = playerVl.merge(checkName, weight, (a, b) -> Math.min(100.0, a + b));

        // --- Logging -----------------------------------------------------
        if (cfg.loggingEnabled()) {
            String loc = "";
            if (cfg.logLocation()) {
                Location l = player.getLocation();
                loc = String.format(" @ %.0f,%.0f,%.0f %s", l.getX(), l.getY(), l.getZ(),
                        l.getWorld() != null ? l.getWorld().getName() : "?");
            }
            plugin.getLogger().warning("[LAC] " + player.getName() + " failed " + checkName
                    + " (" + checkType + ") VL=" + String.format("%.1f", total)
                    + " conf=" + (int) (confidence * 100) + "%" + loc + " - " + detail);
        }

        // --- Debug feed ---------------------------------------------------
        PlayerData data = plugin.data(player);
        if (data.isDebugging(checkName)) {
            player.sendMessage("§8[§cLAC debug§8] §7" + checkName + " §fVL="
                    + String.format("%.1f", total) + " §7" + detail);
        }

        // --- Alerts (rate-limited, threshold-gated) -----------------------
        if (cfg.alertsEnabled() && total >= cc.alertThreshold() && confidence >= 0.6) {
            String key = player.getUniqueId() + ":" + checkName;
            long now = System.currentTimeMillis();
            Long last = lastAlertMs.get(key);
            if (last == null || now - last >= cfg.alertCooldownMs()) {
                lastAlertMs.put(key, now);
                broadcastAlert(cfg, player, checkName, total, confidence);
            }
        }

        // --- Setbacks (movement checks only, rate-limited, high-confidence) -
        // A setback teleports the player, so it must only fire on a confident
        // detection (>=0.75) to avoid disrupting legitimate play.
        if (setback == Setback.MOVEMENT && cfg.setbacksEnabled()
                && total >= cc.setbackThreshold() && confidence >= 0.75
                && data.trySetbackCooldown(cfg.setbackCooldownMs())) {
            Location safe = data.safeLocation();
            if (safe != null && safe.getWorld() != null
                    && safe.getWorld().equals(player.getWorld())) {
                // Already on the server thread when called from events.
                Runnable doSetback = () -> {
                    if (player.isOnline() && !player.isDead()) {
                        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        player.teleportAsync(safe);
                    }
                };
                if (Bukkit.isPrimaryThread()) {
                    doSetback.run();
                } else {
                    Bukkit.getScheduler().runTask(plugin, doSetback);
                }
            }
        }

        // --- Punishments --------------------------------------------------
        checkPunishments(player, total);

        return total;
    }

    private void broadcastAlert(ConfigManager.Snapshot cfg, Player player, String checkName,
                                double total, double confidence) {
        String msg = cfg.alertFormat()
                .replace("%player%", player.getName())
                .replace("%check%", checkName)
                .replace("%vl%", String.format("%.1f", total))
                .replace("%ping%", String.valueOf(player.getPing()))
                .replace("%tps%", String.format("%.1f", plugin.tps()))
                .replace("%confidence%", String.valueOf((int) (confidence * 100)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("lac.alerts")) {
                online.sendMessage(msg);
            }
        }
        // Also mirror alerts to the server log so staff can review later.
        plugin.getLogger().info("[ALERT] " + msg);
    }

    public double total(Player player) {
        Map<String, Double> map = violationsMap.get(player.getUniqueId());
        if (map == null || map.isEmpty()) return 0.0;
        double sum = 0;
        for (double v : map.values()) sum += v;
        return sum;
    }

    public Map<String, Double> getViolations(Player player) {
        Map<String, Double> map = violationsMap.get(player.getUniqueId());
        if (map == null) return Map.of();
        return Map.copyOf(map);
    }

    public void clear(Player player) {
        violationsMap.remove(player.getUniqueId());
        punishedStage.remove(player.getUniqueId());
    }

    /** Called once per second from the async TPS/decay task. */
    public void decay() {
        double rate = plugin.configManager().violationDecayRate();
        violationsMap.values().forEach(map -> {
            map.replaceAll((k, v) -> v - rate);
            map.values().removeIf(v -> v <= 0.0);
        });
    }

    private void checkPunishments(Player player, double checkVl) {
        ConfigManager.Snapshot cfg = plugin.configManager().get();
        if (!cfg.punishmentsEnabled()) return;

        List<Map<?, ?>> actions = plugin.getConfig().getMapList("punishments.actions");
        if (actions.isEmpty()) return;

        int stage = punishedStage.getOrDefault(player.getUniqueId(), -1);
        for (int i = 0; i < actions.size(); i++) {
            if (i <= stage) continue;
            Map<?, ?> actionMap = actions.get(i);
            Object thObj = actionMap.get("threshold");
            Object cmdObj = actionMap.get("command");
            if (!(thObj instanceof Number th) || !(cmdObj instanceof String cmd)) continue;
            if (checkVl >= th.doubleValue()) {
                final int executedStage = i;
                String processedCmd = cmd.replace("%player%", player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Punishment command failed: " + ex.getMessage());
                    }
                });
                punishedStage.put(player.getUniqueId(), executedStage);
            }
        }
    }
}
