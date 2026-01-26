package com.oceanbase.obkv.console.dto;

import lombok.Data;

/**
 * Response DTO for DDL operations
 */
@Data
public class DdlResponse {

    /**
     * Whether the operation was successful
     */
    private boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * The SQL statement that was executed
     */
    private String sql;

    /**
     * Execution time in milliseconds
     */
    private Long executionTime;

    /**
     * Create a successful response
     */
    public static DdlResponse success(String message, String sql, Long executionTime) {
        DdlResponse response = new DdlResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setSql(sql);
        response.setExecutionTime(executionTime);
        return response;
    }

    /**
     * Create an error response
     */
    public static DdlResponse error(String message, String sql) {
        DdlResponse response = new DdlResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setSql(sql);
        return response;
    }
}

