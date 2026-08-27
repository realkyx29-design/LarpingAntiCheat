package me.larping.anticheat.managers;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages violation tracking, confidence scoring, violation decay,
 * administrative alerts, logging, and automated punishments.
 */
public final class ViolationManager {
    public record Evidence(
            String playerName,
            String checkName,
            String checkType,
            double addedAmount,
            double confidence,
            int ping,
            double tps,
            Location location,
            String detail,
            long timestamp
    ) {}

    private final LarpingAntiCheat plugin;
    private final Map<UUID, Map<String, Double>> violationsMap = new ConcurrentHashMap<>();

    public ViolationManager(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    public double add(Player player, String checkName, String checkType, double amount, double confidence, String detail) {
        if (player.hasPermission(plugin.configManager().exemptPermission())) {
            return 0.0;
        }

        UUID uuid = player.getUniqueId();
        Map<String, Double> playerVl = violationsMap.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        
        double sensitivity = plugin.configManager().getSensitivity(checkName);
        double finalAmount = amount * Math.max(0.1, sensitivity);

        double total = playerVl.merge(checkName, finalAmount, (existing, added) -> Math.min(100.0, existing + added));

        Evidence evidence = new Evidence(
                player.getName(),
                checkName,
                checkType,
                finalAmount,
                confidence,
                player.getPing(),
                plugin.tps(),
                player.getLocation(),
                detail,
                System.currentTimeMillis()
        );

        if (plugin.configManager().isLoggingEnabled()) {
            plugin.getLogger().warning("[Violation] " + evidence.playerName() + " failed " + evidence.checkName() + 
                    " (VL: " + String.format("%.2f", total) + ", Confidence: " + (int)(confidence * 100) + "%) - " + detail);
        }

        PlayerData data = plugin.data(player);
        if (data != null && data.isDebugging(checkName)) {
            player.sendMessage("§8[§cLAC Debug§8] §7Check §f" + checkName + " §7triggered. VL: §f" + String.format("%.2f", total) + " §7Detail: §f" + detail);
        }

        double alertThreshold = plugin.getConfig().getDouble("checks." + checkName.toLowerCase().replace(" ", "") + ".alert-threshold", 5.0);
        if (plugin.configManager().isAlertsEnabled() && total >= alertThreshold && confidence >= 0.65) {
            String format = plugin.configManager().alertFormat();
            String msg = format
                    .replace("%player%", player.getName())
                    .replace("%check%", checkName)
                    .replace("%vl%", String.format("%.1f", total))
                    .replace("%ping%", String.valueOf(player.getPing()))
                    .replace("%tps%", String.format("%.1f", plugin.tps()));
            
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("lac.alerts")) {
                    online.sendMessage(msg);
                }
            }
        }

        double setbackThreshold = plugin.getConfig().getDouble("checks." + checkName.toLowerCase().replace(" ", "") + ".setback-threshold", 10.0);
        if (plugin.getConfig().getBoolean("setbacks.enabled", true) && total >= setbackThreshold) {
            if (data != null && data.safeLocation() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(data.safeLocation());
                    }
                });
            }
        }

        checkPunishments(player, total);

        return total;
    }

    public double total(Player player) {
        Map<String, Double> map = violationsMap.get(player.getUniqueId());
        if (map == null || map.isEmpty()) return 0.0;
        return map.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public Map<String, Double> getViolations(Player player) {
        Map<String, Double> map = violationsMap.get(player.getUniqueId());
        if (map == null) return Map.of();
        return Map.copyOf(map);
    }

    public void clear(Player player) {
        violationsMap.remove(player.getUniqueId());
    }

    public void decay() {
        double decayRate = plugin.configManager().violationDecayRate();
        violationsMap.values().forEach(map -> {
            map.replaceAll((k, v) -> Math.max(0.0, v - decayRate));
            map.values().removeIf(v -> v <= 0.0);
        });
    }

    private void checkPunishments(Player player, double totalVl) {
        if (!plugin.getConfig().getBoolean("punishments.enabled", false)) {
            return;
        }

        List<?> actions = plugin.getConfig().getMapList("punishments.actions");
        for (Object obj : actions) {
            if (obj instanceof Map<?, ?> actionMap) {
                try {
                    Object thObj = actionMap.get("threshold");
                    Object cmdObj = actionMap.get("command");
                    if (thObj instanceof Number th && cmdObj instanceof String cmd) {
                        if (totalVl >= th.doubleValue()) {
                            String processedCmd = cmd.replace("%player%", player.getName());
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                            });
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
