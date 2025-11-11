package com.prometheus.minecraft.prometheus_exporter.mixin.accessor;

/**
 * Interface to expose statistics from Connection class
 * Implemented through Mixin
 */
public interface INetworkStatistics {
    long prometheus_exporter$getPacketsSent();
    long prometheus_exporter$getPacketsReceived();
    long prometheus_exporter$getBytesSent();
    long prometheus_exporter$getBytesReceived();
}

