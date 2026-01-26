package com.oceanbase.obkv.console.model;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * Table configuration for DDL operations
 */
@Data
public class TableConfig {

    /**
     * Partition type: range or key
     */
    @NotBlank(message = "Partition type is required")
    private String partitionType = "range"; // Default to range partition

    /**
     * Benchmark model type: hbase or table
     */
    @NotBlank(message = "Model type is required")
    private String modelType = "hbase";

    /**
     * Table name (default: ycsb_test)
     */
    @NotBlank(message = "Table name is required")
    private String tableName = "ycsb_test";

    /**
     * Column family name (default: cf)
     */
    @NotBlank(message = "Column family is required")
    private String columnFamily = "cf";

    // Range partition specific fields
    /**
     * Maximum key value for range partitioning (only required for range partition)
     */
    @Min(value = 1, message = "maxKey must be at least 1")
    private Integer maxKey;

    /**
     * Number of partitions for range partitioning (only required for range partition)
     */
    @Min(value = 1, message = "partitionCount must be at least 1")
    private Integer partitionCount;

    /**
     * Key length for formatting (left-padded with zeros) for range partition
     */
    @Min(value = 1, message = "keyLength must be at least 1")
    private Integer keyLength;

    // Key partition specific fields
    /**
     * Number of partitions for key partitioning
     */
    @Min(value = 1, message = "keyPartitionCount must be at least 1")
    private Integer keyPartitionCount;

    /**
     * Zero padding length for key generation (default: 12)
     */
    @Min(value = 1, message = "zeroPadding must be at least 1")
    private Integer zeroPadding = 12;

    /**
     * Get full table name. HBase mode uses tableName$columnFamily, Table mode just uses tableName.
     */
    public String getFullTableName() {
        if ("table".equalsIgnoreCase(modelType)) {
            return String.format("`%s`", tableName);
        }
        return String.format("`%s$%s`", tableName, columnFamily);
    }
}

