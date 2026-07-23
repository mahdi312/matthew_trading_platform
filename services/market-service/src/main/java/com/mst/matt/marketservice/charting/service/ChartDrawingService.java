package com.mst.matt.marketservice.charting.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mst.matt.marketservice.charting.model.*;
import com.mst.matt.marketservice.charting.repository.ChartDrawingRepository;
import com.mst.matt.marketservice.charting.repository.DrawingLayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for {@link ChartDrawing} and {@link DrawingLayout}.
 *
 * Adapted from desktop {@code service.ChartDrawingService}:
 * {@code UserProfile} replaced by {@code Long userId} from JWT header.
 * Uses {@code REQUIRES_NEW} propagation for saves/deletes to isolate
 * transactions and avoid aborted-transaction cascade on PostgreSQL.
 */
@Service
public class ChartDrawingService {

    private static final Logger log = LoggerFactory.getLogger(ChartDrawingService.class);

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Type POINT_LIST_TYPE = new TypeToken<List<ChartPoint>>() {}.getType();

    public static final int MAX_DRAWINGS_PER_CHART = 200;

    private final ChartDrawingRepository    repository;
    private final DrawingLayoutRepository   layoutRepository;

    public ChartDrawingService(ChartDrawingRepository repository,
                               DrawingLayoutRepository layoutRepository) {
        this.repository      = repository;
        this.layoutRepository = layoutRepository;
    }

    // ── Load ────────────────────────────────────────────────────────────────────

    /** Loads active (no named layout) drawings for a user/symbol/timeframe, oldest first. */
    @Transactional(readOnly = true)
    public List<ChartDrawing> loadDrawings(Long userId, String symbol, String timeframe) {
        if (userId == null) return List.of();
        List<ChartDrawing> list =
                repository.findByUserIdAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
                        userId, symbol, timeframe);
        list.forEach(ChartDrawingService::hydrate);
        return list;
    }

    /** Loads drawings belonging to a specific named layout. */
    @Transactional(readOnly = true)
    public List<ChartDrawing> loadLayout(Long userId, String symbol,
                                         String timeframe, String layoutName) {
        if (userId == null || layoutName == null) return List.of();
        List<ChartDrawing> list =
                repository.findByUserIdAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
                        userId, symbol, timeframe, layoutName);
        list.forEach(ChartDrawingService::hydrate);
        return list;
    }

    // ── Save / Delete ────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChartDrawing save(ChartDrawing drawing) {
        try {
            dehydrate(drawing);
            ChartDrawing saved = repository.save(drawing);
            hydrate(saved);
            return saved;
        } catch (Exception e) {
            log.error("Failed to persist chart drawing (toolType={}): {}",
                    drawing.getToolType(), e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** Permanently deletes ALL active drawings for a user/symbol/timeframe. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAllDrawings(Long userId, String symbol, String timeframe) {
        if (userId == null || symbol == null || timeframe == null) return;
        try {
            repository.deleteActiveDrawings(userId, symbol, timeframe);
            log.info("Deleted all active drawings for userId={} symbol={} timeframe={}",
                    userId, symbol, timeframe);
        } catch (Exception e) {
            log.error("Failed to delete all drawings for {}/{}: {}", symbol, timeframe, e.getMessage(), e);
            throw e;
        }
    }

    public ChartDrawing duplicate(ChartDrawing source) {
        ChartDrawing copy = ChartDrawing.builder()
                .userId(source.getUserId())
                .symbol(source.getSymbol())
                .timeframe(source.getTimeframe())
                .toolType(source.getToolType())
                .points(new ArrayList<>(source.getPoints()))
                .properties(source.getProperties() != null
                        ? copyProperties(source.getProperties())
                        : ChartDrawingProperties.defaultsFor(source.getToolType()))
                .locked(false)
                .createdAtEpoch(System.currentTimeMillis())
                .build();
        return save(copy);
    }

    // ── Named Layout ─────────────────────────────────────────────────────────────

    /**
     * Saves all current active drawings under a new named layout.
     * Existing drawings tagged with {@code layoutName} are replaced.
     */
    @Transactional
    public void saveLayout(Long userId, String symbol, String timeframe,
                           String layoutName, List<ChartDrawing> drawings) {
        if (userId == null || layoutName == null || layoutName.isBlank()) return;

        repository.deleteByLayoutName(userId, symbol, timeframe, layoutName);

        DrawingLayout meta = layoutRepository
                .findByUserIdAndSymbolAndTimeframeAndName(userId, symbol, timeframe, layoutName)
                .orElse(DrawingLayout.builder()
                        .userId(userId).symbol(symbol).timeframe(timeframe).name(layoutName)
                        .build());
        layoutRepository.save(meta);

        for (ChartDrawing src : drawings) {
            ChartDrawing copy = ChartDrawing.builder()
                    .userId(userId)
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .toolType(src.getToolType())
                    .points(new ArrayList<>(src.getPoints()))
                    .properties(src.getProperties() != null
                            ? copyProperties(src.getProperties())
                            : ChartDrawingProperties.defaultsFor(src.getToolType()))
                    .locked(src.isLocked())
                    .layoutName(layoutName)
                    .createdAtEpoch(System.currentTimeMillis())
                    .build();
            dehydrate(copy);
            repository.save(copy);
        }
    }

    @Transactional
    public void deleteLayout(Long userId, String symbol, String timeframe, String layoutName) {
        repository.deleteByLayoutName(userId, symbol, timeframe, layoutName);
        layoutRepository.deleteByUserIdAndSymbolAndTimeframeAndName(userId, symbol, timeframe, layoutName);
    }

    public List<DrawingLayout> listLayouts(Long userId, String symbol, String timeframe) {
        if (userId == null) return List.of();
        return layoutRepository.findByUserIdAndSymbolAndTimeframeOrderBySavedAtEpochDesc(
                userId, symbol, timeframe);
    }

    // ── Hydration helpers ──────────────────────────────────────────────────────

    public static void hydrate(ChartDrawing d) {
        if (d.getPointsJson() != null && !d.getPointsJson().isBlank()) {
            d.setPoints(GSON.fromJson(d.getPointsJson(), POINT_LIST_TYPE));
        } else if (d.getPoints() == null) {
            d.setPoints(new ArrayList<>());
        }
        if (d.getPropertiesJson() != null && !d.getPropertiesJson().isBlank()) {
            d.setProperties(GSON.fromJson(d.getPropertiesJson(), ChartDrawingProperties.class));
        } else if (d.getProperties() == null) {
            d.setProperties(ChartDrawingProperties.defaultsFor(d.getToolType()));
        }
    }

    public static void dehydrate(ChartDrawing d) {
        if (d.getPoints() == null) d.setPoints(new ArrayList<>());
        if (d.getProperties() == null) {
            d.setProperties(ChartDrawingProperties.defaultsFor(d.getToolType()));
        }
        d.setPointsJson(GSON.toJson(d.getPoints()));
        d.setPropertiesJson(GSON.toJson(d.getProperties()));
    }

    private static ChartDrawingProperties copyProperties(ChartDrawingProperties p) {
        return ChartDrawingProperties.builder()
                .color(p.getColor())
                .lineWidth(p.getLineWidth())
                .lineStyle(p.getLineStyle())
                .fillOpacity(p.getFillOpacity())
                .extendLeft(p.isExtendLeft())
                .extendRight(p.isExtendRight())
                .entryPrice(p.getEntryPrice())
                .stopLoss(p.getStopLoss())
                .takeProfit(p.getTakeProfit())
                .channelWidth(p.getChannelWidth())
                .text(p.getText())
                .fontSize(p.getFontSize())
                .textBoxWidth(p.getTextBoxWidth())
                .textBoxHeight(p.getTextBoxHeight())
                .arrowDirection(p.getArrowDirection())
                .mirrorAxis(p.getMirrorAxis())
                .parallelOffset(p.getParallelOffset())
                .build();
    }
}
