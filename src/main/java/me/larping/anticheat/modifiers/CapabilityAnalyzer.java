package me.larping.anticheat.modifiers;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Capabilities} snapshot for a player by reading only real
 * server state: game mode, potion effects, the live movement-speed attribute,
 * worn armour, the held item's attribute modifiers, vanilla enchantments,
 * registered {@link CustomModifierProvider}s, and the built-in custom-enchant
 * scanner.
 *
 * <p>This is the bridge between "what the player is wearing/holding/affected
 * by" and "what the checks should consider possible". Every movement/combat/
 * mining check goes through this so that legitimate modifiers are always
 * accounted for before flagging.
 */
public final class CapabilityAnalyzer {

    private final GenericCustomEnchantScanner scanner = new GenericCustomEnchantScanner();

    public Capabilities analyze(Player player, CustomModifierProvider.Context context) {
        Capabilities c = new Capabilities();

        // ---- Game mode / flight ----
        GameMode gm = player.getGameMode();
        c.creative = gm == GameMode.CREATIVE;
        c.spectator = gm == GameMode.SPECTATOR;
        c.adventure = gm == GameMode.ADVENTURE;
        c.inVehicle = player.isInsideVehicle();
        c.gliding = player.isGliding();
        try {
            c.allowedFlight = player.getAllowFlight();
            c.flying = player.isFlying();
        } catch (Throwable ignored) {
            c.allowedFlight = c.creative || c.spectator;
            c.flying = c.creative || c.spectator;
        }

        ItemStack mainHand = safeItem(player.getInventory() != null
                ? player.getInventory().getItemInMainHand() : null);
        List<ItemStack> equipment = collectEquipment(player);

        // ---- Potion effects ----
        c.speedAmplifier = effectAmplifier(player, PotionEffectType.SPEED);
        c.jumpAmplifier = effectAmplifier(player, PotionEffectType.JUMP_BOOST);
        c.hasteAmplifier = effectAmplifier(player, PotionEffectType.HASTE);
        c.conduitPowerAmplifier = effectAmplifier(player, PotionEffectType.CONDUIT_POWER);
        c.levitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        c.slowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        c.dolphinsGrace = player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE);

        // ---- Live movement speed attribute (items + effects already applied) ----
        double attrSpeed = resolveMovementSpeed(player);
        c.movementSpeed = attrSpeed > 0 ? attrSpeed : 0.1;
        // Custom speed multiplier relative to the vanilla 0.1 base, used to
        // widen limits for clearly-buffed players.
        if (attrSpeed > 0.13) {
            c.speedMultiplier = Math.max(c.speedMultiplier, attrSpeed / 0.1);
        }

        // ---- Mining: tool + haste/enchant ----
        if (mainHand != null) {
            applyMiningCapabilities(c, mainHand, player);
            applyWeaponCapabilities(c, mainHand, player);
        }

        // ---- Generic custom-enchant scan over all equipment ----
        GenericCustomEnchantScanner.Scan scan = scanner.scan(equipment);
        c.speedMultiplier = Math.max(c.speedMultiplier, scan.speedMul);
        c.jumpMultiplier = Math.max(c.jumpMultiplier, scan.jumpMul);
        c.miningSpeedMultiplier = Math.max(c.miningSpeedMultiplier, scan.miningMul);
        c.customDamageBonus += scan.damageBonus;
        c.areaMineRadius = Math.max(c.areaMineRadius, scan.areaRadius);

        // ---- Jump Boost effect -> jump multiplier ----
        if (c.jumpAmplifier >= 0) {
            c.jumpMultiplier = Math.max(c.jumpMultiplier, 1.0 + 0.5 * (c.jumpAmplifier + 1));
        }
        if (c.areaMineRadius > 0) c.areaMining = true;

        // ---- Registered custom-modifier providers (your enchant plugin) ----
        for (CustomModifierProvider provider : CapabilityRegistry.providers()) {
            try {
                provider.apply(player, mainHand, context, c);
            } catch (Throwable ignored) {
                // A misbehaving provider must never break checks.
            }
        }
        if (c.areaMineRadius > 0) c.areaMining = true;

        c.customSummary = scan.notes.length() > 0 ? scan.notes.toString() : "";
        return c;
    }

    // ------------------------------------------------------------------
    // Movement speed attribute (version-safe, like MovementSnapshot)
    // ------------------------------------------------------------------
    private static Attribute speedAttribute;
    private static boolean speedResolved;

    private static Attribute resolveSpeedAttrConstant() {
        if (speedResolved) return speedAttribute;
        speedResolved = true;
        try {
            for (Attribute attr : Attribute.values()) {
                String n = attr.name();
                if (n.equals("MOVEMENT_SPEED") || n.equals("GENERIC_MOVEMENT_SPEED")) {
                    speedAttribute = attr;
                    break;
                }
            }
        } catch (Throwable ignored) { speedAttribute = null; }
        return speedAttribute;
    }

    private double resolveMovementSpeed(Player player) {
        try {
            Attribute attr = resolveSpeedAttrConstant();
            if (attr != null) {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) return inst.getValue();
            }
        } catch (Throwable ignored) { }
        return 0.1;
    }

    // ------------------------------------------------------------------
    // Mining capabilities
    // ------------------------------------------------------------------
    private void applyMiningCapabilities(Capabilities c, ItemStack tool, Player player) {
        String n = tool.getType().name();
        boolean isPickaxe = n.endsWith("_PICKAXE");
        boolean isAxe = n.endsWith("_AXE");
        boolean isShovel = n.endsWith("_SHOVEL") || n.endsWith("_SPADE");
        boolean isHoe = n.endsWith("_HOE");
        boolean isShears = n.equals("SHEARS");
        c.miningTool = isPickaxe || isAxe || isShovel || isHoe || isShears;

        // Vanilla tool tier baseline mining speed.
        double tier;
        if (n.contains("NETHERITE")) tier = 9.0;
        else if (n.contains("DIAMOND")) tier = 8.0;
        else if (n.contains("IRON")) tier = 6.0;
        else if (n.contains("STONE")) tier = 4.0;
        else if (n.contains("GOLDEN") || n.contains("GOLD_")) tier = 12.0;
        else if (n.contains("WOODEN") || n.contains("WOOD_")) tier = 2.0;
        else tier = 2.0;

        // Efficiency enchantment: +speed per level (vanilla formula).
        int efficiency = enchantLevel(tool, "EFFICIENCY");
        double effBonus = efficiency > 0 ? (efficiency * efficiency + 1) : 0;
        double toolSpeed = c.miningTool ? (tier + effBonus) : 1.0;

        // Haste / Conduit Power: +20% mining speed per level.
        double haste = 1.0 + 0.2 * Math.max(c.hasteAmplifier + 1, 0)
                + (c.conduitPowerAmplifier >= 0 ? 0.2 * (c.conduitPowerAmplifier + 1) : 0);
        if (c.hasteAmplifier < 0 && c.conduitPowerAmplifier < 0) haste = 1.0;

        c.miningSpeedMultiplier = Math.max(c.miningSpeedMultiplier, toolSpeed * haste);
    }

    // ------------------------------------------------------------------
    // Weapon capabilities — maximum damage from the actual sword/axe
    // ------------------------------------------------------------------
    private void applyWeaponCapabilities(Capabilities c, ItemStack weapon, Player player) {
        String n = weapon.getType().name();
        boolean isSword = n.endsWith("_SWORD");
        boolean isAxe = n.endsWith("_AXE");
        if (!isSword && !isAxe) return;

        double base;
        if (n.contains("NETHERITE")) base = isAxe ? 10.0 : 8.0;
        else if (n.contains("DIAMOND")) base = isAxe ? 9.0 : 7.0;
        else if (n.contains("IRON")) base = isAxe ? 9.0 : 6.0;
        else if (n.contains("STONE")) base = isAxe ? 9.0 : 5.0;
        else if (n.contains("GOLDEN") || n.contains("GOLD_")) base = isAxe ? 7.0 : 4.0;
        else base = isAxe ? 7.0 : 4.0; // wooden

        // Attribute modifiers on the item (custom swords use ATTACK_DAMAGE).
        double attrDamage = readAttackDamageAttribute(weapon);
        if (attrDamage > base) base = attrDamage;

        // Vanilla Sharpness: ~0.5 + 0.5*level extra (post-1.9: 0.5*level+0.5).
        int sharpness = enchantLevel(weapon, "SHARPNESS");
        double sharpBonus = sharpness > 0 ? (0.5 + sharpness * 0.5) : 0;

        // Strength potion: +130% at level I, +260% level II (melee).
        int str = effectAmplifier(player, PotionEffectType.STRENGTH);
        double strengthMul = str >= 0 ? (1.0 + 0.3 * (str + 1) + 1.0) : 1.0;

        double raw = (base + sharpBonus) * strengthMul + c.customDamageBonus;
        // Critical hits and generous headroom for rounding/custom math.
        double max = raw * 1.6 + 3.0;
        c.maxWeaponDamage = Math.max(c.maxWeaponDamage, max);
    }

    private double readAttackDamageAttribute(ItemStack item) {
        try {
            if (!item.hasItemMeta()) return 0;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return 0;
            // ItemMeta#getAttributeModifiers returns vanilla attack modifiers.
            if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
                for (var entry : meta.getAttributeModifiers().entries()) {
                    org.bukkit.attribute.Attribute attr = entry.getKey();
                    String attrName = attr != null ? attr.name() : "";
                    if (attrName.contains("ATTACK_DAMAGE") || attrName.equals("ATTACKDAMAGE")) {
                        double amount = entry.getValue().getAmount();
                        // The item's ATTACK_DAMAGE modifier value is (finalDamage - 1) for
                        // the held slot in vanilla, so add 1 to recover the damage.
                        return amount + 1.0;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private List<ItemStack> collectEquipment(Player player) {
        List<ItemStack> list = new ArrayList<>();
        try {
            var inv = player.getInventory();
            if (inv != null) {
                list.add(safeItem(inv.getItemInMainHand()));
                list.add(safeItem(inv.getItemInOffHand()));
                list.add(safeItem(inv.getHelmet()));
                list.add(safeItem(inv.getChestplate()));
                list.add(safeItem(inv.getLeggings()));
                list.add(safeItem(inv.getBoots()));
            }
        } catch (Throwable ignored) { }
        return list;
    }

    private static ItemStack safeItem(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? null : item;
    }

    private static int effectAmplifier(Player player, PotionEffectType type) {
        try {
            PotionEffect fx = player.getPotionEffect(type);
            return fx != null ? fx.getAmplifier() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Reads a vanilla enchantment level by name, e.g. "EFFICIENCY" or "SHARPNESS". */
    public static int enchantLevel(ItemStack item, String enchantName) {
        try {
            if (item == null || !item.hasItemMeta()) return 0;
            var meta = item.getItemMeta();
            if (meta == null || !meta.hasEnchants()) return 0;
            for (var entry : meta.getEnchants().entrySet()) {
                String key;
                try {
                    NamespacedKey nk = entry.getKey().getKey();
                    key = nk != null ? nk.getKey().toUpperCase() : entry.getKey().getName().toUpperCase();
                } catch (Throwable t) {
                    key = entry.getKey().getName().toUpperCase();
                }
                if (key.contains(enchantName.toUpperCase())) return entry.getValue();
            }
        } catch (Throwable ignored) { }
        return 0;
    }
}
