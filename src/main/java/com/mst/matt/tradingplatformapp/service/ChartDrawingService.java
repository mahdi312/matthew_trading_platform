package com.mst.matt.tradingplatformapp.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.ChartDrawingRepository;
import com.mst.matt.tradingplatformapp.repository.DrawingLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChartDrawingService {

    // Use only simple types in the Gson graph — no java.time classes.
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();
    private static final Type POINT_LIST_TYPE = new TypeToken<List<ChartPoint>>() {}.getType();

    /** Maximum drawings per chart before the user is warned. */
    public static final int MAX_DRAWINGS_PER_CHART = 200;

    private final ChartDrawingRepository repository;
    private final DrawingLayoutRepository layoutRepository;

    public ChartDrawingService(ChartDrawingRepository repository,
                               DrawingLayoutRepository layoutRepository) {
        this.repository = repository;
        this.layoutRepository = layoutRepository;
    }

    // ── Load / Save / Delete ────────────────────────────────────────────────────

    /**
     * Loads the "active" (no named layout) drawings for a symbol/timeframe.
     */
    public List<ChartDrawing> loadDrawings(UserProfile profile, String symbol, String timeframe) {
        if (profile == null) return List.of();
        List<ChartDrawing> list =
                repository.findByProfileAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
                        profile, symbol, timeframe);
        list.forEach(ChartDrawingService::hydrate);
        return list;
    }

    /**
     * Loads drawings belonging to a specific named layout.
     */
    public List<ChartDrawing> loadLayout(UserProfile profile, String symbol,
                                         String timeframe, String layoutName) {
        if (profile == null || layoutName == null) return List.of();
        List<ChartDrawing> list =
                repository.findByProfileAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
                        profile, symbol, timeframe, layoutName);
        list.forEach(ChartDrawingService::hydrate);
        return list;
    }

    public ChartDrawing save(ChartDrawing drawing) {
        dehydrate(drawing);
        ChartDrawing saved = repository.save(drawing);
        hydrate(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public ChartDrawing duplicate(ChartDrawing source) {
        ChartDrawing copy = ChartDrawing.builder()
                .profile(source.getProfile())
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

    // ── Named Layout (Save / Load / Delete / List) ───────────────────────────

    /**
     * Saves all current active drawings under a new named layout.
     * Existing drawings tagged with {@code layoutName} for this symbol/tf are
     * first removed, then fresh copies (with the layout tag) are inserted.
     *
     * @param profile    active user profile
     * @param symbol     chart symbol
     * @param timeframe  chart timeframe
     * @param layoutName name chosen by the user
     * @param drawings   current drawings to persist under the layout
     */
    @Transactional
    public void saveLayout(UserProfile profile, String symbol, String timeframe,
                           String layoutName, List<ChartDrawing> drawings) {
        if (profile == null || layoutName == null || layoutName.isBlank()) return;

        // Remove previous drawings stored under this layout name
        repository.deleteByProfileAndSymbolAndTimeframeAndLayoutName(profile, symbol, timeframe, layoutName);

        // Ensure the DrawingLayout catalogue entry exists (upsert)
        DrawingLayout meta = layoutRepository
                .findByProfileAndSymbolAndTimeframeAndName(profile, symbol, timeframe, layoutName)
                .orElse(DrawingLayout.builder()
                        .profile(profile).symbol(symbol).timeframe(timeframe).name(layoutName)
                        .build());
        layoutRepository.save(meta);

        // Persist a copy of each drawing tagged with the layout name
        for (ChartDrawing src : drawings) {
            ChartDrawing copy = ChartDrawing.builder()
                    .profile(profile)
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

    /**
     * Deletes the named layout AND all drawings tagged with it.
     */
    @Transactional
    public void deleteLayout(UserProfile profile, String symbol, String timeframe, String layoutName) {
        repository.deleteByProfileAndSymbolAndTimeframeAndLayoutName(profile, symbol, timeframe, layoutName);
        layoutRepository.deleteByProfileAndSymbolAndTimeframeAndName(profile, symbol, timeframe, layoutName);
    }

    /**
     * Returns the list of saved layout names for a given symbol/timeframe.
     */
    public List<DrawingLayout> listLayouts(UserProfile profile, String symbol, String timeframe) {
        if (profile == null) return List.of();
        return layoutRepository.findByProfileAndSymbolAndTimeframeOrderBySavedAtEpochDesc(
                profile, symbol, timeframe);
    }

    // ── Hydration helpers ─────────────────────────────────────────────────────

    /**
     * Deserialises {@code pointsJson} and {@code propertiesJson} into the
     * transient {@code points} and {@code properties} fields.
     *
     * <p>Because {@link ChartPoint} now stores {@code timeEpoch} (a plain
     * {@code long}), Gson can serialise/deserialise it without any reflection
     * into {@code java.time} types — eliminating the
     * {@code InaccessibleObjectException}.
     */
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
