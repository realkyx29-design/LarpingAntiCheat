package me.larping.anticheat.util;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.EnumMap;
import java.util.Map;

/**
 * Vanilla block hardness values used by the FastBreak check to verify a block
 * could legitimately have been broken in the observed time.
 *
 * <p>Values mirror the vanilla {@code destroyTime} table. Missing materials
 * fall back to a conservative default so custom blocks do not false-flag.
 */
public final class BlockHardness {

    private static final Map<Material, Float> HARDNESS = new EnumMap<>(Material.class);
    private static final float DEFAULT_HARDNESS = 2.0f;
    /** Bedrock-like and other unbreakable blocks are simply skipped. */
    private static final float UNBREAKABLE = -1.0f;

    static {
        try {
        // ---- instant / near-instant ----
        put(Material.SHORT_GRASS, 0.0f);
        put(Material.TALL_GRASS, 0.0f);
        put(Material.FERN, 0.0f);
        put(Material.LARGE_FERN, 0.0f);
        put(Material.SEAGRASS, 0.0f);
        put(Material.TALL_SEAGRASS, 0.0f);
        put(Material.VINE, 0.2f);
        put(Material.GLOW_LICHEN, 0.2f);
        put(Material.CAVE_VINES, 0.0f);
        put(Material.CAVE_VINES_PLANT, 0.0f);
        put(Material.SUGAR_CANE, 0.0f);
        put(Material.BAMBOO, 1.0f);
        put(Material.BAMBOO_SAPLING, 1.0f);
        put(Material.SNOW, 0.2f);
        put(Material.TORCH, 0.0f);
        put(Material.WALL_TORCH, 0.0f);
        put(Material.SOUL_TORCH, 0.0f);
        put(Material.REDSTONE_TORCH, 0.0f);
        put(Material.FLOWER_POT, 0.0f);
        put(Material.DECORATED_POT, 0.0f);
        put(Material.CARROTS, 0.0f);
        put(Material.POTATOES, 0.0f);
        put(Material.WHEAT, 0.0f);
        put(Material.BEETROOTS, 0.0f);
        put(Material.NETHER_WART, 0.0f);
        put(Material.SWEET_BERRY_BUSH, 0.0f);
        put(Material.TNT, 0.0f);
        put(Material.FIRE, 0.0f);
        put(Material.SOUL_FIRE, 0.0f);
        put(Material.SLIME_BLOCK, 0.0f);
        put(Material.HONEY_BLOCK, 0.0f);

        // ---- 0.1 – 0.7 soft ----
        put(Material.BROWN_MUSHROOM, 0.0f);
        put(Material.RED_MUSHROOM, 0.0f);
        put(Material.MOSS_CARPET, 0.1f);
        put(Material.MOSS_BLOCK, 0.1f);
        put(Material.PALE_MOSS_CARPET, 0.1f);
        put(Material.PALE_MOSS_BLOCK, 0.1f);
        put(Material.CAKE, 0.5f);
        put(Material.COBWEB, 4.0f);
        put(Material.DIRT, 0.5f);
        put(Material.COARSE_DIRT, 0.5f);
        put(Material.ROOTED_DIRT, 0.5f);
        put(Material.GRASS_BLOCK, 0.6f);
        put(Material.PODZOL, 0.5f);
        put(Material.MYCELIUM, 0.6f);
        put(Material.FARMLAND, 0.6f);
        put(Material.CLAY, 0.6f);
        put(Material.SAND, 0.5f);
        put(Material.RED_SAND, 0.5f);
        put(Material.GRAVEL, 0.6f);
        put(Material.SOUL_SAND, 0.5f);
        put(Material.SOUL_SOIL, 0.5f);
        put(Material.MUD, 0.5f);
        put(Material.PACKED_MUD, 1.0f);
        put(Material.MUD_BRICKS, 1.5f);
        put(Material.SPONGE, 0.6f);
        put(Material.WET_SPONGE, 0.6f);

        // ---- families matched by name suffix (covers all colour/variant
        //      materials, since generic Material.WOOL / CARPET / BED do not
        //      exist in the Bukkit API). ----
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.endsWith("_PLANKS")) put(m, 2.0f);
            else if (n.endsWith("_LOG") || n.endsWith("_WOOD")
                    || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) put(m, 2.0f);
            else if (n.endsWith("_LEAVES")) put(m, 0.2f);
            else if (n.endsWith("_SAPLING")) put(m, 0.0f);
            else if (n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")) put(m, 2.0f);
            else if (n.endsWith("_SLAB") || n.endsWith("_STAIRS")) {
                if (!HARDNESS.containsKey(m)) put(m, 2.0f);
            }
            else if (n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")) put(m, 0.5f);
            else if (n.endsWith("_TRAPDOOR") || n.endsWith("_DOOR")) put(m, 3.0f);
            else if (n.endsWith("_WALL_SIGN") || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN"))
                put(m, 1.0f);
            else if (n.endsWith("_BANNER") || n.endsWith("_WALL_BANNER")) put(m, 1.0f);
            // Coloured soft families (wool 0.8, carpet 0.1, beds 0.2).
            else if (n.endsWith("_WOOL")) put(m, 0.8f);
            else if (n.endsWith("_CARPET")) put(m, 0.1f);
            else if (n.endsWith("_BED")) put(m, 0.2f);
            else if (n.endsWith("_CONCRETE_POWDER")) put(m, 0.5f);
            else if (n.endsWith("_CONCRETE")) put(m, 1.8f);
            else if (n.endsWith("_TERRACOTTA")) put(m, 1.25f);
            else if (n.endsWith("_GLASS_PANE") || n.endsWith("_STAINED_GLASS_PANE")) put(m, 0.3f);
            else if (n.endsWith("_GLASS")) put(m, 0.3f);
        }

        // ---- stone / ores / deepslate ----
        put(Material.STONE, 1.5f);
        put(Material.COBBLESTONE, 2.0f);
        put(Material.MOSSY_COBBLESTONE, 2.0f);
        put(Material.SMOOTH_STONE, 1.5f);
        put(Material.STONE_BRICKS, 1.5f);
        put(Material.CRACKED_STONE_BRICKS, 1.5f);
        put(Material.MOSSY_STONE_BRICKS, 1.5f);
        put(Material.CHISELED_STONE_BRICKS, 1.5f);
        put(Material.GRANITE, 1.5f);
        put(Material.DIORITE, 1.5f);
        put(Material.ANDESITE, 1.5f);
        put(Material.DEEPSLATE, 3.0f);
        put(Material.COBBLED_DEEPSLATE, 3.5f);
        put(Material.POLISHED_DEEPSLATE, 3.5f);
        put(Material.DEEPSLATE_BRICKS, 3.5f);
        put(Material.DEEPSLATE_TILES, 3.5f);
        put(Material.TUFF, 1.5f);
        put(Material.CALCITE, 0.75f);
        put(Material.DRIPSTONE_BLOCK, 1.5f);
        put(Material.OBSIDIAN, 50.0f);
        put(Material.CRYING_OBSIDIAN, 50.0f);
        put(Material.RESPAWN_ANCHOR, 50.0f);
        put(Material.BEDROCK, UNBREAKABLE);
        put(Material.NETHERITE_BLOCK, 50.0f);
        put(Material.ANCIENT_DEBRIS, 30.0f);

        // ---- ores ----
        put(Material.COAL_ORE, 3.0f);
        put(Material.DEEPSLATE_COAL_ORE, 3.0f);
        put(Material.IRON_ORE, 3.0f);
        put(Material.DEEPSLATE_IRON_ORE, 3.0f);
        put(Material.COPPER_ORE, 3.0f);
        put(Material.DEEPSLATE_COPPER_ORE, 3.0f);
        put(Material.GOLD_ORE, 3.0f);
        put(Material.DEEPSLATE_GOLD_ORE, 3.0f);
        put(Material.REDSTONE_ORE, 3.0f);
        put(Material.DEEPSLATE_REDSTONE_ORE, 3.0f);
        put(Material.LAPIS_ORE, 3.0f);
        put(Material.DEEPSLATE_LAPIS_ORE, 3.0f);
        put(Material.DIAMOND_ORE, 3.0f);
        put(Material.DEEPSLATE_DIAMOND_ORE, 3.0f);
        put(Material.EMERALD_ORE, 3.0f);
        put(Material.DEEPSLATE_EMERALD_ORE, 3.0f);
        put(Material.NETHER_GOLD_ORE, 3.0f);
        put(Material.NETHER_QUARTZ_ORE, 3.0f);

        // ---- glass / ice ----
        put(Material.GLASS, 0.3f);
        put(Material.TINTED_GLASS, 0.3f);
        put(Material.GLASS_PANE, 0.3f);
        put(Material.ICE, 0.5f);
        put(Material.PACKED_ICE, 0.5f);
        put(Material.BLUE_ICE, 0.5f);
        put(Material.FROSTED_ICE, 0.5f);
        put(Material.OBSIDIAN, 50.0f);

        // ---- misc common ----
        put(Material.BOOKSHELF, 1.5f);
        put(Material.CRAFTING_TABLE, 2.5f);
        put(Material.FURNACE, 3.5f);
        put(Material.BLAST_FURNACE, 3.5f);
        put(Material.SMOKER, 3.5f);
        put(Material.HOPPER, 3.0f);
        put(Material.DISPENSER, 3.5f);
        put(Material.DROPPER, 3.5f);
        put(Material.IRON_BLOCK, 5.0f);
        put(Material.GOLD_BLOCK, 3.0f);
        put(Material.DIAMOND_BLOCK, 5.0f);
        put(Material.EMERALD_BLOCK, 5.0f);
        put(Material.LAPIS_BLOCK, 3.0f);
        put(Material.REDSTONE_BLOCK, 5.0f);
        put(Material.COPPER_BLOCK, 3.0f);
        put(Material.NETHER_BRICKS, 2.0f);
        put(Material.NETHERRACK, 0.4f);
        put(Material.BASALT, 1.25f);
        put(Material.BLACKSTONE, 1.5f);
        put(Material.END_STONE, 3.0f);
        put(Material.PURPUR_BLOCK, 1.5f);
        put(Material.SANDSTONE, 0.8f);
        put(Material.RED_SANDSTONE, 0.8f);
        put(Material.TERRACOTTA, 1.25f);
        put(Material.GLOWSTONE, 0.3f);
        put(Material.SEA_LANTERN, 0.3f);
        put(Material.SHROOMLIGHT, 0.3f);
        put(Material.PUMPKIN, 1.0f);
        put(Material.CARVED_PUMPKIN, 1.0f);
        put(Material.MELON, 1.0f);
        put(Material.HAY_BLOCK, 0.5f);
        } catch (Throwable ignored) { /* a renamed/removed material on this server version never breaks startup */ }
    }

    private static void put(Material m, float hardness) {
        if (m != null) HARDNESS.put(m, hardness);
    }

    /**
     * Minimum plausible break time in milliseconds for a player breaking the
     * given block. Uses an optimistic (fast) tool assumption so legit players
     * are never flagged, while instant-breaks of hard blocks still are.
     */
    public static long minBreakTimeMs(Block block) {
        Material type = block.getType();
        float h = HARDNESS.getOrDefault(type, DEFAULT_HARDNESS);
        if (h < 0) return Long.MAX_VALUE; // unbreakable
        if (h <= 0.05f) return 0;          // instant-break blocks

        // Vanilla time with a correct (harvesting) tool = hardness * 1.5s.
        // Efficiency V netherite applies roughly an 8-10x speedup, so the
        // floor is hardness * 1.5 / 10.8 seconds, with generous slack for
        // ping, tick timing and client prediction (applied later by caller).
        float baseSeconds = h * 1.5f;
        double seconds = baseSeconds / 10.8;
        return Math.max(40L, (long) (seconds * 1000.0)); // floor ~one game tick
    }

    public static boolean isUnbreakable(Block block) {
        Float h = HARDNESS.get(block.getType());
        return h != null && h < 0;
    }

    private BlockHardness() { }
}
