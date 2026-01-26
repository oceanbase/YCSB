package com.oceanbase.obkv.console.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * SQL connection configuration for DDL operations
 */
@Data
public class SqlConnection {

    /**
     * Database server IP address
     */
    @NotBlank(message = "IP is required")
    private String ip;

    /**
     * Database server port (default: 2883)
     */
    @NotNull(message = "Port is required")
    private Integer port = 2883;

    /**
     * Database user (format: user@tenant#cluster)
     */
    @NotBlank(message = "User is required")
    private String user;

    /**
     * Database password (optional, can be empty for passwordless login)
     */
    private String password;

    /**
     * Database name
     */
    @NotBlank(message = "Database is required")
    private String database;

    /**
     * Build JDBC URL for OceanBase connection
     */
    public String buildJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                ip, port, database);
    }
}

