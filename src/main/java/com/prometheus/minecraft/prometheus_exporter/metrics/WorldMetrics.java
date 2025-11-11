package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Pre;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

import java.util.HashMap;
import java.util.Map;

public class WorldMetrics {
    private final Histogram worldTickTimeHistogram;
    private final Gauge worldEntitiesGauge;
    private final Gauge worldChunksGauge;
    
    private final Map<ResourceKey<Level>, Long> worldTickStartTimes = new HashMap<>();
    
    public WorldMetrics(CollectorRegistry registry) {
        worldTickTimeHistogram = Histogram.build()
            .name("minecraft_world_tick_time_seconds")
            .help("Tick processing time per dimension")
            .labelNames("dimension")
            .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0)
            .register(registry);
        
        worldEntitiesGauge = Gauge.build()
            .name("minecraft_world_entities_total")
            .help("Number of entities per dimension")
            .labelNames("dimension")
            .register(registry);
        
        worldChunksGauge = Gauge.build()
            .name("minecraft_world_chunks_loaded")
            .help("Number of chunks per dimension")
            .labelNames("dimension")
            .register(registry);
    }
    
    public void update(MinecraftServer server) {
        if (server == null) {
            return;
        }
        
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            String dimensionName = dimension.location().toString();
            
            // Entity count
            // getAll() returns Iterable, so use StreamSupport to count
            int entityCount = (int) java.util.stream.StreamSupport.stream(
                level.getEntities().getAll().spliterator(), false).count();
            worldEntitiesGauge.labels(dimensionName).set(entityCount);
            
            // Chunk count
            int chunkCount = level.getChunkSource().getLoadedChunksCount();
            worldChunksGauge.labels(dimensionName).set(chunkCount);
        }
    }
    
    public void onLevelTickStart(Pre event) {
        Level level = event.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel) level;
            ResourceKey<Level> dimension = serverLevel.dimension();
            worldTickStartTimes.put(dimension, System.nanoTime());
        }
    }
    
    public void onLevelTickEnd(Post event) {
        Level level = event.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel) level;
            ResourceKey<Level> dimension = serverLevel.dimension();
            Long startTime = worldTickStartTimes.get(dimension);
            
            if (startTime != null) {
                long durationNanos = System.nanoTime() - startTime;
                double durationSeconds = durationNanos / 1_000_000_000.0;
                worldTickTimeHistogram.labels(dimension.location().toString()).observe(durationSeconds);
                worldTickStartTimes.remove(dimension);
            }
        }
    }
}

