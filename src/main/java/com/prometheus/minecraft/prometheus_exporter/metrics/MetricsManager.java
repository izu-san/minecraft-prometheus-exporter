package com.prometheus.minecraft.prometheus_exporter.metrics;

import com.prometheus.minecraft.prometheus_exporter.config.ExporterConfig;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MetricsManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private HTTPServer httpServer;
    private CollectorRegistry registry;
    private MinecraftServer server;
    private long serverStartTime;
    
    private ServerMetrics serverMetrics;
    private PerformanceMetrics performanceMetrics;
    private PlayerMetrics playerMetrics;
    private WorldMetrics worldMetrics;
    private ResourceMetrics resourceMetrics;
    private NetworkMetrics networkMetrics;
    private EntityTypeMetrics entityTypeMetrics;
    private ChunkMetrics chunkMetrics;
    private ErrorMetrics errorMetrics;
    private ModMetrics modMetrics;
    
    public MetricsManager() {
        registry = CollectorRegistry.defaultRegistry;
        serverMetrics = new ServerMetrics(registry);
        performanceMetrics = new PerformanceMetrics(registry);
        playerMetrics = new PlayerMetrics(registry);
        worldMetrics = new WorldMetrics(registry);
        resourceMetrics = new ResourceMetrics(registry);
        networkMetrics = new NetworkMetrics(registry);
        entityTypeMetrics = new EntityTypeMetrics(registry);
        chunkMetrics = new ChunkMetrics(registry);
        errorMetrics = new ErrorMetrics(registry);
        modMetrics = new ModMetrics(registry);
    }
    
    public void onServerStarting(ServerStartingEvent event) {
        if (!ExporterConfig.isEnabled()) {
            LOGGER.info("Prometheus Exporter is disabled by configuration.");
            return;
        }
        
        server = event.getServer();
        serverStartTime = System.currentTimeMillis();
        
        try {
            String bindAddress = ExporterConfig.getBindAddress();
            int port = ExporterConfig.getPort();
            httpServer = new HTTPServer(new InetSocketAddress(bindAddress, port), registry, true);
            LOGGER.info("Prometheus HTTP server started. Bind address: {}, Port: {}", bindAddress, port);
            LOGGER.info("Metrics endpoint: http://{}:{}/metrics", bindAddress, port);
        } catch (IOException e) {
            LOGGER.error("Failed to start Prometheus HTTP server", e);
        }
        
        // Initialize metrics on server startup
        serverMetrics.initialize(server);
        resourceMetrics.initialize();
        modMetrics.refresh();
        
        // Register WorldMetrics event listeners
        NeoForge.EVENT_BUS.addListener(worldMetrics::onLevelTickStart);
        NeoForge.EVENT_BUS.addListener(worldMetrics::onLevelTickEnd);
    }
    
    public void onServerStopping(ServerStoppingEvent event) {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("Prometheus HTTP server stopped.");
        }
    }
    
    @SubscribeEvent
    public void onServerTick(Pre event) {
        if (server == null) {
            return;
        }
        
        long tickStartTime = System.currentTimeMillis();
        
        // Update server metrics
        serverMetrics.update();
        
        // Update performance metrics
        performanceMetrics.update(server);
        
        // Update player metrics
        playerMetrics.update(server);
        
        // Update world metrics
        worldMetrics.update(server);
        
        // Update resource metrics
        resourceMetrics.update();
        
        // Update network metrics
        networkMetrics.update(server);
        
        // Update entity type metrics
        entityTypeMetrics.update(server);
        
        // Record tick processing time
        long tickDuration = System.currentTimeMillis() - tickStartTime;
        performanceMetrics.recordTickDuration(tickDuration);
    }
    
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (server != null) {
            playerMetrics.onPlayerJoin();
        }
    }
    
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server != null) {
            playerMetrics.onPlayerLeave();
        }
    }
}

