package com.mst.matt.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram Bot for {@code notification-service}.
 *
 * <p>Ported from the desktop monolith's
 * {@code service/alert/TradingTelegramBot.java}. This is the <em>production</em>
 * version; the stub that previously sat in {@code alert-service} has been
 * replaced with a Kafka-only publish (see Step 8 / option (a) in the migration
 * guide — alert-service no longer sends directly).</p>
 *
 * <h3>How to setup</h3>
 * <ol>
 *   <li>Message {@code @BotFather} on Telegram → {@code /newbot} → get token</li>
 *   <li>Message {@code @userinfobot} → get your chat ID</li>
 *   <li>Set in environment / application.yml:
 *     <pre>
 *       telegram.bot.enabled=true
 *       telegram.bot.token=YOUR_TOKEN
 *       telegram.bot.username=YourBotUsername
 *       telegram.bot.chat-ids=CHAT_ID1,CHAT_ID2
 *     </pre>
 *   </li>
 * </ol>
 *
 * <h3>Bot commands</h3>
 * <ul>
 *   <li>{@code /start}  — subscribe to alerts</li>
 *   <li>{@code /stop}   — unsubscribe</li>
 *   <li>{@code /status} — platform status</li>
 * </ul>
 *
 * <p>Conditional on {@code telegram.bot.enabled=true} — the service starts
 * cleanly without a bot token in local dev.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TradingTelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username:TradingBot}")
    private String botUsername;

    /** Statically configured chat IDs (comma-separated) from application config. */
    @Value("${telegram.bot.chat-ids:}")
    private String configuredChatIds;

    /** Dynamically registered chat IDs (via {@code /start} command). */
    private final List<Long> subscribedChatIds = new ArrayList<>();

    public TradingTelegramBot(@Value("${telegram.bot.token:}") String token) {
        super(token);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // ── Incoming message handler ──────────────────────────────────────────────

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        String response = switch (text.split(" ")[0].toLowerCase()) {
            case "/start" -> {
                if (!subscribedChatIds.contains(chatId)) {
                    subscribedChatIds.add(chatId);
                    log.info("[TelegramBot] new subscriber: chatId={}", chatId);
                }
                yield """
                    🚀 *Trading Intelligence Platform*
                    
                    Welcome! You are now subscribed to price alerts and trade events.
                    
                    *Commands:*
                    /status  — Platform status
                    /stop    — Unsubscribe from alerts
                    """;
            }
            case "/stop" -> {
                subscribedChatIds.remove(chatId);
                log.info("[TelegramBot] unsubscribed: chatId={}", chatId);
                yield "✅ You have been unsubscribed from alerts.";
            }
            case "/status" ->
                    "✅ Trading Platform *notification-service* is ONLINE\n" +
                    "🕒 " + java.time.LocalDateTime.now();
            default -> "Unknown command. Try /start for help.";
        };

        sendReply(chatId, response);
    }

    // ── Outbound API ──────────────────────────────────────────────────────────

    /**
     * Sends a Markdown-formatted message to ALL subscribed + configured chat IDs.
     * Called by {@code TelegramNotificationChannel} when a Kafka event is received.
     *
     * @param markdownMessage message in Telegram Markdown v1 format
     */
    @Async("notificationSendExecutor")
    public void sendAlertMessage(String markdownMessage) {
        List<Long> allChats = new ArrayList<>(subscribedChatIds);

        if (configuredChatIds != null && !configuredChatIds.isBlank()) {
            for (String id : configuredChatIds.split(",")) {
                try {
                    Long chatId = Long.parseLong(id.trim());
                    if (!allChats.contains(chatId)) allChats.add(chatId);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (allChats.isEmpty()) {
            log.warn("[TelegramBot] No recipients — use /start or set telegram.bot.chat-ids");
            return;
        }

        allChats.forEach(chatId -> sendReply(chatId, markdownMessage));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendReply(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            execute(message);
        } catch (TelegramApiException ex) {
            log.error("[TelegramBot] send failed to chatId={}: {}", chatId, ex.getMessage());
        }
    }
}
