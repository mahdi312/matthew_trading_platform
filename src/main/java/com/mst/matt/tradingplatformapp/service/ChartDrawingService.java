package com.mst.matt.tradingplatformapp.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.ChartDrawingRepository;
import com.mst.matt.tradingplatformapp.repository.DrawingLayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChartDrawingService {

    private static final Logger log = LoggerFactory.getLogger(ChartDrawingService.class);

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
     *
     * <p><b>Bug fix — LazyInitializationException:</b>
     * The {@code ChartDrawing.profile} association is {@code LAZY}-loaded.  After the
     * Hibernate session that ran the repository query is closed, any code path that
     * calls {@code drawing.getProfile()} (e.g. inside old {@code equals()}) will
     * throw {@code LazyInitializationException}.
     *
     * <p>Defense-in-depth: we force the proxy to initialise by touching
     * {@code d.getProfile().getId()} inside the same transaction so the identifier is
     * available in the detached state without needing the session again.  The primary
     * fix is the safe {@code equals()/hashCode()} override in {@link com.mst.matt.tradingplatformapp.model.ChartDrawing}
     * which no longer touches the proxy at all.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChartDrawing> loadDrawings(UserProfile profile, String symbol, String timeframe) {
        if (profile == null) return List.of();
        List<ChartDrawing> list =
                repository.findByProfileAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
                        profile, symbol, timeframe);
        // Force proxy initialisation while the session is still open
        list.forEach(d -> {
            if (d.getProfile() != null) d.getProfile().getId();
            ChartDrawingService.hydrate(d);
        });
        return list;
    }

    /**
     * Loads drawings belonging to a specific named layout.
     *
     * <p>Same proxy-initialisation guard as {@link #loadDrawings}.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChartDrawing> loadLayout(UserProfile profile, String symbol,
                                         String timeframe, String layoutName) {
        if (profile == null || layoutName == null) return List.of();
        List<ChartDrawing> list =
                repository.findByProfileAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
                        profile, symbol, timeframe, layoutName);
        // Force proxy initialisation while the session is still open
        list.forEach(d -> {
            if (d.getProfile() != null) d.getProfile().getId();
            ChartDrawingService.hydrate(d);
        });
        return list;
    }

    /**
     * Persists a drawing in its <em>own</em> independent transaction.
     *
     * <p><b>Bug fix — aborted-transaction propagation:</b>
     * When the caller (e.g. {@code ChartController.persistDrawing()}) runs inside a
     * transaction that has already been marked rollback-only (e.g. because an earlier
     * {@code INSERT} failed due to the old CHECK constraint on {@code tool_type}),
     * every subsequent statement in that transaction is rejected by PostgreSQL with
     * {@code "current transaction is aborted, commands ignored until end of transaction block"}.
     *
     * <p>Using {@code propagation = REQUIRES_NEW} forces Spring to suspend any ambient
     * transaction and open a brand-new one just for this save.  If the save fails
     * the new transaction is rolled back but the caller's transaction (or lack thereof)
     * is completely unaffected — preventing the cascade of secondary failures.</p>
     *
     * <p>The {@code REQUIRES_NEW} behaviour is especially important on PostgreSQL where
     * a failed statement permanently aborts the surrounding transaction until an explicit
     * rollback is issued.  SQLite (used in the default desktop profile) is also protected
     * because each call now gets its own atomic transaction boundary.</p>
     */
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
            throw e;   // re-throw so caller can handle / log the error
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Permanently deletes ALL drawings for a given profile/symbol/timeframe.
     *
     * <p>Called by "Delete All Drawings" button. Uses {@code REQUIRES_NEW} so any
     * ambient (potentially aborted) transaction is bypassed, ensuring the DELETE
     * always executes in a clean transaction.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAllDrawings(UserProfile profile, String symbol, String timeframe) {
        if (profile == null || symbol == null || timeframe == null) return;
        try {
            repository.deleteByProfileAndSymbolAndTimeframe(profile, symbol, timeframe);
            log.info("Deleted all active drawings for profile={} symbol={} timeframe={}",
                    profile.getId(), symbol, timeframe);
        } catch (Exception e) {
            log.error("Failed to delete all drawings for {}/{}: {}", symbol, timeframe, e.getMessage(), e);
            throw e;
        }
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
