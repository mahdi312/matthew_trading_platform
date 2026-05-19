package com.mst.matt.tradingplatformapp.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;

/**
 * Dispatches notifications via three channels:
 *   1. OS Desktop Tray notification (Java AWT SystemTray)
 *   2. Email (Spring JavaMail / Gmail SMTP)
 *   3. Telegram Bot (rubenlagus TelegramBots library)
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private TradingTelegramBot telegramBot;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${notification.email.to:}")
    private String toEmail;

    // ── Desktop Notification ─────────────────────────────────

    public void sendDesktop(String title, String message) {
        try {
            if (!SystemTray.isSupported()) {
                log.warn("SystemTray not supported on this platform");
                return;
            }

            SystemTray tray = SystemTray.getSystemTray();

            // Use existing tray icon or create a minimal one
            TrayIcon[] icons = tray.getTrayIcons();
            TrayIcon trayIcon;

            if (icons.length > 0) {
                trayIcon = icons[0];
            } else {
                // Create a minimal 1×1 transparent image — empty byte[] causes NPE on some JDKs
                BufferedImage img = new java.awt.image.BufferedImage(
                        1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                trayIcon = new TrayIcon(img, "Trading Platform");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            }

            trayIcon.displayMessage(title, message, MessageType.INFO);
            log.debug("Desktop notification sent: {}", title);

        } catch (Exception e) {
            log.error("Desktop notification failed: {}", e.getMessage());
        }
    }

    // ── Email Notification ───────────────────────────────────

    public void sendEmail(String subject, String body) {
        if (mailSender == null) {
            log.warn("Mail sender not configured. Set SMTP settings in application.properties.");
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No recipient email configured (notification.email.to)");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🔔 " + subject);

            // HTML email body
            String html = buildEmailHtml(subject, body);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Email alert sent: {}", subject);

        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
        }
    }

    private String buildEmailHtml(String title, String body) {
        // Convert body newlines to <br> for HTML
        String htmlBody = body.replace("\n", "<br/>");
        return """
            <!DOCTYPE html>
            <html>
            <body style="background:#0d1117; color:#e6edf3;
                         font-family:'Segoe UI',sans-serif; padding:24px;">
              <div style="max-width:500px; margin:auto;
                          background:#1c2128; border-radius:8px;
                          border:1px solid #30363d; padding:24px;">
                <h2 style="color:#388bfd; margin-top:0;">
                  📈 Trading Intelligence Platform
                </h2>
                <h3 style="color:#e6edf3;">%s</h3>
                <p style="color:#8b949e; line-height:1.6;">%s</p>
                <hr style="border-color:#30363d;"/>
                <p style="color:#484f58; font-size:11px;">
                  This is an automated alert from your Trading Platform.
                </p>
              </div>
            </body>
            </html>
            """.formatted(title, htmlBody);
    }

    // ── Telegram Notification ────────────────────────────────

    public void sendTelegram(String title, String message) {
        if (telegramBot == null) {
            log.warn("Telegram bot not configured.");
            return;
        }
        String formatted = "*" + title + "*\n\n" + message;
        telegramBot.sendAlertMessage(formatted);
    }
}