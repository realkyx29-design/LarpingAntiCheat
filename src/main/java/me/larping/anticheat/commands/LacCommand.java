package me.larping.anticheat.commands;

import me.larping.anticheat.LarpingAntiCheat;
import me.larping.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class LacCommand implements CommandExecutor, TabCompleter {
    private final LarpingAntiCheat plugin;

    public LacCommand(LarpingAntiCheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lac.admin")) {
            sender.sendMessage("§cYou do not have permission to use LarpingAntiCheat administrative commands.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§8[§cLAC§8] §7LarpingAntiCheat v" + plugin.getDescription().getVersion() + " §8- §aActive");
            sender.sendMessage("§7Usage: §f/lac <checks|enable|disable|reload|debug|alerts|info|violations|clear>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "checks": {
                sender.sendMessage("§8[§cLAC§8] §eRegistered Checks Status:");
                for (String check : List.of("speed", "fly", "reach", "autoclicker", "scaffold")) {
                    boolean enabled = plugin.configManager().isCheckEnabled(check);
                    double sensitivity = plugin.configManager().getSensitivity(check);
                    sender.sendMessage("§7- §f" + check + ": " + (enabled ? "§aENABLED" : "§cDISABLED") + " §7(Sensitivity: §f" + sensitivity + "§7)");
                }
                break;
            }
            case "enable":
            case "disable": {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lac " + sub + " <speed|fly|reach|autoclicker|scaffold>");
                    return true;
                }
                String checkName = args[1].toLowerCase();
                String path = "checks." + checkName + ".enabled";
                if (!plugin.getConfig().isSet(path)) {
                    sender.sendMessage("§cUnknown check: §f" + args[1]);
                    return true;
                }
                boolean enable = sub.equals("enable");
                plugin.getConfig().set(path, enable);
                plugin.saveConfig();
                sender.sendMessage("§8[§cLAC§8] Check §f" + checkName + " §7has been " + (enable ? "§aenabled" : "§cdisabled") + ".");
                break;
            }
            case "reload": {
                plugin.configManager().reload();
                sender.sendMessage("§8[§cLAC§8] §aConfiguration successfully reloaded.");
                break;
            }
            case "alerts": {
                boolean alerts = plugin.configManager().isAlertsEnabled();
                plugin.getConfig().set("alerts.enabled", !alerts);
                plugin.saveConfig();
                sender.sendMessage("§8[§cLAC§8] Alert broadcasts are now " + (!alerts ? "§aENABLED" : "§cDISABLED") + ".");
                break;
            }
            case "violations":
            case "info": {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /lac " + sub + " <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                double total = plugin.violations().total(target);
                Map<String, Double> breakdown = plugin.violations().getViolations(target);
                sender.sendMessage("§8[§cLAC§8] §ePlayer §f" + target.getName() + " §eTotal VL: §c" + String.format("%.2f", total));
                if (breakdown.isEmpty()) {
                    sender.sendMessage("§7No violations recorded.");
                } else {
                    breakdown.forEach((chk, vl) -> sender.sendMessage("§7- §f" + chk + ": §e" + String.format("%.2f", vl)));
                }
                break;
            }
            case "clear": {
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
                sender.sendMessage("§8[§cLAC§8] §aCleared all violations for §f" + target.getName() + ".");
                break;
            }
            case "debug": {
                if (!sender.hasPermission("lac.debug")) {
                    sender.sendMessage("§cYou do not have permission to use debug mode.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /lac debug <player> <check|all>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                String check = args[2].toLowerCase();
                PlayerData data = plugin.data(target);
                boolean debugging = !data.isDebugging(check);
                data.setDebug(check, debugging);
                sender.sendMessage("§8[§cLAC§8] Debug mode for §f" + check + " §7on §f" + target.getName() + " is now " + (debugging ? "§aENABLED" : "§cDISABLED") + ".");
                break;
            }
            default:
                sender.sendMessage("§cUnknown subcommand. Use /lac for help.");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lac.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("checks", "enable", "disable", "reload", "alerts", "violations", "info", "clear", "debug")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("enable") || sub.equals("disable")) {
                return Stream.of("speed", "fly", "reach", "autoclicker", "scaffold")
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (sub.equals("violations") || sub.equals("info") || sub.equals("clear") || sub.equals("debug")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            return Stream.of("speed", "fly", "reach", "autoclicker", "scaffold", "all")
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
