package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerMetrics {
    private final Gauge playersOnlineGauge;
    private final Gauge playersMaxGauge;
    private final Counter playerJoinCounter;
    private final Counter playerLeaveCounter;
    private final Gauge playerPingGauge;
    private final Gauge playerDimensionCountGauge;
    private final Set<String> trackedPlayers = new HashSet<>();
    private final Set<String> trackedDimensions = new HashSet<>();
    
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
        
        // Collect currently online player names
        Set<String> currentOnlinePlayers = new HashSet<>();
        
        // Ping value per player
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String playerName = player.getGameProfile().getName();
            int ping = player.connection.latency();
            playerPingGauge.labels(playerName).set(ping);
            currentOnlinePlayers.add(playerName);
        }
        
        // Reset ping to 0 for players who are no longer online
        for (String playerName : trackedPlayers) {
            if (!currentOnlinePlayers.contains(playerName)) {
                playerPingGauge.labels(playerName).set(0);
            }
        }
        
        // Update tracked players set
        trackedPlayers.clear();
        trackedPlayers.addAll(currentOnlinePlayers);
        
        // Number of players per dimension
        Map<ResourceKey<Level>, Integer> dimensionCounts = new HashMap<>();
        Set<String> currentDimensions = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = player.level().dimension();
            String dimensionName = dimension.location().toString();
            dimensionCounts.put(dimension, dimensionCounts.getOrDefault(dimension, 0) + 1);
            currentDimensions.add(dimensionName);
        }
        
        // Set current dimension counts
        for (Map.Entry<ResourceKey<Level>, Integer> entry : dimensionCounts.entrySet()) {
            String dimensionName = entry.getKey().location().toString();
            playerDimensionCountGauge.labels(dimensionName).set(entry.getValue());
        }
        
        // Reset dimensions that no longer have any players to 0
        for (String dimensionName : trackedDimensions) {
            if (!currentDimensions.contains(dimensionName)) {
                playerDimensionCountGauge.labels(dimensionName).set(0);
            }
        }
        
        // Update tracked dimensions set
        trackedDimensions.clear();
        trackedDimensions.addAll(currentDimensions);
    }
    
    public void onPlayerJoin() {
        playerJoinCounter.inc();
    }
    
    public void onPlayerLeave() {
        playerLeaveCounter.inc();
    }
}

