package com.prometheus.minecraft.prometheus_exporter.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects metrics from MekanismGenerators mod, including fission and fusion reactor data,
 * industrial turbine power generation, and induction matrix energy storage.
 * Fission reactor: burn rate (mB/tick), coolant capacity, and fuel capacity.
 * Fusion reactor: injection rate (mB/tick), coolant capacity, deuterium/tritium capacity, and fusion fuel capacity.
 * Industrial Turbine: power generation rate (FE/tick).
 * Induction Matrix: stored energy (FE) and energy capacity (FE).
 */
public class MekanismMetrics {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final Gauge fissionReactorBurnRateGauge;
    private final Gauge fissionReactorCoolantCapacityGauge;
    private final Gauge fissionReactorFuelCapacityGauge;
    
    private final Gauge fusionReactorInjectionRateGauge;
    private final Gauge fusionReactorCoolantCapacityGauge;
    private final Gauge fusionReactorDeuteriumCapacityGauge;
    private final Gauge fusionReactorTritiumCapacityGauge;
    private final Gauge fusionReactorFuelCapacityGauge;
    
    private final Gauge turbinePowerGenerationGauge;
    
    private final Gauge inductionMatrixEnergyGauge;
    private final Gauge inductionMatrixEnergyCapacityGauge;
    
    // Reflection cache for Mekanism classes
    private Class<?> fissionReactorPortClass;
    private Class<?> fissionReactorMultiblockDataClass;
    private Class<?> fusionReactorPortClass;
    private Class<?> fusionReactorMultiblockDataClass;
    private Class<?> turbineValveClass;
    private Class<?> turbineMultiblockDataClass;
    private Class<?> inductionPortClass;
    private Class<?> matrixMultiblockDataClass;
    private Method getMultiblockMethod;
    private Method getBurnRateMethod;
    private Method getActualBurnRateMethod;
    private Method getCoolantCapacityMethod;
    private Method getFuelCapacityMethod;
    private Method getFuelTankMethod;
    private Method getFuelTankCapacityMethod;
    
    // Fusion reactor reflection methods
    private Method getFusionMultiblockMethod;
    private Method getInjectionRateMethod;
    private Method getWaterTankMethod;
    private Method getWaterTankCapacityMethod;
    private Method getDeuteriumTankMethod;
    private Method getTritiumTankMethod;
    private Method getFusionFuelTankMethod;
    private Method getChemicalTankCapacityMethod;
    
    // Turbine reflection methods
    private Method getTurbineMultiblockMethod;
    private Method getProductionRateMethod;
    
    // Induction Matrix reflection methods
    private Method getInductionMultiblockMethod;
    private Method getEnergyMethod;
    private Method getStorageCapMethod;
    
    private boolean mekanismAvailable = false;
    private int updateCounter = 0;
    
    // Cache for reflection fields to improve performance
    private java.lang.reflect.Field cachedChunksField = null;
    private Method cachedGetBlockEntitiesMethod = null;
    private Method cachedGetChunkMapMethod = null;
    private Method cachedGetChunksMethod = null;
    private boolean getChunksMethodUnavailable = false;
    private final Map<Class<?>, Method> chunkHolderMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, java.lang.reflect.Field> chunkHolderFieldCache = new ConcurrentHashMap<>();
    private final Map<Object, BlockPos> fissionAnchorCache = new IdentityHashMap<>();
    private final Map<Object, BlockPos> fusionAnchorCache = new IdentityHashMap<>();
    private final Map<Object, BlockPos> turbineAnchorCache = new IdentityHashMap<>();
    private final Map<Object, BlockPos> inductionAnchorCache = new IdentityHashMap<>();
    private static final int RESCAN_INTERVAL_UPDATES = 120;
    private static final int MAX_CHUNKS_PER_SCAN = 200;
    private int rescanCooldown = 0;
    private final Map<ResourceKey<Level>, Set<BlockPos>> knownFissionAnchors = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<BlockPos>> knownFusionAnchors = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<BlockPos>> knownTurbineAnchors = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<BlockPos>> knownInductionAnchors = new ConcurrentHashMap<>();
    
    public MekanismMetrics(CollectorRegistry registry) {
        fissionReactorBurnRateGauge = Gauge.build()
            .name("mekanism_fission_reactor_burn_rate_mb_per_tick")
            .help("Fission reactor burn rate in mB/tick (actual burn rate)")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fissionReactorCoolantCapacityGauge = Gauge.build()
            .name("mekanism_fission_reactor_coolant_capacity_mb")
            .help("Fission reactor coolant capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fissionReactorFuelCapacityGauge = Gauge.build()
            .name("mekanism_fission_reactor_fuel_capacity_mb")
            .help("Fission reactor fuel capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fusionReactorInjectionRateGauge = Gauge.build()
            .name("mekanism_fusion_reactor_injection_rate_mb_per_tick")
            .help("Fusion reactor injection rate in mB/tick")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fusionReactorCoolantCapacityGauge = Gauge.build()
            .name("mekanism_fusion_reactor_coolant_capacity_mb")
            .help("Fusion reactor coolant (water) capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fusionReactorDeuteriumCapacityGauge = Gauge.build()
            .name("mekanism_fusion_reactor_deuterium_capacity_mb")
            .help("Fusion reactor deuterium fuel capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fusionReactorTritiumCapacityGauge = Gauge.build()
            .name("mekanism_fusion_reactor_tritium_capacity_mb")
            .help("Fusion reactor tritium fuel capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        fusionReactorFuelCapacityGauge = Gauge.build()
            .name("mekanism_fusion_reactor_fuel_capacity_mb")
            .help("Fusion reactor fusion fuel (DT Fuel) capacity in mB")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        turbinePowerGenerationGauge = Gauge.build()
            .name("mekanism_turbine_power_generation_fe_per_tick")
            .help("Industrial turbine power generation rate in FE/tick")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        inductionMatrixEnergyGauge = Gauge.build()
            .name("mekanism_induction_matrix_energy_fe")
            .help("Induction matrix stored energy in FE")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        inductionMatrixEnergyCapacityGauge = Gauge.build()
            .name("mekanism_induction_matrix_energy_capacity_fe")
            .help("Induction matrix energy capacity in FE")
            .labelNames("dimension", "x", "y", "z")
            .register(registry);
        
        // Check if MekanismGenerators is loaded and initialize reflection
        checkMekanismAvailable();
    }
    
    private void checkMekanismAvailable() {
        try {
            ModList modList = ModList.get();
            if (!modList.isLoaded("mekanismgenerators")) {
                LOGGER.debug("MekanismGenerators is not loaded, skipping Mekanism metrics");
                return;
            }
            
            LOGGER.info("MekanismGenerators detected, initializing fission reactor metrics...");
            
            // Try to find the fission reactor port class
            // This is the TileEntity that we can access from the world
            String portClassName = "mekanism.generators.common.tile.fission.TileEntityFissionReactorPort";
            try {
                fissionReactorPortClass = Class.forName(portClassName);
                LOGGER.debug("Found fission reactor port class: {}", portClassName);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Could not find fission reactor port class: {}", portClassName, e);
                return;
            }
            
            // Try to find the multiblock data class
            String multiblockDataClassName = "mekanism.generators.common.content.fission.FissionReactorMultiblockData";
            try {
                fissionReactorMultiblockDataClass = Class.forName(multiblockDataClassName);
                LOGGER.debug("Found fission reactor multiblock data class: {}", multiblockDataClassName);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Could not find fission reactor multiblock data class: {}", multiblockDataClassName, e);
                return;
            }
            
            // Get methods from TileEntityFissionReactorPort
            try {
                getMultiblockMethod = fissionReactorPortClass.getMethod("getMultiblock");
                LOGGER.debug("Found getMultiblock method");
            } catch (NoSuchMethodException e) {
                LOGGER.warn("Could not find getMultiblock method", e);
                return;
            }
            
            // Get methods from FissionReactorMultiblockData
            // Note: We'll make initialization more resilient - if one method fails, we continue with others
            // Each method/field is tried independently, and failures are logged but don't stop initialization
            
            // Try to get lastBurnRate field (actual burn rate)
            try {
                getActualBurnRateMethod = fissionReactorMultiblockDataClass.getMethod("getActualBurnRate");
                LOGGER.debug("Found getActualBurnRate method");
            } catch (NoSuchMethodException e) {
                // Fallback to field access
                boolean found = false;
                for (String fieldName : new String[]{"lastBurnRate", "actualBurnRate", "burnRate"}) {
                    try {
                        java.lang.reflect.Field field = fissionReactorMultiblockDataClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        getActualBurnRateMethod = null; // We'll use field access instead
                        LOGGER.debug("Found {} field for burn rate", fieldName);
                        found = true;
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (!found) {
                    LOGGER.debug("Could not find burn rate method or field - burn rate metrics may be unavailable");
                }
            }
            
            // Get burn rate (configured rate) - optional, not critical
            try {
                getBurnRateMethod = fissionReactorMultiblockDataClass.getMethod("getBurnRate");
                LOGGER.debug("Found getBurnRate method");
            } catch (NoSuchMethodException e) {
                try {
                    java.lang.reflect.Field rateLimitField = fissionReactorMultiblockDataClass.getDeclaredField("rateLimit");
                    rateLimitField.setAccessible(true);
                    getBurnRateMethod = null;
                    LOGGER.debug("Found rateLimit field");
                } catch (NoSuchFieldException ex) {
                    LOGGER.debug("Could not find rateLimit field or method (optional)");
                }
            }
            
            // Get coolant capacity - try multiple approaches, but don't fail if not found
            // Note: @ComputerMethod annotated methods may not be directly accessible via reflection
            LOGGER.info("Initializing coolant capacity access for fission reactor...");
            try {
                // First try: getCoolantCapacity() method (public method)
                getCoolantCapacityMethod = fissionReactorMultiblockDataClass.getMethod("getCoolantCapacity");
                LOGGER.info("✓ Found getCoolantCapacity() method - will use direct method call");
            } catch (NoSuchMethodException e) {
                // Log available methods for debugging
                LOGGER.info("✗ getCoolantCapacity() not found. Searching for alternative methods...");
                try {
                    Method[] methods = fissionReactorMultiblockDataClass.getMethods();
                    int coolantMethodCount = 0;
                    for (Method m : methods) {
                        if (m.getName().toLowerCase().contains("coolant")) {
                            coolantMethodCount++;
                            LOGGER.info("  Found method: {} (return: {})", m.getName(), m.getReturnType().getSimpleName());
                        }
                    }
                    if (coolantMethodCount == 0) {
                        LOGGER.info("  No methods containing 'coolant' found");
                    }
                } catch (Exception ignored) {
                }
                
                // Second try: getCoolantTank() and then get capacity from tank
                try {
                    Method getCoolantTankMethod = fissionReactorMultiblockDataClass.getMethod("getCoolantTank");
                    // Store the tank getter - we'll use it in updateFissionReactorMetrics to get the tank, then getCapacity
                    getCoolantCapacityMethod = getCoolantTankMethod;
                    LOGGER.info("✓ Found getCoolantTank() method - will get capacity from tank object");
                } catch (NoSuchMethodException ex) {
                    // Third try: getCoolant() method (returns Either<ChemicalStack, FluidStack>)
                    try {
                        Method getCoolantMethod = fissionReactorMultiblockDataClass.getMethod("getCoolant");
                        // This returns Either, but we can't easily get capacity from it
                        // So we'll try to access the coolantTank field directly
                        LOGGER.info("✓ Found getCoolant() method (returns Either) - will try to access coolantTank field");
                        getCoolantCapacityMethod = null; // We'll use field access instead
                    } catch (NoSuchMethodException ex2) {
                        // Fourth try: field access - access coolantTank field and get capacity from it
                        boolean found = false;
                        try {
                            // Try to access the coolantTank field (public final MergedTank)
                            java.lang.reflect.Field coolantTankField = fissionReactorMultiblockDataClass.getField("coolantTank");
                            coolantTankField.setAccessible(true);
                            getCoolantCapacityMethod = null; // We'll use field access
                            LOGGER.info("✓ Found coolantTank field (public) - will get capacity from MergedTank");
                            found = true;
                        } catch (NoSuchFieldException fieldEx) {
                            // Try alternative field names
                            LOGGER.info("  coolantTank field not found, trying alternative field names...");
                            for (String fieldName : new String[]{"coolantCapacity", "maxCoolant", "coolantTankCapacity", "cooledCoolantCapacity"}) {
                                try {
                                    java.lang.reflect.Field field = fissionReactorMultiblockDataClass.getDeclaredField(fieldName);
                                    field.setAccessible(true);
                                    getCoolantCapacityMethod = null; // We'll use field access
                                    LOGGER.info("✓ Found {} field (private) - will use field access", fieldName);
                                    found = true;
                                    break;
                                } catch (NoSuchFieldException ignored) {
                                }
                            }
                        }
                        if (!found) {
                            LOGGER.warn("✗ Could not find any coolant capacity method or field - coolant metrics will be disabled");
                            getCoolantCapacityMethod = null;
                        }
                    }
                }
            }
            
            // Get fuel tank and capacity
            Class<?> chemicalTankClass = null;
            try {
                chemicalTankClass = Class.forName("mekanism.api.chemical.IChemicalTank");
                try {
                    getFuelTankCapacityMethod = chemicalTankClass.getMethod("getCapacity");
                } catch (NoSuchMethodException capacityEx) {
                    LOGGER.debug("IChemicalTank.getCapacity() not found: {}", capacityEx.getMessage());
                    getFuelTankCapacityMethod = null;
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("IChemicalTank class not found: {}", e.getMessage());
            }

            Method resolvedFuelMethod = null;
            String[] fuelMethodCandidates = {"getFuelTank", "getFuel", "fuelTank"};
            for (String candidate : fuelMethodCandidates) {
                try {
                    resolvedFuelMethod = fissionReactorMultiblockDataClass.getMethod(candidate);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (resolvedFuelMethod == null && chemicalTankClass != null) {
                for (Method method : fissionReactorMultiblockDataClass.getMethods()) {
                    if (method.getParameterCount() == 0
                        && chemicalTankClass.isAssignableFrom(method.getReturnType())
                        && method.getName().toLowerCase().contains("fuel")) {
                        resolvedFuelMethod = method;
                        break;
                    }
                }
            }
            if (resolvedFuelMethod != null) {
                getFuelTankMethod = resolvedFuelMethod;
                LOGGER.debug("Resolved fission fuel tank accessor: {}", resolvedFuelMethod.getName());
            } else {
                LOGGER.debug("Fission fuel tank accessor not found via methods, will rely on field fallbacks");
                boolean found = false;
                for (String fieldName : new String[]{"fuelCapacity", "maxFuel", "fuelTankCapacity"}) {
                    try {
                        java.lang.reflect.Field field = fissionReactorMultiblockDataClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        found = true;
                        LOGGER.debug("Found {} field for fuel capacity fallback", fieldName);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (!found) {
                    LOGGER.debug("Could not find fuel capacity field fallback - fuel metrics may be unavailable");
                }
            }
            
            // Log that we've completed fission reactor initialization attempts
            LOGGER.info("Completed fission reactor method discovery - some methods may use field access or be unavailable");
            
            // Initialize fusion reactor reflection
            String fusionPortClassName = "mekanism.generators.common.tile.fusion.TileEntityFusionReactorPort";
            try {
                fusionReactorPortClass = Class.forName(fusionPortClassName);
                LOGGER.debug("Found fusion reactor port class: {}", fusionPortClassName);
                
                String fusionMultiblockDataClassName = "mekanism.generators.common.content.fusion.FusionReactorMultiblockData";
                fusionReactorMultiblockDataClass = Class.forName(fusionMultiblockDataClassName);
                LOGGER.debug("Found fusion reactor multiblock data class: {}", fusionMultiblockDataClassName);
                
                // Get methods from TileEntityFusionReactorPort (same as fission)
                getFusionMultiblockMethod = fusionReactorPortClass.getMethod("getMultiblock");
                
                // Get methods from FusionReactorMultiblockData
                getInjectionRateMethod = fusionReactorMultiblockDataClass.getMethod("getInjectionRate");
                
                // Get tank methods
                getWaterTankMethod = fusionReactorMultiblockDataClass.getMethod("getWater");
                getDeuteriumTankMethod = fusionReactorMultiblockDataClass.getMethod("getDeuterium");
                getTritiumTankMethod = fusionReactorMultiblockDataClass.getMethod("getTritium");
                getFusionFuelTankMethod = fusionReactorMultiblockDataClass.getMethod("getDTFuel");
                
                // Get capacity methods from tanks
                Class<?> fluidTankClass = Class.forName("mekanism.api.fluid.IExtendedFluidTank");
                getWaterTankCapacityMethod = fluidTankClass.getMethod("getCapacity");
                
                Class<?> fusionChemicalTankClass = Class.forName("mekanism.api.chemical.IChemicalTank");
                getChemicalTankCapacityMethod = fusionChemicalTankClass.getMethod("getCapacity");
                
                LOGGER.debug("Fusion reactor reflection methods initialized");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                LOGGER.warn("Failed to initialize fusion reactor reflection methods, fusion reactor metrics will be disabled", e);
                fusionReactorPortClass = null;
                fusionReactorMultiblockDataClass = null;
            }
            
            // Initialize turbine reflection (MekanismGenerators)
            String turbineValveClassName = "mekanism.generators.common.tile.turbine.TileEntityTurbineValve";
            try {
                turbineValveClass = Class.forName(turbineValveClassName);
                LOGGER.debug("Found turbine valve class: {}", turbineValveClassName);
                
                String turbineMultiblockDataClassName = "mekanism.generators.common.content.turbine.TurbineMultiblockData";
                turbineMultiblockDataClass = Class.forName(turbineMultiblockDataClassName);
                LOGGER.debug("Found turbine multiblock data class: {}", turbineMultiblockDataClassName);
                
                getTurbineMultiblockMethod = turbineValveClass.getMethod("getMultiblock");
                getProductionRateMethod = turbineMultiblockDataClass.getMethod("getProductionRate");
                
                LOGGER.debug("Turbine reflection methods initialized");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                LOGGER.warn("Failed to initialize turbine reflection methods, turbine metrics will be disabled", e);
                turbineValveClass = null;
                turbineMultiblockDataClass = null;
            }
            
            // Initialize induction matrix reflection (Mekanism core)
            String inductionPortClassName = "mekanism.common.tile.multiblock.TileEntityInductionPort";
            try {
                inductionPortClass = Class.forName(inductionPortClassName);
                LOGGER.debug("Found induction port class: {}", inductionPortClassName);
                
                String matrixMultiblockDataClassName = "mekanism.common.content.matrix.MatrixMultiblockData";
                matrixMultiblockDataClass = Class.forName(matrixMultiblockDataClassName);
                LOGGER.debug("Found matrix multiblock data class: {}", matrixMultiblockDataClassName);
                
                getInductionMultiblockMethod = inductionPortClass.getMethod("getMultiblock");
                getEnergyMethod = matrixMultiblockDataClass.getMethod("getEnergy");
                getStorageCapMethod = matrixMultiblockDataClass.getMethod("getStorageCap");
                
                LOGGER.debug("Induction matrix reflection methods initialized");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                LOGGER.warn("Failed to initialize induction matrix reflection methods, induction matrix metrics will be disabled", e);
                inductionPortClass = null;
                matrixMultiblockDataClass = null;
            }
            
            // Mark as available if we found at least the port class and multiblock method
            if (fissionReactorPortClass != null && getMultiblockMethod != null) {
                mekanismAvailable = true;
                LOGGER.info("MekanismGenerators fission reactor metrics initialized successfully");
            } else {
                LOGGER.warn("MekanismGenerators detected but could not initialize fission reactor metrics - port class or multiblock method not found");
            }
        } catch (Exception e) {
            // Log the error but don't fail completely if we have the essential classes
            LOGGER.warn("Error during Mekanism reflection initialization: {}", e.getMessage());
            LOGGER.debug("Full exception:", e);
            
            // If we have at least the port class and multiblock method, we can still try to use field access
            if (fissionReactorPortClass != null && getMultiblockMethod != null) {
                mekanismAvailable = true;
                LOGGER.info("Mekanism metrics partially initialized - will attempt to use field access for data retrieval");
            }
        }
        
        // Log initialization status
        if (mekanismAvailable) {
            LOGGER.info("Mekanism metrics initialized - Fission: {}, Fusion: {}, Turbine: {}, Induction Matrix: {}", 
                fissionReactorPortClass != null ? "enabled" : "disabled",
                fusionReactorPortClass != null ? "enabled" : "disabled",
                turbineValveClass != null ? "enabled" : "disabled",
                inductionPortClass != null ? "enabled" : "disabled");
        } else {
            LOGGER.info("Mekanism metrics not initialized - MekanismGenerators mod not found or essential classes not available");
        }
    }
    
    public void update(MinecraftServer server) {
        if (!mekanismAvailable || server == null) {
            return;
        }

        updateCounter++;
        int updateInterval = com.prometheus.minecraft.prometheus_exporter.config.ExporterConfig.getMekanismMetricsUpdateInterval();
        if (updateCounter < updateInterval) {
            return;
        }
        updateCounter = 0;

        fissionReactorBurnRateGauge.clear();
        fissionReactorCoolantCapacityGauge.clear();
        fissionReactorFuelCapacityGauge.clear();

        fusionReactorInjectionRateGauge.clear();
        fusionReactorCoolantCapacityGauge.clear();
        fusionReactorDeuteriumCapacityGauge.clear();
        fusionReactorTritiumCapacityGauge.clear();
        fusionReactorFuelCapacityGauge.clear();

        turbinePowerGenerationGauge.clear();

        inductionMatrixEnergyGauge.clear();
        inductionMatrixEnergyCapacityGauge.clear();

        Set<Object> processedFission = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> processedFusion = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> processedTurbine = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> processedInduction = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenFission = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenFusion = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenTurbine = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenInduction = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimensionKey = level.dimension();
            String dimensionName = dimensionKey.location().toString();
            processKnownFissionAnchors(level, dimensionKey, dimensionName, processedFission, seenFission);
            processKnownFusionAnchors(level, dimensionKey, dimensionName, processedFusion, seenFusion);
            processKnownTurbineAnchors(level, dimensionKey, dimensionName, processedTurbine, seenTurbine);
            processKnownInductionAnchors(level, dimensionKey, dimensionName, processedInduction, seenInduction);
        }

        boolean shouldPerformScan = rescanCooldown <= 0 || !hasKnownAnchors();
        if (shouldPerformScan) {
            rescanCooldown = RESCAN_INTERVAL_UPDATES;
            performWorldScan(server, processedFission, processedFusion, processedTurbine, processedInduction,
                seenFission, seenFusion, seenTurbine, seenInduction);
        } else if (rescanCooldown > 0) {
            rescanCooldown--;
        }

        pruneAnchorCache(fissionAnchorCache, seenFission);
        pruneAnchorCache(fusionAnchorCache, seenFusion);
        pruneAnchorCache(turbineAnchorCache, seenTurbine);
        pruneAnchorCache(inductionAnchorCache, seenInduction);
    }

    private void performWorldScan(MinecraftServer server,
                                  Set<Object> processedFission, Set<Object> processedFusion,
                                  Set<Object> processedTurbine, Set<Object> processedInduction,
                                  Set<Object> seenFission, Set<Object> seenFusion,
                                  Set<Object> seenTurbine, Set<Object> seenInduction) {
        int remainingBudget = MAX_CHUNKS_PER_SCAN;

        for (ServerLevel level : server.getAllLevels()) {
            if (remainingBudget <= 0) {
                break;
            }

            ResourceKey<Level> dimensionKey = level.dimension();
            String dimensionName = dimensionKey.location().toString();

            try {
                var chunkSource = level.getChunkSource();
                if (cachedGetChunkMapMethod == null
                    || !cachedGetChunkMapMethod.getDeclaringClass().isAssignableFrom(chunkSource.getClass())) {
                    cachedGetChunkMapMethod = null;
                }

                if (cachedGetChunkMapMethod == null) {
                    try {
                        cachedGetChunkMapMethod = chunkSource.getClass().getMethod("getChunkMap");
                    } catch (NoSuchMethodException e) {
                        try {
                            cachedGetChunkMapMethod = chunkSource.getClass().getDeclaredMethod("chunkMap");
                            cachedGetChunkMapMethod.setAccessible(true);
                        } catch (NoSuchMethodException ex) {
                            LOGGER.warn("Could not find getChunkMap method in ChunkSource for dimension: {}", dimensionName);
                            continue;
                        }
                    }
                }

                Object chunkMap;
                try {
                    chunkMap = cachedGetChunkMapMethod.invoke(chunkSource);
                } catch (Exception e) {
                    LOGGER.warn("Failed to invoke getChunkMap for dimension: {}", dimensionName, e);
                    continue;
                }

                if (chunkMap == null) {
                    LOGGER.debug("ChunkMap is null for dimension: {}", dimensionName);
                    continue;
                }

                Object chunksObj = null;
                boolean usedGetChunksMethod = false;

                if (!getChunksMethodUnavailable) {
                    if (cachedGetChunksMethod == null
                        || !cachedGetChunksMethod.getDeclaringClass().isAssignableFrom(chunkMap.getClass())) {
                        cachedGetChunksMethod = null;
                    }

                    if (cachedGetChunksMethod == null) {
                        try {
                            cachedGetChunksMethod = chunkMap.getClass().getMethod("getChunks");
                            usedGetChunksMethod = true;
                        } catch (NoSuchMethodException e) {
                            getChunksMethodUnavailable = true;
                        }
                    }

                    if (cachedGetChunksMethod != null) {
                        try {
                            chunksObj = cachedGetChunksMethod.invoke(chunkMap);
                            usedGetChunksMethod = true;
                        } catch (Exception e) {
                            LOGGER.warn("Failed to invoke ChunkMap.getChunks() for dimension: {}", dimensionName, e);
                            chunksObj = null;
                            getChunksMethodUnavailable = true;
                        }
                    }
                }

                if (chunksObj == null) {
                    if (cachedChunksField == null
                        || !cachedChunksField.getDeclaringClass().isAssignableFrom(chunkMap.getClass())) {
                        cachedChunksField = null;
                    }

                    if (cachedChunksField == null) {
                        String[] possibleFieldNames = {
                            "chunks",
                            "updatingChunkMap",
                            "loadedChunks",
                            "chunkCache",
                            "visibleChunkMap",
                            "entityMap",
                            "chunkMap"
                        };

                        for (String fieldName : possibleFieldNames) {
                            try {
                                cachedChunksField = chunkMap.getClass().getDeclaredField(fieldName);
                                cachedChunksField.setAccessible(true);
                                break;
                            } catch (NoSuchFieldException ignored) {
                            }
                        }

                        if (cachedChunksField == null) {
                            LOGGER.warn("Could not find chunks field in ChunkMap for dimension: {}. Available fields:", dimensionName);
                            try {
                                java.lang.reflect.Field[] fields = chunkMap.getClass().getDeclaredFields();
                                for (java.lang.reflect.Field field : fields) {
                                    LOGGER.warn("  Field: {} (type: {})", field.getName(), field.getType().getSimpleName());
                                }
                            } catch (Exception e) {
                                LOGGER.warn("  Failed to list fields", e);
                            }
                        }
                    }

                    if (cachedChunksField != null) {
                        try {
                            chunksObj = cachedChunksField.get(chunkMap);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to get chunks field from ChunkMap for dimension: {}", dimensionName, e);
                            chunksObj = null;
                        }
                    }
                }

                if (chunksObj == null) {
                    continue;
                }

                Iterable<?> chunkIterable;
                if (chunksObj instanceof Map<?, ?> chunksMap) {
                    if (chunksMap.isEmpty()) {
                        continue;
                    }
                    chunkIterable = chunksMap.values();
                } else if (chunksObj instanceof Iterable<?> iterable) {
                    chunkIterable = iterable;
                } else {
                    LOGGER.warn("Chunks container is not a Map or Iterable (type: {}) for dimension: {}",
                        chunksObj.getClass().getName(), dimensionName);
                    continue;
                }

                int processedChunks = 0;
                int foundBlockEntities = 0;
                int foundFissionPorts = 0;
                int sampleCount = 0;

                for (Object chunkObj : chunkIterable) {
                    if (remainingBudget <= 0) {
                        break;
                    }

                    if (sampleCount < 3) {
                        LOGGER.debug("Sample chunk object type for dimension {}: {} (source: {})",
                            dimensionName, chunkObj != null ? chunkObj.getClass().getName() : "null",
                            usedGetChunksMethod ? "ChunkMap.getChunks()" :
                                (cachedChunksField != null ? cachedChunksField.getName() : "Iterable"));
                        sampleCount++;
                    }

                    LevelChunk chunk = resolveLevelChunk(chunkObj);
                    if (chunk == null) {
                        continue;
                    }

                    processedChunks++;
                    remainingBudget--;

                    try {
                        if (cachedGetBlockEntitiesMethod == null) {
                            cachedGetBlockEntitiesMethod = LevelChunk.class.getDeclaredMethod("getBlockEntities");
                            cachedGetBlockEntitiesMethod.setAccessible(true);
                        }
                        Object blockEntitiesObj = cachedGetBlockEntitiesMethod.invoke(chunk);

                        if (blockEntitiesObj instanceof Map<?, ?> blockEntities) {
                            foundBlockEntities += blockEntities.size();

                            for (Object blockEntityObj : blockEntities.values()) {
                                if (fissionReactorPortClass != null && fissionReactorPortClass.isInstance(blockEntityObj)) {
                                    foundFissionPorts++;
                                    updateFissionReactorMetrics(blockEntityObj, dimensionKey, dimensionName, level, processedFission, seenFission);
                                } else if (fusionReactorPortClass != null && fusionReactorPortClass.isInstance(blockEntityObj)) {
                                    updateFusionReactorMetrics(blockEntityObj, dimensionKey, dimensionName, level, processedFusion, seenFusion);
                                } else if (turbineValveClass != null && turbineValveClass.isInstance(blockEntityObj)) {
                                    updateTurbineMetrics(blockEntityObj, dimensionKey, dimensionName, level, processedTurbine, seenTurbine);
                                } else if (inductionPortClass != null && inductionPortClass.isInstance(blockEntityObj)) {
                                    updateInductionMatrixMetrics(blockEntityObj, dimensionKey, dimensionName, level, processedInduction, seenInduction);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Failed to access block entities from chunk at {} in dimension: {}",
                            chunk.getPos(), dimensionName, e);
                    }
                }

                if (processedChunks > 0) {
                    LOGGER.debug("Scanned {} chunks ({} block entities, {} fission ports) in dimension {}",
                        processedChunks, foundBlockEntities, foundFissionPorts, dimensionName);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to iterate chunks for Mekanism metrics in dimension: {}", dimensionName, e);
            }
        }
    }
    
    private void pruneAnchorCache(Map<Object, BlockPos> cache, Set<Object> seenKeys) {
        cache.entrySet().removeIf(entry -> !seenKeys.contains(entry.getKey()));
    }

    private BlockPos updateAnchorCache(Map<Object, BlockPos> cache, Object key, BlockPos candidate) {
        if (candidate == null) {
            return cache.get(key);
        }
        BlockPos current = cache.get(key);
        if (current == null || isBetterAnchor(candidate, current)) {
            cache.put(key, candidate);
            return candidate;
        }
        return current;
    }

    private boolean isBetterAnchor(BlockPos candidate, BlockPos current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (candidate.getY() != current.getY()) {
            return candidate.getY() < current.getY();
        }
        if (candidate.getX() != current.getX()) {
            return candidate.getX() < current.getX();
        }
        return candidate.getZ() < current.getZ();
    }

    private String formatBlockPos(BlockPos pos) {
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private Set<BlockPos> getOrCreateAnchorSet(Map<ResourceKey<Level>, Set<BlockPos>> map, ResourceKey<Level> dimension) {
        return map.computeIfAbsent(dimension, key -> ConcurrentHashMap.newKeySet());
    }

    private boolean hasKnownAnchors() {
        return knownFissionAnchors.values().stream().anyMatch(set -> !set.isEmpty())
            || knownFusionAnchors.values().stream().anyMatch(set -> !set.isEmpty())
            || knownTurbineAnchors.values().stream().anyMatch(set -> !set.isEmpty())
            || knownInductionAnchors.values().stream().anyMatch(set -> !set.isEmpty());
    }

    private Object unwrapDynamicValue(Object value) {
        if (value instanceof Optional<?> optional) {
            return unwrapDynamicValue(optional.orElse(null));
        }
        if (value instanceof CompletableFuture<?> future) {
            return unwrapDynamicValue(future.getNow(null));
        }
        if (value != null && "com.mojang.datafixers.util.Either".equals(value.getClass().getName())) {
            try {
                Method leftMethod = value.getClass().getMethod("left");
                Method rightMethod = value.getClass().getMethod("right");
                Optional<?> left = (Optional<?>) leftMethod.invoke(value);
                Object leftValue = unwrapDynamicValue(left.orElse(null));
                if (leftValue != null) {
                    return leftValue;
                }
                Optional<?> right = (Optional<?>) rightMethod.invoke(value);
                return unwrapDynamicValue(right.orElse(null));
            } catch (Exception e) {
                LOGGER.debug("Failed to unwrap Either while resolving value: {}", e.getMessage());
            }
        }
        return value;
    }

    private Double invokeCapacity(Object target, Method method) {
        try {
            Object result = method.invoke(target);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to invoke {} on {}: {}", method.getName(), target.getClass().getName(), e.getMessage());
        }
        return null;
    }

    private Double extractTankCapacity(Object tankObj, Method primaryCapacityMethod) {
        return extractTankCapacity(tankObj, primaryCapacityMethod, 0);
    }

    private Double extractTankCapacity(Object tankObj, Method primaryCapacityMethod, int depth) {
        if (depth > 5) {
            return null;
        }
        Object actual = unwrapDynamicValue(tankObj);
        if (actual == null) {
            return null;
        }

        if (primaryCapacityMethod != null && primaryCapacityMethod.getDeclaringClass().isAssignableFrom(actual.getClass())) {
            Double capacity = invokeCapacity(actual, primaryCapacityMethod);
            if (capacity != null) {
                return capacity;
            }
        }

        try {
            Method capacityMethod = actual.getClass().getMethod("getCapacity");
            Double capacity = invokeCapacity(actual, capacityMethod);
            if (capacity != null) {
                return capacity;
            }
        } catch (NoSuchMethodException ignored) {
        }

        String[] nestedTankAccessors = {"getChemicalTank", "getFluidTank", "getGasTank", "getTank"};
        for (String accessor : nestedTankAccessors) {
            try {
                Method nestedMethod = actual.getClass().getMethod(accessor);
                Object nestedTank = nestedMethod.invoke(actual);
                if (nestedTank != actual) {
                    Double capacity = extractTankCapacity(nestedTank, primaryCapacityMethod, depth + 1);
                    if (capacity != null) {
                        return capacity;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke nested tank accessor {} on {}: {}", accessor, actual.getClass().getName(), e.getMessage());
            }
        }
        return null;
    }

    private Double resolveNumericField(Object target, Class<?> ownerClass, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = ownerClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to access field {} on {}: {}", fieldName, ownerClass.getName(), e.getMessage());
            }
        }
        return null;
    }

    private Double resolveTankCapacityFromFields(Object target, Class<?> ownerClass, Method capacityMethod, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = ownerClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object tankObj = field.get(target);
                Double capacity = extractTankCapacity(tankObj, capacityMethod);
                if (capacity != null) {
                    return capacity;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to access tank field {} on {}: {}", fieldName, ownerClass.getName(), e.getMessage());
            }
        }
        return null;
    }

    private double resolveFissionBurnRate(Object multiblockData) {
        double burnRate = 0.0;
        try {
            if (getActualBurnRateMethod != null) {
                Object burnRateObj = getActualBurnRateMethod.invoke(multiblockData);
                if (burnRateObj instanceof Number number) {
                    return number.doubleValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to invoke getActualBurnRate: {}", e.getMessage());
        }

        for (String fieldName : new String[]{"lastBurnRate", "actualBurnRate", "burnRate"}) {
            try {
                java.lang.reflect.Field field = fissionReactorMultiblockDataClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object burnRateObj = field.get(multiblockData);
                if (burnRateObj instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to access burn rate field {}: {}", fieldName, e.getMessage());
            }
        }

        return burnRate;
    }

    private Double resolveFissionCoolantCapacity(Object multiblockData) {
        try {
            if (getCoolantCapacityMethod != null) {
                Object result = getCoolantCapacityMethod.invoke(multiblockData);
                if (result instanceof Number number) {
                    return number.doubleValue();
                }
                Double capacity = extractTankCapacity(result, null);
                if (capacity != null) {
                    return capacity;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to invoke coolant capacity method: {}", e.getMessage());
        }

        Double viaTankField = resolveTankCapacityFromFields(multiblockData, fissionReactorMultiblockDataClass, null, "coolantTank");
        if (viaTankField != null) {
            return viaTankField;
        }

        return resolveNumericField(multiblockData, fissionReactorMultiblockDataClass,
            "cooledCoolantCapacity", "coolantCapacity", "maxCoolant", "coolantTankCapacity");
    }

    private Double resolveFissionFuelCapacity(Object multiblockData) {
        try {
            if (getFuelTankMethod != null) {
                Object tankObj = getFuelTankMethod.invoke(multiblockData);
                Double capacity = extractTankCapacity(tankObj, getFuelTankCapacityMethod);
                if (capacity != null) {
                    return capacity;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to invoke fuel tank method: {}", e.getMessage());
        }

        Double viaTankField = resolveTankCapacityFromFields(multiblockData, fissionReactorMultiblockDataClass, getFuelTankCapacityMethod,
            "fuelTank", "fuel");
        if (viaTankField != null) {
            return viaTankField;
        }

        return resolveNumericField(multiblockData, fissionReactorMultiblockDataClass,
            "fuelCapacity", "maxFuel", "fuelTankCapacity");
    }
    
    private void collectFissionMetrics(Object multiblockData, String dimensionName, BlockPos labelPos) {
        int x = labelPos.getX();
        int y = labelPos.getY();
        int z = labelPos.getZ();

        String[] labels = {dimensionName, String.valueOf(x), String.valueOf(y), String.valueOf(z)};

        double burnRate = resolveFissionBurnRate(multiblockData);
        fissionReactorBurnRateGauge.labels(labels).set(burnRate);
        LOGGER.debug("  Burn rate: {} mB/tick", burnRate);

        Double coolantCapacity = resolveFissionCoolantCapacity(multiblockData);
        if (coolantCapacity != null) {
            fissionReactorCoolantCapacityGauge.labels(labels).set(coolantCapacity);
            LOGGER.debug("  Coolant capacity: {} mB", coolantCapacity);
        } else {
            LOGGER.warn("✗ Could not determine coolant capacity for fission reactor {}", formatBlockPos(labelPos));
        }

        Double fuelCapacity = resolveFissionFuelCapacity(multiblockData);
        if (fuelCapacity != null) {
            fissionReactorFuelCapacityGauge.labels(labels).set(fuelCapacity);
            LOGGER.debug("  Fuel capacity: {} mB", fuelCapacity);
        } else {
            LOGGER.warn("✗ Could not determine fuel capacity for fission reactor {}", formatBlockPos(labelPos));
        }
    }

    private void collectFusionMetrics(Object multiblockData, String dimensionName, BlockPos labelPos) {
        int x = labelPos.getX();
        int y = labelPos.getY();
        int z = labelPos.getZ();

        String[] labels = {dimensionName, String.valueOf(x), String.valueOf(y), String.valueOf(z)};

        try {
            Object injectionRateObj = getInjectionRateMethod.invoke(multiblockData);
            if (injectionRateObj instanceof Number) {
                double injectionRate = ((Number) injectionRateObj).doubleValue();
                fusionReactorInjectionRateGauge.labels(labels).set(injectionRate);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get fusion injection rate", e);
        }

        try {
            Object waterTank = getWaterTankMethod.invoke(multiblockData);
            Double capacity = extractTankCapacity(waterTank, getWaterTankCapacityMethod);
            if (capacity != null) {
                fusionReactorCoolantCapacityGauge.labels(labels).set(capacity);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get fusion coolant capacity", e);
        }

        try {
            Object deuteriumTank = getDeuteriumTankMethod.invoke(multiblockData);
            Double capacity = extractTankCapacity(deuteriumTank, getChemicalTankCapacityMethod);
            if (capacity != null) {
                fusionReactorDeuteriumCapacityGauge.labels(labels).set(capacity);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get fusion deuterium capacity", e);
        }

        try {
            Object tritiumTank = getTritiumTankMethod.invoke(multiblockData);
            Double capacity = extractTankCapacity(tritiumTank, getChemicalTankCapacityMethod);
            if (capacity != null) {
                fusionReactorTritiumCapacityGauge.labels(labels).set(capacity);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get fusion tritium capacity", e);
        }

        try {
            Object fuelTank = getFusionFuelTankMethod.invoke(multiblockData);
            Double capacity = extractTankCapacity(fuelTank, getChemicalTankCapacityMethod);
            if (capacity != null) {
                fusionReactorFuelCapacityGauge.labels(labels).set(capacity);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get fusion fuel capacity", e);
        }
    }

    private void collectTurbineMetrics(Object multiblockData, String dimensionName, BlockPos labelPos) {
        int x = labelPos.getX();
        int y = labelPos.getY();
        int z = labelPos.getZ();
        String[] labels = {dimensionName, String.valueOf(x), String.valueOf(y), String.valueOf(z)};

        try {
            Object productionRateObj = getProductionRateMethod.invoke(multiblockData);
            if (productionRateObj instanceof Number) {
                double productionRate = ((Number) productionRateObj).doubleValue();
                turbinePowerGenerationGauge.labels(labels).set(productionRate);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get turbine production rate", e);
        }
    }

    private void collectInductionMetrics(Object multiblockData, String dimensionName, BlockPos labelPos) {
        int x = labelPos.getX();
        int y = labelPos.getY();
        int z = labelPos.getZ();
        String[] labels = {dimensionName, String.valueOf(x), String.valueOf(y), String.valueOf(z)};

        try {
            Object energyObj = getEnergyMethod.invoke(multiblockData);
            if (energyObj instanceof Number) {
                double energy = ((Number) energyObj).doubleValue();
                inductionMatrixEnergyGauge.labels(labels).set(energy);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get induction matrix energy", e);
        }

        try {
            Object capacityObj = getStorageCapMethod.invoke(multiblockData);
            if (capacityObj instanceof Number) {
                double capacity = ((Number) capacityObj).doubleValue();
                inductionMatrixEnergyCapacityGauge.labels(labels).set(capacity);
            }
        } catch (Exception e) {
            LOGGER.trace("Failed to get induction matrix energy capacity", e);
        }
    }

    private void updateFissionReactorMetrics(Object reactorPort, ResourceKey<Level> dimensionKey, String dimensionName, ServerLevel level, Set<Object> processed, Set<Object> seen) {
        try {
            BlockPos portPos = ((net.minecraft.world.level.block.entity.BlockEntity) reactorPort).getBlockPos();

            Object multiblockData;
            try {
                multiblockData = getMultiblockMethod.invoke(reactorPort);
            } catch (Exception e) {
                LOGGER.debug("Failed to obtain fission multiblock data at {} in {}: {}", formatBlockPos(portPos), dimensionName, e.getMessage());
                return;
            }

            if (multiblockData == null) {
                LOGGER.info("⚠ Fission reactor multiblock data is null at {} in {} - reactor may not be formed", formatBlockPos(portPos), dimensionName);
                return;
            }

            seen.add(multiblockData);
            BlockPos anchorPos = updateAnchorCache(fissionAnchorCache, multiblockData, portPos);
            BlockPos labelPos = anchorPos != null ? anchorPos : portPos;

            if (!processed.add(multiblockData)) {
                return;
            }

            getOrCreateAnchorSet(knownFissionAnchors, dimensionKey).add(labelPos);
            collectFissionMetrics(multiblockData, dimensionName, labelPos);
        } catch (Exception e) {
            LOGGER.debug("Failed to update fission reactor metrics", e);
        }
    }
    
    private void updateFusionReactorMetrics(Object reactorPort, ResourceKey<Level> dimensionKey, String dimensionName, ServerLevel level, Set<Object> processed, Set<Object> seen) {
        try {
            BlockPos portPos = ((net.minecraft.world.level.block.entity.BlockEntity) reactorPort).getBlockPos();
            
            // Get multiblock data
            Object multiblockData = getFusionMultiblockMethod.invoke(reactorPort);
            if (multiblockData == null) {
                return; // Reactor not formed
            }
            
            seen.add(multiblockData);
            BlockPos anchorPos = updateAnchorCache(fusionAnchorCache, multiblockData, portPos);
            BlockPos labelPos = anchorPos != null ? anchorPos : portPos;

            if (!processed.add(multiblockData)) {
                return;
            }

            getOrCreateAnchorSet(knownFusionAnchors, dimensionKey).add(labelPos);
            collectFusionMetrics(multiblockData, dimensionName, labelPos);
        } catch (Exception e) {
            LOGGER.debug("Failed to update fusion reactor metrics", e);
        }
    }
    
    private void updateTurbineMetrics(Object turbineValve, ResourceKey<Level> dimensionKey, String dimensionName, ServerLevel level, Set<Object> processed, Set<Object> seen) {
        try {
            BlockPos portPos = ((net.minecraft.world.level.block.entity.BlockEntity) turbineValve).getBlockPos();
            
            // Get multiblock data
            Object multiblockData = getTurbineMultiblockMethod.invoke(turbineValve);
            if (multiblockData == null) {
                return; // Turbine not formed
            }
            
            seen.add(multiblockData);
            BlockPos anchorPos = updateAnchorCache(turbineAnchorCache, multiblockData, portPos);
            BlockPos labelPos = anchorPos != null ? anchorPos : portPos;

            if (!processed.add(multiblockData)) {
                return;
            }

            getOrCreateAnchorSet(knownTurbineAnchors, dimensionKey).add(labelPos);
            collectTurbineMetrics(multiblockData, dimensionName, labelPos);
        } catch (Exception e) {
            LOGGER.debug("Failed to update turbine metrics", e);
        }
    }
    
    private void updateInductionMatrixMetrics(Object inductionPort, ResourceKey<Level> dimensionKey, String dimensionName, ServerLevel level, Set<Object> processed, Set<Object> seen) {
        try {
            BlockPos portPos = ((net.minecraft.world.level.block.entity.BlockEntity) inductionPort).getBlockPos();
            
            // Get multiblock data
            Object multiblockData = getInductionMultiblockMethod.invoke(inductionPort);
            if (multiblockData == null) {
                return; // Matrix not formed
            }
            
            seen.add(multiblockData);
            BlockPos anchorPos = updateAnchorCache(inductionAnchorCache, multiblockData, portPos);
            BlockPos labelPos = anchorPos != null ? anchorPos : portPos;

            if (!processed.add(multiblockData)) {
                return;
            }

            getOrCreateAnchorSet(knownInductionAnchors, dimensionKey).add(labelPos);
            collectInductionMetrics(multiblockData, dimensionName, labelPos);
        } catch (Exception e) {
            LOGGER.debug("Failed to update induction matrix metrics", e);
        }
    }

    private boolean processKnownFissionAnchors(ServerLevel level, ResourceKey<Level> dimensionKey, String dimensionName,
                                               Set<Object> processed, Set<Object> seen) {
        Set<BlockPos> anchors = knownFissionAnchors.get(dimensionKey);
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        boolean processedAny = false;
        Iterator<BlockPos> iterator = anchors.iterator();
        while (iterator.hasNext()) {
            BlockPos anchor = iterator.next();
            var blockEntity = level.getBlockEntity(anchor);
            if (blockEntity == null || !fissionReactorPortClass.isInstance(blockEntity)) {
                iterator.remove();
                continue;
            }
            Object multiblockData;
            try {
                multiblockData = getMultiblockMethod.invoke(blockEntity);
            } catch (Exception e) {
                LOGGER.debug("Failed to obtain fission multiblock data at {} in {}: {}", formatBlockPos(anchor), dimensionName, e.getMessage());
                continue;
            }
            if (multiblockData == null) {
                iterator.remove();
                continue;
            }
            seen.add(multiblockData);
            updateAnchorCache(fissionAnchorCache, multiblockData, anchor);
            if (!processed.add(multiblockData)) {
                continue;
            }
            collectFissionMetrics(multiblockData, dimensionName, anchor);
            processedAny = true;
        }
        if (anchors.isEmpty()) {
            rescanCooldown = 0;
        }
        return processedAny;
    }

    private boolean processKnownFusionAnchors(ServerLevel level, ResourceKey<Level> dimensionKey, String dimensionName,
                                              Set<Object> processed, Set<Object> seen) {
        Set<BlockPos> anchors = knownFusionAnchors.get(dimensionKey);
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        boolean processedAny = false;
        Iterator<BlockPos> iterator = anchors.iterator();
        while (iterator.hasNext()) {
            BlockPos anchor = iterator.next();
            var blockEntity = level.getBlockEntity(anchor);
            if (blockEntity == null || !fusionReactorPortClass.isInstance(blockEntity)) {
                iterator.remove();
                continue;
            }
            Object multiblockData;
            try {
                multiblockData = getFusionMultiblockMethod.invoke(blockEntity);
            } catch (Exception e) {
                LOGGER.debug("Failed to obtain fusion multiblock data at {} in {}: {}", formatBlockPos(anchor), dimensionName, e.getMessage());
                continue;
            }
            if (multiblockData == null) {
                iterator.remove();
                continue;
            }
            seen.add(multiblockData);
            updateAnchorCache(fusionAnchorCache, multiblockData, anchor);
            if (!processed.add(multiblockData)) {
                continue;
            }
            collectFusionMetrics(multiblockData, dimensionName, anchor);
            processedAny = true;
        }
        if (anchors.isEmpty()) {
            rescanCooldown = 0;
        }
        return processedAny;
    }

    private boolean processKnownTurbineAnchors(ServerLevel level, ResourceKey<Level> dimensionKey, String dimensionName,
                                               Set<Object> processed, Set<Object> seen) {
        Set<BlockPos> anchors = knownTurbineAnchors.get(dimensionKey);
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        boolean processedAny = false;
        Iterator<BlockPos> iterator = anchors.iterator();
        while (iterator.hasNext()) {
            BlockPos anchor = iterator.next();
            var blockEntity = level.getBlockEntity(anchor);
            if (blockEntity == null || !turbineValveClass.isInstance(blockEntity)) {
                iterator.remove();
                continue;
            }
            Object multiblockData;
            try {
                multiblockData = getTurbineMultiblockMethod.invoke(blockEntity);
            } catch (Exception e) {
                LOGGER.debug("Failed to obtain turbine multiblock data at {} in {}: {}", formatBlockPos(anchor), dimensionName, e.getMessage());
                continue;
            }
            if (multiblockData == null) {
                iterator.remove();
                continue;
            }
            seen.add(multiblockData);
            updateAnchorCache(turbineAnchorCache, multiblockData, anchor);
            if (!processed.add(multiblockData)) {
                continue;
            }
            collectTurbineMetrics(multiblockData, dimensionName, anchor);
            processedAny = true;
        }
        if (anchors.isEmpty()) {
            rescanCooldown = 0;
        }
        return processedAny;
    }

    private boolean processKnownInductionAnchors(ServerLevel level, ResourceKey<Level> dimensionKey, String dimensionName,
                                                 Set<Object> processed, Set<Object> seen) {
        Set<BlockPos> anchors = knownInductionAnchors.get(dimensionKey);
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        boolean processedAny = false;
        Iterator<BlockPos> iterator = anchors.iterator();
        while (iterator.hasNext()) {
            BlockPos anchor = iterator.next();
            var blockEntity = level.getBlockEntity(anchor);
            if (blockEntity == null || !inductionPortClass.isInstance(blockEntity)) {
                iterator.remove();
                continue;
            }
            Object multiblockData;
            try {
                multiblockData = getInductionMultiblockMethod.invoke(blockEntity);
            } catch (Exception e) {
                LOGGER.debug("Failed to obtain induction multiblock data at {} in {}: {}", formatBlockPos(anchor), dimensionName, e.getMessage());
                continue;
            }
            if (multiblockData == null) {
                iterator.remove();
                continue;
            }
            seen.add(multiblockData);
            updateAnchorCache(inductionAnchorCache, multiblockData, anchor);
            if (!processed.add(multiblockData)) {
                continue;
            }
            collectInductionMetrics(multiblockData, dimensionName, anchor);
            processedAny = true;
        }
        if (anchors.isEmpty()) {
            rescanCooldown = 0;
        }
        return processedAny;
    }

    private LevelChunk resolveLevelChunk(Object chunkObj) {
        if (chunkObj == null) {
            return null;
        }

        if (chunkObj instanceof LevelChunk levelChunk) {
            return levelChunk;
        }

        Class<?> holderClass = chunkObj.getClass();

        Method cachedMethod = chunkHolderMethodCache.get(holderClass);
        if (cachedMethod != null) {
            LevelChunk chunk = invokeChunkHolderMethod(chunkObj, cachedMethod);
            if (chunk != null) {
                return chunk;
            }
            chunkHolderMethodCache.remove(holderClass);
        }

        String[] methodCandidates = {"getTickingChunk", "getFullChunk", "getChunkIfPresent", "getLevelChunk"};
        for (String methodName : methodCandidates) {
            try {
                Method method = holderClass.getMethod(methodName);
                LevelChunk chunk = invokeChunkHolderMethod(chunkObj, method);
                if (chunk != null) {
                    chunkHolderMethodCache.put(holderClass, method);
                    return chunk;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to invoke method {} on {}: {}", methodName, holderClass.getName(), e.getMessage());
            }
        }

        java.lang.reflect.Field cachedField = chunkHolderFieldCache.get(holderClass);
        if (cachedField != null) {
            LevelChunk chunk = readChunkHolderField(chunkObj, cachedField);
            if (chunk != null) {
                return chunk;
            }
            chunkHolderFieldCache.remove(holderClass);
        }

        String[] fieldCandidates = {"chunk", "fullChunk", "tickingChunk"};
        for (String fieldName : fieldCandidates) {
            try {
                java.lang.reflect.Field field = holderClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                LevelChunk chunk = readChunkHolderField(chunkObj, field);
                if (chunk != null) {
                    chunkHolderFieldCache.put(holderClass, field);
                    return chunk;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Failed to access field {} on {}: {}", fieldName, holderClass.getName(), e.getMessage());
            }
        }

        return null;
    }

    private LevelChunk invokeChunkHolderMethod(Object holder, Method method) {
        try {
            Object result = method.invoke(holder);
            return extractLevelChunk(result);
        } catch (Exception e) {
            LOGGER.debug("Exception invoking {} on {}: {}", method.getName(), holder.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private LevelChunk readChunkHolderField(Object holder, java.lang.reflect.Field field) {
        try {
            Object value = field.get(holder);
            return extractLevelChunk(value);
        } catch (Exception e) {
            LOGGER.debug("Exception reading field {} on {}: {}", field.getName(), holder.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private LevelChunk extractLevelChunk(Object value) {
        Object actual = unwrapDynamicValue(value);
        if (actual instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        if (actual instanceof ChunkAccess chunkAccess && chunkAccess instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        return null;
    }
}



