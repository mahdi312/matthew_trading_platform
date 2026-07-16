package com.mst.matt.contracts.provider.registry;

import com.mst.matt.contracts.enums.AssetClass;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic, asset-class-aware provider registry with per-provider Resilience4j
 * circuit breakers and priority-ordered fallback chains.
 *
 * <h3>Key concepts</h3>
 * <ul>
 *   <li><b>Provider type</b> — a specific interface: {@code OhlcvDataProvider},
 *       {@code NewsProvider}, etc.</li>
 *   <li><b>Registry key</b> — {@code (AssetClass, providerName)} pair.</li>
 *   <li><b>Priority chain</b> — ordered list of providers per asset class;
 *       if provider[0] fails (circuit open or exception), provider[1] is tried,
 *       and so on.</li>
 *   <li><b>Circuit breaker</b> — one Resilience4j {@link CircuitBreaker} per
 *       {@code providerName}, shared across all asset classes for that provider.
 *       A noisy provider trips its own breaker without affecting others.</li>
 * </ul>
 *
 * <h3>Instantiation (no {@code @Component} here)</h3>
 * <p>Consuming services declare a {@code @Bean} in their own {@code @Configuration}:</p>
 * <pre>{@code
 * @Bean
 * public ProviderRegistry<OhlcvDataProvider> ohlcvRegistry(
 *         List<OhlcvDataProvider> providers) {
 *     return ProviderRegistry.<OhlcvDataProvider>builder()
 *             .registerAll(providers)
 *             .build();
 * }
 * }</pre>
 *
 * <h3>Fallback execution</h3>
 * <p>Call {@link #executeWithFallback(AssetClass, ProviderCall)} — the registry
 * iterates the priority chain for the asset class, wraps each call in its
 * circuit breaker, and returns the first successful result. If all providers
 * fail or are open, a {@link ProviderUnavailableException} is thrown.</p>
 *
 * @param <T> the provider interface type (must extend {@link DataProvider})
 */
public final class ProviderRegistry<T extends DataProvider> {

    // ── Internal state ────────────────────────────────────────────────────────

    /**
     * priority chain per asset class: AssetClass → [provider0, provider1, …]
     * (insertion order = priority order).
     */
    private final Map<AssetClass, List<T>> chainByAssetClass;

    /**
     * flat map: providerName → provider instance
     * (used to retrieve individual providers by name).
     */
    private final Map<String, T> byName;

    /**
     * per-provider circuit breakers: providerName → CircuitBreaker.
     * One breaker per named provider; shared across all asset classes.
     */
    private final Map<String, CircuitBreaker> circuitBreakers;

    // ── Constructor (use Builder) ─────────────────────────────────────────────

    private ProviderRegistry(Map<AssetClass, List<T>> chainByAssetClass,
                              Map<String, T> byName,
                              Map<String, CircuitBreaker> circuitBreakers) {
        this.chainByAssetClass = chainByAssetClass;
        this.byName            = byName;
        this.circuitBreakers   = circuitBreakers;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Returns the priority-ordered provider chain for an asset class.
     *
     * @param assetClass the asset class to look up
     * @return immutable, ordered list of providers; empty if none registered
     */
    public List<T> getChain(AssetClass assetClass) {
        return chainByAssetClass.getOrDefault(assetClass, List.of());
    }

    /**
     * Returns the primary (highest-priority) provider for an asset class.
     *
     * @param assetClass target asset class
     * @return {@link Optional} containing the primary provider, or empty if
     *         no provider is registered for this asset class
     */
    public Optional<T> getPrimary(AssetClass assetClass) {
        List<T> chain = getChain(assetClass);
        return chain.isEmpty() ? Optional.empty() : Optional.of(chain.get(0));
    }

    /**
     * Returns the primary provider, throwing if none is registered.
     *
     * @param assetClass target asset class
     * @return the primary provider
     * @throws NoSuchElementException if no provider is registered
     */
    public T requirePrimary(AssetClass assetClass) {
        return getPrimary(assetClass)
                .orElseThrow(() -> new NoSuchElementException(
                        "No provider registered for AssetClass: " + assetClass));
    }

    /**
     * Resolve a provider by its exact {@link DataProvider#providerName()}.
     *
     * @param providerName the provider name to look up
     * @return {@link Optional} containing the provider, or empty
     */
    public Optional<T> getByName(String providerName) {
        return Optional.ofNullable(byName.get(providerName));
    }

    /**
     * Returns all registered providers across all asset classes (deduplicated).
     *
     * @return immutable collection of all registered providers
     */
    public Collection<T> getAll() {
        return Collections.unmodifiableCollection(byName.values());
    }

    /**
     * Returns the {@link CircuitBreaker} for a specific provider.
     *
     * @param providerName the provider name
     * @return the circuit breaker; never {@code null} if the provider is registered
     * @throws NoSuchElementException if the provider is not registered
     */
    public CircuitBreaker getCircuitBreaker(String providerName) {
        CircuitBreaker cb = circuitBreakers.get(providerName);
        if (cb == null) {
            throw new NoSuchElementException(
                    "No circuit breaker found for provider: " + providerName);
        }
        return cb;
    }

    // ── Fallback execution ────────────────────────────────────────────────────

    /**
     * Execute a provider call with automatic fallback through the priority chain.
     *
     * <p>For each provider in the chain (highest priority first):</p>
     * <ol>
     *   <li>If the provider's circuit breaker is OPEN, skip to the next provider.</li>
     *   <li>Otherwise, execute the call wrapped in the circuit breaker.</li>
     *   <li>On success, return the result immediately.</li>
     *   <li>On failure, the circuit breaker records the failure (and may open),
     *       then try the next provider in the chain.</li>
     * </ol>
     *
     * @param assetClass the asset class context for chain selection
     * @param call       the provider operation to execute
     * @param <R>        the return type of the provider call
     * @return the result from the first successful provider
     * @throws ProviderUnavailableException if all providers in the chain fail
     *         or have open circuit breakers
     */
    public <R> R executeWithFallback(AssetClass assetClass, ProviderCall<T, R> call) {
        List<T> chain = getChain(assetClass);
        if (chain.isEmpty()) {
            throw new ProviderUnavailableException(
                    "No providers registered for AssetClass: " + assetClass);
        }

        List<Throwable> errors = new ArrayList<>();
        for (T provider : chain) {
            CircuitBreaker cb = circuitBreakers.get(provider.providerName());
            if (cb != null && cb.getState() == CircuitBreaker.State.OPEN) {
                errors.add(new RuntimeException(
                        "Circuit breaker OPEN for provider: " + provider.providerName()));
                continue;
            }
            try {
                Supplier<R> decorated = (cb != null)
                        ? CircuitBreaker.decorateSupplier(cb, () -> call.execute(provider))
                        : () -> call.execute(provider);
                return decorated.get();
            } catch (Exception ex) {
                errors.add(ex);
            }
        }

        ProviderUnavailableException failure = new ProviderUnavailableException(
                "All providers exhausted for AssetClass " + assetClass
                + " — " + errors.size() + " failure(s)");
        errors.forEach(failure::addSuppressed);
        throw failure;
    }

    // ── Functional interface ──────────────────────────────────────────────────

    /**
     * Functional interface for a single provider call used in
     * {@link #executeWithFallback}.
     *
     * @param <T> provider type
     * @param <R> return type
     */
    @FunctionalInterface
    public interface ProviderCall<T, R> {
        /**
         * Execute the operation on the given provider.
         *
         * @param provider the provider to call
         * @return the result
         */
        R execute(T provider);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Builder for {@link ProviderRegistry}.
     *
     * @param <T> provider interface type
     */
    public static final class Builder<T extends DataProvider> {

        private final Map<AssetClass, List<T>> chainByAssetClass = new ConcurrentHashMap<>();
        private final Map<String, T> byName = new LinkedHashMap<>();
        private CircuitBreakerConfig circuitBreakerConfig = defaultCircuitBreakerConfig();

        /**
         * Override the default {@link CircuitBreakerConfig} applied to every
         * provider. Call this before {@link #register} calls.
         *
         * @param config custom config
         * @return this builder
         */
        public Builder<T> withCircuitBreakerConfig(CircuitBreakerConfig config) {
            this.circuitBreakerConfig = config;
            return this;
        }

        /**
         * Register a single provider. The provider's
         * {@link DataProvider#supportedAssetClasses()} determines which chains it
         * is added to. Registration order within each asset class determines
         * priority (first registered = highest priority).
         *
         * @param provider the provider to register
         * @return this builder
         */
        public Builder<T> register(T provider) {
            String name = provider.providerName();
            byName.putIfAbsent(name, provider);
            for (AssetClass ac : provider.supportedAssetClasses()) {
                chainByAssetClass
                        .computeIfAbsent(ac, k -> new ArrayList<>())
                        .add(provider);
            }
            return this;
        }

        /**
         * Register all providers in the given collection (in iteration order).
         *
         * @param providers providers to register
         * @return this builder
         */
        public Builder<T> registerAll(Iterable<? extends T> providers) {
            providers.forEach(this::register);
            return this;
        }

        /**
         * Build the {@link ProviderRegistry}.
         *
         * @return immutable registry
         */
        public ProviderRegistry<T> build() {
            // Build one circuit breaker per unique provider name
            CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
            Map<String, CircuitBreaker> cbs = new ConcurrentHashMap<>();
            for (String name : byName.keySet()) {
                cbs.put(name, cbRegistry.circuitBreaker(name, circuitBreakerConfig));
            }

            // Make chains immutable
            Map<AssetClass, List<T>> immutableChains = new ConcurrentHashMap<>();
            chainByAssetClass.forEach((ac, list) ->
                    immutableChains.put(ac, Collections.unmodifiableList(new ArrayList<>(list))));

            return new ProviderRegistry<>(
                    Collections.unmodifiableMap(immutableChains),
                    Collections.unmodifiableMap(new LinkedHashMap<>(byName)),
                    Collections.unmodifiableMap(cbs));
        }

        // ── Defaults ──────────────────────────────────────────────────────────

        /**
         * Default circuit breaker configuration applied to every provider:
         * <ul>
         *   <li>Failure-rate threshold: 50% (open after 5 of last 10 calls fail)</li>
         *   <li>Wait duration in OPEN state: 30 seconds before moving to HALF_OPEN</li>
         *   <li>Permitted calls in HALF_OPEN: 3</li>
         *   <li>Slow-call duration threshold: 10 seconds</li>
         *   <li>Slow-call rate threshold: 80%</li>
         * </ul>
         */
        private static CircuitBreakerConfig defaultCircuitBreakerConfig() {
            return CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .slidingWindowSize(10)
                    .slowCallDurationThreshold(Duration.ofSeconds(10))
                    .slowCallRateThreshold(80)
                    .build();
        }
    }

    /**
     * Create a new {@link Builder} for this registry.
     *
     * @param <T> provider interface type
     * @return new builder instance
     */
    public static <T extends DataProvider> Builder<T> builder() {
        return new Builder<>();
    }
}
