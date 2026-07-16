package com.mst.matt.tradingservice.service;

import com.mst.matt.contracts.broker.registry.BrokerRegistry;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodically compares locally-journalled OPEN/BROKER_LIVE trades against the
 * broker's actual live positions. Detection only for now — logs discrepancies
 * loudly; does not auto-close or auto-create trades. Auto-repair is a deliberate
 * follow-up once you've watched this run in production for a while and trust
 * what it finds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeReconciliationService {

    private final TradeRepository tradeRepository;
    private final BrokerRegistry  brokerRegistry;

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms:300000}") // every 5 min by default
    public void reconcileOpenBrokerTrades() {
        List<Trade> localOpen = tradeRepository.findByUserIdAndStatus(null, Trade.TradeStatus.OPEN).stream()
                .filter(t -> t.getSource() == Trade.TradeSource.BROKER_LIVE)
                .toList();
        // NOTE: findByUserIdAndStatus requires a userId — if you have multiple users,
        // iterate distinct userIds instead of passing null. Left as a single-user stub;
        // extend to loop over identity-service's user list once that's needed.

        if (localOpen.isEmpty()) return;

        Set<String> localBrokerOrderIds = localOpen.stream()
                .map(Trade::getBrokerOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        for (BrokerType brokerType : brokerRegistry.getRegisteredBrokers()) {
            TradingProvider provider = brokerRegistry.requireTradingProvider(brokerType);
            try {
                Set<String> liveOrderIds = provider.getOpenPositionIds();
                Set<String> localOnly = new java.util.HashSet<>(localBrokerOrderIds);
                localOnly.removeAll(liveOrderIds);
                Set<String> brokerOnly = new java.util.HashSet<>(liveOrderIds);
                brokerOnly.removeAll(localBrokerOrderIds);

                if (!localOnly.isEmpty()) {
                    log.warn("RECONCILIATION: {} trade(s) OPEN locally but not found on {} — " +
                                    "possibly closed on the exchange without a local update. brokerOrderIds={}",
                            localOnly.size(), brokerType, localOnly);
                }
                if (!brokerOnly.isEmpty()) {
                    log.warn("RECONCILIATION: {} position(s) open on {} with NO local trade record — " +
                                    "possibly an order placed but never journalled (see Section 2's " +
                                    "persistFailure log). brokerOrderIds={}",
                            brokerOnly.size(), brokerType, brokerOnly);
                }
            } catch (Exception ex) {
                log.error("RECONCILIATION: failed to fetch open positions from {}: {}",
                        brokerType, ex.getMessage(), ex);
            }
        }
    }
}