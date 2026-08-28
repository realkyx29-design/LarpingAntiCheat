package me.larping.anticheat.modifiers;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Zero-config recogniser for common custom-enchantment abilities.
 *
 * <p>Custom enchant plugins typically encode an enchant's effect in its
 * display name and/or lore (e.g. an item named {@code "Pickaxe of Vein Mining"}
 * with lore {@code "Breaks a 3x3 area"}). This scanner inspects the held item
 * and armour display text for well-known ability keywords and reports the
 * recognised capability. It is intentionally conservative — it only widens the
 * limit when an item clearly advertises a matching ability — and it is fully
 * backed up by explicit config values and the provider API.
 *
 * <p>Everything is defensive: if the item has no readable meta, it returns
 * nothing and vanilla limits apply.
 */
public final class GenericCustomEnchantScanner {

    public static final class Scan {
        public double speedMul = 1.0;
        public double jumpMul = 1.0;
        public double miningMul = 1.0;
        public double damageBonus = 0.0;
        public int areaRadius = 0;
        public StringBuilder notes = new StringBuilder();

        void note(String s) {
            if (notes.length() > 0) notes.append(", ");
            notes.append(s);
        }
    }

    public Scan scan(List<ItemStack> items) {
        Scan out = new Scan();
        if (items == null) return out;
        for (ItemStack item : items) {
            if (item == null) continue;
            Material type = item.getType();
            String text = readableText(item).toLowerCase();
            if (text.isEmpty()) continue;

            boolean isArmor = type.name().endsWith("_BOOTS") || type.name().endsWith("_LEGGINGS")
                    || type.name().endsWith("_CHESTPLATE") || type.name().endsWith("_HELMET");
            boolean isWeapon = type.name().endsWith("_SWORD") || type.name().endsWith("_AXE");
            boolean isPickaxe = type.name().endsWith("_PICKAXE");

            // ---- Movement: speed / jump ----
            if (text.contains("speed") || text.contains("swiftness") || text.contains("haste boots")
                    || text.contains("fast boots") || text.contains("sprint")) {
                if (isArmor || text.contains("speed")) {
                    out.speedMul = Math.max(out.speedMul, 2.0);
                    out.note("speed");
                }
            }
            if (text.contains("jump") || text.contains("leap") || text.contains("spring")) {
                out.jumpMul = Math.max(out.jumpMul, 2.0);
                out.note("jump");
            }

            // ---- Mining: speed / area ----
            if (isPickaxe) {
                if (text.contains("haste") || text.contains("fast mine") || text.contains("rapid")
                        || text.contains("swift") || text.contains("speed")) {
                    out.miningMul = Math.max(out.miningMul, 6.0);
                    out.note("fast-mine");
                }
                int r = areaRadiusFromText(text);
                if (r > 0) {
                    out.areaRadius = Math.max(out.areaRadius, r);
                    out.note("area-" + (r == 1 ? "3x3" : r == 2 ? "5x5" : (2 * r + 1) + "x" + (2 * r + 1)));
                }
            }

            // ---- Combat: damage ----
            if (isWeapon) {
                if (text.contains("sharpness") || text.contains("power") || text.contains("damage")
                        || text.contains("strength") || text.contains("slayer")) {
                    out.damageBonus += 6.0;
                    out.note("damage");
                }
            }
        }
        return out;
    }

    /** Detects 3x3 / 4x4 (and similar) area-mining mentions in item text. */
    private int areaRadiusFromText(String text) {
        // "3x3" / "3 x 3" -> radius 1 ; "5x5" -> radius 2 ; "4x4" treated as radius 2
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d)\\s*[x×]\\s*\\d").matcher(text);
        int best = 0;
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 3 && n <= 7) {
                    int radius = n / 2; // 3->1, 5->2, 7->3
                    best = Math.max(best, radius);
                }
            } catch (NumberFormatException ignored) { }
        }
        if (best == 0 && (text.contains("veinmine") || text.contains("vein mine")
                || text.contains("vein-miner") || text.contains("area mine") || text.contains("area-break")
                || text.contains("excavate") || text.contains("vein"))) {
            best = 1; // default to a 3x3-ish vein
        }
        return best;
    }

    @SuppressWarnings("deprecation")
    private String readableText(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        try {
            if (!item.hasItemMeta()) return "";
            var meta = item.getItemMeta();
            if (meta == null) return "";
            if (meta.hasDisplayName()) sb.append(meta.getDisplayName()).append(' ');
            if (meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line != null) sb.append(line).append(' ');
                }
            }
        } catch (Throwable ignored) { }
        // Strip section-sign colour codes.
        return sb.toString().replaceAll("§[0-9a-fk-or]", "");
    }
}
