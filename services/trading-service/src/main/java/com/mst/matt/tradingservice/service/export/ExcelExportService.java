package com.mst.matt.tradingservice.service.export;

import com.mst.matt.tradingservice.dto.FundamentalsReportDto;
import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.model.YearlyFinancialRow;
import com.mst.matt.tradingservice.service.TradeService;
import com.mst.matt.tradingservice.service.TradeService.PortfolioStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a fully styled multi-sheet Excel (.xlsx) report for a user's trade journal.
 *
 * <p>Ported from the desktop monolith's {@code ExcelExportService}.  Adaptations for the
 * microservice environment:</p>
 * <ul>
 *   <li>Accepts {@code Long userId} instead of a {@code UserProfile} entity.</li>
 *   <li>Writes to an {@link OutputStream} (for HTTP streaming) instead of a file path.</li>
 *   <li>Accepts an optional {@link FundamentalsReportDto} for the Sheet 5 context (fetched
 *       from reference-data-service; pass {@code null} to skip the fundamentals sheet).</li>
 * </ul>
 *
 * <h3>Sheets</h3>
 * <ol>
 *   <li>Trade Log — color-coded trade table with borders</li>
 *   <li>Summary Dashboard — KPI cards with merged cells</li>
 *   <li>Asset Breakdown — pivot table with embedded bar chart</li>
 *   <li>Equity Curve — running P&amp;L table with embedded line chart</li>
 *   <li>Fundamentals (optional) — company financials context from reference-data-service</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final TradeService tradeService;

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Color palette (ARGB hex) ───────────────────────────────────────────
    private static final String C_BG_HEADER  = "FF161B22";
    private static final String C_BG_CARD    = "FF1C2128";
    private static final String C_ACCENT     = "FF388BFD";
    private static final String C_GREEN      = "FF3FB950";
    private static final String C_RED        = "FFF85149";
    private static final String C_TEXT_MAIN  = "FFE6EDF3";
    private static final String C_TEXT_DIM   = "FF8B949E";
    private static final String C_ROW_PROFIT = "FF1A2B1A";
    private static final String C_ROW_LOSS   = "FF2B1A1A";
    private static final String C_ROW_OPEN   = "FF1A2030";

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Export a trade-journal Excel report for the given user.
     *
     * @param userId       the user whose trades to export
     * @param profileName  display name used in the summary sheet title
     * @param out          the output stream to write the workbook bytes to (caller closes it)
     * @param fundamentals optional fundamentals context; {@code null} skips Sheet 5
     */
    public void export(Long userId, String profileName,
                       OutputStream out, FundamentalsReportDto fundamentals)
            throws IOException {

        log.info("Exporting Excel report for userId={} profile={}", userId, profileName);

        List<Trade>    trades = tradeService.getTradesForUser(userId);
        PortfolioStats stats  = tradeService.getStats(userId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            StyleKit sk = new StyleKit(wb);

            buildTradeLogSheet(wb, sk, trades);
            buildSummarySheet(wb, sk, stats, profileName);
            buildAssetBreakdownSheet(wb, sk, trades);
            buildEquityCurveSheet(wb, sk, stats);
            if (fundamentals != null) {
                buildFundamentalsSheet(wb, sk, fundamentals);
            }

            wb.write(out);
        }
        log.info("Excel export complete for userId={}", userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHEET 1 — TRADE LOG
    // ═══════════════════════════════════════════════════════════════════════

    private void buildTradeLogSheet(XSSFWorkbook wb, StyleKit sk, List<Trade> trades) {
        XSSFSheet sheet = wb.createSheet("Trade Log");
        sheet.createFreezePane(0, 2);

        // Title row
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(32);
        Cell tc = titleRow.createCell(0);
        tc.setCellValue("Trading Intelligence Platform — Trade Log");
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

        // Header row
        Row hdr = sheet.createRow(1);
        hdr.setHeightInPoints(24);
        String[] headers = {
            "#", "Symbol", "Asset Type", "Direction", "Exchange",
            "Entry Price", "Exit Price", "Quantity", "P&L $",
            "P&L %", "Status", "Entry Time", "Notes"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(sk.headerStyle);
        }

        // Data rows
        int rowIdx = 2;
        for (Trade t : trades) {
            Row row = sheet.createRow(rowIdx);
            row.setHeightInPoints(20);

            boolean profit = t.getPnlAmount() != null
                    && t.getPnlAmount().compareTo(BigDecimal.ZERO) >= 0;
            boolean open   = t.getStatus() == Trade.TradeStatus.OPEN;

            CellStyle rowStyle = open ? sk.openRowStyle
                    : profit ? sk.profitRowStyle : sk.lossRowStyle;

            setCell(row, 0, rowIdx - 1,                 rowStyle);
            setCell(row, 1, t.getSymbol(),              rowStyle);
            setCell(row, 2, t.getAssetType().name(),    rowStyle);

            Cell dirCell = row.createCell(3);
            dirCell.setCellValue(t.getDirection().name());
            dirCell.setCellStyle(t.getDirection() == Trade.TradeDirection.LONG
                    ? sk.longStyle : sk.shortStyle);

            setCell(row, 4, nvl(t.getExchange()),        rowStyle);
            setCell(row, 5, t.getEntryPrice().doubleValue(), sk.priceStyle);
            setCell(row, 6, t.getExitPrice() != null
                    ? t.getExitPrice().doubleValue() : 0.0, sk.priceStyle);
            setCell(row, 7, t.getQuantity().doubleValue(), sk.numStyle);

            Cell pnlCell = row.createCell(8);
            if (t.getPnlAmount() != null) {
                pnlCell.setCellValue(t.getPnlAmount().doubleValue());
                pnlCell.setCellStyle(profit ? sk.posMoneyStyle : sk.negMoneyStyle);
            } else {
                pnlCell.setCellValue("OPEN");
                pnlCell.setCellStyle(sk.openMoneyStyle);
            }

            Cell pctCell = row.createCell(9);
            if (t.getPnlPercent() != null) {
                pctCell.setCellValue(t.getPnlPercent().doubleValue() / 100.0);
                pctCell.setCellStyle(profit ? sk.posPctStyle : sk.negPctStyle);
            }

            setCell(row, 10, t.getStatus().name(),     rowStyle);
            setCell(row, 11, t.getEntryTime() != null
                    ? t.getEntryTime().format(DTF) : "", rowStyle);
            setCell(row, 12, nvl(t.getNotes()),         rowStyle);

            rowIdx++;
        }

        int[] colWidths = {3000,8000,7000,6000,7000,8000,8000,7000,8000,7000,6000,10000,15000};
        for (int i = 0; i < colWidths.length; i++) sheet.setColumnWidth(i, colWidths[i]);
        sheet.setAutoFilter(new CellRangeAddress(1, Math.max(rowIdx - 1, 2), 0, 12));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHEET 2 — SUMMARY DASHBOARD
    // ═══════════════════════════════════════════════════════════════════════

    private void buildSummarySheet(XSSFWorkbook wb, StyleKit sk,
                                   PortfolioStats stats, String profileName) {
        XSSFSheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 12000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 10000);
        sheet.setColumnWidth(4, 10000);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(40);
        Cell tc = title.createCell(0);
        tc.setCellValue("Portfolio Summary — " + profileName
                + "   |   Generated: " + LocalDateTime.now().format(DTF));
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        Object[][] kpis = {
            {"Total P&L",     fmtMoney(stats.getTotalPnl()),
                    "Total Invested", fmtMoney(stats.getTotalInvested())},
            {"Win Rate",      fmtPct(stats.getWinRate()),
                    "Total Trades",   String.valueOf(stats.getTotalTrades())},
            {"Wins / Losses", stats.getWins() + " W / " + stats.getLosses() + " L",
                    "Open Trades",    String.valueOf(stats.getOpenTrades())},
            {"Best Trade",    fmtMoney(stats.getBestTrade()),
                    "Worst Trade",    fmtMoney(stats.getWorstTrade())},
            {"Profit Factor", fmt2(stats.getProfitFactor()),
                    "Avg Win",        fmtMoney(stats.getAvgWin())},
            {"Total Fees",    fmtMoney(stats.getTotalFees()),
                    "Avg Loss",       fmtMoney(stats.getAvgLoss())},
        };

        int r = 2;
        for (Object[] row : kpis) {
            Row dataRow = sheet.createRow(r++);
            dataRow.setHeightInPoints(40);

            Cell lbl1 = dataRow.createCell(1); lbl1.setCellValue((String) row[0]); lbl1.setCellStyle(sk.kpiLabelStyle);
            Cell val1 = dataRow.createCell(2); val1.setCellValue((String) row[1]);
            val1.setCellStyle(isNegative((String) row[1]) ? sk.kpiValueRedStyle : sk.kpiValueGreenStyle);
            Cell lbl2 = dataRow.createCell(3); lbl2.setCellValue((String) row[2]); lbl2.setCellStyle(sk.kpiLabelStyle);
            Cell val2 = dataRow.createCell(4); val2.setCellValue((String) row[3]); val2.setCellStyle(sk.kpiValueStyle);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHEET 3 — ASSET BREAKDOWN + BAR CHART
    // ═══════════════════════════════════════════════════════════════════════

    private void buildAssetBreakdownSheet(XSSFWorkbook wb, StyleKit sk, List<Trade> trades) {
        XSSFSheet sheet = wb.createSheet("Asset Breakdown");

        Row tr = sheet.createRow(0);
        tr.setHeightInPoints(28);
        Cell tc = tr.createCell(0);
        tc.setCellValue("Asset Performance Breakdown");
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        Row hdr = sheet.createRow(1);
        String[] cols = {"Asset Type","Trades","Wins","Losses","Total P&L","Win Rate"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(sk.headerStyle);
            sheet.setColumnWidth(i, 8000);
        }

        Map<String, List<Trade>> byType = trades.stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .collect(Collectors.groupingBy(t -> t.getAssetType().name()));

        int row = 2;
        for (Map.Entry<String, List<Trade>> entry : byType.entrySet()) {
            List<Trade> group = entry.getValue();
            long wins = group.stream().filter(t ->
                    t.getPnlAmount() != null
                    && t.getPnlAmount().compareTo(BigDecimal.ZERO) > 0).count();
            long losses = group.size() - wins;
            BigDecimal totalPnl = group.stream()
                    .filter(t -> t.getPnlAmount() != null)
                    .map(Trade::getPnlAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double winRate = group.isEmpty() ? 0 : (double) wins / group.size() * 100;

            Row dr = sheet.createRow(row++);
            dr.setHeightInPoints(20);
            setCell(dr, 0, entry.getKey(), sk.dataStyle);
            setCell(dr, 1, group.size(),   sk.numStyle);
            setCell(dr, 2, (int) wins,     sk.posNumStyle);
            setCell(dr, 3, (int) losses,   sk.negNumStyle);
            Cell pnlC = dr.createCell(4);
            pnlC.setCellValue(totalPnl.doubleValue());
            pnlC.setCellStyle(totalPnl.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
            Cell wrc = dr.createCell(5);
            wrc.setCellValue(winRate / 100.0);
            wrc.setCellStyle(sk.posPctStyle);
        }

        if (row > 2) {
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, row + 2, 7, row + 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText("P&L by Asset Type");
            chart.setTitleOverlay(false);

            XDDFCategoryAxis  bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis     leftAxis   = chart.createValueAxis(AxisPosition.LEFT);

            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(
                    sheet, new CellRangeAddress(2, row - 1, 0, 0));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory
                    .fromNumericCellRange(sheet, new CellRangeAddress(2, row - 1, 4, 4));

            XDDFBarChartData data = (XDDFBarChartData)
                    chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
            data.setBarDirection(BarDirection.COL);
            XDDFBarChartData.Series series =
                    (XDDFBarChartData.Series) data.addSeries(cats, vals);
            series.setTitle("P&L ($)", null);
            chart.plot(data);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHEET 4 — EQUITY CURVE + LINE CHART
    // ═══════════════════════════════════════════════════════════════════════

    private void buildEquityCurveSheet(XSSFWorkbook wb, StyleKit sk, PortfolioStats stats) {
        XSSFSheet sheet = wb.createSheet("Equity Curve");

        Row tr = sheet.createRow(0);
        tr.setHeightInPoints(28);
        Cell tc = tr.createCell(0);
        tc.setCellValue("Cumulative P&L (Equity Curve)");
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        Row hdr = sheet.createRow(1);
        setCellH(hdr, 0, "Trade #",        sk.headerStyle);
        setCellH(hdr, 1, "Trade P&L ($)",  sk.headerStyle);
        setCellH(hdr, 2, "Cumulative ($)",  sk.headerStyle);
        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 8000);
        sheet.setColumnWidth(2, 10000);

        List<BigDecimal> curve = stats.getEquityCurve();
        int row = 2;
        BigDecimal prev = BigDecimal.ZERO;

        for (int i = 0; i < curve.size(); i++) {
            BigDecimal cum     = curve.get(i);
            BigDecimal tradePnl = cum.subtract(prev);
            prev = cum;

            Row dr = sheet.createRow(row + i);
            dr.setHeightInPoints(18);
            setCell(dr, 0, i + 1, sk.numStyle);
            Cell pnlC = dr.createCell(1);
            pnlC.setCellValue(tradePnl.doubleValue());
            pnlC.setCellStyle(tradePnl.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
            Cell cumC = dr.createCell(2);
            cumC.setCellValue(cum.doubleValue());
            cumC.setCellStyle(cum.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
        }

        if (!curve.isEmpty()) {
            int lastRow = row + curve.size() - 1;
            XSSFDrawing      drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor  = drawing.createAnchor(0, 0, 0, 0, 4, 1, 12, 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText("Equity Curve — Cumulative P&L");
            chart.setTitleOverlay(false);

            XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis    yAxis = chart.createValueAxis(AxisPosition.LEFT);
            xAxis.setTitle("Trade #");
            yAxis.setTitle("P&L ($)");

            XDDFNumericalDataSource<Double> xData = XDDFDataSourcesFactory
                    .fromNumericCellRange(sheet, new CellRangeAddress(row, lastRow, 0, 0));
            XDDFNumericalDataSource<Double> yData = XDDFDataSourcesFactory
                    .fromNumericCellRange(sheet, new CellRangeAddress(row, lastRow, 2, 2));

            XDDFLineChartData lineData = (XDDFLineChartData)
                    chart.createData(ChartTypes.LINE, xAxis, yAxis);
            XDDFLineChartData.Series s =
                    (XDDFLineChartData.Series) lineData.addSeries(xData, yData);
            s.setTitle("Cumulative P&L", null);
            s.setSmooth(true);
            s.setMarkerStyle(MarkerStyle.NONE);
            chart.plot(lineData);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHEET 5 — FUNDAMENTALS (from reference-data-service)
    // ═══════════════════════════════════════════════════════════════════════

    private void buildFundamentalsSheet(XSSFWorkbook wb, StyleKit sk,
                                        FundamentalsReportDto report) {
        XSSFSheet sheet = wb.createSheet("Fundamentals");
        sheet.setDisplayGridlines(false);

        Row title = sheet.createRow(0);
        Cell tc = title.createCell(0);
        tc.setCellValue("Fundamentals — " + (report.getCompanyName() != null
                ? report.getCompanyName() : report.getSymbol()));
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row meta = sheet.createRow(2);
        String[] metaCells = {
                "Symbol: "   + safe(report.getSymbol()),
                "Sector: "   + safe(report.getSector()),
                "Industry: " + safe(report.getIndustry()),
                "Country: "  + safe(report.getCountry()),
                "Provider: " + safe(report.getProviderUsed())
        };
        for (int i = 0; i < metaCells.length; i++) {
            Cell c = meta.createCell(i);
            c.setCellValue(metaCells[i]);
            c.setCellStyle(sk.openRowStyle);
        }

        Row hdr = sheet.createRow(4);
        String[] cols = {"Fiscal Year","Revenue","Gross Profit","Operating Income",
                         "Net Income","EBITDA","Currency"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(sk.headerStyle);
        }

        int rowIdx = 5;
        if (report.getYearlyRows() != null) {
            for (YearlyFinancialRow r : report.getYearlyRows()) {
                Row row = sheet.createRow(rowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(safe(r.getFiscalYear()));
                c0.setCellStyle(sk.openRowStyle);
                writeMoneyCell(row, 1, r.getTotalRevenue(),    sk);
                writeMoneyCell(row, 2, r.getGrossProfit(),     sk);
                writeMoneyCell(row, 3, r.getOperatingIncome(), sk);
                writeMoneyCell(row, 4, r.getNetIncome(),       sk);
                writeMoneyCell(row, 5, r.getEbitda(),          sk);
                Cell c6 = row.createCell(6);
                c6.setCellValue(safe(r.getCurrency()));
                c6.setCellStyle(sk.openRowStyle);
            }
        }

        if (report.getSummaryText() != null && !report.getSummaryText().isBlank()) {
            Row s = sheet.createRow(rowIdx + 1);
            Cell sc = s.createCell(0);
            sc.setCellValue("Summary: " + report.getSummaryText());
            sc.setCellStyle(sk.openRowStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx + 1, rowIdx + 1, 0, 6));
        }

        for (int i = 0; i < 7; i++) sheet.setColumnWidth(i, 4800);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Cell / Style helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static void setCell(Row row, int col, int val, CellStyle style) {
        Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }
    private static void setCell(Row row, int col, double val, CellStyle style) {
        Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }
    private static void setCell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }
    private static void setCellH(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }
    private static void writeMoneyCell(Row row, int col, BigDecimal value, StyleKit sk) {
        Cell c = row.createCell(col);
        if (value == null) { c.setCellValue("-"); c.setCellStyle(sk.openRowStyle); }
        else { c.setCellValue(value.doubleValue()); c.setCellStyle(sk.priceStyle); }
    }

    private static String nvl(String s)  { return s != null ? s : ""; }
    private static String safe(String s) { return s == null || s.isBlank() ? "-" : s; }

    // ── Formatting helpers ────────────────────────────────────────────────
    private static String fmtMoney(BigDecimal v) {
        if (v == null) return "$0.00";
        return String.format("%s$%,.2f", v.compareTo(BigDecimal.ZERO) < 0 ? "-" : "",
                v.abs());
    }
    private static String fmtPct(BigDecimal v) {
        return v == null ? "0.0%" : String.format("%.1f%%", v.doubleValue());
    }
    private static String fmt2(BigDecimal v)   {
        return v == null ? "0.00" : String.format("%.2f", v.doubleValue());
    }
    private static boolean isNegative(String s) {
        return s != null && s.startsWith("-");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  StyleKit — Central style factory
    // ═══════════════════════════════════════════════════════════════════════

    private static class StyleKit {
        final CellStyle titleStyle, headerStyle, dataStyle;
        final CellStyle profitRowStyle, lossRowStyle, openRowStyle;
        final CellStyle posMoneyStyle, negMoneyStyle, openMoneyStyle;
        final CellStyle posPctStyle, negPctStyle;
        final CellStyle longStyle, shortStyle;
        final CellStyle numStyle, posNumStyle, negNumStyle;
        final CellStyle kpiLabelStyle, kpiValueStyle, kpiValueGreenStyle, kpiValueRedStyle;
        final CellStyle priceStyle;

        StyleKit(XSSFWorkbook wb) {
            short moneyfmt = wb.createDataFormat().getFormat("$#,##0.00;-$#,##0.00");
            short pctfmt   = wb.createDataFormat().getFormat("0.00%");
            short numfmt   = wb.createDataFormat().getFormat("#,##0");

            titleStyle = base(wb); font(wb, titleStyle, C_ACCENT, 16, true);
            fill(titleStyle, C_BG_HEADER); titleStyle.setAlignment(HorizontalAlignment.LEFT);

            headerStyle = base(wb); font(wb, headerStyle, C_TEXT_DIM, 11, true);
            fill(headerStyle, C_BG_HEADER); border(headerStyle);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            dataStyle = base(wb); font(wb, dataStyle, C_TEXT_MAIN, 11, false);
            fill(dataStyle, C_BG_CARD); border(dataStyle);

            profitRowStyle = base(wb); font(wb, profitRowStyle, C_TEXT_MAIN, 11, false);
            fill(profitRowStyle, C_ROW_PROFIT); border(profitRowStyle);

            lossRowStyle = base(wb); font(wb, lossRowStyle, C_TEXT_MAIN, 11, false);
            fill(lossRowStyle, C_ROW_LOSS); border(lossRowStyle);

            openRowStyle = base(wb); font(wb, openRowStyle, C_TEXT_MAIN, 11, false);
            fill(openRowStyle, C_ROW_OPEN); border(openRowStyle);

            posMoneyStyle = base(wb); font(wb, posMoneyStyle, C_GREEN, 11, true);
            fill(posMoneyStyle, C_ROW_PROFIT); posMoneyStyle.setDataFormat(moneyfmt); border(posMoneyStyle);

            negMoneyStyle = base(wb); font(wb, negMoneyStyle, C_RED, 11, true);
            fill(negMoneyStyle, C_ROW_LOSS); negMoneyStyle.setDataFormat(moneyfmt); border(negMoneyStyle);

            openMoneyStyle = base(wb); font(wb, openMoneyStyle, C_TEXT_MAIN, 11, false);
            fill(openMoneyStyle, C_ROW_OPEN); border(openMoneyStyle);

            posPctStyle = base(wb); font(wb, posPctStyle, C_GREEN, 11, false);
            fill(posPctStyle, C_ROW_PROFIT); posPctStyle.setDataFormat(pctfmt); border(posPctStyle);

            negPctStyle = base(wb); font(wb, negPctStyle, C_RED, 11, false);
            fill(negPctStyle, C_ROW_LOSS); negPctStyle.setDataFormat(pctfmt); border(negPctStyle);

            longStyle = base(wb); font(wb, longStyle, C_GREEN, 11, true);
            fill(longStyle, C_ROW_PROFIT); border(longStyle);

            shortStyle = base(wb); font(wb, shortStyle, C_RED, 11, true);
            fill(shortStyle, C_ROW_LOSS); border(shortStyle);

            numStyle = base(wb); font(wb, numStyle, C_TEXT_MAIN, 11, false);
            fill(numStyle, C_BG_CARD); numStyle.setDataFormat(numfmt); border(numStyle);

            posNumStyle = base(wb); font(wb, posNumStyle, C_GREEN, 11, false);
            fill(posNumStyle, C_BG_CARD); border(posNumStyle);

            negNumStyle = base(wb); font(wb, negNumStyle, C_RED, 11, false);
            fill(negNumStyle, C_BG_CARD); border(negNumStyle);

            kpiLabelStyle = base(wb); font(wb, kpiLabelStyle, C_TEXT_DIM, 12, false);
            fill(kpiLabelStyle, C_BG_CARD); border(kpiLabelStyle);
            kpiLabelStyle.setAlignment(HorizontalAlignment.RIGHT);

            kpiValueStyle = base(wb); font(wb, kpiValueStyle, C_TEXT_MAIN, 13, true);
            fill(kpiValueStyle, C_BG_CARD); border(kpiValueStyle);

            kpiValueGreenStyle = base(wb); font(wb, kpiValueGreenStyle, C_GREEN, 13, true);
            fill(kpiValueGreenStyle, C_BG_CARD); border(kpiValueGreenStyle);

            kpiValueRedStyle = base(wb); font(wb, kpiValueRedStyle, C_RED, 13, true);
            fill(kpiValueRedStyle, C_BG_CARD); border(kpiValueRedStyle);

            priceStyle = base(wb); font(wb, priceStyle, C_TEXT_MAIN, 11, false);
            fill(priceStyle, C_BG_CARD); priceStyle.setDataFormat(moneyfmt); border(priceStyle);
        }

        private static CellStyle base(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }
        private static void font(XSSFWorkbook wb, CellStyle s, String argb, int sz, boolean bold) {
            XSSFFont f = wb.createFont();
            f.setColor(new XSSFColor(hexToBytes(argb), null));
            f.setFontHeightInPoints((short) sz);
            f.setBold(bold);
            s.setFont(f);
        }
        private static void fill(CellStyle s, String argb) {
            ((XSSFCellStyle) s).setFillForegroundColor(
                    new XSSFColor(hexToBytes(argb), null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        private static void border(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
        private static byte[] hexToBytes(String argb) {
            // ARGB → RGBA (POI uses RGBA)
            long v = Long.parseLong(argb, 16);
            return new byte[]{
                    (byte)((v >> 16) & 0xFF),
                    (byte)((v >> 8)  & 0xFF),
                    (byte)( v        & 0xFF)
            };
        }
    }
}
