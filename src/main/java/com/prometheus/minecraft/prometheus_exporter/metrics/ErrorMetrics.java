package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.CollectorRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ErrorMetrics {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final Counter serverErrorsCounter;
    private final Counter tickErrorsCounter;
    
    private boolean initialized = false;
    
    public ErrorMetrics(CollectorRegistry registry) {
        serverErrorsCounter = Counter.build()
            .name("minecraft_server_errors_total")
            .help("Total number of server errors")
            .labelNames("error_type")
            .register(registry);
        
        tickErrorsCounter = Counter.build()
            .name("minecraft_tick_errors_total")
            .help("Total number of tick processing errors")
            .register(registry);
        
        // Set up global exception handler
        setupGlobalExceptionHandler();
    }
    
    private void setupGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String errorType = throwable.getClass().getSimpleName();
            serverErrorsCounter.labels(errorType).inc();
            LOGGER.error("Uncaught exception in thread: {}", thread.getName(), throwable);
        });
        
        // Note: ServerTickEvent is an abstract class, so it cannot be registered directly as a listener
        // Tick error monitoring is implemented in MetricsManager using @SubscribeEvent
    }
    
    public void recordError(String errorType) {
        serverErrorsCounter.labels(errorType).inc();
    }
    
    public void recordTickError() {
        tickErrorsCounter.inc();
    }
}

