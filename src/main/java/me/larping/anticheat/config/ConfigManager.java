package me.larping.anticheat.config;

import me.larping.anticheat.LarpingAntiCheat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads and caches the plugin configuration.
 *
 * <p>Performance: a single immutable {@link Snapshot} is built on enable and
 * on {@code /lac reload}; all check hot-paths read primitive fields from the
 * snapshot instead of performing YAML ({@code Map}) lookups, path walking,
 * string building and boxing for every player move event.
 */
public final class ConfigManager {

    /** Immutable parsed configuration. Replaced atomically on reload. */
    public record Snapshot(
            Map<String, CheckConfig> checks,
            boolean alertsEnabled,
            String alertFormat,
            boolean loggingEnabled,
            boolean logLocation,
            String exemptPermission,
            double violationDecayPerSecond,
            boolean compensatePing,
            boolean compensateTps,
            int lagCompensationPing,
            double lagCompensationTps,
            long teleportGraceMs,
            long joinGraceMs,
            long respawnGraceMs,
            long worldChangeGraceMs,
            boolean setbacksEnabled,
            long setbackCooldownMs,
            boolean enforceCancelAttacks,
            boolean enforceCancelBlocks,
            boolean enforceCancelBreaks,
            boolean enforceCorrectMovement,
            boolean enforceFlight,
            boolean enforceVelocity,
            boolean punishmentsEnabled,
            boolean customModsEnabled,
            boolean customMovementComp,
            boolean customVelocityComp,
            boolean customCombatComp,
            boolean customPlacementComp,
            double alertCooldownMs
    ) {
        public CheckConfig check(String name) {
            CheckConfig c = checks.get(name.toLowerCase());
            return c != null ? c : CheckDefaults.get(name.toLowerCase());
        }

        public boolean checkEnabled(String name) {
            CheckConfig c = checks.get(name.toLowerCase());
            return c == null || c.enabled();
        }
    }

    private final LarpingAntiCheat plugin;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public ConfigManager(LarpingAntiCheat plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        snapshot.set(build(plugin.getConfig()));
    }

    public Snapshot get() {
        return snapshot.get();
    }

    // --- convenience accessors used by commands/manager ---
    public boolean isAlertsEnabled() { return get().alertsEnabled(); }
    public String alertFormat() { return get().alertFormat(); }
    public boolean isLoggingEnabled() { return get().loggingEnabled(); }
    public String exemptPermission() { return get().exemptPermission(); }
    public double violationDecayRate() { return get().violationDecayPerSecond(); }
    public boolean isCheckEnabled(String check) { return get().checkEnabled(check); }
    public double getSensitivity(String check) { return get().check(check).sensitivity(); }

    private static Snapshot build(FileConfiguration cfg) {
        Map<String, CheckConfig> checks = new HashMap<>();

        for (Map.Entry<String, CheckDefaults.Def> e : CheckDefaults.all().entrySet()) {
            String key = e.getKey();
            CheckConfig d = e.getValue().config();
            ConfigurationSection sec = cfg.getConfigurationSection("checks." + key);

            double[] slots = { d.v1(), d.v2(), d.v3() };
            // Pull check-specific tuning keys by their documented names.
            // The tuning map is insertion-ordered (LinkedHashMap); insertion
            // order defines the v1/v2/v3 slots — do NOT sort here.
            if (sec != null) {
                int slot = 0;
                for (Map.Entry<String, Double> t : e.getValue().tuning().entrySet()) {
                    slots[slot] = sec.getDouble(t.getKey(), t.getValue());
                    slot++;
                }
            }

            checks.put(key, new CheckConfig(
                    key,
                    cfg.getBoolean("checks." + key + ".enabled", d.enabled()),
                    cfg.getDouble("checks." + key + ".sensitivity", d.sensitivity()),
                    cfg.getDouble("checks." + key + ".alert-threshold", d.alertThreshold()),
                    cfg.getDouble("checks." + key + ".setback-threshold", d.setbackThreshold()),
                    cfg.getDouble("checks." + key + ".punishment-threshold", d.punishmentThreshold()),
                    slots[0], slots[1], slots[2]
            ));
        }

        return new Snapshot(
                checks,
                cfg.getBoolean("alerts.enabled", true),
                cfg.getString("alerts.format",
                        "[LAC] §f%player% §7failed §f%check% §7(VL §c%vl%§7, ping §e%ping%ms§7, tps §e%tps%§7)"),
                cfg.getBoolean("logging.enabled", false),
                cfg.getBoolean("logging.include-location", true),
                cfg.getString("exempt-permission", "lac.bypass"),
                cfg.getDouble("violation-decay-per-second", 0.25),
                cfg.getBoolean("compensation.ping", true),
                cfg.getBoolean("compensation.tps", true),
                cfg.getInt("compensation.lag-ping", 120),
                cfg.getDouble("compensation.lag-tps", 19.0),
                cfg.getLong("grace-periods.teleport", 1000),
                cfg.getLong("grace-periods.join", 2000),
                cfg.getLong("grace-periods.respawn", 2000),
                cfg.getLong("grace-periods.world-change", 1500),
                cfg.getBoolean("setbacks.enabled", false),
                cfg.getLong("setbacks.cooldown-ms", 1200),
                cfg.getBoolean("enforcement.cancel-attacks", true),
                cfg.getBoolean("enforcement.cancel-blocks", false),
                cfg.getBoolean("enforcement.cancel-breaks", false),
                cfg.getBoolean("enforcement.correct-movement", false),
                cfg.getBoolean("enforcement.enforce-flight", true),
                cfg.getBoolean("enforcement.enforce-velocity", false),
                cfg.getBoolean("punishments.enabled", false),
                cfg.getBoolean("compatibility.custom-mods.enabled", true),
                cfg.getBoolean("compatibility.custom-mods.movement-compensation", true),
                cfg.getBoolean("compatibility.custom-mods.velocity-compensation", true),
                cfg.getBoolean("compatibility.custom-mods.combat-compensation", true),
                cfg.getBoolean("compatibility.custom-mods.block-placement-compensation", true),
                cfg.getDouble("alerts.cooldown-ms", 1500)
        );
    }
}
