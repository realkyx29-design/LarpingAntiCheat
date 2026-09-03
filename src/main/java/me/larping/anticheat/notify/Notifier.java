package me.larping.anticheat.notify;

import me.larping.anticheat.LarpingAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Vulcan-style notification and logging layer.
 *
 * <p>Responsible for everything staff-facing:
 * <ul>
 *   <li>branded chat alerts with a hoverable box of debug details (type, VL,
 *       confidence, ping, TPS, offsets, location, time), sent to players with
 *       the alerts permission;</li>
 *   <li>an equivalent detailed console line on every flag (always, so server
 *       logs have a full record);</li>
 *   <li>setback / correction notices;</li>
 *   <li>the enable banner;</li>
 *   <li>a verbose debug feed to players tracking a check.</li>
 * </ul>
 *
 * <p>The chat alert uses the bundled BungeeCord chat-component API to attach a
 * hover tooltip. That API ({@code net.md_5.bungee.api.chat.*}) ships with both
 * Spigot and Paper; if it cannot be reflected onto (an unusual environment),
 * the notifier transparently falls back to a plain coloured message, so it
 * never throws on a production server.
 */
public final class Notifier {

    public static final String PREFIX = "§8[§c§lHyphon§8]§r ";

    private final LarpingAntiCheat plugin;
    private final boolean hoverAvailable;

    public Notifier(LarpingAntiCheat plugin) {
        this.plugin = plugin;
        this.hoverAvailable = detectHoverApi();
    }

    // ---------------------------------------------------------------
    // Flag alert (chat + console)
    // ---------------------------------------------------------------

    /**
     * Sends a staff alert and writes a console record.
     *
     * @param setback whether a movement correction was triggered for this flag
     */
    public void alert(Player player, String check, String type, String category,
                      double vl, double confidence, String detail, boolean setback) {
        try {
            doAlert(player, check, type, category, vl, confidence, detail, setback);
        } catch (Throwable t) {
            plugin.getLogger().warning("Alert render failed for " + check + ": " + t);
        }
    }

    /**
     * Always writes a console skid line for a violation, in the format:
     * [Hyphon] detected a skid: <player> : <what> : <count> time(s)
     * plus a detail line. This runs even below the chat-alert threshold so
     * logs are never silent. Rate-limited per (player, check) to avoid spam.
     */
    public void logFlag(Player player, String check, String category,
                         double vl, double confidence, String detail) {
        try {
            String key = player.getUniqueId() + ":" + check;
            long now = System.currentTimeMillis();
            long last = logThrottle.getOrDefault(key, 0L);
            if (now - last < 1000L) return; // max one skid line per check per second
            logThrottle.put(key, now);

            String what = describe(check, category);
            int count = (int) Math.max(1, Math.round(vl));
            plugin.getLogger().info(String.format(
                    "[Hyphon] detected a skid: %s : %s : %d time(s)",
                    player.getName(), what, count));
            Location l = player.getLocation();
            String coords = l != null
                    ? String.format("%.0f,%.0f,%.0f", l.getX(), l.getY(), l.getZ()) : "?";
            plugin.getLogger().info(String.format(
                    "    detail: %s conf=%d%% ping=%dms tps=%.1f @%s | %s",
                    check, (int) (confidence * 100), player.getPing(), plugin.tps(),
                    coords, detail));
            // Mirror to Discord (non-blocking, no-op if no webhook configured).
            try {
                plugin.discordLog("🚨 **[Hyphon] detected a skid**\n"
                        + "**Player:** " + player.getName()
                        + "\n**Detected:** " + what
                        + "\n**Check:** " + check
                        + "\n**Count:** " + count
                        + "\n**Location:** " + coords
                        + "\n**Ping:** " + player.getPing() + "ms | **TPS:** "
                        + String.format("%.1f", plugin.tps()));
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
    }
    private final java.util.Map<String, Long> logThrottle = new java.util.concurrent.ConcurrentHashMap<>();

    private void doAlert(Player player, String check, String type, String category,
                         double vl, double confidence, String detail, boolean setback) {
        String name = player.getName();
        double tps = plugin.tps();
        int ping = player.getPing();
        Location l = player.getLocation();
        String coords = String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ());
        String world = l.getWorld() != null ? l.getWorld().getName() : "?";
        // Human-readable description of what was detected.
        String what = describe(check, category);

        String hover = String.join("\n",
                "§c§lHyphon §7v" + plugin.getDescription().getVersion(),
                "",
                "§7Player: §f" + name + " §8(" + world + ")",
                "§7Check: §f" + check + " §8(" + type + ")",
                "§7Category: §f" + category,
                "§7Violation: §c" + String.format("%.1f", vl),
                "§7Confidence: §f" + (int) (confidence * 100) + "%",
                "§7Ping: §f" + ping + "ms",
                "§7TPS: §f" + String.format("%.1f", tps),
                "§7Location: §f" + coords,
                "§7Action: §f" + (setback ? "Setback" : "Log"),
                "",
                "§7Info:",
                "§c" + detail);

        // Chat alert (rate limiting is done by the caller).
        String base = PREFIX + "§f" + name + " §7failed §f" + check
                + " §7§o(" + type + ")§r §7VL §c" + String.format("%.1f", vl)
                + " §7• §f" + (int) (confidence * 100) + "%";
        if (setback) base += " §8[§e↶§8]";

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("lac.alerts")) continue;
            sendHover(staff, base, hover);
        }

        // Full console record in the requested format:
        // [Hyphon] detected a skid: <player> : <what was detected> : <count>
        plugin.getLogger().info(String.format(
                "[Hyphon] detected a skid: %s : %s : %d time(s)",
                name, what, (int) Math.max(1, Math.round(vl))));
        // Detailed technical line for staff who want offsets/ping.
        plugin.getLogger().info(String.format(
                "    detail %s (%s) VL=%.1f conf=%d%% ping=%dms tps=%.1f @%s %s | %s",
                name, check, vl, (int) (confidence * 100), ping, tps,
                coords, world, detail));
    }

    /** Human-readable detection description for the console log. */
    private String describe(String check, String category) {
        return switch (check.toLowerCase()) {
            case "speed" -> "moving faster than their gear/effects allow";
            case "fly" -> "flying without permission";
            case "boatfly" -> "boat-flying (boat held aloft)";
            case "timer" -> "game-timer manipulation";
            case "phase" -> "phasing through solid blocks";
            case "groundspoof" -> "spoofing ground state (NoFall)";
            case "jesus" -> "walking on liquid";
            case "spider" -> "climbing walls like a spider";
            case "step" -> "step-high/auto-step exploit";
            case "blink" -> "blink/teleport-ahead exploit";
            case "noknockback" -> "cancelling knockback";
            case "noslow" -> "ignoring item-use slowdown";
            case "reach" -> "attacking beyond reach";
            case "killaura" -> "aimbot/kill-aura combat";
            case "weapondamage" -> "dealing more damage than their weapon can";
            case "scaffold" -> "placing blocks too fast/out of reach";
            case "fastbreak" -> "breaking blocks faster than their tool allows";
            case "nuker" -> "nuker-style mass/out-of-reach block breaking";
            case "autototem" -> "automated totem swapping";
            case "autoweb" -> "automated/impossible cobweb placement";
            case "autocrystal" -> "automated crystal placement/breaking";
            default -> check + " (" + category + ")";
        };
    }

    /** Movement correction / setback notice (debug + console). */
    public void setback(Player player, String check) {
        String msg = PREFIX + "§7Setback §f" + player.getName() + " §7(§f" + check + "§7)";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("lac.alerts")) {
                // Only admins with verbose on get every setback; keep chat quiet.
                if (staff.hasPermission("lac.verbose")) staff.sendMessage(msg);
            }
        }
        plugin.getLogger().fine("[SETBACK] " + player.getName() + " " + check);
    }

    /** Verbose flag feed for staff tracking a player/check. */
    public void debug(Player target, String check, double vl, String detail) {
        String msg = "§8[§cHyphon debug§8] §7" + check + " §f" + target.getName()
                + " §7VL=" + String.format("%.1f", vl) + " §8| §7" + detail;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("lac.verbose") && !staff.equals(target)) {
                staff.sendMessage(msg);
            }
        }
        target.sendMessage(msg);
    }

    /** Plain branded system message to a single sender. */
    public void info(CommandSender to, String msg) {
        to.sendMessage(PREFIX + msg);
    }

    // ---------------------------------------------------------------
    // Startup banner
    // ---------------------------------------------------------------

    public void banner(int checkCount) {
        try {
            String v = plugin.getDescription() != null ? plugin.getDescription().getVersion() : "?";
            org.bukkit.command.CommandSender console = Bukkit.getConsoleSender();
            if (console == null) return;
            String[] lines = {
                    "§8§m--------------------------------------------",
                    PREFIX + "§cHyphon §7v" + v,
                    "§7Server-authoritative anti-cheat for Paper 1.21+",
                    "§7" + checkCount + " checks active §8• §7enforcement §aenabled",
                    "§7Bypass §flac.bypass §8• §7alerts §flac.alerts §8• §7verbose §flac.verbose",
                    "§8§m--------------------------------------------"
            };
            for (String line : lines) console.sendMessage(line);
        } catch (Throwable ignored) {
            // Never let the banner break enable.
        }
    }

    // ---------------------------------------------------------------
    // Rich chat via BungeeCord components (with safe fallback)
    // ---------------------------------------------------------------

    private boolean detectHoverApi() {
        try {
            Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class.forName("net.md_5.bungee.api.chat.HoverEvent");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sendHover(Player player, String line, String hoverText) {
        if (!hoverAvailable) {
            player.sendMessage(line);
            return;
        }
        try {
            // Build via reflection so we compile/run without a BungeeCord
            // dependency on the build classpath; the classes exist at runtime.
            Class<?> textComp = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class<?> baseComp = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Object component = textComp.getDeclaredConstructor().newInstance();

            // textComponent.setText(String)
            textComp.getMethod("setText", String.class).invoke(component, line);

            // HoverEvent(Action, Component[]) legacy constructor (Spigot/Paper).
            Class<?> hoverEvent = Class.forName("net.md_5.bungee.api.chat.HoverEvent");
            Class<?> actionEnum = Class.forName("net.md_5.bungee.api.chat.HoverEvent$Action");
            Object showText = Enum.valueOf((Class<Enum>) actionEnum, "SHOW_TEXT");

            Object hoverComp = textComp.getDeclaredConstructor().newInstance();
            textComp.getMethod("setText", String.class).invoke(hoverComp, hoverText);
            Object[] compArr = (Object[]) java.lang.reflect.Array.newInstance(baseComp, 1);
            compArr[0] = hoverComp;

            Constructor<?> heCtor = hoverEvent.getConstructor(actionEnum, compArr.getClass());
            Object hover = heCtor.newInstance(showText, (Object) compArr);
            textComp.getMethod("setHoverEvent", hoverEvent).invoke(component, hover);

            Object[] outer = (Object[]) java.lang.reflect.Array.newInstance(baseComp, 1);
            outer[0] = component;
            // Player#spigot().sendMessage(BaseComponent...)
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            Method send = null;
            for (Method m : spigot.getClass().getMethods()) {
                if (m.getName().equals("sendMessage")
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isArray()
                        && baseComp.isAssignableFrom(m.getParameterTypes()[0].getComponentType())) {
                    send = m;
                    break;
                }
            }
            if (send != null) {
                send.invoke(spigot, (Object) outer);
            } else {
                player.sendMessage(line);
            }
        } catch (Throwable t) {
            player.sendMessage(line); // graceful fallback
        }
    }
}
