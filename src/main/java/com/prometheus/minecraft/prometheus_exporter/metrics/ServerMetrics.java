package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Info;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class ServerMetrics {
    private final Counter uptimeCounter;
    private final Gauge runningGauge;
    private final Info versionInfo;
    
    private long serverStartTime;
    
    public ServerMetrics(CollectorRegistry registry) {
        uptimeCounter = Counter.build()
            .name("minecraft_server_uptime_seconds_total")
            .help("Total seconds since server startup")
            .register(registry);
        
        runningGauge = Gauge.build()
            .name("minecraft_server_running")
            .help("1 if running, 0 if stopped")
            .register(registry);
        
        versionInfo = Info.build()
            .name("minecraft_server_version")
            .help("Minecraft and NeoForge version information")
            .labelNames("minecraft_version", "neoforge_version")
            .register(registry);
        
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }
    
    public void initialize(MinecraftServer server) {
        serverStartTime = System.currentTimeMillis();
        
        // Set version information
        String mcVersion = SharedConstants.getCurrentVersion().getName();
        // NeoForge version retrieval method has changed, temporarily set to "unknown"
        // Will be updated in the future with proper version retrieval
        String neoForgeVersion = "unknown";
        // For Info metrics with labels, call labels() with label values before calling info()
        versionInfo.labels(mcVersion, neoForgeVersion).info();
        
        runningGauge.set(1.0);
    }
    
    private void onServerStarting(ServerStartingEvent event) {
        serverStartTime = System.currentTimeMillis();
        runningGauge.set(1.0);
    }
    
    private void onServerStopping(ServerStoppingEvent event) {
        runningGauge.set(0.0);
    }
    
    private long lastUptimeSeconds = 0;
    
    public void update() {
        if (serverStartTime > 0) {
            long currentUptimeSeconds = (System.currentTimeMillis() - serverStartTime) / 1000;
            long diff = currentUptimeSeconds - lastUptimeSeconds;
            if (diff > 0) {
                uptimeCounter.inc(diff);
                lastUptimeSeconds = currentUptimeSeconds;
            }
        }
    }
}

