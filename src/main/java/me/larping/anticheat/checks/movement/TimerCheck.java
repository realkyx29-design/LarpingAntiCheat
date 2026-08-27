package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import me.larping.anticheat.config.CheckConfig;
import org.bukkit.entity.Player;

/**
 * Game-speed (timer) check based on a packet-credit balance.
 *
 * <p>The vanilla client sends roughly one move packet per 50ms tick. A timer
 * cheat speeds the client clock, producing move packets faster than the server
 * tick rate. Instead of comparing individual intervals (which a single lag
 * burst wrecks), we keep a balance sampled once per server tick window:
 *
 * <pre>
 *   balance += packetsReceived - expectedPackets
 * </pre>
 *
 * A legit client oscillates around zero. Lag spikes coalesce then flush
 * packets, but the balance gain from a spike is a one-off (and capped per
 * window), whereas a timer hack pushes the balance positive <i>sustained</i>.
 * That distinction (fast windows in a row) is what kills the false positives
 * while catching timers from ~1.15× upward.
 */
public final class TimerCheck extends MovementCheck {

    public TimerCheck() {
        super("Timer");
    }

    @Override
    public void evaluate(Player player, CheckContext ctx) {
        // Per-move event: just count packets. The balance is updated and
        // evaluated once per tick by {@link #onTick(CheckContext)}.
        if (exempt(ctx)) {
            ctx.data().movePacketsThisWindow = 0;
            ctx.data().lastTimerTickMs = 0;
            return;
        }
        if (!checkEnabled(ctx)) return;
        ctx.data().movePacketsThisWindow++;
    }

    /** Called once per server tick by the flush task. Main-thread. */
    public void onTick(CheckContext ctx) {
        if (exempt(ctx) || !checkEnabled(ctx)) {
            ctx.data().movePacketsThisWindow = 0;
            ctx.data().timerBalance(0);
            ctx.data().timerFastWindows(0);
            return;
        }

        CheckConfig cc = ctx.cfg().check("timer");
        double allowedFactor = cc.v3() <= 0 ? 1.16 : cc.v3();
        double threshold = cc.v1() <= 0 ? 9.0 : cc.v1();
        int minFastWindows = (int) (cc.v2() <= 0 ? 10 : cc.v2());

        // Legit + slack: up to allowedFactor packets expected per tick.
        // Coalesced lag bursts are capped so they cannot fast-build balance.
        int packets = Math.min(ctx.data().movePacketsThisWindow, 3);
        ctx.data().movePacketsThisWindow = 0;

        double balance = ctx.data().timerBalance();
        balance += packets - allowedFactor;
        // Negative (client catching up / slow) floors at zero quickly.
        if (balance < 0) balance = Math.max(0.0, balance + 0.5);
        // Burst pool absorbs the occasional 2-packet tick.
        balance = Math.min(balance, threshold + 12.0);
        ctx.data().timerBalance(balance);

        if (balance > threshold) {
            int fast = ctx.data().timerFastWindows() + 1;
            ctx.data().timerFastWindows(fast);
            if (fast >= minFastWindows) {
                ctx.plugin().violations().flag(ctx.player(), checkName, "Movement",
                        0.4, 0.88,
                        "balance=" + String.format("%.1f", balance)
                                + " fastWindows=" + fast
                                + " estSpeed=" + String.format("%.2f", 1.0 + balance / 40.0) + "x",
                        me.larping.anticheat.managers.ViolationManager.Setback.NONE);
                // Decay so it reports periodically rather than every tick.
                ctx.data().timerBalance(threshold * 0.6);
                ctx.data().timerFastWindows((int) (minFastWindows * 0.7));
            }
        } else {
            int fast = ctx.data().timerFastWindows();
            if (fast > 0) ctx.data().timerFastWindows(fast - 1);
        }
    }
}
