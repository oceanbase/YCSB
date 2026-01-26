package com.oceanbase.obkv.console.controller;

import com.oceanbase.obkv.console.dto.ApiResponse;
import com.oceanbase.obkv.console.dto.CreateTableRequest;
import com.oceanbase.obkv.console.dto.DdlResponse;
import com.oceanbase.obkv.console.dto.TableOperationRequest;
import com.oceanbase.obkv.console.model.SqlConnection;
import com.oceanbase.obkv.console.model.TableConfig;
import com.oceanbase.obkv.console.service.DdlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Controller for DDL operations
 */
@Slf4j
@RestController
@RequestMapping("/api/ddl")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DdlController {

    private final DdlService ddlService;

    /**
     * Generate CREATE TABLE SQL (preview only, does not execute)
     */
    @PostMapping("/generate-sql")
    public ApiResponse<String> generateSql(@Valid @RequestBody TableConfig config) {
        log.info("Generating SQL for table {} with maxKey={}, partitionCount={}, keyLength={}",
                config.getTableName(), config.getMaxKey(), config.getPartitionCount(), config.getKeyLength());
        
        try {
            String sql = ddlService.generateCreateTableSql(config);
            return ApiResponse.success(sql, "SQL 生成成功");
        } catch (Exception e) {
            log.error("Failed to generate SQL: {}", e.getMessage());
            return ApiResponse.error("SQL 生成失败: " + e.getMessage());
        }
    }

    /**
     * Set global binlog_row_image to minimal
     */
    @PostMapping("/minimal-binlog")
    public ApiResponse<DdlResponse> setMinimalBinlog(@Valid @RequestBody SqlConnection connection) {
        log.info("Setting global binlog_row_image=minimal");
        
        try {
            DdlResponse response = ddlService.setMinimalBinlogRowImage(connection);
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to set binlog_row_image: {}", e.getMessage());
            return ApiResponse.error("设置失败: " + e.getMessage());
        }
    }

    /**
     * Set global undo_retention=0
     */
    @PostMapping("/undo-retention")
    public ApiResponse<DdlResponse> setUndoRetention(@Valid @RequestBody SqlConnection connection) {
        log.info("Setting global undo_retention=0");
        
        try {
            DdlResponse response = ddlService.setUndoRetention(connection);
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to set undo_retention: {}", e.getMessage());
            return ApiResponse.error("设置失败: " + e.getMessage());
        }
    }

    /**
     * Set global kv_group_commit_batch_size
     */
    @PostMapping("/batch-size")
    public ApiResponse<DdlResponse> setBatchSize(@RequestBody CreateTableRequest request) {
        // Reuse CreateTableRequest as it contains SqlConnection and TableConfig (we'll use partitionCount as size or similar)
        // Or better, just use a map or a new DTO. But for simplicity let's use CreateTableRequest and use partitionCount for size.
        // Wait, CreateTableRequest has TableConfig which has partitionCount.
        int size = request.getTableConfig().getPartitionCount();
        log.info("Setting global kv_group_commit_batch_size={}", size);
        
        try {
            DdlResponse response = ddlService.setKvGroupCommitBatchSize(request.getSqlConnection(), size);
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to set batch_size: {}", e.getMessage());
            return ApiResponse.error("设置失败: " + e.getMessage());
        }
    }

    /**
     * Set global kv_group_commit_rw_mode
     */
    @PostMapping("/rw-mode")
    public ApiResponse<DdlResponse> setRwMode(@RequestBody CreateTableRequest request) {
        String mode = request.getTableConfig().getTableName();
        log.info("Setting global kv_group_commit_rw_mode={}", mode);
        
        try {
            DdlResponse response = ddlService.setKvGroupCommitRwMode(request.getSqlConnection(), mode);
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to set rw_mode: {}", e.getMessage());
            return ApiResponse.error("设置失败: " + e.getMessage());
        }
    }

    /**
     * Create a table
     */
    @PostMapping("/create-table")
    public ApiResponse<DdlResponse> createTable(@Valid @RequestBody CreateTableRequest request) {
        log.info("Creating table {} with maxKey={}, partitionCount={}, keyLength={}",
                request.getTableConfig().getTableName(),
                request.getTableConfig().getMaxKey(),
                request.getTableConfig().getPartitionCount(),
                request.getTableConfig().getKeyLength());
        
        try {
            DdlResponse response = ddlService.createTable(
                    request.getSqlConnection(),
                    request.getTableConfig()
            );
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to create table: {}", e.getMessage());
            return ApiResponse.error("建表失败: " + e.getMessage());
        }
    }

    /**
     * Truncate a table
     */
    @PostMapping("/truncate-table")
    public ApiResponse<DdlResponse> truncateTable(@Valid @RequestBody TableOperationRequest request) {
        log.info("Truncating table {}", request.getFullTableName());
        
        try {
            DdlResponse response = ddlService.truncateTable(
                    request.getSqlConnection(),
                    request.getTableName(),
                    request.getColumnFamily(),
                    request.getModelType()
            );
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to truncate table: {}", e.getMessage());
            return ApiResponse.error("清空表失败: " + e.getMessage());
        }
    }

    /**
     * Drop a table
     */
    @PostMapping("/drop-table")
    public ApiResponse<DdlResponse> dropTable(@Valid @RequestBody TableOperationRequest request) {
        log.info("Dropping table {}", request.getFullTableName());
        
        try {
            DdlResponse response = ddlService.dropTable(
                    request.getSqlConnection(),
                    request.getTableName(),
                    request.getColumnFamily(),
                    request.getModelType()
            );
            
            if (response.isSuccess()) {
                return ApiResponse.success(response, response.getMessage());
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to drop table: {}", e.getMessage());
            return ApiResponse.error("删除表失败: " + e.getMessage());
        }
    }
}

