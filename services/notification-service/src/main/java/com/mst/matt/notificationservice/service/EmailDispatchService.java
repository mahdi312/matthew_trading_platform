package com.mst.matt.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * SMTP email-dispatch service for {@code notification-service}.
 *
 * <p>Ported and adapted from the desktop monolith's
 * {@code service/alert/NotificationService.sendEmail} — the JavaFX
 * AWT/SystemTray code is stripped (not meaningful in a headless service)
 * and the bean is made conditional on {@code spring.mail.host} being set,
 * so the service starts cleanly without SMTP credentials in local dev.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * emailDispatchService.send("Alert fired: BTCUSDT above 50000",
 *                            "Your BTCUSDT alert triggered at $50,123.");
 * </pre>
 *
 * <h3>Configuration (application.yml / env)</h3>
 * <ul>
 *   <li>{@code spring.mail.host} — SMTP host (e.g. smtp.gmail.com)</li>
 *   <li>{@code spring.mail.port} — SMTP port (e.g. 587)</li>
 *   <li>{@code spring.mail.username} — sender / from address</li>
 *   <li>{@code spring.mail.password} — SMTP password / app-password</li>
 *   <li>{@code notification.email.to} — recipient address(es); comma-separated</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailDispatchService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${notification.email.to:}")
    private String toEmail;

    public EmailDispatchService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Returns {@code true} if SMTP is fully configured (sender + recipient set).
     */
    public boolean isConfigured() {
        return fromEmail != null && !fromEmail.isBlank()
                && toEmail != null && !toEmail.isBlank();
    }

    /**
     * Sends an HTML email notification.
     *
     * @param subject email subject (the "🔔 " prefix is added automatically)
     * @param body    plain-text body — newlines are converted to {@code <br/>}
     */
    public void send(String subject, String body) {
        if (!isConfigured()) {
            log.warn("[EmailDispatch] SMTP not fully configured (from={} to={}) — skipping",
                    fromEmail, toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🔔 " + subject);
            helper.setText(buildHtml(subject, body), true);
            mailSender.send(message);
            log.info("[EmailDispatch] sent: subject={}", subject);
        } catch (Exception ex) {
            log.error("[EmailDispatch] failed: subject={} error={}", subject, ex.getMessage(), ex);
        }
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private static String buildHtml(String title, String body) {
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
}
