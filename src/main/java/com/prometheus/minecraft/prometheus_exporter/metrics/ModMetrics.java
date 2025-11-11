package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.CollectorRegistry;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Collects metadata related to NeoForge mods such as load status, file information and version details.
 * These metrics are intended to help operators verify which mods are active on the server and
 * inspect basic resource characteristics for each mod jar.
 */
public class ModMetrics {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Gauge modCountGauge;
    private final Gauge modLoadedGauge;
    private final Gauge modVersionGauge;
    private final Gauge modFileSizeGauge;
    private final Gauge modClassCountGauge;
    private final Counter modLoadFailureCounter;
    private final Histogram modLoadTimeHistogram;
    private final Gauge modInfoGauge;

    public ModMetrics(CollectorRegistry registry) {
        modCountGauge = Gauge.build()
            .name("minecraft_mods_loaded_total")
            .help("Total number of mods reported by NeoForge")
            .register(registry);

        modLoadedGauge = Gauge.build()
            .name("minecraft_mod_loaded_status")
            .help("Indicator gauge for loaded mods (value is always 1 when the mod is present)")
            .labelNames("mod_id", "display_name", "namespace")
            .register(registry);

        modVersionGauge = Gauge.build()
            .name("minecraft_mod_version_info")
            .help("Version information for loaded mods (value is always 1)")
            .labelNames("mod_id", "version")
            .register(registry);

        modFileSizeGauge = Gauge.build()
            .name("minecraft_mod_jar_size_bytes")
            .help("Size of the mod jar on disk in bytes")
            .labelNames("mod_id")
            .register(registry);

        modClassCountGauge = Gauge.build()
            .name("minecraft_mod_scanned_class_count")
            .help("Number of classes discovered during NeoForge scanning for each mod file")
            .labelNames("mod_id")
            .register(registry);

        modLoadFailureCounter = Counter.build()
            .name("minecraft_mod_load_failures_total")
            .help("Total number of mod loading failures")
            .labelNames("mod_id", "error_type")
            .register(registry);

        modLoadTimeHistogram = Histogram.build()
            .name("minecraft_mod_load_time_seconds")
            .help("Mod loading time in seconds")
            .labelNames("mod_id")
            .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0)
            .register(registry);

        modInfoGauge = Gauge.build()
            .name("minecraft_mod_info")
            .help("Complete mod information including ID, display name, namespace, version, and JAR size. Value is always 1 (presence indicator).")
            .labelNames("mod_id", "display_name", "namespace", "version", "jar_size_bytes")
            .register(registry);
    }

    /**
     * Refreshes metrics by taking a snapshot of the current mod list.
     * This should be invoked once the server is starting (mods already constructed).
     */
    public void refresh() {
        ModList modList = ModList.get();
        Collection<?> mods = modList.getMods();

        modLoadedGauge.clear();
        modVersionGauge.clear();
        modFileSizeGauge.clear();
        modClassCountGauge.clear();
        modInfoGauge.clear();

        modCountGauge.set(mods.size());

        // Record mod loading failures
        recordModLoadingFailures();

        for (Object modInfo : mods) {
            String modId = getString(modInfo, "getModId");
            if (modId == null || modId.isEmpty()) {
                modId = "unknown";
            }

            String displayName = getString(modInfo, "getDisplayName");
            if (displayName == null || displayName.isEmpty()) {
                displayName = modId;
            }

            modLoadedGauge.labels(modId, displayName, modId).set(1);

            String version = "unknown";
            Object versionObj = invoke(modInfo, "getVersion");
            if (versionObj != null) {
                version = versionObj.toString();
            }
            modVersionGauge.labels(modId, version).set(1);

            Path filePath = resolveModFilePath(modInfo);
            long jarSize = 0;
            if (filePath != null) {
                try {
                    jarSize = Files.size(filePath);
                    modFileSizeGauge.labels(modId).set(jarSize);
                } catch (IOException e) {
                    LOGGER.warn("Failed to determine mod file size for {} ({})", modId, filePath, e);
                }

                Integer classCount = resolveClassCount(modInfo);
                if (classCount != null) {
                    modClassCountGauge.labels(modId).set(classCount);
                }
            }

            // Record all mod information in a single metric for easy table display
            // Value is always 1 (presence indicator), all info is in labels
            String namespace = modId; // Use mod_id as namespace if not available separately
            modInfoGauge.labels(modId, displayName, namespace, version, String.valueOf(jarSize)).set(1);

            // Try to record mod load time if available
            recordModLoadTime(modInfo, modId);
        }
    }

    /**
     * Records mod loading failures from ModLoader.getLoadingIssues()
     */
    private void recordModLoadingFailures() {
        try {
            // Use reflection to access ModLoader.getLoadingIssues()
            Class<?> modLoaderClass = Class.forName("net.neoforged.fml.ModLoader");
            Method getLoadingIssuesMethod = modLoaderClass.getMethod("getLoadingIssues");
            Object issuesObj = getLoadingIssuesMethod.invoke(null);
            
            if (issuesObj instanceof List<?> issues) {
                for (Object issue : issues) {
                    String modId = "unknown";
                    String errorType = "unknown";
                    
                    // Try to get mod ID from the issue
                    String modIdFromIssue = getString(issue, "getModId");
                    if (modIdFromIssue != null && !modIdFromIssue.isEmpty()) {
                        modId = modIdFromIssue;
                    }
                    
                    // Try to get error type/translation key
                    String translationKey = getString(issue, "translationKey");
                    if (translationKey != null && !translationKey.isEmpty()) {
                        errorType = translationKey;
                    } else {
                        // Fallback to class name
                        errorType = issue.getClass().getSimpleName();
                    }
                    
                    modLoadFailureCounter.labels(modId, errorType).inc();
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to access ModLoader.getLoadingIssues()", e);
        }
    }

    /**
     * Records mod load time if available from mod metadata
     */
    private void recordModLoadTime(Object modInfo, String modId) {
        try {
            // Try to get load time from mod container or mod info
            Object container = invoke(modInfo, "getOwningFile");
            if (container != null) {
                // Try various methods that might contain load time information
                Object loadTimeObj = invoke(container, "getLoadTime");
                if (loadTimeObj instanceof Number) {
                    double loadTimeSeconds = ((Number) loadTimeObj).doubleValue() / 1_000_000_000.0; // Convert nanoseconds to seconds
                    modLoadTimeHistogram.labels(modId).observe(loadTimeSeconds);
                }
            }
        } catch (Exception e) {
            // Silently ignore - load time may not be available for all mods
            LOGGER.trace("Could not determine load time for mod {}", modId, e);
        }
    }

    private Path resolveModFilePath(Object modInfo) {
        if (modInfo == null) {
            return null;
        }

        Object owningFile = invoke(modInfo, "getOwningFile");
        if (owningFile == null) {
            return null;
        }

        Object modFile = invoke(owningFile, "getFile");
        if (modFile == null) {
            return null;
        }

        Object filePathObj = invoke(modFile, "getFilePath");
        if (filePathObj instanceof Path path) {
            return path;
        }
        return null;
    }

    private Integer resolveClassCount(Object modInfo) {
        if (modInfo == null) {
            return null;
        }

        Object owningFile = invoke(modInfo, "getOwningFile");
        if (owningFile == null) {
            return null;
        }

        Object modFile = invoke(owningFile, "getFile");
        if (modFile == null) {
            return null;
        }

        Object scanResult = invoke(modFile, "getScanResult");
        if (scanResult == null) {
            return null;
        }

        Object classes = invoke(scanResult, "getClasses");
        if (classes instanceof Collection<?> collection) {
            return collection.size();
        }
        return null;
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to invoke {} on {}", methodName, target.getClass().getName(), e);
            return null;
        }
    }

    private String getString(Object target, String methodName) {
        Object result = invoke(target, methodName);
        return result != null ? result.toString() : null;
    }
}


