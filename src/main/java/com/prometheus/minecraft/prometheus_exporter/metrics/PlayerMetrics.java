package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class PlayerMetrics {
    private final Gauge playersOnlineGauge;
    private final Gauge playersMaxGauge;
    private final Counter playerJoinCounter;
    private final Counter playerLeaveCounter;
    private final Gauge playerPingGauge;
    private final Gauge playerDimensionCountGauge;
    
    public PlayerMetrics(CollectorRegistry registry) {
        playersOnlineGauge = Gauge.build()
            .name("minecraft_players_online")
            .help("Current number of online players")
            .register(registry);
        
        playersMaxGauge = Gauge.build()
            .name("minecraft_players_max")
            .help("Maximum number of connections")
            .register(registry);
        
        playerJoinCounter = Counter.build()
            .name("minecraft_player_join_total")
            .help("Total number of player joins")
            .register(registry);
        
        playerLeaveCounter = Counter.build()
            .name("minecraft_player_leave_total")
            .help("Total number of player leaves")
            .register(registry);
        
        playerPingGauge = Gauge.build()
            .name("minecraft_player_ping_milliseconds")
            .help("Player ping value")
            .labelNames("player")
            .register(registry);
        
        playerDimensionCountGauge = Gauge.build()
            .name("minecraft_player_dimension_count")
            .help("Number of players per dimension")
            .labelNames("dimension")
            .register(registry);
    }
    
    public void update(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        
        int onlineCount = server.getPlayerList().getPlayerCount();
        int maxPlayers = server.getPlayerList().getMaxPlayers();
        
        playersOnlineGauge.set(onlineCount);
        playersMaxGauge.set(maxPlayers);
        
        // Ping value per player
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int ping = player.connection.latency();
            playerPingGauge.labels(player.getGameProfile().getName()).set(ping);
        }
        
        // Number of players per dimension
        Map<ResourceKey<Level>, Integer> dimensionCounts = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = player.level().dimension();
            dimensionCounts.put(dimension, dimensionCounts.getOrDefault(dimension, 0) + 1);
        }
        
        // Clear existing labels before setting new values
        // Note: Prometheus Gauge is independent per label,
        // so old label values need to be reset to 0
        for (Map.Entry<ResourceKey<Level>, Integer> entry : dimensionCounts.entrySet()) {
            playerDimensionCountGauge.labels(entry.getKey().location().toString()).set(entry.getValue());
        }
    }
    
    public void onPlayerJoin() {
        playerJoinCounter.inc();
    }
    
    public void onPlayerLeave() {
        playerLeaveCounter.inc();
    }
}

