package com.mst.matt.tradingservice.controller;

import com.mst.matt.tradingservice.dto.FundamentalsReportDto;
import com.mst.matt.tradingservice.service.YearlyReportService;
import com.mst.matt.tradingservice.service.YearlyReportService.YearlyPnlRow;
import com.mst.matt.tradingservice.service.export.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for trade-journal reports and Excel exports.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/reports/yearly}         — per-year P&L summary rows for the caller</li>
 *   <li>{@code GET /api/reports/export.xlsx}    — streams a full Excel workbook</li>
 * </ul>
 *
 * <p>The {@code userId} is extracted from the JWT {@code userId} claim forwarded by the
 * gateway.  Trading-service does not have a direct dependency on identity-service's JPA
 * layer; it reads the claim from the request header {@code X-User-Id} that the gateway
 * injects after validating the JWT.</p>
 *
 * <p>For any fundamentals data needed by the yearly report (e.g. company financials
 * context in the Excel Sheet 5), this controller calls
 * {@link YearlyReportService#getFundamentalsContext(String, String)} which in turn calls
 * reference-data-service's {@code FundamentalsProvider} API over HTTP — no logic is
 * duplicated here.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final YearlyReportService yearlyReportService;
    private final ExcelExportService  excelExportService;

    // ── GET /api/reports/yearly ───────────────────────────────────────────────

    /**
     * Returns a list of per-year P&L aggregation rows for the authenticated user.
     *
     * <p>Optionally enriched with fundamentals context for the given symbol; when
     * {@code symbol} is provided the controller appends a {@code fundamentals} field
     * to the response by calling reference-data-service over HTTP.</p>
     *
     * @param userId   user ID injected by the API gateway from the JWT claim
     * @param symbol   optional equity symbol for fundamentals context (e.g. "AAPL")
     * @param provider optional fundamentals provider override (e.g. "FINNHUB")
     */
    @GetMapping("/yearly")
    public YearlyReportResponse getYearlyReport(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestParam(value = "symbol",   required = false) String symbol,
            @RequestParam(value = "provider", required = false) String provider) {

        List<YearlyPnlRow> rows = yearlyReportService.getYearlyPnl(userId);

        // Optionally fetch fundamentals context for the given symbol
        FundamentalsReportDto fundamentals = null;
        if (symbol != null && !symbol.isBlank()) {
            fundamentals = yearlyReportService.getFundamentalsContext(symbol, provider);
        }

        return new YearlyReportResponse(rows, fundamentals);
    }

    // ── GET /api/reports/export.xlsx ─────────────────────────────────────────

    /**
     * Streams a fully styled multi-sheet Excel (.xlsx) workbook for the authenticated user.
     *
     * <p>Optionally includes a Sheet 5 fundamentals snapshot when {@code symbol} is provided.
     * The fundamentals data is fetched from reference-data-service on the fly.</p>
     *
     * @param userId      user ID injected by the API gateway from the JWT claim
     * @param profileName display name used in the summary sheet title (defaults to "My Portfolio")
     * @param symbol      optional equity symbol for fundamentals sheet (e.g. "AAPL")
     * @param provider    optional fundamentals provider override
     * @param response    the HTTP response to stream the workbook bytes into
     */
    @GetMapping("/export.xlsx")
    public void exportExcel(
            @RequestHeader(value = "X-User-Id",    required = false, defaultValue = "0") Long userId,
            @RequestParam(value = "profileName",   required = false, defaultValue = "My Portfolio") String profileName,
            @RequestParam(value = "symbol",         required = false) String symbol,
            @RequestParam(value = "provider",       required = false) String provider,
            HttpServletResponse response) throws IOException {

        String filename = "trade-report-" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        // Fetch fundamentals if symbol is provided
        FundamentalsReportDto fundamentals = null;
        if (symbol != null && !symbol.isBlank()) {
            fundamentals = yearlyReportService.getFundamentalsContext(symbol, provider);
        }

        log.info("Streaming Excel export for userId={} profile={} symbol={}", userId, profileName, symbol);
        excelExportService.export(userId, profileName, response.getOutputStream(), fundamentals);
    }

    // ── Response wrapper ──────────────────────────────────────────────────────

    /**
     * Combined response for {@code GET /api/reports/yearly} — trade P&L rows + optional fundamentals.
     */
    public record YearlyReportResponse(
            List<YearlyPnlRow> yearlyPnl,
            FundamentalsReportDto fundamentals
    ) {}
}
