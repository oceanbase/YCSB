package com.oceanbase.obkv.console.dto;

import com.oceanbase.obkv.console.model.ClientConfig;
import com.oceanbase.obkv.console.model.ObkvConnection;
import com.oceanbase.obkv.console.model.WorkloadParams;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * Request DTO for executing a benchmark task
 */
@Data
public class TaskRequest {

    /**
     * Workload name (workloada ~ workloadf)
     */
    @NotBlank(message = "Workload is required")
    private String workload;

    /**
     * Benchmark model type (hbase or table)
     */
    @NotBlank(message = "Model type is required")
    @Pattern(regexp = "hbase|table", message = "Model type must be 'hbase' or 'table'")
    private String modelType = "hbase";

    /**
     * Table name for the benchmark
     */
    @NotBlank(message = "Table name is required")
    private String tableName = "ycsb_test";

    /**
     * Column family for the benchmark
     */
    @NotBlank(message = "Column family is required")
    private String columnFamily = "cf";

    /**
     * Number of segments for parallel loading (optional)
     */
    private Integer segments;

    /**
     * OBKV connection configuration
     */
    @NotNull(message = "OBKV connection is required")
    @Valid
    private ObkvConnection obkvConnection;

    /**
     * Client configuration
     */
    @Valid
    private ClientConfig clientConfig = new ClientConfig();

    /**
     * Workload parameters
     */
    @Valid
    private WorkloadParams workloadParams = new WorkloadParams();
}

