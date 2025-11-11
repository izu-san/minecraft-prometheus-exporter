package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.atomic.AtomicLong;

public class PerformanceMetrics {
    private final Gauge tpsGauge;
    private final Histogram tickDurationHistogram;
    private final Counter tickLagCounter;
    private final Gauge loadedChunksGauge;
    private final Gauge entitiesLoadedGauge;
    private final Gauge tileEntitiesLoadedGauge;
    
    private final AtomicLong[] tickTimes = new AtomicLong[20];
    private int tickIndex = 0;
    private long lastTickTime = System.currentTimeMillis();
    private static final long TICK_LAG_THRESHOLD_MS = 50; // Consider lag if exceeds 50ms
    
    public PerformanceMetrics(CollectorRegistry registry) {
        for (int i = 0; i < tickTimes.length; i++) {
            tickTimes[i] = new AtomicLong(0);
        }
        
        tpsGauge = Gauge.build()
            .name("minecraft_tps")
            .help("Current TPS (ideal value is 20)")
            .register(registry);
        
        tickDurationHistogram = Histogram.build()
            .name("minecraft_tick_duration_milliseconds")
            .help("Processing time per tick")
            .buckets(1, 5, 10, 20, 50, 100, 200, 500, 1000)
            .register(registry);
        
        tickLagCounter = Counter.build()
            .name("minecraft_tick_lag_count_total")
            .help("Total number of lagged ticks (e.g., exceeding 50ms)")
            .register(registry);
        
        loadedChunksGauge = Gauge.build()
            .name("minecraft_loaded_chunks")
            .help("Total number of chunks across all dimensions")
            .register(registry);
        
        entitiesLoadedGauge = Gauge.build()
            .name("minecraft_entities_loaded")
            .help("Total number of entities")
            .register(registry);
        
        tileEntitiesLoadedGauge = Gauge.build()
            .name("minecraft_tile_entities_loaded")
            .help("Total number of BlockEntities")
            .register(registry);
    }
    
    public void update(MinecraftServer server) {
        long currentTime = System.currentTimeMillis();
        long tickTime = currentTime - lastTickTime;
        lastTickTime = currentTime;
        
        // Calculate TPS (average of last 20 ticks)
        tickTimes[tickIndex].set(tickTime);
        tickIndex = (tickIndex + 1) % 20;
        
        long sum = 0;
        int count = 0;
        for (AtomicLong time : tickTimes) {
            long t = time.get();
            if (t > 0) {
                sum += t;
                count++;
            }
        }
        
        if (count > 0) {
            double avgTickTime = sum / (double) count;
            double tps = 1000.0 / avgTickTime;
            tpsGauge.set(Math.min(tps, 20.0)); // Limit to maximum 20
        }
        
        // Count lagged ticks
        if (tickTime > TICK_LAG_THRESHOLD_MS) {
            tickLagCounter.inc();
        }
        
        // Aggregate chunks, entities, and BlockEntities across all dimensions
        if (server != null) {
            int totalChunks = 0;
            int totalEntities = 0;
            int totalTileEntities = 0;
            
            for (ServerLevel level : server.getAllLevels()) {
                totalChunks += level.getChunkSource().getLoadedChunksCount();
                // getAll() returns Iterable, so use StreamSupport to count
                totalEntities += (int) java.util.stream.StreamSupport.stream(
                    level.getEntities().getAll().spliterator(), false).count();
                // BlockEntity count retrieval
                // Note: BlockEntity retrieval requires protected access, currently disabled
                // Will be implemented in the future using Mixin or Access Transformer
                // totalTileEntities += ...;
            }
            
            loadedChunksGauge.set(totalChunks);
            entitiesLoadedGauge.set(totalEntities);
            tileEntitiesLoadedGauge.set(totalTileEntities);
        }
    }
    
    public void recordTickDuration(long durationMs) {
        tickDurationHistogram.observe(durationMs);
    }
}

