package me.larping.anticheat.commands;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.checks.Check;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Administrative command handler for LarpingAntiCheat.
 *
 * <p>Check names are derived from the live {@link me.larping.anticheat.managers.CheckManager}
 * registry, so new checks appear automatically without hardcoded lists.
 */
public final class LacCommand implements CommandExecutor, TabCompleter {
    private boolean admin(CommandSender s) {
        if (s.isOp()) return true;
        return s.hasPermission("hyphon.admin") || s.hasPermission("lac.admin");
    }
    private boolean debugPerm(CommandSender s) {
        if (s.isOp()) return true;
        return s.hasPermission("hyphon.debug") || s.hasPermission("lac.debug");
    }


    private final LarpingAntiCheat plugin;

    public LacCommand(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    private List<String> checkNames() {
        List<String> names = new ArrayList<>();
        for (Check c : plugin.checkManager().all()) names.add(c.name().toLowerCase());
        return names;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!admin(sender)) {
            sender.sendMessage("§cYou do not have permission to use Hyphon.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§8[§cHyphon§8] §7Hyphon v" + plugin.getDescription().getVersion());
            sender.sendMessage("§7Usage: §f/lac <checks|enable|disable|reload|alerts|violations|clear|debug>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "checks" -> {
                sender.sendMessage("§8[§cHyphon§8] §eActive checks:");
                for (Check c : plugin.checkManager().all()) {
                    CheckConfig cc = plugin.configManager().get().check(c.name());
                    sender.sendMessage("§7 - §f" + c.name()
                            + (cc.enabled() ? " §aENABLED" : " §cDISABLED")
                            + " §7(sens §f" + String.format("%.2f", cc.sensitivity())
                            + "§7, alert VL §f" + cc.alertThreshold() + "§7)");
                }
            }
            case "enable", "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lac " + sub + " <check>");
                    return true;
                }
                String name = args[1].toLowerCase();
                if (plugin.checkManager().get(name) == null) {
                    sender.sendMessage("§cUnknown check: §f" + args[1] + " §7(use /lac checks).");
                    return true;
                }
                boolean enable = sub.equals("enable");
                plugin.getConfig().set("checks." + name + ".enabled", enable);
                plugin.saveConfig();
                plugin.configManager().reload();
                sender.sendMessage("§8[§cHyphon§8] Check §f" + name + " §7is now "
                        + (enable ? "§aenabled" : "§cdisabled") + "§7.");
            }
            case "reload" -> {
                plugin.configManager().reload();
                sender.sendMessage("§8[§cHyphon§8] §aConfiguration reloaded.");
            }
            case "alerts" -> {
                boolean now = !plugin.configManager().isAlertsEnabled();
                plugin.getConfig().set("alerts.enabled", now);
                plugin.saveConfig();
                plugin.configManager().reload();
                sender.sendMessage("§8[§cHyphon§8] Alerts are now "
                        + (now ? "§aENABLED" : "§cDISABLED") + ".");
            }
            case "violations", "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lac " + sub + " <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                Map<String, Double> breakdown = plugin.violations().getViolations(target);
                double total = plugin.violations().total(target);
                sender.sendMessage("§8[§cHyphon§8] §e" + target.getName()
                        + " §7total VL: §c" + String.format("%.1f", total));
                if (breakdown.isEmpty()) {
                    sender.sendMessage("§7No violations.");
                } else {
                    breakdown.entrySet().stream()
                            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                            .forEach(e -> sender.sendMessage("§7 - §f" + e.getKey()
                                    + "§7: §e" + String.format("%.1f", e.getValue())));
                }
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lac clear <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.violations().clear(target);
                plugin.data(target).resetBuffers();
                sender.sendMessage("§8[§cHyphon§8] §aCleared violations for §f" + target.getName() + "§a.");
            }
            case "debug" -> {
                if (!debugPerm(sender)) {
                    sender.sendMessage("§cYou do not have permission for debug mode.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /lac debug <player> <check|all|off>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                PlayerData data = plugin.data(target);
                String check = args[2].toLowerCase();
                if (check.equals("off")) {
                    data.setDebug("all", false);
                    sender.sendMessage("§8[§cHyphon§8] Debug disabled for §f" + target.getName() + "§7.");
                } else {
                    boolean off = data.isDebugging(check);
                    if (!check.equals("all")) {
                        data.setDebug(check, !off);
                    } else {
                        data.setDebug("all", !off);
                    }
                    sender.sendMessage("§8[§cHyphon§8] Debug §f" + check + " §7on §f" + target.getName()
                            + " §7is now " + (off ? "§cOFF" : "§aON") + ".");
                }
            }
            default -> sender.sendMessage("§cUnknown subcommand. Use §f/lac§c for help.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!admin(sender)) return List.of();

        if (args.length == 1) {
            return Stream.of("checks", "enable", "disable", "reload", "alerts",
                            "violations", "info", "clear", "debug")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("enable") || sub.equals("disable")) {
                return checkNames().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (List.of("violations", "info", "clear", "debug").contains(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            return Stream.concat(Stream.of("all", "off"), checkNames().stream())
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
