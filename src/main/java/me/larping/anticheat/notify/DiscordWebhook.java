package me.larping.anticheat.notify;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Minimal, dependency-free Discord webhook client.
 *
 * <p>Sends Hyphon alerts to a Discord channel via an incoming webhook. All
 * network work runs on a single background daemon thread, off the server
 * tick, and every failure is swallowed (logging must never lag or crash the
 * game). The webhook URL is read from config
 * ({@code logs.discord-webhook}); when empty, nothing is sent.
 *
 * <p>Messages are JSON-escaped and sent as the {@code content} field so they
 * work without an embed API dependency.
 */
public final class DiscordWebhook {

    private final String url;
    private ScheduledExecutorService pool;

    public DiscordWebhook(String url) {
        this.url = (url == null || url.isBlank()) ? null : url.trim();
        if (this.url != null) {
            this.pool = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Hyphon-Discord");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
    }

    public boolean isEnabled() {
        return url != null;
    }

    /** Sends a plain-text alert. Safe to call from the main thread. */
    public void send(String message) {
        if (url == null || message == null || pool == null) return;
        final String payload = buildJson(message);
        try {
            pool.schedule(() -> post(payload), 0, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            // pool may be shut down; ignore.
        }
    }

    private void post(String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Hyphon-AntiCheat");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setDoOutput(true);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            // 2xx = success; 429 = rate limited (skip silently).
            if (code < 200 || (code >= 300 && code != 429)) {
                // non-fatal: just stop.
            }
        } catch (Throwable ignored) {
            // Never propagate network errors.
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String buildJson(String message) {
        // Keep Discord's 2000-char content limit.
        String text = message;
        if (text.length() > 1900) text = text.substring(0, 1900) + " ...";
        return "{\"content\":\"" + escape(text) + "\"}";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public void shutdown() {
        if (pool != null) {
            try { pool.shutdownNow(); } catch (Throwable ignored) { }
        }
    }
}
