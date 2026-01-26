package com.oceanbase.obkv.console.service;

import com.oceanbase.obkv.console.dto.DdlResponse;
import com.oceanbase.obkv.console.model.SqlConnection;
import com.oceanbase.obkv.console.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

/**
 * Service for DDL operations (create, truncate, drop tables)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdlService {

    private final SqlService sqlService;

    /**
     * Generate CREATE TABLE SQL based on model type and partition type
     */
    public String generateCreateTableSql(TableConfig config) {
        String modelType = config.getModelType();
        
        if ("table".equalsIgnoreCase(modelType)) {
            return generateTableModelSql(config);
        } else {
            // HBase model
            String partitionType = config.getPartitionType();
            if ("key".equalsIgnoreCase(partitionType)) {
                return generateKeyPartitionTableSql(config);
            } else {
                return generateRangePartitionTableSql(config);
            }
        }
    }

    /**
     * Generate CREATE TABLE SQL for Table model
     */
    private String generateTableModelSql(TableConfig config) {
        StringBuilder sql = new StringBuilder();
        String fullTableName = config.getFullTableName();
        
        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");
        sql.append("  `ycsb_key` varbinary(1024) NOT NULL,\n");
        sql.append("  `ycsb_value` varbinary(1048576) DEFAULT NULL,\n");
        sql.append("  PRIMARY KEY (`ycsb_key`)\n");
        sql.append(")");

        String partitionType = config.getPartitionType();
        if ("key".equalsIgnoreCase(partitionType)) {
            int partitionCount = config.getKeyPartitionCount() != null ? config.getKeyPartitionCount() : 4;
            sql.append(" PARTITION BY KEY(`ycsb_key`) PARTITIONS ").append(partitionCount);
        } else {
            // Range partition by key (using the same logic as HBase range partition)
            int maxKey = config.getMaxKey();
            int partitionCount = config.getPartitionCount();
            int keyLength = config.getKeyLength();
            int step = maxKey / partitionCount;

            sql.append(" PARTITION BY RANGE COLUMNS(`ycsb_key`) (\n");
            for (int i = 0; i < partitionCount; i++) {
                int boundary = (i + 1) * step;
                String formattedKey = String.format("%0" + keyLength + "d", boundary);

                if (i == partitionCount - 1) {
                    sql.append("  PARTITION `p").append(i).append("` VALUES LESS THAN (MAXVALUE)\n");
                } else {
                    sql.append("  PARTITION `p").append(i).append("` VALUES LESS THAN ('")
                       .append(formattedKey).append("'),\n");
                }
            }
            sql.append(")");
        }

        return sql.toString();
    }

    /**
     * Generate CREATE TABLE SQL for HBase model with single-level range partition
     */
    public String generateRangePartitionTableSql(TableConfig config) {
        StringBuilder sql = new StringBuilder();
        
        String fullTableName = config.getFullTableName();
        int maxKey = config.getMaxKey();
        int partitionCount = config.getPartitionCount();
        int keyLength = config.getKeyLength();
        int step = maxKey / partitionCount;

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");
        sql.append("  `K` varbinary(1024) NOT NULL,\n");
        sql.append("  `Q` varbinary(256) NOT NULL,\n");
        sql.append("  `T` bigint(20) NOT NULL,\n");
        sql.append("  `V` varbinary(1048576) DEFAULT NULL,\n");
        sql.append("  PRIMARY KEY (`K`, `Q`, `T`)\n");
        sql.append(") PARTITION BY RANGE COLUMNS(`K`) (\n");

        for (int i = 0; i < partitionCount; i++) {
            int boundary = (i + 1) * step;
            String formattedKey = String.format("%0" + keyLength + "d", boundary);

            if (i == partitionCount - 1) {
                sql.append("  PARTITION `p").append(i).append("` VALUES LESS THAN (MAXVALUE)\n");
            } else {
                sql.append("  PARTITION `p").append(i).append("` VALUES LESS THAN ('")
                   .append(formattedKey).append("')");
                if (i < partitionCount - 1) {
                    sql.append(",");
                }
                sql.append("\n");
            }
        }

        sql.append(")");

        return sql.toString();
    }
    
    /**
     * Generate CREATE TABLE SQL for HBase model with key partition
     */
    public String generateKeyPartitionTableSql(TableConfig config) {
        StringBuilder sql = new StringBuilder();
        
        String fullTableName = config.getFullTableName();
        int partitionCount = config.getKeyPartitionCount() != null ? config.getKeyPartitionCount() : 4; // default to 4 if not specified

        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");
        sql.append("  `K` varbinary(1024) NOT NULL,\n");
        sql.append("  `Q` varbinary(256) NOT NULL,\n");
        sql.append("  `T` bigint(20) NOT NULL,\n");
        sql.append("  `V` varbinary(1048576) DEFAULT NULL,\n");
        sql.append("  PRIMARY KEY (`K`, `Q`, `T`)\n");
        sql.append(") PARTITION BY KEY(`K`) PARTITIONS ").append(partitionCount).append("\n");

        return sql.toString();
    }

    /**
     * Generate TRUNCATE TABLE SQL
     */
    public String generateTruncateTableSql(String tableName, String columnFamily, String modelType) {
        String fullTableName;
        if ("table".equalsIgnoreCase(modelType)) {
            fullTableName = String.format("`%s`", tableName);
        } else {
            fullTableName = String.format("`%s$%s`", tableName, columnFamily);
        }
        return "TRUNCATE TABLE " + fullTableName;
    }

    /**
     * Generate DROP TABLE SQL
     */
    public String generateDropTableSql(String tableName, String columnFamily, String modelType) {
        String fullTableName;
        if ("table".equalsIgnoreCase(modelType)) {
            fullTableName = String.format("`%s`", tableName);
        } else {
            fullTableName = String.format("`%s$%s`", tableName, columnFamily);
        }
        return "DROP TABLE IF EXISTS " + fullTableName;
    }

    /**
     * Set global binlog_row_image to minimal
     */
    public DdlResponse setMinimalBinlogRowImage(SqlConnection connection) {
        String sql = "SET GLOBAL binlog_row_image=minimal;";
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Successfully set global binlog_row_image=minimal in {}ms", executionTime);
            return DdlResponse.success(
                    "全局变量 binlog_row_image 已设置为 minimal",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to set binlog_row_image: {}", e.getMessage());
            return DdlResponse.error("设置 binlog_row_image 失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Set global undo_retention to 0
     */
    public DdlResponse setUndoRetention(SqlConnection connection) {
        String sql = "ALTER SYSTEM SET undo_retention=0;";
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Successfully set undo_retention=0 in {}ms", executionTime);
            return DdlResponse.success(
                    "系统变量 undo_retention 已设置为 0",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to set undo_retention: {}", e.getMessage());
            return DdlResponse.error("设置 undo_retention 失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Set global kv_group_commit_batch_size
     */
    public DdlResponse setKvGroupCommitBatchSize(SqlConnection connection, int size) {
        String sql = "ALTER SYSTEM SET kv_group_commit_batch_size=" + size + ";";
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Successfully set kv_group_commit_batch_size={} in {}ms", size, executionTime);
            return DdlResponse.success(
                    "系统变量 kv_group_commit_batch_size 已设置为 " + size,
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to set kv_group_commit_batch_size: {}", e.getMessage());
            return DdlResponse.error("设置 kv_group_commit_batch_size 失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Set global kv_group_commit_rw_mode
     */
    public DdlResponse setKvGroupCommitRwMode(SqlConnection connection, String mode) {
        String sql = "ALTER SYSTEM SET kv_group_commit_rw_mode='" + mode + "';";
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Successfully set kv_group_commit_rw_mode='{}' in {}ms", mode, executionTime);
            return DdlResponse.success(
                    "系统变量 kv_group_commit_rw_mode 已设置为 '" + mode + "'",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to set kv_group_commit_rw_mode: {}", e.getMessage());
            return DdlResponse.error("设置 kv_group_commit_rw_mode 失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Create a table
     */
    public DdlResponse createTable(SqlConnection connection, TableConfig config) {
        String sql = generateCreateTableSql(config);
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Table {} created successfully in {}ms", config.getFullTableName(), executionTime);
            return DdlResponse.success(
                    "表 " + config.getFullTableName() + " 创建成功",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to create table: {}", e.getMessage());
            return DdlResponse.error("创建表失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Truncate a table
     */
    public DdlResponse truncateTable(SqlConnection connection, String tableName, String columnFamily, String modelType) {
        String sql = generateTruncateTableSql(tableName, columnFamily, modelType);
        String fullTableName;
        if ("table".equalsIgnoreCase(modelType)) {
            fullTableName = String.format("`%s`", tableName);
        } else {
            fullTableName = String.format("`%s$%s`", tableName, columnFamily);
        }
        
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Table {} truncated successfully in {}ms", fullTableName, executionTime);
            return DdlResponse.success(
                    "表 " + fullTableName + " 已清空",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to truncate table: {}", e.getMessage());
            return DdlResponse.error("清空表失败: " + e.getMessage(), sql);
        }
    }

    /**
     * Drop a table
     */
    public DdlResponse dropTable(SqlConnection connection, String tableName, String columnFamily, String modelType) {
        String sql = generateDropTableSql(tableName, columnFamily, modelType);
        String fullTableName;
        if ("table".equalsIgnoreCase(modelType)) {
            fullTableName = String.format("`%s`", tableName);
        } else {
            fullTableName = String.format("`%s$%s`", tableName, columnFamily);
        }
        
        try {
            long executionTime = sqlService.executeStatementTimed(connection, sql);
            log.info("Table {} dropped successfully in {}ms", fullTableName, executionTime);
            return DdlResponse.success(
                    "表 " + fullTableName + " 已删除",
                    sql,
                    executionTime
            );
        } catch (SQLException e) {
            log.error("Failed to drop table: {}", e.getMessage());
            return DdlResponse.error("删除表失败: " + e.getMessage(), sql);
        }
    }
}

