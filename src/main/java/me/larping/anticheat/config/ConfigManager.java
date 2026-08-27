package me.larping.anticheat.config;

import me.larping.anticheat.LarpingAntiCheat;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager {
    private final LarpingAntiCheat plugin;

    public ConfigManager(LarpingAntiCheat plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public boolean isAlertsEnabled() {
        return config().getBoolean("alerts.enabled", true);
    }

    public String alertFormat() {
        return config().getString("alerts.format", "[LAC] Player §f%player% §7failed §f%check% §7(VL: §c%vl%§7, Ping: §e%ping%ms§7, TPS: §e%tps%§7)");
    }

    public boolean isLoggingEnabled() {
        return config().getBoolean("logging.enabled", false);
    }

    public String exemptPermission() {
        return config().getString("exempt-permission", "lac.bypass");
    }

    public double violationDecayRate() {
        return config().getDouble("violation-decay-per-second", 0.15);
    }

    public boolean isCheckEnabled(String check) {
        return config().getBoolean("checks." + check.toLowerCase() + ".enabled", true);
    }

    public double getSensitivity(String check) {
        return config().getDouble("checks." + check.toLowerCase() + ".sensitivity", 0.5);
    }
}
