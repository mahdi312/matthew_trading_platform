package com.mst.matt.tradingplatformapp.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram bot for sending price alerts and receiving commands.
 *
 * HOW TO SETUP:
 * 1. Message @BotFather on Telegram → /newbot → get your token
 * 2. Message @userinfobot → get your chat ID
 * 3. Set in application.properties:
 *      telegram.bot.token=YOUR_TOKEN
 *      telegram.bot.username=YourBotUsername
 *      telegram.bot.chat-ids=YOUR_CHAT_ID
 *
 * COMMANDS:
 *   /start   → Subscribe to alerts
 *   /stop    → Unsubscribe
 *   /status  → Check platform status
 *   /price BTCUSDT  → Get current price
 */
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TradingTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TradingTelegramBot.class);

    @Value("${telegram.bot.username:TradingBot}")
    private String botUsername;

    // Comma-separated list of chat IDs that should receive alerts
    @Value("${telegram.bot.chat-ids:}")
    private String configuredChatIds;

    // Dynamically registered chat IDs (via /start command)
    private final List<Long> subscribedChatIds = new ArrayList<>();

    public TradingTelegramBot(@Value("${telegram.bot.token:}") String token) {
        super(token);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Handles incoming messages / commands from users.
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        String response = switch (text.split(" ")[0].toLowerCase()) {
            case "/start" -> {
                if (!subscribedChatIds.contains(chatId)) {
                    subscribedChatIds.add(chatId);
                }
                yield """
                    🚀 *Trading Intelligence Platform*
                    
                    Welcome! You are now subscribed to price alerts.
                    
                    *Commands:*
                    /status  — Platform status
                    /price SYMBOL — Get current price
                    /stop    — Unsubscribe from alerts
                    """;
            }
            case "/stop" -> {
                subscribedChatIds.remove(chatId);
                yield "✅ You have been unsubscribed from alerts.";
            }
            case "/status" ->
                    "✅ Trading Platform is *ONLINE*\n" +
                            "📡 Live streams: Active\n" +
                            "🕒 " + java.time.LocalDateTime.now();
            case "/price" -> {
                String[] parts = text.split(" ");
                if (parts.length < 2) yield "Usage: /price SYMBOL";
                yield "Use the app to check live prices. Symbol: " + parts[1];
            }
            default -> "Unknown command. Try /start for help.";
        };

        sendReply(chatId, response);
    }

    /**
     * Sends an alert message to ALL subscribed chat IDs.
     * Called by NotificationService when an alert fires.
     */
    public void sendAlertMessage(String markdownMessage) {
        List<Long> allChats = new ArrayList<>(subscribedChatIds);

        // Also include statically configured chat IDs from properties
        if (!configuredChatIds.isBlank()) {
            for (String id : configuredChatIds.split(",")) {
                try {
                    Long chatId = Long.parseLong(id.trim());
                    if (!allChats.contains(chatId)) allChats.add(chatId);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (allChats.isEmpty()) {
            log.warn("No Telegram recipients. Use /start to subscribe or set telegram.bot.chat-ids");
            return;
        }

        allChats.forEach(chatId -> sendReply(chatId, markdownMessage));
    }

    private void sendReply(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram send failed to {}: {}", chatId, e.getMessage());
        }
    }
}