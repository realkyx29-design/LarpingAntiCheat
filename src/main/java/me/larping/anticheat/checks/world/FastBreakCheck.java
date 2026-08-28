package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.modifiers.Capabilities;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Fast-break detection that is item-and-block aware.
 *
 * <p>The minimum plausible break time is derived from the block hardness
 * divided by the player's <b>real</b> mining speed — pickaxe tier, Efficiency,
 * Haste/Mining Fatigue, and recognised custom fast-mining enchants
 * ({@code caps.miningSpeedMultiplier}). A custom pickaxe that mines much faster
 * widens the allowance automatically. Legitimate instant-break and area-mining
 * swings are exempt. This check only flags times that are impossible for the
 * block plus the item actually held, and never blocks — it logs/accumulates.
 */
public final class FastBreakCheck extends WorldCheck {

    public FastBreakCheck() {
        super("FastBreak");
    }

    @Override
    public void evaluate(CheckContext ctx) {
        // Driven by break-start / break events.
    }

    public void recordBreakStart(Player player, Block block, Capabilities caps, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;
        ctx.data().recordBreakStart(blockKey(block));
    }

    public void evaluateBreak(Player player, BlockBreakEvent event, Capabilities caps, CheckContext ctx) {
        if (ctx.isFullyExempt() || !checkEnabled(ctx)) return;

        Block block = event.getBlock();
        ctx.data().recordBreak();

        if (me.larping.anticheat.util.BlockHardness.isUnbreakable(block)) {
            flag(ctx, 1.0, 0.995, "brokeUnbreakable=" + block.getType().name().toLowerCase());
            return;
        }

        CheckConfig cc = ctx.cfg().check("fastbreak");

        double toolSpeed = caps != null ? caps.miningSpeedMultiplier : 1.0;
        if (toolSpeed < 1.0) toolSpeed = 1.0;

        // Mining Fatigue slows mining (and breaks instamine): never flag when
        // affected — slower breaking can't be FastBreak.
        if (caps != null && caps.hasteAmplifier <= -100) {
            // marker if we ever track fatigue explicitly
        }

        long vanillaMin = me.larping.anticheat.util.BlockHardness.minBreakTimeMs(block);
        long expected = (long) (vanillaMin / toolSpeed);
        expected = Math.max(expected, 5L); // almost-instant with a fast custom tool

        long observed = ctx.data().consumeBreakDuration(blockKey(block));
        if (observed < 0) return; // missed start (lag / area swing) — do NOT flag

        boolean area = caps != null && caps.areaMining;

        // Only flag when the observed time is far below even the most generous
        // legit estimate AND it is not an area swing. Use a 40% floor plus a
        // large absolute slack so ping/cusom-enchant variance never trips it.
        long allowance = (long) (expected * 0.4) - 60; // generous slack
        if (!area && vanillaMin > 60 && observed < allowance) {
            double buf = ctx.data().adjustBuffer("fastbreak", 1.0, 32.0);
            if (buf >= cc.v2()) {
                flag(ctx, 0.6, 0.9,
                        "breakTime=" + observed + "ms minAllowed=" + allowance + "ms"
                                + " block=" + block.getType().name().toLowerCase()
                                + " toolSpeed=" + String.format("%.1f", toolSpeed)
                                + " buffer=" + (int) buf);
            }
        } else {
            ctx.data().adjustBuffer("fastbreak", -1.5, 32.0);
        }
    }

    static long blockKey(Block block) {
        long x = block.getX() & 0x3FFFFFFL;
        long y = (block.getY() & 0xFFFL) << 26;
        long z = (block.getZ() & 0x3FFFFFFL) << 38;
        return x | y | z;
    }
}
