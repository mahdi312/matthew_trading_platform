package com.mst.matt.tradingplatformapp.service.export;

import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.TradeService;
import com.mst.matt.tradingplatformapp.service.TradeService.PortfolioStats;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a fully styled multi-sheet Excel report.
 *
 * Sheet 1: Trade Log       — color-coded trade table with borders
 * Sheet 2: Summary         — dashboard-style KPI cards with merged cells
 * Sheet 3: Asset Breakdown — pivot table with embedded bar chart
 * Sheet 4: Equity Curve    — running P&L table with embedded line chart
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    @Autowired private TradeService tradeService;

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Color palette (ARGB hex) ────────────────────────────
    private static final String C_BG_DARK    = "FF0D1117";
    private static final String C_BG_CARD    = "FF1C2128";
    private static final String C_BG_HEADER  = "FF161B22";
    private static final String C_ACCENT     = "FF388BFD";
    private static final String C_GREEN      = "FF3FB950";
    private static final String C_RED        = "FFF85149";
    private static final String C_YELLOW     = "FFD29922";
    private static final String C_TEXT_MAIN  = "FFE6EDF3";
    private static final String C_TEXT_DIM   = "FF8B949E";
    private static final String C_BORDER     = "FF30363D";
    private static final String C_ROW_PROFIT = "FF1A2B1A";
    private static final String C_ROW_LOSS   = "FF2B1A1A";
    private static final String C_ROW_OPEN   = "FF1A2030";

    /**
     * Main export method.
     * @param profile     Active user profile
     * @param outputPath  Full file path e.g. "/home/user/report.xlsx"
     */
    public void export(UserProfile profile, String outputPath) throws IOException {
        log.info("Exporting Excel report for profile: {}", profile.getName());

        List<Trade>     trades = tradeService.getTradesForProfile(profile);
        PortfolioStats  stats  = tradeService.getStats(profile);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // Sheet styles
            StyleKit styles = new StyleKit(wb);

            // ── Sheet 1: Trade Log ──────────────────────────
            buildTradeLogSheet(wb, styles, trades);

            // ── Sheet 2: Summary Dashboard ──────────────────
            buildSummarySheet(wb, styles, stats, profile);

            // ── Sheet 3: Asset Breakdown ─────────────────────
            buildAssetBreakdownSheet(wb, styles, trades);

            // ── Sheet 4: Equity Curve ────────────────────────
            buildEquityCurveSheet(wb, styles, stats);

            // Write file
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                wb.write(fos);
            }
            log.info("Excel export complete: {}", outputPath);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SHEET 1 — TRADE LOG
    // ══════════════════════════════════════════════════════════

    private void buildTradeLogSheet(XSSFWorkbook wb, StyleKit sk,
                                    List<Trade> trades) {
        XSSFSheet sheet = wb.createSheet("📋 Trade Log");
        sheet.setTabColor(new XSSFColor(new byte[]{(byte)0x38,(byte)0x8B,(byte)0xFD}, null));

        // Freeze top row
        sheet.createFreezePane(0, 2);

        // ── Title row ────────────────────────────────────────
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(32);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("📈 Trading Intelligence Platform — Trade Log");
        titleCell.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

        // ── Header row ────────────────────────────────────────
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(24);
        String[] headers = {
                "#", "Symbol", "Asset Type", "Direction", "Exchange",
                "Entry Price", "Exit Price", "Quantity", "P&L $",
                "P&L %", "Status", "Entry Time", "Notes"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(sk.headerStyle);
        }

        // ── Data rows ─────────────────────────────────────────
        int rowIdx = 2;
        for (Trade t : trades) {
            Row row = sheet.createRow(rowIdx);
            row.setHeightInPoints(20);

            boolean profit = t.getPnlAmount() != null
                    && t.getPnlAmount().compareTo(BigDecimal.ZERO) >= 0;
            boolean open   = t.getStatus() == Trade.TradeStatus.OPEN;

            CellStyle rowStyle = open ? sk.openRowStyle
                    : profit ? sk.profitRowStyle : sk.lossRowStyle;

            setCell(row, 0, rowIdx - 1,        rowStyle);
            setCell(row, 1, t.getSymbol(),     rowStyle);
            setCell(row, 2, t.getAssetType().name(), rowStyle);

            Cell dirCell = row.createCell(3);
            dirCell.setCellValue(t.getDirection().name());
            dirCell.setCellStyle(t.getDirection() == Trade.TradeDirection.LONG
                    ? sk.longStyle : sk.shortStyle);

            setCell(row, 4, nvl(t.getExchange()),      rowStyle);
            setCell(row, 5, t.getEntryPrice().doubleValue(), sk.priceStyle);
            setCell(row, 6, t.getExitPrice() != null
                    ? t.getExitPrice().doubleValue() : 0.0, sk.priceStyle);
            setCell(row, 7, t.getQuantity().doubleValue(),   sk.numStyle);

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

            setCell(row, 10, t.getStatus().name(), rowStyle);
            setCell(row, 11, t.getEntryTime() != null
                    ? t.getEntryTime().format(DTF) : "", rowStyle);
            setCell(row, 12, nvl(t.getNotes()), rowStyle);

            rowIdx++;
        }

        // ── Auto-size columns ─────────────────────────────────
        int[] colWidths = {
                3000,8000,7000,6000,7000,8000,8000,7000,
                8000,7000,6000,10000,15000
        };
        for (int i = 0; i < colWidths.length; i++)
            sheet.setColumnWidth(i, colWidths[i]);

        // ── Auto-filter ───────────────────────────────────────
        sheet.setAutoFilter(new CellRangeAddress(1, rowIdx - 1, 0, 12));
    }

    // ══════════════════════════════════════════════════════════
    //  SHEET 2 — SUMMARY DASHBOARD
    // ══════════════════════════════════════════════════════════

    private void buildSummarySheet(XSSFWorkbook wb, StyleKit sk,
                                   PortfolioStats stats, UserProfile profile) {
        XSSFSheet sheet = wb.createSheet("📊 Summary");
        sheet.setTabColor(new XSSFColor(new byte[]{(byte)0x3F,(byte)0xB9,(byte)0x50}, null));
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 12000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 10000);
        sheet.setColumnWidth(4, 10000);

        // Title
        Row title = sheet.createRow(0);
        title.setHeightInPoints(40);
        Cell tc = title.createCell(0);
        tc.setCellValue("📈 Portfolio Summary — " + profile.getName()
                + "   |   Generated: "
                + LocalDateTime.now().format(DTF));
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // KPI cards (2×3 grid)
        Object[][] kpis = {
                {"Total P&L",      fmtMoney(stats.getTotalPnl()),
                        "Total Invested", fmtMoney(stats.getTotalInvested())},
                {"Win Rate",       fmtPct(stats.getWinRate()),
                        "Total Trades",   String.valueOf(stats.getTotalTrades())},
                {"Wins / Losses",  stats.getWins() + " W / " + stats.getLosses() + " L",
                        "Open Trades",    String.valueOf(stats.getOpenTrades())},
                {"Best Trade",     fmtMoney(stats.getBestTrade()),
                        "Worst Trade",    fmtMoney(stats.getWorstTrade())},
                {"Profit Factor",  fmt2(stats.getProfitFactor()),
                        "Avg Win",        fmtMoney(stats.getAvgWin())},
                {"Total Fees",     fmtMoney(stats.getTotalFees()),
                        "Avg Loss",       fmtMoney(stats.getAvgLoss())},
        };

        int r = 2;
        for (Object[] row : kpis) {
            Row dataRow = sheet.createRow(r);
            dataRow.setHeightInPoints(40);

            Cell lbl1 = dataRow.createCell(1);
            lbl1.setCellValue((String) row[0]);
            lbl1.setCellStyle(sk.kpiLabelStyle);

            Cell val1 = dataRow.createCell(2);
            val1.setCellValue((String) row[1]);
            val1.setCellStyle(isNegative((String) row[1])
                    ? sk.kpiValueRedStyle : sk.kpiValueGreenStyle);

            Cell lbl2 = dataRow.createCell(3);
            lbl2.setCellValue((String) row[2]);
            lbl2.setCellStyle(sk.kpiLabelStyle);

            Cell val2 = dataRow.createCell(4);
            val2.setCellValue((String) row[3]);
            val2.setCellStyle(sk.kpiValueStyle);

            r++;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SHEET 3 — ASSET BREAKDOWN + BAR CHART
    // ══════════════════════════════════════════════════════════

    private void buildAssetBreakdownSheet(XSSFWorkbook wb, StyleKit sk,
                                          List<Trade> trades) {
        XSSFSheet sheet = wb.createSheet("🗂 Asset Breakdown");

        // Title
        Row tr = sheet.createRow(0);
        tr.setHeightInPoints(28);
        Cell tc = tr.createCell(0);
        tc.setCellValue("Asset Performance Breakdown");
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0,0,0,5));

        // Header
        Row hdr = sheet.createRow(1);
        String[] cols = {"Asset Type","Trades","Wins","Losses","Total P&L","Win Rate"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(sk.headerStyle);
            sheet.setColumnWidth(i, 8000);
        }

        // Group trades by asset type
        Map<String, List<Trade>> byType = trades.stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .collect(Collectors.groupingBy(
                        t -> t.getAssetType().name()));

        int row = 2;
        for (Map.Entry<String, List<Trade>> entry : byType.entrySet()) {
            List<Trade> group  = entry.getValue();
            long wins          = group.stream().filter(t ->
                    t.getPnlAmount() != null
                            && t.getPnlAmount().compareTo(BigDecimal.ZERO) > 0).count();
            long losses        = group.size() - wins;
            BigDecimal totalPnl = group.stream()
                    .filter(t -> t.getPnlAmount() != null)
                    .map(Trade::getPnlAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double winRate     = group.isEmpty() ? 0
                    : (double) wins / group.size() * 100;

            Row dr = sheet.createRow(row++);
            dr.setHeightInPoints(20);
            setCell(dr, 0, entry.getKey(),      sk.dataStyle);
            setCell(dr, 1, group.size(),         sk.numStyle);
            setCell(dr, 2, (int) wins,           sk.posNumStyle);
            setCell(dr, 3, (int) losses,         sk.negNumStyle);
            Cell pnlC = dr.createCell(4);
            pnlC.setCellValue(totalPnl.doubleValue());
            pnlC.setCellStyle(totalPnl.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
            Cell wrc = dr.createCell(5);
            wrc.setCellValue(winRate / 100.0);
            wrc.setCellStyle(sk.posPctStyle);
        }

        // ── Embedded Bar Chart ────────────────────────────────
        if (row > 2) {
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(
                    0, 0, 0, 0, 0, row + 2, 7, row + 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText("P&L by Asset Type");
            chart.setTitleOverlay(false);

            XDDFCategoryAxis  bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis     leftAxis   = chart.createValueAxis(AxisPosition.LEFT);

            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(
                    sheet, new CellRangeAddress(2, row - 1, 0, 0));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory
                    .fromNumericCellRange(sheet,
                            new CellRangeAddress(2, row - 1, 4, 4));

            XDDFBarChartData data = (XDDFBarChartData)
                    chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
            data.setBarDirection(BarDirection.COL);
            XDDFBarChartData.Series series =
                    (XDDFBarChartData.Series) data.addSeries(cats, vals);
            series.setTitle("P&L ($)", null);

            chart.plot(data);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SHEET 4 — EQUITY CURVE + LINE CHART
    // ══════════════════════════════════════════════════════════

    private void buildEquityCurveSheet(XSSFWorkbook wb, StyleKit sk,
                                       PortfolioStats stats) {
        XSSFSheet sheet = wb.createSheet("📈 Equity Curve");

        Row tr = sheet.createRow(0);
        tr.setHeightInPoints(28);
        Cell tc = tr.createCell(0);
        tc.setCellValue("Cumulative P&L (Equity Curve)");
        tc.setCellStyle(sk.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0,0,0,2));

        // Header
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
            BigDecimal cum = curve.get(i);
            BigDecimal tradePnl = cum.subtract(prev);
            prev = cum;

            Row dr  = sheet.createRow(row + i);
            dr.setHeightInPoints(18);
            setCell(dr, 0, i + 1,              sk.numStyle);
            Cell pnlC = dr.createCell(1);
            pnlC.setCellValue(tradePnl.doubleValue());
            pnlC.setCellStyle(tradePnl.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
            Cell cumC = dr.createCell(2);
            cumC.setCellValue(cum.doubleValue());
            cumC.setCellStyle(cum.compareTo(BigDecimal.ZERO) >= 0
                    ? sk.posMoneyStyle : sk.negMoneyStyle);
        }

        // ── Embedded Line Chart ───────────────────────────────
        if (!curve.isEmpty()) {
            int lastRow = row + curve.size() - 1;
            XSSFDrawing   drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(
                    0, 0, 0, 0, 4, 1, 12, 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText("Equity Curve — Cumulative P&L");
            chart.setTitleOverlay(false);

            XDDFCategoryAxis  xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis     yAxis = chart.createValueAxis(AxisPosition.LEFT);
            xAxis.setTitle("Trade #");
            yAxis.setTitle("P&L ($)");

            XDDFNumericalDataSource<Double> xData =
                    XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                            new CellRangeAddress(row, lastRow, 0, 0));
            XDDFNumericalDataSource<Double> yData =
                    XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                            new CellRangeAddress(row, lastRow, 2, 2));

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

    // ══════════════════════════════════════════════════════════
    //  StyleKit — Central style factory
    // ══════════════════════════════════════════════════════════

    private static class StyleKit {
        final CellStyle titleStyle, headerStyle, dataStyle;
        final CellStyle profitRowStyle, lossRowStyle, openRowStyle;
        final CellStyle posMoneyStyle, negMoneyStyle, openMoneyStyle;
        final CellStyle posPctStyle, negPctStyle;
        final CellStyle longStyle, shortStyle;
        final CellStyle numStyle, posNumStyle, negNumStyle;
        final CellStyle kpiLabelStyle, kpiValueStyle,
                kpiValueGreenStyle, kpiValueRedStyle;
        final CellStyle priceStyle;

        StyleKit(XSSFWorkbook wb) {
            // Title
            titleStyle = createBase(wb);
            setFont(wb, titleStyle, C_ACCENT, 16, true);
            setFill(titleStyle, C_BG_HEADER);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);

            // Header
            headerStyle = createBase(wb);
            setFont(wb, headerStyle, C_TEXT_DIM, 11, true);
            setFill(headerStyle, C_BG_HEADER);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(headerStyle);

            // Data
            dataStyle = createBase(wb);
            setFont(wb, dataStyle, C_TEXT_MAIN, 11, false);
            setFill(dataStyle, C_BG_CARD);
            setBorder(dataStyle);

            // Row styles
            profitRowStyle = createBase(wb);
            setFont(wb, profitRowStyle, C_TEXT_MAIN, 11, false);
            setFill(profitRowStyle, C_ROW_PROFIT);
            setBorder(profitRowStyle);

            lossRowStyle = createBase(wb);
            setFont(wb, lossRowStyle, C_TEXT_MAIN, 11, false);
            setFill(lossRowStyle, C_ROW_LOSS);
            setBorder(lossRowStyle);

            openRowStyle = createBase(wb);
            setFont(wb, openRowStyle, C_TEXT_MAIN, 11, false);
            setFill(openRowStyle, C_ROW_OPEN);
            setBorder(openRowStyle);

            // Money
            posMoneyStyle = createBase(wb);
            setFont(wb, posMoneyStyle, C_GREEN, 11, true);
            setFill(posMoneyStyle, C_ROW_PROFIT);
            posMoneyStyle.setDataFormat(wb.createDataFormat()
                    .getFormat("$#,##0.00;-$#,##0.00"));
            setBorder(posMoneyStyle);

            negMoneyStyle = createBase(wb);
            setFont(wb, negMoneyStyle, C_RED, 11, true);
            setFill(negMoneyStyle, C_ROW_LOSS);
            negMoneyStyle.setDataFormat(wb.createDataFormat()
                    .getFormat("$#,##0.00;-$#,##0.00"));
            setBorder(negMoneyStyle);

            openMoneyStyle = createBase(wb);
            setFont(wb, openMoneyStyle, C_YELLOW, 11, false);
            setFill(openMoneyStyle, C_ROW_OPEN);
            setBorder(openMoneyStyle);

            // Pct
            posPctStyle = createBase(wb);
            setFont(wb, posPctStyle, C_GREEN, 11, false);
            posPctStyle.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            setFill(posPctStyle, C_ROW_PROFIT);
            setBorder(posPctStyle);

            negPctStyle = createBase(wb);
            setFont(wb, negPctStyle, C_RED, 11, false);
            negPctStyle.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            setFill(negPctStyle, C_ROW_LOSS);
            setBorder(negPctStyle);

            // Direction
            longStyle = createBase(wb);
            setFont(wb, longStyle, C_GREEN, 11, true);
            setFill(longStyle, C_ROW_PROFIT);
            longStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(longStyle);

            shortStyle = createBase(wb);
            setFont(wb, shortStyle, C_RED, 11, true);
            setFill(shortStyle, C_ROW_LOSS);
            shortStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(shortStyle);

            // Numeric
            numStyle = createBase(wb);
            setFont(wb, numStyle, C_TEXT_MAIN, 11, false);
            setFill(numStyle, C_BG_CARD);
            numStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(numStyle);

            posNumStyle = createBase(wb);
            setFont(wb, posNumStyle, C_GREEN, 11, false);
            setFill(posNumStyle, C_ROW_PROFIT);
            posNumStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(posNumStyle);

            negNumStyle = createBase(wb);
            setFont(wb, negNumStyle, C_RED, 11, false);
            setFill(negNumStyle, C_ROW_LOSS);
            negNumStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(negNumStyle);

            // Price
            priceStyle = createBase(wb);
            setFont(wb, priceStyle, C_TEXT_MAIN, 11, false);
            setFill(priceStyle, C_BG_CARD);
            priceStyle.setDataFormat(wb.createDataFormat()
                    .getFormat("#,##0.########"));
            priceStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(priceStyle);

            // KPI
            kpiLabelStyle = createBase(wb);
            setFont(wb, kpiLabelStyle, C_TEXT_DIM, 12, false);
            setFill(kpiLabelStyle, C_BG_HEADER);
            kpiLabelStyle.setAlignment(HorizontalAlignment.RIGHT);

            kpiValueStyle = createBase(wb);
            setFont(wb, kpiValueStyle, C_TEXT_MAIN, 18, true);
            setFill(kpiValueStyle, C_BG_CARD);
            kpiValueStyle.setAlignment(HorizontalAlignment.CENTER);
            kpiValueStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            kpiValueGreenStyle = createBase(wb);
            setFont(wb, kpiValueGreenStyle, C_GREEN, 18, true);
            setFill(kpiValueGreenStyle, C_ROW_PROFIT);
            kpiValueGreenStyle.setAlignment(HorizontalAlignment.CENTER);

            kpiValueRedStyle = createBase(wb);
            setFont(wb, kpiValueRedStyle, C_RED, 18, true);
            setFill(kpiValueRedStyle, C_ROW_LOSS);
            kpiValueRedStyle.setAlignment(HorizontalAlignment.CENTER);
        }

        private CellStyle createBase(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(false);
            return s;
        }

        private void setFont(XSSFWorkbook wb, CellStyle style,
                             String argbColor, int size, boolean bold) {
            XSSFFont font = wb.createFont();
            font.setColor(new XSSFColor(
                    hexToBytes(argbColor), null));
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setFontName("Calibri");
            style.setFont(font);
        }

        private void setFill(CellStyle style, String argbColor) {
            ((XSSFCellStyle) style).setFillForegroundColor(
                    new XSSFColor(hexToBytes(argbColor), null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private void setBorder(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            ((XSSFCellStyle) style).setBottomBorderColor(
                    new XSSFColor(hexToBytes(C_BORDER), null));
            ((XSSFCellStyle) style).setTopBorderColor(
                    new XSSFColor(hexToBytes(C_BORDER), null));
            ((XSSFCellStyle) style).setLeftBorderColor(
                    new XSSFColor(hexToBytes(C_BORDER), null));
            ((XSSFCellStyle) style).setRightBorderColor(
                    new XSSFColor(hexToBytes(C_BORDER), null));
        }

        private byte[] hexToBytes(String argb) {
            return new byte[]{
                    (byte) Long.parseLong(argb.substring(0,2), 16),
                    (byte) Long.parseLong(argb.substring(2,4), 16),
                    (byte) Long.parseLong(argb.substring(4,6), 16),
                    (byte) Long.parseLong(argb.substring(6,8), 16)
            };
        }
    }

    // ── Cell helpers ─────────────────────────────────────────

    private void setCell(Row row, int col, Object val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val instanceof String)  c.setCellValue((String) val);
        if (val instanceof Integer) c.setCellValue((Integer) val);
        if (val instanceof Double)  c.setCellValue((Double) val);
        if (val instanceof Long)    c.setCellValue((Long) val);
        c.setCellStyle(style);
    }

    private void setCellH(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    private String fmtMoney(BigDecimal v) {
        if (v == null) return "$0.00";
        return (v.compareTo(BigDecimal.ZERO) >= 0 ? "$" : "-$")
                + v.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String fmtPct(BigDecimal v) {
        if (v == null) return "0.00%";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String fmt2(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean isNegative(String s) {
        return s != null && s.startsWith("-");
    }

    private String nvl(String s) { return s != null ? s : ""; }
}