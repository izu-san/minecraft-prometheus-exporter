
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

Metrics
=======

This mod exposes various Minecraft server metrics via Prometheus. The metrics endpoint is available at `http://<bindAddress>:<port>/metrics` (default: `http://0.0.0.0:9225/metrics`).

## Server Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_server_uptime_seconds_total` | Counter | Total seconds since server startup | - |
| `minecraft_server_running` | Gauge | Server running status (1 = running, 0 = stopped) | - |
| `minecraft_server_version` | Info | Minecraft and NeoForge version information | `minecraft_version`, `neoforge_version` |

## Performance Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_tps` | Gauge | Current TPS (ideal value is 20) | - |
| `minecraft_tick_duration_milliseconds` | Histogram | Processing time per tick | - |
| `minecraft_tick_lag_count_total` | Counter | Total number of lagged ticks (exceeding 50ms) | - |
| `minecraft_loaded_chunks` | Gauge | Total number of chunks across all dimensions | - |
| `minecraft_entities_loaded` | Gauge | Total number of entities | - |
| `minecraft_tile_entities_loaded` | Gauge | Total number of BlockEntities | - |

## Player Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_players_online` | Gauge | Current number of online players | - |
| `minecraft_players_max` | Gauge | Maximum number of connections | - |
| `minecraft_player_join_total` | Counter | Total number of player joins | - |
| `minecraft_player_leave_total` | Counter | Total number of player leaves | - |
| `minecraft_player_ping_milliseconds` | Gauge | Player ping value | `player` |
| `minecraft_player_dimension_count` | Gauge | Number of players per dimension | `dimension` |

## World Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_world_tick_time_seconds` | Histogram | Tick processing time per dimension | `dimension` |
| `minecraft_world_entities_total` | Gauge | Number of entities per dimension | `dimension` |
| `minecraft_world_chunks_loaded` | Gauge | Number of chunks per dimension | `dimension` |

## Entity Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_entities_by_type_total` | Gauge | Number of entities by type | `entity_type`, `dimension` |
| `minecraft_tile_entities_by_type_total` | Gauge | Number of BlockEntities by type | `block_entity_type`, `dimension` |

## Chunk Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_chunks_loaded_total` | Counter | Total number of chunks loaded | `dimension` |
| `minecraft_chunks_unloaded_total` | Counter | Total number of chunks unloaded | `dimension` |
| `minecraft_chunk_generation_time_seconds` | Histogram | Chunk generation time | `dimension` |

## Network Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_network_packets_sent_total` | Counter | Total number of packets sent | - |
| `minecraft_network_packets_received_total` | Counter | Total number of packets received | - |
| `minecraft_network_bytes_sent_total` | Counter | Total number of bytes sent | - |
| `minecraft_network_bytes_received_total` | Counter | Total number of bytes received | - |

## Resource Metrics (JVM)

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `jvm_memory_used_bytes` | Gauge | Used heap memory | - |
| `jvm_memory_max_bytes` | Gauge | JVM maximum heap capacity | - |
| `system_cpu_load` | Gauge | CPU usage | - |
| `process_uptime_seconds_total` | Counter | JVM uptime | - |
| `jvm_gc_collection_count_total` | Counter | Total number of GC executions | `gc` |
| `jvm_gc_collection_seconds_total` | Counter | GC execution time in seconds | `gc` |
| `jvm_memory_pool_bytes_used` | Gauge | Memory pool usage | `pool` |
| `jvm_memory_pool_bytes_max` | Gauge | Memory pool maximum capacity | `pool` |
| `jvm_memory_pool_bytes_committed` | Gauge | Memory pool committed amount | `pool` |
| `jvm_threads_current` | Gauge | Current number of threads | - |
| `jvm_threads_daemon` | Gauge | Number of daemon threads | - |
| `jvm_threads_peak` | Gauge | Peak number of threads | - |

## Mod Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `minecraft_mods_loaded_total` | Gauge | Total number of mods reported by NeoForge | - |
| `minecraft_mod_loaded_status` | Gauge | Indicator gauge for loaded mods (value is always 1 when the mod is present) | `mod_id`, `display_name`, `namespace` |
| `minecraft_mod_version_info` | Gauge | Version information for loaded mods (value is always 1) | `mod_id`, `version` |
| `minecraft_mod_jar_size_bytes` | Gauge | Size of the mod jar on disk in bytes | `mod_id` |
| `minecraft_mod_scanned_class_count` | Gauge | Number of classes discovered during NeoForge scanning for each mod file | `mod_id` |
| `minecraft_mod_load_failures_total` | Counter | Total number of mod loading failures | `mod_id`, `error_type` |
| `minecraft_mod_load_time_seconds` | Histogram | Mod loading time in seconds | `mod_id` |
| `minecraft_mod_info` | Gauge | Complete mod information including ID, display name, namespace, version, and JAR size. Value is always 1 (presence indicator) | `mod_id`, `display_name`, `namespace`, `version` |

## Mekanism Metrics

These metrics require Mekanism and MekanismGenerators mods to be installed. Metrics are updated every 20 ticks (1 second) by default to reduce server load. Update frequency can be configured via `mekanismMetricsUpdateInterval` in `prometheus-exporter.yaml`.

### Fission Reactor Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `mekanism_fission_reactor_burn_rate_mb_per_tick` | Gauge | Fission reactor burn rate in mB/tick (actual burn rate) | `dimension`, `x`, `y`, `z` |
| `mekanism_fission_reactor_coolant_capacity_mb` | Gauge | Fission reactor coolant capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fission_reactor_coolant_stored_mb` | Gauge | Fission reactor coolant stored amount in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fission_reactor_fuel_capacity_mb` | Gauge | Fission reactor fuel capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fission_reactor_fuel_stored_mb` | Gauge | Fission reactor fuel stored amount in mB | `dimension`, `x`, `y`, `z` |

### Fusion Reactor Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `mekanism_fusion_reactor_injection_rate_mb_per_tick` | Gauge | Fusion reactor injection rate in mB/tick | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_coolant_capacity_mb` | Gauge | Fusion reactor coolant (water) capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_coolant_stored_mb` | Gauge | Fusion reactor coolant (water) stored amount in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_deuterium_capacity_mb` | Gauge | Fusion reactor deuterium fuel capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_deuterium_stored_mb` | Gauge | Fusion reactor deuterium fuel stored amount in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_tritium_capacity_mb` | Gauge | Fusion reactor tritium fuel capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_tritium_stored_mb` | Gauge | Fusion reactor tritium fuel stored amount in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_fuel_capacity_mb` | Gauge | Fusion reactor fusion fuel (DT Fuel) capacity in mB | `dimension`, `x`, `y`, `z` |
| `mekanism_fusion_reactor_fuel_stored_mb` | Gauge | Fusion reactor fusion fuel (DT Fuel) stored amount in mB | `dimension`, `x`, `y`, `z` |

### Industrial Turbine Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `mekanism_turbine_power_generation_fe_per_tick` | Gauge | Industrial turbine power generation rate in FE/tick | `dimension`, `x`, `y`, `z` |

### Induction Matrix Metrics

| Metric Name | Type | Description | Labels |
|------------|------|-------------|--------|
| `mekanism_induction_matrix_energy_fe` | Gauge | Induction matrix stored energy in FE | `dimension`, `x`, `y`, `z` |
| `mekanism_induction_matrix_energy_capacity_fe` | Gauge | Induction matrix energy capacity in FE | `dimension`, `x`, `y`, `z` |