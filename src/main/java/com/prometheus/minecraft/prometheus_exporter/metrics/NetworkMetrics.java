package com.prometheus.minecraft.prometheus_exporter.metrics;

import com.prometheus.minecraft.prometheus_exporter.mixin.accessor.INetworkStatistics;
import io.prometheus.client.Counter;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetworkMetrics {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final Counter packetsSentCounter;
    private final Counter packetsReceivedCounter;
    private final Counter bytesSentCounter;
    private final Counter bytesReceivedCounter;
    
    // Track previous values to calculate deltas
    private long lastTotalPacketsSent = 0;
    private long lastTotalPacketsReceived = 0;
    private long lastTotalBytesSent = 0;
    private long lastTotalBytesReceived = 0;
    
    public NetworkMetrics(CollectorRegistry registry) {
        packetsSentCounter = Counter.build()
            .name("minecraft_network_packets_sent_total")
            .help("Total number of packets sent")
            .register(registry);
        
        packetsReceivedCounter = Counter.build()
            .name("minecraft_network_packets_received_total")
            .help("Total number of packets received")
            .register(registry);
        
        bytesSentCounter = Counter.build()
            .name("minecraft_network_bytes_sent_total")
            .help("Total number of bytes sent")
            .register(registry);
        
        bytesReceivedCounter = Counter.build()
            .name("minecraft_network_bytes_received_total")
            .help("Total number of bytes received")
            .register(registry);
    }
    
    public void update(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        
        // Aggregate statistics from all player connections
        long totalPacketsSent = 0;
        long totalPacketsReceived = 0;
        long totalBytesSent = 0;
        long totalBytesReceived = 0;
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Connection connection = player.connection.getConnection();
            if (connection instanceof INetworkStatistics stats) {
                totalPacketsSent += stats.prometheus_exporter$getPacketsSent();
                totalPacketsReceived += stats.prometheus_exporter$getPacketsReceived();
                totalBytesSent += stats.prometheus_exporter$getBytesSent();
                totalBytesReceived += stats.prometheus_exporter$getBytesReceived();
            }
        }
        
        // Calculate deltas and update counters
        long packetsSentDelta = totalPacketsSent - lastTotalPacketsSent;
        long packetsReceivedDelta = totalPacketsReceived - lastTotalPacketsReceived;
        long bytesSentDelta = totalBytesSent - lastTotalBytesSent;
        long bytesReceivedDelta = totalBytesReceived - lastTotalBytesReceived;
        
        if (packetsSentDelta > 0) {
            packetsSentCounter.inc(packetsSentDelta);
        }
        if (packetsReceivedDelta > 0) {
            packetsReceivedCounter.inc(packetsReceivedDelta);
        }
        if (bytesSentDelta > 0) {
            bytesSentCounter.inc(bytesSentDelta);
        }
        if (bytesReceivedDelta > 0) {
            bytesReceivedCounter.inc(bytesReceivedDelta);
        }
        
        // Update last values
        lastTotalPacketsSent = totalPacketsSent;
        lastTotalPacketsReceived = totalPacketsReceived;
        lastTotalBytesSent = totalBytesSent;
        lastTotalBytesReceived = totalBytesReceived;
    }
}

