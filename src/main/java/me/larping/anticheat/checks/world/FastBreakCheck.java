package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.util.BlockHardness;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Fast-break / instant-break detection.
 *
 * <p>Measures the elapsed time between the damage-start event and the block
 * break event for each block. Vanilla needs {@code hardness * 1.5 / toolSpeed}
 * seconds; breaking a block in a fraction of that time is instamine / fast
 * break. Soft blocks (dirt etc.) legitimately take a fraction of a second, so
 * we use block-type-aware floor times with generous slack.
 */
public final class FastBreakCheck extends WorldCheck {

    public FastBreakCheck() {
        super("FastBreak");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        // Driven by break-start / break events.
    }

    public void recordBreakStart(Player player, Block block, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        ctx.data().recordBreakStart(blockKey(block));
    }

    public void evaluateBreak(Player player, Block block, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        ctx.data().recordBreak();
        CheckConfig cc = ctx.cfg().check("fastbreak");
        double minFraction = cc.v1() > 0 ? cc.v1() : 0.55;

        if (BlockHardness.isUnbreakable(block)) {
            // Breaking bedrock / end portal frame is simply impossible.
            flag(ctx, 1.0, 0.995, "brokeUnbreakable=" + block.getType().name().toLowerCase());
            return;
        }

        long expected = BlockHardness.minBreakTimeMs(block);
        long observed = ctx.data().consumeBreakDuration(blockKey(block));
        if (observed < 0) return; // we missed the start event (lag/join mid-break)

        long allowance = (long) (expected * minFraction);
        if (expected > 120 && observed < allowance) {
            double buf = ctx.data().adjustBuffer("fastbreak", 1.0, 32.0);
            if (buf >= cc.v2()) {
                flag(ctx, 0.6, 0.9,
                        "breakTime=" + observed + "ms min=" + allowance + "ms"
                                + " block=" + block.getType().name().toLowerCase()
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("fastbreak", -1.5, 32.0);
        }
    }

    static long blockKey(Block block) {
        // Packed coordinates within world: unique for ~33M blocks range.
        long x = block.getX() & 0x3FFFFFFL;
        long y = (block.getY() & 0xFFFL) << 26;
        long z = (block.getZ() & 0x3FFFFFFL) << 38;
        return x | y | z;
    }
}
