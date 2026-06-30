package com.mst.matt.tradingplatformapp.service.alert;

import com.mst.matt.tradingplatformapp.model.PriceAlert;
import com.mst.matt.tradingplatformapp.model.PriceAlert.AlertType;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.PriceAlertRepository;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the full alert lifecycle:
 *   create → check (fire or not) → notification dispatch → toggle → delete
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private PriceAlertRepository  alertRepository;
    @Mock private PriceRouter           priceRouter;
    @Mock private NotificationService   notificationService;

    @InjectMocks
    private AlertService alertService;

    private UserProfile profile;
    private PriceAlert  baseAlert;

    @BeforeEach
    void setUp() {
        profile = UserProfile.builder()
                .id(1L)
                .name("Test Profile")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        baseAlert = PriceAlert.builder()
                .id(10L)
                .profile(profile)
                .symbol("BTCUSDT")
                .alertType(AlertType.PRICE_ABOVE)
                .targetPrice(new BigDecimal("70000"))
                .active(true)
                .triggered(false)
                .repeating(false)
                .notifyDesktop(true)
                .notifyEmail(false)
                .notifyTelegram(false)
                .build();
    }

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAlert – delegates to repository and returns saved entity")
    void createAlert_savesAndReturns() {
        when(alertRepository.save(any(PriceAlert.class))).thenReturn(baseAlert);

        PriceAlert saved = alertService.createAlert(baseAlert);

        verify(alertRepository, times(1)).save(baseAlert);
        assertThat(saved).isEqualTo(baseAlert);
    }

    @Test
    @DisplayName("createAlert – alert starts active and not triggered")
    void createAlert_startsActiveAndNotTriggered() {
        PriceAlert newAlert = PriceAlert.builder()
                .profile(profile)
                .symbol("AAPL")
                .alertType(AlertType.PRICE_BELOW)
                .targetPrice(new BigDecimal("150"))
                .active(true)
                .triggered(false)
                .build();

        when(alertRepository.save(any())).thenReturn(newAlert);
        PriceAlert result = alertService.createAlert(newAlert);

        assertThat(result.isActive()).isTrue();
        assertThat(result.isTriggered()).isFalse();
    }

    // ── CHECK / FIRE ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkAlerts – PRICE_ABOVE fires when current price >= target")
    void checkAlerts_priceAbove_fires() {
        PriceQuote quote = buildQuote("BTCUSDT", "75000", "2.5");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(baseAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendDesktop(anyString(), anyString());
        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isTriggered()).isTrue();
        assertThat(captor.getValue().getTriggeredAt()).isNotNull();
        // Non-repeating alert should be deactivated after firing
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("checkAlerts – PRICE_ABOVE does NOT fire when price is below target")
    void checkAlerts_priceAbove_doesNotFire() {
        PriceQuote quote = buildQuote("BTCUSDT", "65000", "1.0");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(baseAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));

        alertService.checkAlerts();

        verify(notificationService, never()).sendDesktop(anyString(), anyString());
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkAlerts – PRICE_BELOW fires when current price <= target")
    void checkAlerts_priceBelow_fires() {
        PriceAlert belowAlert = baseAlert.toBuilder()
                .alertType(AlertType.PRICE_BELOW)
                .targetPrice(new BigDecimal("60000"))
                .build();

        PriceQuote quote = buildQuote("BTCUSDT", "55000", "-3.0");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(belowAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("checkAlerts – PCT_CHANGE_24H fires when change exceeds threshold")
    void checkAlerts_pctChange_fires() {
        PriceAlert pctAlert = baseAlert.toBuilder()
                .alertType(AlertType.PCT_CHANGE_24H)
                .targetPrice(null)
                .percentageThreshold(new BigDecimal("5.0"))
                .build();

        PriceQuote quote = buildQuote("BTCUSDT", "70000", "7.5");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(pctAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("checkAlerts – repeating alert stays active after firing")
    void checkAlerts_repeating_staysActive() {
        PriceAlert repeating = baseAlert.toBuilder().repeating(true).build();
        PriceQuote quote = buildQuote("BTCUSDT", "80000", "5.0");

        when(alertRepository.findByActiveTrue()).thenReturn(List.of(repeating));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();   // still active
        assertThat(captor.getValue().isTriggered()).isTrue(); // but flagged as triggered
    }

    @Test
    @DisplayName("checkAlerts – already-triggered non-repeating alert is skipped")
    void checkAlerts_alreadyTriggered_nonRepeating_skipped() {
        PriceAlert triggered = baseAlert.toBuilder().triggered(true).build();
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(triggered));

        alertService.checkAlerts();

        verify(priceRouter, never()).getQuote(anyString());
        verify(notificationService, never()).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("checkAlerts – no quote available → alert not fired")
    void checkAlerts_noQuote_notFired() {
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(baseAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.empty());

        alertService.checkAlerts();

        verify(notificationService, never()).sendDesktop(anyString(), anyString());
    }

    // ── NOTIFICATION CHANNELS ────────────────────────────────────────────────

    @Test
    @DisplayName("fireAlert – sends email when notifyEmail=true")
    void fireAlert_sendsEmail() {
        PriceAlert emailAlert = baseAlert.toBuilder()
                .notifyEmail(true)
                .notifyDesktop(false)
                .build();
        PriceQuote quote = buildQuote("BTCUSDT", "75000", "2.5");

        when(alertRepository.findByActiveTrue()).thenReturn(List.of(emailAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendEmail(anyString(), anyString());
        verify(notificationService, never()).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("fireAlert – sends Telegram when notifyTelegram=true")
    void fireAlert_sendsTelegram() {
        PriceAlert tgAlert = baseAlert.toBuilder()
                .notifyTelegram(true)
                .notifyDesktop(false)
                .build();
        PriceQuote quote = buildQuote("BTCUSDT", "75000", "2.5");

        when(alertRepository.findByActiveTrue()).thenReturn(List.of(tgAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendTelegram(anyString(), anyString());
    }

    @Test
    @DisplayName("fireAlert – sends all three channels when all enabled")
    void fireAlert_allChannels() {
        PriceAlert allChannels = baseAlert.toBuilder()
                .notifyDesktop(true)
                .notifyEmail(true)
                .notifyTelegram(true)
                .build();
        PriceQuote quote = buildQuote("BTCUSDT", "75000", "2.5");

        when(alertRepository.findByActiveTrue()).thenReturn(List.of(allChannels));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.checkAlerts();

        verify(notificationService).sendDesktop(anyString(), anyString());
        verify(notificationService).sendEmail(anyString(), anyString());
        verify(notificationService).sendTelegram(anyString(), anyString());
    }

    // ── TOGGLE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleAlert – deactivates an active alert")
    void toggleAlert_deactivate() {
        when(alertRepository.findById(10L)).thenReturn(Optional.of(baseAlert));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.toggleAlert(10L, false);

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("toggleAlert – re-activating an alert resets triggered flag")
    void toggleAlert_reactivate_resetsTriggered() {
        PriceAlert triggered = baseAlert.toBuilder().triggered(true).active(false).build();
        when(alertRepository.findById(10L)).thenReturn(Optional.of(triggered));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.toggleAlert(10L, true);

        ArgumentCaptor<PriceAlert> captor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().isTriggered()).isFalse(); // re-armed
    }

    @Test
    @DisplayName("toggleAlert – does nothing when alert ID not found")
    void toggleAlert_notFound_noOp() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        alertService.toggleAlert(99L, false);

        verify(alertRepository, never()).save(any());
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAlert – delegates to repository")
    void deleteAlert_callsRepository() {
        alertService.deleteAlert(10L);
        verify(alertRepository, times(1)).deleteById(10L);
    }

    // ── GET FOR PROFILE ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAlertsForProfile – returns repository result in correct order")
    void getAlertsForProfile_returnsAlerts() {
        List<PriceAlert> expected = List.of(baseAlert);
        when(alertRepository.findByProfileOrderByCreatedAtDesc(profile))
                .thenReturn(expected);

        List<PriceAlert> result = alertService.getAlertsForProfile(profile);

        assertThat(result).isEqualTo(expected);
        verify(alertRepository).findByProfileOrderByCreatedAtDesc(profile);
    }

    // ── INDICATOR ALERT ───────────────────────────────────────────────────────

    @Test
    @DisplayName("triggerIndicatorAlert – fires matching INDICATOR_BUY_SIGNAL alerts")
    void triggerIndicatorAlert_buy_firesMatching() {
        PriceAlert buyAlert = baseAlert.toBuilder()
                .alertType(AlertType.INDICATOR_BUY_SIGNAL)
                .targetPrice(null)
                .repeating(false)
                .triggered(false)
                .build();

        PriceQuote quote = buildQuote("BTCUSDT", "70000", "1.0");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(buyAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertService.triggerIndicatorAlert("BTCUSDT", true);

        verify(notificationService).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("triggerIndicatorAlert – does not fire SELL alert when BUY signal received")
    void triggerIndicatorAlert_buy_doesNotFireSell() {
        PriceAlert sellAlert = baseAlert.toBuilder()
                .alertType(AlertType.INDICATOR_SELL_SIGNAL)
                .targetPrice(null)
                .build();

        PriceQuote quote = buildQuote("BTCUSDT", "70000", "1.0");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(sellAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));

        alertService.triggerIndicatorAlert("BTCUSDT", true); // BUY signal

        verify(notificationService, never()).sendDesktop(anyString(), anyString());
    }

    @Test
    @DisplayName("triggerIndicatorAlert – skips symbol mismatch")
    void triggerIndicatorAlert_symbolMismatch_skipped() {
        PriceAlert ethAlert = baseAlert.toBuilder()
                .symbol("ETHUSDT")
                .alertType(AlertType.INDICATOR_BUY_SIGNAL)
                .targetPrice(null)
                .build();

        PriceQuote quote = buildQuote("BTCUSDT", "70000", "1.0");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(ethAlert));
        when(priceRouter.getQuote("BTCUSDT")).thenReturn(Optional.of(quote));

        alertService.triggerIndicatorAlert("BTCUSDT", true);

        verify(notificationService, never()).sendDesktop(anyString(), anyString());
    }

    // ── CHANNEL VALIDATION ────────────────────────────────────────────────────

    @Test
    @DisplayName("NotificationService.buildChannelWarning – warns when email not configured")
    void channelWarning_emailNotConfigured() {
        // Create a partial mock or just test via NotificationService directly
        NotificationService svc = new NotificationService();
        String warning = svc.buildChannelWarning(true, false);
        // mailSender is null (not injected), so should warn
        assertThat(warning).isNotNull();
        assertThat(warning).contains("Email");
    }

    @Test
    @DisplayName("NotificationService.buildChannelWarning – no warning for desktop-only alert")
    void channelWarning_desktopOnly_noWarning() {
        NotificationService svc = new NotificationService();
        String warning = svc.buildChannelWarning(false, false);
        assertThat(warning).isNull();
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private PriceQuote buildQuote(String symbol, String price, String changePct) {
        return PriceQuote.builder()
                .symbol(symbol)
                .price(new BigDecimal(price))
                .changePct24h(new BigDecimal(changePct))
                .isUp(new BigDecimal(changePct).compareTo(BigDecimal.ZERO) > 0)
                .build();
    }
}
