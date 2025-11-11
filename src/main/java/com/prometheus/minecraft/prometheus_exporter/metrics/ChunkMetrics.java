package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkMetrics {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final Counter chunksLoadedCounter;
    private final Counter chunksUnloadedCounter;
    private final Histogram chunkGenerationTimeHistogram;
    
    // Record chunk generation start time
    private final Map<Long, Long> chunkGenerationStartTimes = new ConcurrentHashMap<>();
    
    public ChunkMetrics(CollectorRegistry registry) {
        chunksLoadedCounter = Counter.build()
            .name("minecraft_chunks_loaded_total")
            .help("Total number of chunks loaded")
            .labelNames("dimension")
            .register(registry);
        
        chunksUnloadedCounter = Counter.build()
            .name("minecraft_chunks_unloaded_total")
            .help("Total number of chunks unloaded")
            .labelNames("dimension")
            .register(registry);
        
        chunkGenerationTimeHistogram = Histogram.build()
            .name("minecraft_chunk_generation_time_seconds")
            .help("Chunk generation time")
            .labelNames("dimension")
            .buckets(0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0)
            .register(registry);
        
        // Register event listeners
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);
    }
    
    private void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) event.getLevel();
            ResourceKey<Level> dimension = level.dimension();
            String dimensionName = dimension.location().toString();
            
            chunksLoadedCounter.labels(dimensionName).inc();
            
            // Start measuring chunk generation time
            long chunkKey = event.getChunk().getPos().toLong();
            chunkGenerationStartTimes.put(chunkKey, System.nanoTime());
        }
    }
    
    private void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) event.getLevel();
            ResourceKey<Level> dimension = level.dimension();
            String dimensionName = dimension.location().toString();
            
            chunksUnloadedCounter.labels(dimensionName).inc();
            
            // End measuring chunk generation time
            long chunkKey = event.getChunk().getPos().toLong();
            Long startTime = chunkGenerationStartTimes.remove(chunkKey);
            if (startTime != null) {
                long durationNanos = System.nanoTime() - startTime;
                double durationSeconds = durationNanos / 1_000_000_000.0;
                chunkGenerationTimeHistogram.labels(dimensionName).observe(durationSeconds);
            }
        }
    }
}

