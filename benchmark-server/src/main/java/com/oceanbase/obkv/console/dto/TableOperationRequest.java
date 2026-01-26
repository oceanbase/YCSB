package com.oceanbase.obkv.console.dto;

import com.oceanbase.obkv.console.model.SqlConnection;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Request DTO for table operations (truncate, drop)
 */
@Data
public class TableOperationRequest {

    /**
     * SQL connection configuration
     */
    @NotNull(message = "SQL connection is required")
    @Valid
    private SqlConnection sqlConnection;

    /**
     * Table name
     */
    @NotBlank(message = "Table name is required")
    private String tableName = "ycsb_test";

    /**
     * Column family
     */
    @NotBlank(message = "Column family is required")
    private String columnFamily = "cf";

    /**
     * Benchmark model type: hbase or table
     */
    private String modelType = "hbase";

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

