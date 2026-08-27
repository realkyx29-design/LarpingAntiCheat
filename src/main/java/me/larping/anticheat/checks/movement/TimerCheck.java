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
            if (diff < 30) {
                int buffer = context.data().timerBuffer(context.data().timerBuffer() + 1);
                if (buffer >= 8) {
                    double vlAmount = 0.4;
                    double confidence = 0.82;
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
                context.data().timerBuffer(context.data().timerBuffer() - 1);
            }
        }
    }
}
