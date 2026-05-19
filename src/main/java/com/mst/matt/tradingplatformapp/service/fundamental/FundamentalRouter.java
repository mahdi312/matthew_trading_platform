package com.mst.matt.tradingplatformapp.service.fundamental;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FundamentalRouter {

    private static final Logger log = LoggerFactory.getLogger(FundamentalRouter.class);

    private final List<FundamentalService> services;

    public FundamentalRouter(List<FundamentalService> services) {
        this.services = services.stream().filter(FundamentalService::isEnabled).toList();
    }

    public List<FundamentalDataProvider> enabledProviders() {
        List<FundamentalDataProvider> list = new ArrayList<>();
        list.add(FundamentalDataProvider.AUTO);
        for (FundamentalService s : services) {
            list.add(s.getProviderId());
        }
        return list;
    }

    public Optional<FundamentalsReport> fetch(String symbol, UserProfile profile) {
        FundamentalDataProvider preferred = profile != null
                ? FundamentalDataProvider.fromString(profile.getFundamentalProvider())
                : FundamentalDataProvider.AUTO;

        List<FundamentalService> order = order(preferred);
        for (FundamentalService svc : order) {
            try {
                Optional<FundamentalsReport> report = svc.fetchReport(symbol);
                if (report.isPresent() && !report.get().getYearlyRows().isEmpty()) {
                    return report;
                }
                if (report.isPresent() && report.get().getCompanyName() != null) {
                    return report;
                }
            } catch (Exception e) {
                log.warn("{} fundamentals failed for {}: {}",
                        svc.getProviderId(), symbol, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private List<FundamentalService> order(FundamentalDataProvider preferred) {
        List<FundamentalDataProvider> ids = new ArrayList<>();
        if (preferred != FundamentalDataProvider.AUTO) {
            ids.add(preferred);
        }
        ids.add(FundamentalDataProvider.ALPHA_VANTAGE);
        ids.add(FundamentalDataProvider.FINNHUB);

        List<FundamentalService> result = new ArrayList<>();
        for (FundamentalDataProvider id : ids) {
            services.stream()
                    .filter(s -> s.getProviderId() == id)
                    .findFirst()
                    .ifPresent(s -> {
                        if (!result.contains(s)) result.add(s);
                    });
        }
        for (FundamentalService s : services) {
            if (!result.contains(s)) result.add(s);
        }
        return result;
    }
}
