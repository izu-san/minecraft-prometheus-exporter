package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;

public class EntityTypeMetrics {
    private final Gauge entitiesByTypeGauge;
    private final Gauge tileEntitiesByTypeGauge;
    
    public EntityTypeMetrics(CollectorRegistry registry) {
        entitiesByTypeGauge = Gauge.build()
            .name("minecraft_entities_by_type_total")
            .help("Number of entities by type")
            .labelNames("entity_type", "dimension")
            .register(registry);
        
        tileEntitiesByTypeGauge = Gauge.build()
            .name("minecraft_tile_entities_by_type_total")
            .help("Number of BlockEntities by type")
            .labelNames("block_entity_type", "dimension")
            .register(registry);
    }
    
    public void update(MinecraftServer server) {
        if (server == null) {
            return;
        }
        
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            String dimensionName = dimension.location().toString();
            
            // Statistics by entity type
            Map<String, Integer> entityTypeCounts = new HashMap<>();
            for (Entity entity : level.getEntities().getAll()) {
                String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                entityTypeCounts.put(entityType, entityTypeCounts.getOrDefault(entityType, 0) + 1);
            }
            
            for (Map.Entry<String, Integer> entry : entityTypeCounts.entrySet()) {
                entitiesByTypeGauge.labels(entry.getKey(), dimensionName).set(entry.getValue());
            }
            
            // Statistics by BlockEntity type
            // Note: BlockEntity retrieval requires protected access, currently disabled
            // Will be implemented in the future using Mixin or Access Transformer
            // Map<String, Integer> blockEntityTypeCounts = new HashMap<>();
            // ... BlockEntity statistics code ...
            
            // Temporarily set to 0 (will set actual statistics once implementation is complete)
            // tileEntitiesByTypeGauge.labels(entry.getKey(), dimensionName).set(entry.getValue());
        }
    }
}

