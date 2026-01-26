package com.oceanbase.obkv.console.model;

import lombok.Data;

import java.util.Properties;

/**
 * Client configuration for benchmark
 */
@Data
public class ClientConfig {

    /**
     * Connection pool size (server.connection.pool.size)
     */
    private Integer connectionPoolSize = 20;

    /**
     * RPC operation timeout in milliseconds (rpc.operation.timeout)
     */
    private Integer rpcOperationTimeout = 10000;

    /**
     * RPC execute timeout in milliseconds (rpc.execute.timeout)
     */
    private Integer rpcExecuteTimeout = 15000;

    /**
     * Enable debug logging (obkv.debug)
     */
    private Boolean debug = false;

    /**
     * Netty buffer low watermark (bolt.netty.buffer.low.watermark)
     * Optional, null means use default
     */
    private Integer nettyBufferLowWatermark;

    /**
     * Netty buffer high watermark (bolt.netty.buffer.high.watermark)
     * Optional, null means use default
     */
    private Integer nettyBufferHighWatermark;

    /**
     * Convert to properties for workload file
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("server.connection.pool.size", String.valueOf(connectionPoolSize));
        props.setProperty("rpc.operation.timeout", String.valueOf(rpcOperationTimeout));
        props.setProperty("rpc.execute.timeout", String.valueOf(rpcExecuteTimeout));
        props.setProperty("obkv.debug", String.valueOf(debug));
        // Optional netty buffer watermarks
        if (nettyBufferLowWatermark != null && nettyBufferLowWatermark > 0) {
            props.setProperty("bolt.netty.buffer.low.watermark", String.valueOf(nettyBufferLowWatermark));
        }
        if (nettyBufferHighWatermark != null && nettyBufferHighWatermark > 0) {
            props.setProperty("bolt.netty.buffer.high.watermark", String.valueOf(nettyBufferHighWatermark));
        }
        return props;
    }
}

