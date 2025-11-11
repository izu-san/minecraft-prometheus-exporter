package com.prometheus.minecraft.prometheus_exporter;

import com.prometheus.minecraft.prometheus_exporter.config.ExporterConfig;
import com.prometheus.minecraft.prometheus_exporter.metrics.MetricsManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PrometheusExporterMod.MODID)
public class PrometheusExporterMod {
    public static final String MODID = "prometheus_exporter";
    private static final Logger LOGGER = LogManager.getLogger();
    
    private MetricsManager metricsManager;
    
    public PrometheusExporterMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Prometheus Exporter Mod...");
        
        // Load configuration
        ExporterConfig.init();
        
        // Initialize metrics manager
        metricsManager = new MetricsManager();
        
        // Register events
        NeoForge.EVENT_BUS.register(metricsManager);
        NeoForge.EVENT_BUS.addListener(metricsManager::onServerStarting);
        NeoForge.EVENT_BUS.addListener(metricsManager::onServerStopping);
        
        LOGGER.info("Prometheus Exporter Mod initialization completed. Port: {}", ExporterConfig.getPort());
    }
}

