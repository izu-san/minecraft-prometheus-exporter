package com.prometheus.minecraft.prometheus_exporter.mixin;

import com.prometheus.minecraft.prometheus_exporter.mixin.accessor.INetworkStatistics;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements INetworkStatistics {
    @Unique
    private long packetsSent = 0;
    @Unique
    private long packetsReceived = 0;
    @Unique
    private long bytesSent = 0;
    @Unique
    private long bytesReceived = 0;
    
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        packetsSent++;
        // Estimate packet size (actual size is determined after serialization, so this is an estimate)
        bytesSent += estimatePacketSize(packet);
    }
    
    @Inject(method = "doHandlePacket", at = @At("HEAD"))
    private void onReceivePacket(Packet<?> packet, net.minecraft.network.protocol.PacketFlow flow, CallbackInfo ci) {
        if (flow == net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            packetsReceived++;
            bytesReceived += estimatePacketSize(packet);
        }
    }
    
    @Unique
    private int estimatePacketSize(Packet<?> packet) {
        // Estimate packet size (actual size is not accurate due to variable-length encoding)
        // To get more accurate values, need to measure the actual byte array size
        return 64; // Default estimate
    }
    
    @Override
    public long prometheus_exporter$getPacketsSent() {
        return packetsSent;
    }
    
    @Override
    public long prometheus_exporter$getPacketsReceived() {
        return packetsReceived;
    }
    
    @Override
    public long prometheus_exporter$getBytesSent() {
        return bytesSent;
    }
    
    @Override
    public long prometheus_exporter$getBytesReceived() {
        return bytesReceived;
    }
}

