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

    /** Strictness of an enforcement decision. */
    public enum Action {
        /** Only log / alert / accrue VL. */
        NONE,
        /** Cancel the originating event (attack / block place / block break). */
        CANCEL_EVENT,
        /** Correct movement by snapping the player back (setTo). */
        MOVEMENT_SETBACK
    }

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

        // Exempt players never accrue VL: live OP whitelist and bypass nodes.
        if (isExempt(player, cfg)) {
            return 0.0;
        }

        CheckConfig cc = cfg.check(checkName);

        Map<String, Double> playerVl =
                violationsMap.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        // Sensitivity mildly scales weight; never lets VL be entirely dodged.
        double weight = amount * (0.5 + cc.sensitivity() * 0.5);
        double total = playerVl.merge(checkName, weight, (a, b) -> Math.min(100.0, a + b));

        PlayerData data = plugin.data(player);

        // --- Verbose debug feed (players tracking this check) -----------
        if (data.isDebugging(checkName)) {
            plugin.notifier().debug(player, checkName, total, detail);
        }

        // --- Movement correction decision ------------------------------
        // Only the clearly-impossible checks (fly/phase/blink) request a
        // correction, and only when setbacks are enabled and the signal is
        // very strong. The listener additionally refuses to apply it while
        // the player is laggy or in a legitimate state (elytra/velocity/
        // liquid/vehicle), so normal walking, elytra flight or lag never
        // cause a rubber-band.
        boolean correcting = setback == Setback.MOVEMENT && cfg.setbacksEnabled()
                && total >= cc.setbackThreshold() && confidence >= 0.9;
        if (correcting) {
            data.markMovementCorrection();
        }

        // --- Console log: ALWAYS record the violation in the skid format
        // so logs always work even below the chat-alert threshold.
        try {
            plugin.notifier().logFlag(player, checkName, categoryFor(checkName),
                    total, confidence, detail);
        } catch (Throwable ignored) { }

        // --- Staff chat alert (rate-limited, threshold-gated) -----------
        if (cfg.alertsEnabled() && total >= cc.alertThreshold() && confidence >= 0.6) {
            String key = player.getUniqueId() + ":" + checkName;
            long now = System.currentTimeMillis();
            Long last = lastAlertMs.get(key);
            if (last == null || now - last >= cfg.alertCooldownMs()) {
                lastAlertMs.put(key, now);
                plugin.notifier().alert(player, checkName, checkType, categoryFor(checkName),
                        total, confidence, detail, correcting);
            }
        }

        // --- Punishments --------------------------------------------------
        checkPunishments(player, total);

        return total;
    }

    /** Short category label used in alerts/hover. */
    private String categoryFor(String check) {
        String c = check.toLowerCase();
        if (c.equals("reach") || c.equals("killaura")) return "Combat";
        if (c.equals("scaffold") || c.equals("fastbreak") || c.equals("nuker")) return "World";
        return "Movement";
    }

    /**
     * Whether the player's current movement should be rejected (snapped back
     * to a known-legal position). True only when a movement correction was
     * recently requested AND the per-player cooldown allows it.
     */
    public boolean shouldCorrectMovement(Player player) {
        if (isExempt(player, plugin.configManager().get())) return false;
        if (!plugin.configManager().get().setbacksEnabled()) return false;
        PlayerData data = plugin.data(player);
        return data.consumeMovementCorrection(plugin.configManager().get().setbackCooldownMs());
    }

    /**
     * Whether an action (attack / place / break) should be cancelled. True
     * only for sustained, high-violation checks in the relevant category —
     * this is the server-authoritative enforcement that actually stops a
     * cheated action instead of merely reporting it. Gated well above alert
     * thresholds so a single borderline packet never cancels legit play.
     */
    public boolean shouldCancelEvent(Player player, String... checks) {
        for (String c : checks) {
            double vl = checkVl(player, c);
            double cancel = plugin.configManager().get().check(c).setbackThreshold();
            // Any single category check sustained past its cancel level.
            if (vl >= cancel) {
                return true;
            }
        }
        return false;
    }

    /** Current VL for a single check (0 if none). Used by enforcement gates. */
    public double checkVl(Player player, String check) {
        if (isExempt(player, plugin.configManager().get())) return 0.0;
        Map<String, Double> map = violationsMap.get(player.getUniqueId());
        if (map == null) return 0.0;
        return map.getOrDefault(check, 0.0);
    }

    /**
     * Dynamic exemption: live server-operator state plus the bypass permission
     * nodes. OPs never get VL, setbacks, alerts, or automatic punishments.
     */
    private boolean isExempt(Player player, ConfigManager.Snapshot cfg) {
        try {
            if (player.isOp()) return true;
        } catch (Throwable ignored) { }
        try {
            String node = cfg.exemptPermission();
            if (node != null && player.hasPermission(node)) return true;
            if (player.hasPermission("hyphon.bypass") || player.hasPermission("lac.bypass")) return true;
        } catch (Throwable ignored) { }
        return false;
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

    /**
     * Whether an attack should be cancelled. Combines the hard-impossible
     * signals (handled directly in the listener) with a low, evidence-based
     * combat threshold: a few repeated reach/aim violations in a short time
     * is enough to start denying hits, so a smooth aimbot that stays inside
     * the instant limits cannot farm free damage. Requires several confirmations
     * so lag or a single borderline hit never cancels.
     */
    public boolean shouldCancelAttack(Player player) {
        if (isExempt(player, plugin.configManager().get())) return false;
        if (!plugin.configManager().get().enforceCancelAttacks()) return false;
        double reach = checkVl(player, "Reach");
        double aura = checkVl(player, "KillAura");
        double weapon = checkVl(player, "WeaponDamage");
        double automation = checkVl(player, "CombatAutomation");
        // Sustained violations: cancel once any combat check has a handful.
        return reach >= 4.0 || aura >= 4.0 || weapon >= 5.0 || automation >= 5.0;
    }

}
