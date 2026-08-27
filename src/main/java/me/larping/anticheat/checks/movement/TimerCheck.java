package me.larping.anticheat.checks.movement;

import me.larping.anticheat.checks.CheckContext;
import org.bukkit.entity.Player;

public final class TimerCheck extends MovementCheck {

    public TimerCheck() {
        super("Timer");
    }

    @Override
    public void evaluate(Player player, CheckContext context) {
        if (shouldBypass(player, context)) return;
        if (!context.plugin().configManager().isCheckEnabled("timer")) return;

        long now = System.currentTimeMillis();
        long lastPacket = context.data().lastPacketTime();
        context.data().recordPacketTime(now);

        if (lastPacket > 0) {
            long diff = now - lastPacket;
            // Extremely conservative threshold: only flag when packets arrive faster than 15ms consistently (over 66 packets/sec)
            if (diff < 15) {
                int buffer = context.data().timerBuffer(context.data().timerBuffer() + 1);
                int minConfirm = context.plugin().getConfig().getInt("checks.timer.min-confirmations", 12);
                if (buffer >= minConfirm) {
                    double vlAmount = 0.3;
                    double confidence = 0.80;
                    context.plugin().violations().add(
                            player,
                            "Timer",
                            "Movement",
                            vlAmount,
                            confidence,
                            "packetInterval=" + diff + "ms, buffer=" + buffer
                    );
                }
            } else {
                context.data().timerBuffer(context.data().timerBuffer() - 2);
            }
        }
    }
}
