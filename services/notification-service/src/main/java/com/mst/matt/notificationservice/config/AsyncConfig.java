
package com.mst.matt.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded thread pool for notification dispatch (email/Telegram sends).
 *
 * <p>Deliberately separate from Spring's default {@code applicationTaskExecutor} —
 * a burst of notifications should never be able to starve other {@code @Async} usage
 * in this service, and a named pool gives you readable thread dumps
 * ("notif-dispatch-1", not "task-1").</p>
 *
 * <h3>Sizing</h3>
 * <p>Core/max sized for "a handful of channels per event, occasional bursts on
 * market-wide alerts" — not high-throughput. {@link ThreadPoolExecutor.CallerRunsPolicy}
 * means that if the queue does fill up, the calling (Kafka listener) thread runs the
 * task itself rather than silently dropping it — degrades to synchronous behavior
 * under extreme overload instead of losing a notification.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationSendExecutor")
    public Executor notificationSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}