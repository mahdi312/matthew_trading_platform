package com.mst.matt.tradingplatformapp.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.ChartDrawingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChartDrawingService {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();
    private static final Type POINT_LIST_TYPE = new TypeToken<List<ChartPoint>>() {}.getType();

    private final ChartDrawingRepository repository;

    public ChartDrawingService(ChartDrawingRepository repository) {
        this.repository = repository;
    }

    public List<ChartDrawing> loadDrawings(UserProfile profile, String symbol, String timeframe) {
        if (profile == null) return List.of();
        List<ChartDrawing> list = repository.findByProfileAndSymbolAndTimeframeOrderByCreatedAtAsc(
                profile, symbol, timeframe);
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
                .createdAt(LocalDateTime.now())
                .build();
        return save(copy);
    }

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
                .fillOpacity(p.getFillOpacity())
                .extendLeft(p.isExtendLeft())
                .extendRight(p.isExtendRight())
                .entryPrice(p.getEntryPrice())
                .stopLoss(p.getStopLoss())
                .takeProfit(p.getTakeProfit())
                .channelWidth(p.getChannelWidth())
                .text(p.getText())
                .fontSize(p.getFontSize())
                .arrowDirection(p.getArrowDirection())
                .build();
    }
}
