package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceMetrics {
    private final Gauge jvmMemoryUsedGauge;
    private final Gauge jvmMemoryMaxGauge;
    private final Gauge systemCpuLoadGauge;
    private final Counter processUptimeCounter;
    
    // JVM detailed metrics
    private final Counter gcCollectionCountCounter;
    private final Counter gcCollectionTimeCounter;
    private final Gauge jvmMemoryPoolUsedGauge;
    private final Gauge jvmMemoryPoolMaxGauge;
    private final Gauge jvmMemoryPoolCommittedGauge;
    private final Gauge jvmThreadsCurrentGauge;
    private final Gauge jvmThreadsDaemonGauge;
    private final Gauge jvmThreadsPeakGauge;
    
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private long jvmStartTime;
    private long lastUptimeSeconds = 0;
    
    // Keep previous GC statistics values
    private final Map<String, Long> lastGcCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastGcTimes = new ConcurrentHashMap<>();
    
    public ResourceMetrics(CollectorRegistry registry) {
        osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        runtimeBean = ManagementFactory.getRuntimeMXBean();
        memoryBean = ManagementFactory.getMemoryMXBean();
        threadBean = ManagementFactory.getThreadMXBean();
        
        jvmMemoryUsedGauge = Gauge.build()
            .name("jvm_memory_used_bytes")
            .help("Used heap memory")
            .register(registry);
        
        jvmMemoryMaxGauge = Gauge.build()
            .name("jvm_memory_max_bytes")
            .help("JVM maximum heap capacity")
            .register(registry);
        
        systemCpuLoadGauge = Gauge.build()
            .name("system_cpu_load")
            .help("CPU usage")
            .register(registry);
        
        processUptimeCounter = Counter.build()
            .name("process_uptime_seconds_total")
            .help("JVM uptime")
            .register(registry);
        
        // GC statistics
        gcCollectionCountCounter = Counter.build()
            .name("jvm_gc_collection_count_total")
            .help("Total number of GC executions")
            .labelNames("gc")
            .register(registry);
        
        gcCollectionTimeCounter = Counter.build()
            .name("jvm_gc_collection_seconds_total")
            .help("GC execution time in seconds")
            .labelNames("gc")
            .register(registry);
        
        // Memory pool statistics
        jvmMemoryPoolUsedGauge = Gauge.build()
            .name("jvm_memory_pool_bytes_used")
            .help("Memory pool usage")
            .labelNames("pool")
            .register(registry);
        
        jvmMemoryPoolMaxGauge = Gauge.build()
            .name("jvm_memory_pool_bytes_max")
            .help("Memory pool maximum capacity")
            .labelNames("pool")
            .register(registry);
        
        jvmMemoryPoolCommittedGauge = Gauge.build()
            .name("jvm_memory_pool_bytes_committed")
            .help("Memory pool committed amount")
            .labelNames("pool")
            .register(registry);
        
        // Thread statistics
        jvmThreadsCurrentGauge = Gauge.build()
            .name("jvm_threads_current")
            .help("Current number of threads")
            .register(registry);
        
        jvmThreadsDaemonGauge = Gauge.build()
            .name("jvm_threads_daemon")
            .help("Number of daemon threads")
            .register(registry);
        
        jvmThreadsPeakGauge = Gauge.build()
            .name("jvm_threads_peak")
            .help("Peak number of threads")
            .register(registry);
    }
    
    public void initialize() {
        jvmStartTime = runtimeBean.getStartTime();
    }
    
    public void update() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        jvmMemoryUsedGauge.set(usedMemory);
        jvmMemoryMaxGauge.set(maxMemory);
        
        // CPU usage
        double cpuLoad = osBean.getProcessCpuLoad();
        if (cpuLoad >= 0) {
            systemCpuLoadGauge.set(cpuLoad);
        }
        
        // JVM uptime in seconds
        long currentUptimeSeconds = (System.currentTimeMillis() - jvmStartTime) / 1000;
        long diff = currentUptimeSeconds - lastUptimeSeconds;
        if (diff > 0) {
            processUptimeCounter.inc(diff);
            lastUptimeSeconds = currentUptimeSeconds;
        }
        
        // Update GC statistics
        updateGCMetrics();
        
        // Update memory pool statistics
        updateMemoryPoolMetrics();
        
        // Update thread statistics
        updateThreadMetrics();
    }
    
    private void updateGCMetrics() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            
            // Calculate difference from previous value
            Long lastCount = lastGcCounts.get(gcName);
            Long lastTime = lastGcTimes.get(gcName);
            
            if (lastCount != null && count >= lastCount) {
                long countDiff = count - lastCount;
                gcCollectionCountCounter.labels(gcName).inc(countDiff);
            }
            
            if (lastTime != null && time >= lastTime) {
                // Convert milliseconds to seconds
                double timeDiff = (time - lastTime) / 1000.0;
                gcCollectionTimeCounter.labels(gcName).inc(timeDiff);
            }
            
            lastGcCounts.put(gcName, count);
            lastGcTimes.put(gcName, time);
        }
    }
    
    private void updateMemoryPoolMetrics() {
        List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean poolBean : poolBeans) {
            String poolName = poolBean.getName();
            MemoryUsage usage = poolBean.getUsage();
            
            if (usage != null) {
                jvmMemoryPoolUsedGauge.labels(poolName).set(usage.getUsed());
                long max = usage.getMax();
                if (max >= 0) {
                    jvmMemoryPoolMaxGauge.labels(poolName).set(max);
                }
                jvmMemoryPoolCommittedGauge.labels(poolName).set(usage.getCommitted());
            }
        }
    }
    
    private void updateThreadMetrics() {
        int threadCount = threadBean.getThreadCount();
        int daemonThreadCount = threadBean.getDaemonThreadCount();
        long peakThreadCount = threadBean.getPeakThreadCount();
        
        jvmThreadsCurrentGauge.set(threadCount);
        jvmThreadsDaemonGauge.set(daemonThreadCount);
        jvmThreadsPeakGauge.set(peakThreadCount);
    }
}

