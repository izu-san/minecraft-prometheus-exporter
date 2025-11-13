package com.prometheus.minecraft.prometheus_exporter.config;

import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ExporterConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CONFIG_FILE = "prometheus-exporter.yaml";
    private static String bindAddress = "0.0.0.0";
    private static int port = 9225;
    private static boolean enabled = true;
    private static int mekanismMetricsUpdateInterval = 20; // Update every 20 ticks (default: 1 time per second)
    
    public static void init() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        
        if (!Files.exists(configPath)) {
            createDefaultConfig(configPath);
        }
        
        loadConfig(configPath);
    }
    
    private static void createDefaultConfig(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            
            String defaultConfig = """
                # Prometheus Exporter Configuration
                # HTTP server bind address (default: 0.0.0.0)
                # 0.0.0.0 binds to all network interfaces
                # Specify localhost or 127.0.0.1 to bind only to localhost
                bindAddress: 0.0.0.0
                # HTTP server port number (default: 9225)
                port: 9225
                # Enable exporter (true/false)
                enabled: true
                # Mekanism metrics update interval in ticks (default: 20)
                # Lower values = more frequent updates but higher server load
                # Higher values = less frequent updates but lower server load
                # Recommended: 10-40 ticks (2-0.5 times per second)
                # Default: 20 ticks = 1 update per second
                mekanismMetricsUpdateInterval: 20
                """;
            
            Files.writeString(configPath, defaultConfig, StandardCharsets.UTF_8);
            LOGGER.info("Created default configuration file: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create configuration file", e);
        }
    }
    
    private static void loadConfig(Path configPath) {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Map<String, Object> config = yaml.load(inputStream);
            
            if (config == null) {
                config = new HashMap<>();
            }
            
            // Load bind address setting
            Object bindAddressObj = config.get("bindAddress");
            if (bindAddressObj != null && bindAddressObj instanceof String) {
                bindAddress = (String) bindAddressObj;
            }
            
            // Load port setting
            Object portObj = config.get("port");
            if (portObj != null) {
                if (portObj instanceof Integer) {
                    port = (Integer) portObj;
                } else if (portObj instanceof String) {
                    port = Integer.parseInt((String) portObj);
                } else if (portObj instanceof Number) {
                    port = ((Number) portObj).intValue();
                }
            }
            
            // Load enabled/disabled setting
            Object enabledObj = config.get("enabled");
            if (enabledObj != null) {
                if (enabledObj instanceof Boolean) {
                    enabled = (Boolean) enabledObj;
                } else if (enabledObj instanceof String) {
                    enabled = Boolean.parseBoolean((String) enabledObj);
                }
            }
            
            // Load Mekanism metrics update interval
            Object intervalObj = config.get("mekanismMetricsUpdateInterval");
            if (intervalObj != null) {
                if (intervalObj instanceof Integer) {
                    mekanismMetricsUpdateInterval = Math.max(1, (Integer) intervalObj);
                } else if (intervalObj instanceof String) {
                    mekanismMetricsUpdateInterval = Math.max(1, Integer.parseInt((String) intervalObj));
                } else if (intervalObj instanceof Number) {
                    mekanismMetricsUpdateInterval = Math.max(1, ((Number) intervalObj).intValue());
                }
            }
            
            LOGGER.info("Configuration loaded - Bind address: {}, Port: {}, Enabled: {}, Mekanism update interval: {} ticks", 
                bindAddress, port, enabled, mekanismMetricsUpdateInterval);
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration file", e);
        } catch (Exception e) {
            LOGGER.error("Failed to parse configuration file", e);
        }
    }
    
    public static String getBindAddress() {
        return bindAddress;
    }
    
    public static int getPort() {
        return port;
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static int getMekanismMetricsUpdateInterval() {
        return mekanismMetricsUpdateInterval;
    }
}

