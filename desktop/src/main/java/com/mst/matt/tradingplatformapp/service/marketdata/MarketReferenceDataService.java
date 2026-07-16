package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.repository.CompanyRepository;
import com.mst.matt.tradingplatformapp.repository.MarketRepository;
import com.mst.matt.tradingplatformapp.repository.ShareRepository;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalRouter;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MarketReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketReferenceDataService.class);

    private final MarketRepository marketRepository;
    private final ShareRepository shareRepository;
    private final CompanyRepository companyRepository;
    private final FundamentalRouter fundamentalRouter;

    public MarketReferenceDataService(MarketRepository marketRepository,
                                      ShareRepository shareRepository,
                                      CompanyRepository companyRepository,
                                      FundamentalRouter fundamentalRouter) {
        this.marketRepository = marketRepository;
        this.shareRepository = shareRepository;
        this.companyRepository = companyRepository;
        this.fundamentalRouter = fundamentalRouter;
    }

    @Transactional
    public Share ensureShare(String symbol, MarketDataProvider provider,
                             Optional<PriceQuote> quote, UserProfile profile) {
        String sym = symbol.toUpperCase();
        AssetClass assetClass = AssetClassDetector.detect(sym);
        Trade.AssetType assetType = toAssetType(assetClass);
        Market market = ensureMarket(assetType, provider);
        Company company = assetType == Trade.AssetType.STOCK
                ? ensureCompany(sym, profile).orElse(null)
                : null;

        String displayName = quote.map(PriceQuote::getAssetName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(sym);

        return shareRepository.findBySymbolAndMarketId(sym, market.getId())
                .map(existing -> updateShare(existing, displayName, company))
                .orElseGet(() -> shareRepository.save(Share.builder()
                        .symbol(sym)
                        .name(displayName)
                        .market(market)
                        .company(company)
                        .assetType(assetType)
                        .baseCurrency(extractBase(sym, assetType))
                        .quoteCurrency(extractQuote(sym, assetType))
                        .active(true)
                        .build()));
    }

    private Market ensureMarket(Trade.AssetType assetType, MarketDataProvider provider) {
        String code = assetType.name() + "_" + provider.name();
        return marketRepository.findByCode(code).orElseGet(() ->
                marketRepository.save(Market.builder()
                        .code(code)
                        .name(provider.getLabel() + " " + assetType.name())
                        .assetType(assetType)
                        .exchangeName(provider.getLabel())
                        .currency(defaultCurrency(assetType))
                        .description("Market data from " + provider.getLabel())
                        .active(true)
                        .build()));
    }

    private Optional<Company> ensureCompany(String symbol, UserProfile profile) {
        return companyRepository.findByTicker(symbol).or(() -> {
            Optional<FundamentalsReport> report = fundamentalRouter.fetch(symbol, profile);
            if (report.isEmpty()) {
                return Optional.empty();
            }
            FundamentalsReport r = report.get();
            Company saved = companyRepository.save(Company.builder()
                    .name(r.getCompanyName() != null ? r.getCompanyName() : symbol)
                    .ticker(symbol)
                    .sector(r.getSector())
                    .industry(r.getIndustry())
                    .country(r.getCountry())
                    .dataProvider(r.getProviderUsed())
                    .description(r.getSummaryText())
                    .build());
            log.info("Stored company reference data for {}", symbol);
            return Optional.of(saved);
        });
    }

    private Share updateShare(Share share, String name, Company company) {
        share.setName(name);
        if (company != null) {
            share.setCompany(company);
        }
        return shareRepository.save(share);
    }

    private static Trade.AssetType toAssetType(AssetClass assetClass) {
        return switch (assetClass) {
            case CRYPTO -> Trade.AssetType.CRYPTO;
            case FOREX -> Trade.AssetType.FOREX;
            case STOCK -> Trade.AssetType.STOCK;
            case COMMODITY -> Trade.AssetType.COMMODITY;
            case INDEX -> Trade.AssetType.INDEX;
        };
    }

    private static String defaultCurrency(Trade.AssetType assetType) {
        return switch (assetType) {
            case FOREX -> "USD";
            case CRYPTO -> "USDT";
            default -> "USD";
        };
    }

    private static String extractBase(String symbol, Trade.AssetType assetType) {
        if (assetType == Trade.AssetType.FOREX && symbol.length() == 6) {
            return symbol.substring(0, 3);
        }
        if (assetType == Trade.AssetType.CRYPTO && symbol.endsWith("USDT")) {
            return symbol.substring(0, symbol.length() - 4);
        }
        return symbol;
    }

    private static String extractQuote(String symbol, Trade.AssetType assetType) {
        if (assetType == Trade.AssetType.FOREX && symbol.length() == 6) {
            return symbol.substring(3);
        }
        if (assetType == Trade.AssetType.CRYPTO && symbol.endsWith("USDT")) {
            return "USDT";
        }
        return "USD";
    }
}
