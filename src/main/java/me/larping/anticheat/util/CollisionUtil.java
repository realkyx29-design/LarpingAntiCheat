package me.larping.anticheat.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.Collection;

/**
 * Server-authoritative collision helpers.
 *
 * <p>Everything here relies on real block collision shapes
 * ({@link Block#getCollisionShape()}) so stairs, slabs, fences, walls,
 * scaffolding, snow layers and other partial blocks are handled correctly
 * without fragile material-name matching.
 *
 * <p>All methods are cheap (a handful of block lookups with early exit) and run
 * on the server main thread, at most a few times per player per tick.
 */
public final class CollisionUtil {

    /** Player AABB half-width and height, matching vanilla. */
    public static final double PLAYER_HALF_WIDTH = 0.3;
    public static final double PLAYER_HEIGHT = 1.8;

    private CollisionUtil() { }

    /** Builds the player collision box at a given position (feet at x,y,z). */
    public static BoundingBox playerBox(double x, double y, double z) {
        return new BoundingBox(
                x - PLAYER_HALF_WIDTH, y, z - PLAYER_HALF_WIDTH,
                x + PLAYER_HALF_WIDTH, y + PLAYER_HEIGHT, z + PLAYER_HALF_WIDTH);
    }

    /**
     * True if any block collision shape overlaps the given box.
     * Only inspects the block range the box spans — typically 2-9 blocks with
     * early termination on the first hit.
     */
    public static boolean hasCollision(World world, BoundingBox box) {
        if (world == null) return false;
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        // Guard against pathological boxes (huge movements).
        if ((maxX - minX) > 8 || (maxY - minY) > 8 || (maxZ - minZ) > 8) return false;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isSolid()) continue;
                    Collection<BoundingBox> shapes;
                    try {
                        shapes = b.getCollisionShape().getBoundingBoxes();
                    } catch (Exception ignored) {
                        // Cross-world / unloaded edge cases: assume no collision.
                        continue;
                    }
                    if (shapes.isEmpty()) continue;
                    for (BoundingBox shape : shapes) {
                        if (shape.overlaps(box)) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Server-side ground check: a thin slab just under the player's feet
     * overlaps a real collision shape. Uses a shrunken footprint so walking
     * beside a wall (where {@code isOnGround()} is ambiguous) still works.
     */
    public static boolean isOnGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        BoundingBox feet = playerBox(loc.getX(), loc.getY(), loc.getZ());
        // Probe a 0.08 tall slab immediately below the feet.
        BoundingBox probe = new BoundingBox(
                feet.getMinX() + 0.05, feet.getMinY() - 0.08, feet.getMinZ() + 0.05,
                feet.getMaxX() - 0.05, feet.getMinY() - 0.001, feet.getMaxZ() - 0.05);
        return hasCollision(loc.getWorld(), probe);
    }

    /**
     * True if the player box at {@code loc} intersects a solid block
     * (used by the Phase check on path samples).
     */
    public static boolean isInsideBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return hasCollision(loc.getWorld(), playerBox(loc.getX(), loc.getY(), loc.getZ()));
    }

    /**
     * Samples the straight path from {@code from} to {@code to} and returns
     * true if any player-sized sample intersects solid collision.
     *
     * @param step distance between samples (blocks); ~0.5 is dense enough to
     *             catch wall phasing at any speed without excessive checks.
     */
    public static boolean pathCollides(Location from, Location to, double step) {
        if (from == null || to == null || from.getWorld() == null) return false;
        if (from.getWorld() != to.getWorld()) return false;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001) return false;
        int samples = Math.min(40, (int) Math.ceil(dist / step));
        World world = from.getWorld();
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (samples + 1);
            double x = from.getX() + dx * t;
            double y = from.getY() + dy * t;
            double z = from.getZ() + dz * t;
            if (hasCollision(world, playerBox(x, y, z))) return true;
        }
        return false;
    }

    /**
     * True when the player is touching a solid block on the side of their body
     * at roughly head height (used by the Spider / wall-climb check).
     */
    public static boolean touchingWall(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        // Check four thin slabs hugging the sides of the upper body so ground
        // contact doesn't count as wall contact.
        double s = 0.10;
        BoundingBox[] probes = {
                new BoundingBox(x - PLAYER_HALF_WIDTH - s, y + 0.7, z - PLAYER_HALF_WIDTH,
                        x - PLAYER_HALF_WIDTH, y + 1.6, z + PLAYER_HALF_WIDTH),
                new BoundingBox(x + PLAYER_HALF_WIDTH, y + 0.7, z - PLAYER_HALF_WIDTH,
                        x + PLAYER_HALF_WIDTH + s, y + 1.6, z + PLAYER_HALF_WIDTH),
                new BoundingBox(x - PLAYER_HALF_WIDTH, y + 0.7, z - PLAYER_HALF_WIDTH - s,
                        x + PLAYER_HALF_WIDTH, y + 1.6, z - PLAYER_HALF_WIDTH),
                new BoundingBox(x - PLAYER_HALF_WIDTH, y + 0.7, z + PLAYER_HALF_WIDTH,
                        x + PLAYER_HALF_WIDTH, y + 1.6, z + PLAYER_HALF_WIDTH + s),
        };
        for (BoundingBox p : probes) {
            if (hasCollision(world, p)) return true;
        }
        return false;
    }
}
