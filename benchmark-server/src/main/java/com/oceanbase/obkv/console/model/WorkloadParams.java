package com.oceanbase.obkv.console.model;

import lombok.Data;

import javax.validation.constraints.Min;
import java.util.Properties;

/**
 * Workload parameters for benchmark
 */
@Data
public class WorkloadParams {

    /**
     * Number of records to load
     */
    @Min(value = 1, message = "recordcount must be at least 1")
    private Integer recordcount = 1000;

    /**
     * Number of operations to perform
     */
    @Min(value = 0, message = "operationcount must be non-negative")
    private Integer operationcount = 1000;

    /**
     * Number of client threads
     */
    @Min(value = 1, message = "threadcount must be at least 1")
    private Integer threadcount = 1;

    /**
     * Target operations per second (0 means no limit)
     */
    @Min(value = 0, message = "target must be non-negative")
    private Integer target = 0;

    /**
     * Field length in bytes
     */
    @Min(value = 1, message = "fieldlength must be at least 1")
    private Integer fieldlength = 100;

    /**
     * Maximum execution time in seconds (0 means no limit)
     * This limits how long the benchmark runs regardless of operationcount
     */
    @Min(value = 0, message = "maxExecutionTime must be non-negative")
    private Integer maxExecutionTime = 0;

    /**
     * Number of fields/columns per record (null means use workload default)
     */
    private Integer fieldcount;

    /**
     * Read proportion (0.0 - 1.0, null means use workload default)
     */
    private Double readproportion;

    /**
     * Update proportion (0.0 - 1.0, null means use workload default)
     */
    private Double updateproportion;

    /**
     * Scan proportion (0.0 - 1.0, null means use workload default)
     */
    private Double scanproportion;

    /**
     * Insert proportion (0.0 - 1.0, null means use workload default)
     */
    private Double insertproportion;

    /**
     * Request distribution (null means use workload default)
     * Options: uniform, zipfian, latest, sequential, hotspot
     */
    private String requestdistribution;

    /**
     * Zero padding length for key generation (default: 12)
     */
    private Integer zeropadding;

    /**
     * Core workload insertion retry limit for both Load and Run phases (default: 0)
     */
    private Integer coreWorkloadInsertionRetryLimit = 0;

    /**
     * Core workload insertion retry interval in seconds (default: 3.0)
     */
    private Double coreWorkloadInsertionRetryInterval = 3.0;

    /**
     * Runtime batch max wait time in seconds (default: 1200)
     */
    private Integer runtimeBatchMaxWait = 1200;

    /**
     * Batch Put size per operation
     */
    private Integer batchPutSize;

    /**
     * HBase write version (timestamp)
     */
    private Long hbaseVersion;

    // ========== Partitioning Parameters ==========

    /**
     * Start timestamp for range partitioning (milliseconds)
     */
    private Long rangePartitionStartTs;

    /**
     * Duration of each range partition in milliseconds
     */
    private Long rangePartitionDurationMs;

    /**
     * Number of range partitions
     */
    private Integer rangePartitionCount;

    /**
     * Total number of keys/IDs to cycle through
     */
    private Integer keyCount;

    /**
     * Whether to enable time range test mode (for HBase model)
     */
    private Boolean enableTimeRangeTestMode;

    /**
     * Convert to properties for workload file
     */
    public Properties toProperties() {
        return toProperties("hbase");
    }

    /**
     * Convert to properties for workload file with model context
     */
    public Properties toProperties(String modelType) {
        Properties props = new Properties();
        boolean isTable = "table".equalsIgnoreCase(modelType);

        props.setProperty("recordcount", String.valueOf(recordcount));
        props.setProperty("operationcount", String.valueOf(operationcount));
        props.setProperty("threadcount", String.valueOf(threadcount));
        if (target != null && target > 0) {
            props.setProperty("target", String.valueOf(target));
        }
        props.setProperty("fieldlength", String.valueOf(fieldlength));
        if (maxExecutionTime != null && maxExecutionTime > 0) {
            props.setProperty("maxexecutiontime", String.valueOf(maxExecutionTime));
        }
        // Optional field count
        if (fieldcount != null && fieldcount > 0) {
            props.setProperty("fieldcount", String.valueOf(fieldcount));
        }
        // Optional operation proportions (only set if provided)
        if (readproportion != null) {
            props.setProperty("readproportion", String.valueOf(readproportion));
        }
        if (updateproportion != null) {
            props.setProperty("updateproportion", String.valueOf(updateproportion));
        }
        if (scanproportion != null) {
            props.setProperty("scanproportion", String.valueOf(scanproportion));
        }
        if (insertproportion != null) {
            props.setProperty("insertproportion", String.valueOf(insertproportion));
        }
        // Request distribution
        if (requestdistribution != null && !requestdistribution.isEmpty()) {
            props.setProperty("requestdistribution", requestdistribution);
        }
        // Zero padding
        if (zeropadding != null && zeropadding > 0) {
            props.setProperty("zeropadding", String.valueOf(zeropadding));
        }
        // Core workload insertion retry limit
        if (coreWorkloadInsertionRetryLimit != null) {
            props.setProperty("core_workload_insertion_retry_limit", String.valueOf(coreWorkloadInsertionRetryLimit));
        }
        // Core workload insertion retry interval
        if (coreWorkloadInsertionRetryInterval != null) {
            props.setProperty("core_workload_insertion_retry_interval", String.valueOf(coreWorkloadInsertionRetryInterval));
        }
        // Runtime batch max wait time
        if (runtimeBatchMaxWait != null) {
            props.setProperty("runtime.batch.max.wait", String.valueOf(runtimeBatchMaxWait));
        }
        // Batch Put size
        if (batchPutSize != null && batchPutSize > 0) {
            props.setProperty("batchput.size.per.op", String.valueOf(batchPutSize));
        }
        // HBase write version
        if (hbaseVersion != null) {
            props.setProperty("hbase.oceanbase.version", String.valueOf(hbaseVersion));
        }

        // Partitioning parameters
        if (rangePartitionStartTs != null) {
            props.setProperty("obkv.rangePartitionStartTs", String.valueOf(rangePartitionStartTs));
        }
        if (rangePartitionDurationMs != null) {
            props.setProperty("obkv.rangePartitionDurationMs", String.valueOf(rangePartitionDurationMs));
        }
        if (rangePartitionCount != null) {
            props.setProperty("obkv.rangePartitionCount", String.valueOf(rangePartitionCount));
        }
        if (keyCount != null) {
            props.setProperty(isTable ? "obkv.idCount" : "obkv.keyCount", String.valueOf(keyCount));
        }
        if (enableTimeRangeTestMode != null) {
            props.setProperty("obkv.enableTimeRangeTestMode", String.valueOf(enableTimeRangeTestMode));
        }

        return props;
    }
}

