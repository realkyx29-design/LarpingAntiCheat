package me.larping.anticheat.checks.world;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import me.larping.anticheat.modifiers.Capabilities;
import me.larping.anticheat.util.BlockHardness;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Fast-break / instant-break detection that understands the player's actual
 * tool.
 *
 * <p>The minimum plausible break time is derived from the block hardness
 * <b>divided by the player's real mining speed</b> — held pickaxe tier,
 * Efficiency enchant, Haste/Conduit-Power effects and recognised custom fast-
 * mining enchantments (via {@link Capabilities#miningSpeedMultiplier}). A
 * custom pickaxe that mines much faster therefore widens the allowance
 * automatically; there is no vanilla-only constant.
 *
 * <p>Legitimate area-mining (3x3/4x4 custom pickaxe abilities) is exempted via
 * {@link Capabilities#areaMining}: when the tool advertises an area ability and
 * the broken block is within that radius of the targeted block, speed-based
 * flagging is suppressed, leaving only the impossibility checks (breaking an
 * unbreakable block) in place.
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

        if (BlockHardness.isUnbreakable(block)) {
            // Bedrock / end portal frame etc. cannot be broken regardless of tool.
            flag(ctx, 1.0, 0.995, "brokeUnbreakable=" + block.getType().name().toLowerCase());
            return;
        }

        CheckConfig cc = ctx.cfg().check("fastbreak");
        double minFraction = cc.v1() > 0 ? cc.v1() : 0.55;

        // Mining speed from the player's real pickaxe/enchants/effects.
        double toolSpeed = caps != null ? caps.miningSpeedMultiplier : 1.0;
        if (toolSpeed < 1.0) toolSpeed = 1.0;

        long vanillaMin = BlockHardness.minBreakTimeMs(block);
        // Divide by the tool's mining speed; a fast/enchanted/custom pickaxe
        // legitimately removes the block in a fraction of the vanilla time.
        long expected = (long) (vanillaMin / toolSpeed);
        // A hard floor of ~one tick even for the fastest legitimate instamine,
        // so true zero-tick nuker packets (observed ~0ms) still stand out.
        expected = Math.max(expected, 20L);

        long observed = ctx.data().consumeBreakDuration(blockKey(block));
        if (observed < 0) return; // missed the start event (lag / join mid-break)

        // Area-mining ability: a custom pickaxe breaking neighbouring blocks
        // as part of one swing can appear nearly simultaneous. When the tool
        // is recognised as an area miner, suppress the per-block speed flag;
        // Nuker still validates reach and overall rate separately.
        boolean area = caps != null && caps.areaMining;

        long allowance = (long) (expected * minFraction);
        if (!area && vanillaMin > 80 && observed < allowance) {
            double buf = ctx.data().adjustBuffer("fastbreak", 1.0, 32.0);
            if (buf >= cc.v2()) {
                flag(ctx, 0.6, 0.9,
                        "breakTime=" + observed + "ms min=" + allowance + "ms"
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
